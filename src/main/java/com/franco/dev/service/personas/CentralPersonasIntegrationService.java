package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.personas.dto.ClienteSyncRequest;
import com.franco.dev.service.personas.dto.PersonaSyncRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.function.Supplier;

@Service
public class CentralPersonasIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(CentralPersonasIntegrationService.class);

    private static final String PERSONA_ENDPOINT = "/api/personas";
    private static final String CLIENTE_ENDPOINT = "/api/personas/clientes";
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final RestTemplate restTemplate;
    private final Environment environment;
    private final JdbcTemplate jdbcTemplate;
    long esperaReplicaMs = 3000;
    long pasoReplicaMs = 150;

    public CentralPersonasIntegrationService(Environment environment, JdbcTemplate jdbcTemplate) {
        this.environment = environment;
        this.jdbcTemplate = jdbcTemplate;
        // Propio y con plazos: el PDV espera esta respuesta; el bean compartido no tiene timeouts.
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3000);
        f.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(f);
    }

    /**
     * true si la tabla ({@code personas.persona} o {@code personas.cliente}) llega por una suscripcion de esta base:
     * entonces la fila creada en el central no se escribe aca, se espera la replica. Si no se puede consultar,
     * se asume que si (escribirla a mano trabaria la suscripcion con duplicate key).
     */
    public boolean llegaPorReplica(String tabla) {
        try {
            Boolean r = jdbcTemplate.queryForObject(
                    "select exists(select 1 from pg_subscription_rel where srrelid = to_regclass(?))", Boolean.class, tabla);
            return !Boolean.FALSE.equals(r);
        } catch (Exception e) {
            log.warn("No se pudo consultar si {} esta suscripta, se espera la replica: {}", tabla, e.getMessage());
            return true;
        }
    }

    /** Busca la fila hasta que llegue por replica, con un tope de {@code esperaReplicaMs}. */
    public <T> Optional<T> esperarReplica(Supplier<Optional<T>> buscar) {
        long limite = System.currentTimeMillis() + esperaReplicaMs;
        while (true) {
            Optional<T> r = buscar.get();
            if (r.isPresent() || System.currentTimeMillis() >= limite) return r;
            try {
                Thread.sleep(pasoReplicaMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return buscar.get();
            }
        }
    }

    public Persona syncPersona(Persona persona) {
        PersonaSyncRequest request = buildPersonaRequest(persona);
        String url = buildUrl(PERSONA_ENDPOINT);
        return executePost(url, request, Persona.class);
    }

    public Cliente syncCliente(Cliente cliente) {
        ClienteSyncRequest request = buildClienteRequest(cliente);
        String url = buildUrl(CLIENTE_ENDPOINT);
        return executePost(url, request, Cliente.class);
    }

    public byte[] downloadImage(Long personaId) {
        String url = buildUrl(PERSONA_ENDPOINT + "/" + personaId + "/imagen");
        return executeGet(url, byte[].class);
    }

    /**
     * Sincroniza solo el nombre de archivo de imagen con el servidor central.
     * NO transfiere el archivo físico, solo actualiza la referencia en la BD del
     * servidor central.
     * 
     * @param personaId    ID de la persona
     * @param nombreImagen Nombre del archivo de imagen
     * @return true si la sincronización fue exitosa, false si falló
     */
    public boolean syncImageMetadata(Long personaId, String nombreImagen) {
        try {
            String url = buildUrl(PERSONA_ENDPOINT + "/" + personaId + "/imagen-metadata");
            com.franco.dev.service.personas.dto.ImagenMetadataRequest request = new com.franco.dev.service.personas.dto.ImagenMetadataRequest();
            request.setNombreImagen(nombreImagen);

            restTemplate.postForEntity(url, new HttpEntity<>(request, authHeaders()), Void.class);
            return true;
        } catch (RestClientException e) {
            return false;
        }
    }

    private PersonaSyncRequest buildPersonaRequest(Persona persona) {
        PersonaSyncRequest request = new PersonaSyncRequest();
        request.setId(persona.getId());
        request.setNombre(persona.getNombre());
        request.setApodo(persona.getApodo());
        request.setSexo(persona.getSexo());
        request.setDocumento(persona.getDocumento());
        request.setEmail(persona.getEmail());
        request.setDireccion(persona.getDireccion());
        request.setTelefono(persona.getTelefono());
        request.setSocialMedia(persona.getSocialMedia());
        request.setImagenes(persona.getImagenes());

        if (persona.getUsuario() != null) {
            request.setUsuarioId(persona.getUsuario().getId());
        }

        if (persona.getCiudad() != null) {
            request.setCiudadId(persona.getCiudad().getId());
        }

        LocalDateTime nacimiento = persona.getNacimiento();
        if (nacimiento != null) {
            request.setNacimiento(ISO_FORMATTER.format(nacimiento));
        }

        return request;
    }

    private ClienteSyncRequest buildClienteRequest(Cliente cliente) {
        ClienteSyncRequest request = new ClienteSyncRequest();
        request.setId(cliente.getId());
        request.setTipo(cliente.getTipo());
        request.setCredito(cliente.getCredito());
        request.setCodigo(cliente.getCodigo());
        request.setTributa(cliente.getTributa());
        request.setVerificadoSet(cliente.getVerificadoSet());

        if (cliente.getPersona() != null) {
            request.setPersonaId(cliente.getPersona().getId());
        }

        if (cliente.getSucursal() != null) {
            request.setSucursalId(cliente.getSucursal().getId());
        }

        Usuario usuario = cliente.getUsuario();
        if (usuario != null) {
            request.setUsuarioId(usuario.getId());
        }

        return request;
    }

    private String buildUrl(String endpoint) {
        String host = environment.getProperty("ipServidorCentral");
        if (!StringUtils.hasText(host)) {
            throw new IllegalStateException("Propiedad 'ipServidorCentral' no configurada");
        }
        String baseUrl = host.startsWith("http") ? host : "http://" + host;
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String fullUrl = baseUrl + endpoint;
        org.slf4j.LoggerFactory.getLogger(CentralPersonasIntegrationService.class)
                .debug("Construyendo URL para servidor central: {}", fullUrl);
        return fullUrl;
    }

    private <T, R> R executePost(String url, T body, Class<R> responseType) {
        log.debug("Intentando sincronizar con servidor central. URL: {}, Tipo: {}", url, responseType.getSimpleName());

        try {
            ResponseEntity<R> response = restTemplate.postForEntity(url, new HttpEntity<>(body, authHeaders()), responseType);
            if (response.getBody() == null) {
                log.error("Respuesta vacía del servidor central. URL: {}", url);
                throw new IllegalStateException("Respuesta vacia al sincronizar con el servidor central");
            }
            log.debug("Sincronización exitosa con servidor central. URL: {}", url);
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.FORBIDDEN) {
                log.error("El servidor central rechazo la sincronizacion ({}). URL: {}. Si es 401, revisar que la clave JWT "
                        + "del filial sea la misma que acepta el central.", e.getStatusCode().value(), url);
            }
            throw new IllegalStateException("Error la conectar con el servidor central: " + e.getMessage(), e);
        } catch (RestClientException e) {
            throw new IllegalStateException("Error la conectar con el servidor central: " + e.getMessage(), e);
        }
    }

    private <R> R executeGet(String url, Class<R> responseType) {
        log.debug("Intentando descargar de servidor central. URL: {}", url);

        try {
            ResponseEntity<R> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(authHeaders()), responseType);
            if (response.getBody() == null) {
                log.warn("Respuesta vacía al descargar imagen. URL: {}", url);
                return null;
            }
            log.debug("Descarga exitosa de servidor central. URL: {}", url);
            return response.getBody();
        } catch (RestClientException e) {
            // Servidor central no disponible es una situación esperada en modo offline
            return null;
        }
    }

    /** Reenvia el Authorization del request en curso; sin request (hilo propio) no manda ninguno. */
    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            String auth = attributes.getRequest().getHeader("Authorization");
            if (auth != null) headers.set("Authorization", auth);
        }
        return headers;
    }
}
