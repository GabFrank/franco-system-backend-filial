package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.enums.TipoCliente;
import com.franco.dev.dto.factura.ClienteFacturaDTO;
import com.franco.dev.repository.personas.ClienteRepository;
import com.franco.dev.repository.personas.PersonaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Materializa en esta filial un cliente que el servidor central mando junto con una
 * factura.
 * <p>
 * Hace falta porque personas y clientes no llegan por replicacion logica a todas las
 * filiales: al facturar desde el central contra un cliente que esta filial no conoce, la
 * factura terminaba guardada con {@code cliente_id} null, sin error ni log.
 * <p>
 * Escribe por repositorio a proposito, sin pasar por {@link PersonaService#save}: ese
 * camino sincroniza toda Persona nueva contra el central y falla si no lo consigue, algo
 * que aca seria a la vez redundante e imposible — el dato ya viene DEL central, y esa
 * llamada no tiene autenticacion configurada (ver GabFrank/franco-system-backend-filial#107).
 * El central es el dueño del cliente; la filial solo guarda una copia local, que es lo
 * mismo que ya hace {@code PersonaService} con las personas que sincroniza.
 * <p>
 * Los ids son los del central y se respetan tal cual, para que la fila coincida si mas
 * tarde la misma llega por replicacion en vez de duplicarse.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClienteDelCentralService {

    private final ClienteRepository clienteRepository;
    private final PersonaRepository personaRepository;

    /**
     * Devuelve el cliente local, creandolo a partir de los datos del central si todavia
     * no existe.
     *
     * @param clienteId id del cliente segun el central
     * @param datos     copia enviada por el central; puede ser null si el central no la mando
     * @return el cliente local, o vacio si no existe y no hay datos para crearlo
     */
    @Transactional
    public Optional<Cliente> resolverOMaterializar(Long clienteId, ClienteFacturaDTO datos) {
        if (clienteId == null) {
            return Optional.empty();
        }

        Optional<Cliente> local = clienteRepository.findById(clienteId);
        if (local.isPresent()) {
            return local;
        }

        if (datos == null) {
            return Optional.empty();
        }

        Persona persona = materializarPersona(datos);

        Cliente cliente = new Cliente();
        cliente.setId(clienteId);
        cliente.setTipo(parsearTipo(datos.getTipo()));
        cliente.setCredito(datos.getCredito());
        cliente.setCodigo(datos.getCodigo());
        cliente.setTributa(datos.getTributa());
        cliente.setVerificadoSet(datos.getVerificadoSet());
        cliente.setActivo(datos.getActivo() != null ? datos.getActivo() : true);
        cliente.setPersona(persona);
        cliente.setCreadoEn(LocalDateTime.now());

        Cliente guardado = clienteRepository.save(cliente);
        log.info("Cliente {} materializado desde el central para poder facturar", clienteId);
        return Optional.of(guardado);
    }

    /**
     * La persona puede existir aunque el cliente no: se reutiliza en vez de duplicarla.
     */
    private Persona materializarPersona(ClienteFacturaDTO datos) {
        if (datos.getPersonaId() == null) {
            return null;
        }

        Optional<Persona> existente = personaRepository.findById(datos.getPersonaId());
        if (existente.isPresent()) {
            return existente.get();
        }

        Persona persona = new Persona();
        persona.setId(datos.getPersonaId());
        persona.setNombre(datos.getPersonaNombre());
        persona.setApodo(datos.getPersonaApodo());
        persona.setDocumento(datos.getPersonaDocumento());
        persona.setSexo(datos.getPersonaSexo());
        persona.setDireccion(datos.getPersonaDireccion());
        persona.setTelefono(datos.getPersonaTelefono());
        persona.setEmail(datos.getPersonaEmail());
        persona.setCreadoEn(LocalDateTime.now());

        return personaRepository.save(persona);
    }

    /**
     * Un tipo desconocido no justifica rechazar la factura: se cae a NORMAL, que es el
     * valor por defecto del sistema.
     */
    private TipoCliente parsearTipo(String tipo) {
        if (tipo == null || tipo.trim().isEmpty()) {
            return TipoCliente.NORMAL;
        }
        try {
            return TipoCliente.valueOf(tipo.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Tipo de cliente '{}' desconocido en esta filial; se usa NORMAL", tipo);
            return TipoCliente.NORMAL;
        }
    }
}
