package com.franco.dev.graphql.personas;

import com.franco.dev.domain.general.Contacto;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.graphql.personas.input.ClienteInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.general.ContactoService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.sifen.dto.response.ConsultaRucResponse;
import com.franco.dev.service.sifen.service.SifenService;

import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import graphql.GraphQLException;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class ClienteGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private static final Logger log = LoggerFactory.getLogger(ClienteGraphQL.class);

    @Autowired
    private ClienteService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PersonaService personaService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private ContactoService contactoService;

    @Autowired
    private SifenService sifenService;

    public Optional<Cliente> cliente(Long id) {
        return service.findById(id);
    }

    public List<Cliente> clientePorTelefono(String texto) {
        List<Contacto> contactoList = contactoService.findByTelefonoOrNombre(texto);
        List<Cliente> clienteList = new ArrayList<>();
        for (Contacto c : contactoList) {
            clienteList.add(service.findByPersonaId(c.getPersona().getId()));
        }
        return clienteList;
    }

    public List<Cliente> clientes(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<Cliente> clientePorPersona(String texto) {
        return service.findByAll(texto);
    }

    public Cliente clientePorPersonaDocumento(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return null;
        }

        String documento = texto.trim();
        Cliente cliente = service.findByPersonaDocumento(documento);
        if (cliente == null) {
            String documentoNormalizado = documento.replaceAll("[^0-9]", "");
            if (!documentoNormalizado.isEmpty() && !documentoNormalizado.equals(documento)) {
                cliente = service.findByPersonaDocumento(documentoNormalizado);
            }
        }

        if (cliente != null) {
            if (Boolean.TRUE.equals(cliente.getVerificadoSet())) {
                return cliente;
            }
            return actualizarClienteDesdeSifen(cliente, documento);
        }

        return crearClienteDesdeSifen(documento);
    }

    public Cliente saveCliente(ClienteInput input) {
        Boolean modifPersona = false;
        ModelMapper m = new ModelMapper();
        Cliente e = m.map(input, Cliente.class);
        if (input.getUsuarioId() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getSucursalId() != null) e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        if (input.getPersonaId() != null) e.setPersona(personaService.findById(input.getPersonaId()).orElse(null));
        if (e.getPersona() == null) {
            Persona newPersona = new Persona();
            newPersona.setNombre(input.getNombre());
            newPersona.setDireccion(input.getDireccion());
            newPersona.setDocumento(input.getDocumento());
            if (e.getUsuario() != null) newPersona.setUsuario(e.getUsuario());
            newPersona.setCreadoEn(LocalDateTime.now());
            newPersona = personaService.save(newPersona);
            e.setPersona(newPersona);
        }

        if (input.getDireccion() != null && !input.getDireccion().equals(e.getPersona().getDireccion())) {
            e.getPersona().setDireccion(input.getDireccion());
            modifPersona = true;
        }
        if (input.getNombre() != null && !input.getNombre().equals(e.getPersona().getNombre())) {
            e.getPersona().setNombre(input.getNombre());
            modifPersona = true;
        }
        if (modifPersona) {
            personaService.save(e.getPersona());
        }
        return service.save(e);
    }

    public Boolean deleteCliente(Long id) {
        return service.deleteById(id);
    }

    public Long countCliente() {
        return service.count();
    }

    public Cliente clientePorPersonaId(Long id) {
        return service.findByPersonaId(id);
    }

    public Cliente clientePorPersonaIdFromServer(Long id) {
//        return propagacionService.solicitarCliente(id);
        return null;
    }

    public ConsultaRucResponse consultaRuc(String ruc) {
        ConsultaRucResponse respuesta = ejecutarConsultaRuc(ruc);
        if (respuesta == null) {
            return null;
        }
        validarContribuyente(respuesta);
        return respuesta;
    }

    private Cliente actualizarClienteDesdeSifen(Cliente cliente, String documento) {
        if (!sifenService.isSifenEnabled()) {
            return cliente;
        }

        ConsultaRucResponse respuesta = ejecutarConsultaRuc(documento);
        if (respuesta == null) {
            // Si SIFEN está habilitado pero la consulta retorna null, puede ser error de conexión
            log.warn("No se pudo obtener respuesta de SIFEN para documento: {}", documento);
            return cliente; // Retornar cliente sin actualizar
        }

        validarContribuyente(respuesta);

        Persona persona = cliente.getPersona();
        if (persona == null) {
            persona = buscarPersonaPorDocumento(documento);
        }

        // Intentar sincronizar persona, pero no fallar si no se puede guardar en servidor central
        try {
            persona = sincronizarPersonaDesdeSifen(persona, documento, respuesta);
            cliente.setPersona(persona);
        } catch (Exception e) {
            log.warn("No se pudo sincronizar persona con servidor central para documento {}: {}", documento, e.getMessage());
            // Continuar con la información de SIFEN aunque no se haya guardado
            // Crear persona en memoria (sin guardar) con información de SIFEN
            if (persona == null) {
                persona = new Persona();
                persona.setDocumento(documento);
            }
            // Actualizar datos localmente sin guardar (solo en memoria)
            String nombreProcesado = formatearRazonSocial(respuesta.getRazonSocial());
            if (nombreProcesado != null && !nombreProcesado.isEmpty()) {
                persona.setNombre(nombreProcesado);
            }
            if (respuesta.getDireccion() != null && !respuesta.getDireccion().trim().isEmpty()) {
                persona.setDireccion(respuesta.getDireccion());
            }
            cliente.setPersona(persona);
            // NOTA: La persona no tiene ID porque no se pudo guardar en el servidor central
            // El cliente se retornará con esta información pero no estará persistido
        }

        cliente.setVerificadoSet(true);
        cliente.setTributa(estadoContribuyenteActivo(respuesta));

        Integer tipoContribuyente = parseTipoContribuyente(respuesta.getCodigoEstadoContribuyente());
        if (tipoContribuyente != null) {
            cliente.setTipoContribuyente(tipoContribuyente);
        }

        // Intentar guardar cliente, pero no fallar si no se puede guardar en servidor central
        try {
            return service.save(cliente);
        } catch (Exception e) {
            log.warn("No se pudo guardar cliente en servidor central para documento {}: {}", documento, e.getMessage());
            // Retornar cliente con información actualizada aunque no se haya guardado
            return cliente;
        }
    }

    private Cliente crearClienteDesdeSifen(String documento) {
        if (!sifenService.isSifenEnabled()) {
            return null;
        }

        ConsultaRucResponse respuesta = ejecutarConsultaRuc(documento);
        if (respuesta == null) {
            // Si SIFEN está habilitado pero la consulta retorna null, puede ser error de conexión
            log.warn("No se pudo obtener respuesta de SIFEN para documento: {}", documento);
            return null; // No se puede crear cliente sin información de SIFEN
        }

        validarContribuyente(respuesta);

        Persona persona = buscarPersonaPorDocumento(documento);
        
        // Intentar sincronizar persona, pero no fallar si no se puede guardar en servidor central
        try {
            persona = sincronizarPersonaDesdeSifen(persona, documento, respuesta);
        } catch (Exception e) {
            log.warn("No se pudo sincronizar persona con servidor central para documento {}: {}", documento, e.getMessage());
            // Crear persona en memoria (sin guardar) con información de SIFEN
            if (persona == null) {
                persona = new Persona();
                persona.setDocumento(documento);
            }
            // Actualizar datos localmente sin guardar (solo en memoria)
            String nombreProcesado = formatearRazonSocial(respuesta.getRazonSocial());
            if (nombreProcesado != null && !nombreProcesado.isEmpty()) {
                persona.setNombre(nombreProcesado);
            }
            if (respuesta.getDireccion() != null && !respuesta.getDireccion().trim().isEmpty()) {
                persona.setDireccion(respuesta.getDireccion());
            }
            // NOTA: La persona no tiene ID porque no se pudo guardar en el servidor central
        }

        Cliente nuevoCliente = new Cliente();
        if (persona.getId() != null) {
            nuevoCliente.setId(persona.getId());
        }
        nuevoCliente.setPersona(persona);
        nuevoCliente.setVerificadoSet(true);
        nuevoCliente.setTributa(estadoContribuyenteActivo(respuesta));
        nuevoCliente.setCreadoEn(LocalDateTime.now());

        Integer tipoContribuyente = parseTipoContribuyente(respuesta.getCodigoEstadoContribuyente());
        nuevoCliente.setTipoContribuyente(tipoContribuyente);

        // Intentar guardar cliente, pero no fallar si no se puede guardar en servidor central
        try {
            return service.save(nuevoCliente);
        } catch (Exception e) {
            log.warn("No se pudo guardar cliente en servidor central para documento {}: {}", documento, e.getMessage());
            // Retornar cliente con información de SIFEN aunque no se haya guardado
            // NOTA: Este cliente no tendrá ID persistido, pero tiene la información de SIFEN
            return nuevoCliente;
        }
    }

    private Persona sincronizarPersonaDesdeSifen(Persona persona, String documento, ConsultaRucResponse respuesta) {
        if (persona == null) {
            persona = new Persona();
            persona.setDocumento(documento);
        } else if (persona.getDocumento() == null || persona.getDocumento().trim().isEmpty()) {
            persona.setDocumento(documento);
        }

        String nombreProcesado = formatearRazonSocial(respuesta.getRazonSocial());
        if (nombreProcesado != null && !nombreProcesado.isEmpty()) {
            persona.setNombre(nombreProcesado);
        }

        if (respuesta.getDireccion() != null && !respuesta.getDireccion().trim().isEmpty()) {
            persona.setDireccion(respuesta.getDireccion());
        }

        return personaService.save(persona);
    }

    private ConsultaRucResponse ejecutarConsultaRuc(String documento) {
        if (documento == null || documento.trim().isEmpty()) {
            return null;
        }

        if (!sifenService.isSifenEnabled()) {
            return null;
        }

        String documentoNormalizado = documento.replaceAll("[^0-9]", "");
        if (documentoNormalizado.isEmpty()) {
            documentoNormalizado = documento.trim();
        }

        ConsultaRucResponse respuesta = sifenService.consultaRuc(documentoNormalizado);
        log.info("Respuesta SIFEN para RUC {}: {}", documentoNormalizado, respuesta);
        return respuesta;
    }

    private void validarContribuyente(ConsultaRucResponse respuesta) {
        boolean sinDatos = respuesta.getRuc() == null || respuesta.getRuc().trim().isEmpty();
        if (sinDatos) {
            throw new GraphQLException("El cliente no es contribuyente de SET");
        }
    }

    private Boolean estadoContribuyenteActivo(ConsultaRucResponse respuesta) {
        if (respuesta.getEstadoContribuyente() == null) {
            return null;
        }
        return "ACTIVO".equalsIgnoreCase(respuesta.getEstadoContribuyente());
    }

    private Integer parseTipoContribuyente(String codigo) {
        if (codigo == null || codigo.trim().isEmpty()) {
            return null;
        }

        try {
            return Integer.parseInt(codigo.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Persona buscarPersonaPorDocumento(String documento) {
        if (documento == null || documento.trim().isEmpty()) {
            return null;
        }

        String documentoTrim = documento.trim();
        Persona persona = personaService.findByDocumento(documentoTrim);
        if (persona != null) {
            return persona;
        }

        String documentoNormalizado = documentoTrim.replaceAll("[^0-9]", "");
        if (!documentoNormalizado.isEmpty() && !documentoNormalizado.equals(documentoTrim)) {
            return personaService.findByDocumento(documentoNormalizado);
        }

        return null;
    }

    private String formatearRazonSocial(String razonSocial) {
        if (razonSocial == null) {
            return null;
        }

        String valor = razonSocial.trim();
        if (valor.isEmpty()) {
            return null;
        }

        int indiceComa = valor.indexOf(',');
        if (indiceComa < 0) {
            return valor;
        }

        String apellido = valor.substring(0, indiceComa).trim();
        String nombres = valor.substring(indiceComa + 1).trim();

        if (nombres.isEmpty()) {
            return valor.replace(",", " ").trim();
        }

        if (apellido.isEmpty()) {
            return nombres;
        }

        return nombres + " " + apellido;
    }

}
