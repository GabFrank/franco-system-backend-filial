package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.personas.dto.ClienteSyncRequest;
import com.franco.dev.service.personas.dto.PersonaSyncRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class CentralPersonasIntegrationService {

    private static final String PERSONA_ENDPOINT = "/api/personas";
    private static final String CLIENTE_ENDPOINT = "/api/personas/clientes";
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final RestTemplate restTemplate;
    private final Environment environment;

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
        // Log para debugging
        org.slf4j.LoggerFactory.getLogger(CentralPersonasIntegrationService.class)
                .debug("Construyendo URL para servidor central: {}", fullUrl);
        return fullUrl;
    }

    private <T, R> R executePost(String url, T body, Class<R> responseType) {
        org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CentralPersonasIntegrationService.class);
        log.debug("Intentando sincronizar con servidor central. URL: {}, Tipo: {}", url, responseType.getSimpleName());

        try {
            ResponseEntity<R> response = restTemplate.postForEntity(url, body, responseType);
            if (response.getBody() == null) {
                log.error("Respuesta vacía del servidor central. URL: {}", url);
                throw new IllegalStateException("Respuesta vacia al sincronizar con el servidor central");
            }
            log.debug("Sincronización exitosa con servidor central. URL: {}", url);
            return response.getBody();
        } catch (RestClientException e) {
            throw new IllegalStateException("Error la conectar con el servidor central: " + e.getMessage(), e);
        }
    }

    private <R> R executeGet(String url, Class<R> responseType) {
        org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CentralPersonasIntegrationService.class);
        log.debug("Intentando descargar de servidor central. URL: {}", url);

        try {
            ResponseEntity<R> response = restTemplate.getForEntity(url, responseType);
            if (response.getBody() == null) {
                log.warn("Respuesta vacía al descargar imagen. URL: {}", url);
                return null;
            }
            log.debug("Descarga exitosa de servidor central. URL: {}", url);
            return response.getBody();
        } catch (RestClientException e) {
            log.error("Error al descargar de servidor central. URL: {}, Error: {}", url, e.getMessage());
            return null;
        }
    }
}
