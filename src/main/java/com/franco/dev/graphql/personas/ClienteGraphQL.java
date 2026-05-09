package com.franco.dev.graphql.personas;

import com.franco.dev.domain.general.Contacto;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.graphql.personas.dto.ClienteDatosBasicos;
import com.franco.dev.graphql.personas.dto.ClienteResponse;
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
import java.util.Collections;
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

    /**
     * Método legacy - Mantiene compatibilidad con frontend antiguo
     * Retorna Cliente solo si tiene ID válido (fue guardado exitosamente)
     * Retorna null si no se pudo guardar (para evitar error de GraphQL con ID null)
     */
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
            try {
                Cliente clienteActualizado = actualizarClienteDesdeSifen(cliente, documento);
                // Solo retornar si tiene ID válido
                if (clienteActualizado != null && clienteActualizado.getId() != null) {
                    return clienteActualizado;
                }
                // Si no tiene ID, retornar null (no se pudo guardar)
                return null;
            } catch (Exception e) {
                log.warn("Error al actualizar cliente desde SIFEN para documento {}: {}", documento, e.getMessage());
                // Retornar cliente existente solo si tiene ID
                if (cliente.getId() != null) {
                    return cliente;
                }
                return null;
            }
        }

        try {
            Cliente nuevoCliente = crearClienteDesdeSifen(documento);
            // Solo retornar si tiene ID válido
            if (nuevoCliente != null && nuevoCliente.getId() != null) {
                return nuevoCliente;
            }
            // Si no tiene ID, retornar null (no se pudo guardar)
            return null;
        } catch (GraphQLException e) {
            // Si es un error de validación (ej: no es contribuyente), propagar la excepción
            throw e;
        } catch (Exception e) {
            log.error("Error crítico al crear cliente desde SIFEN para documento {}: {}", documento, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Método nuevo - Retorna ClienteResponse con información detallada
     * Incluye cliente (si fue guardado), datosBasicos (si no se pudo guardar),
     * warnings y errores para mejor manejo en el frontend
     */
    public ClienteResponse clientePorPersonaDocumentoDetallado(String texto) {
        ClienteResponse.ClienteResponseBuilder responseBuilder = ClienteResponse.builder()
                .warnings(new ArrayList<>())
                .errores(new ArrayList<>());

        if (texto == null || texto.trim().isEmpty()) {
            responseBuilder.exito(false)
                    .errores(Collections.singletonList("El documento no puede estar vacío"));
            return responseBuilder.build();
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
                // Cliente existente y verificado, retornar directamente
                responseBuilder.cliente(cliente)
                        .exito(true);
                return responseBuilder.build();
            }
            // Cliente existente pero no verificado, actualizar desde SIFEN
            return actualizarClienteDesdeSifenConResponse(cliente, documento);
        }

        // Cliente no existe, crear desde SIFEN
        return crearClienteDesdeSifenConResponse(documento);
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
        } catch (IllegalStateException e) {
            // Capturar específicamente errores de sincronización con servidor central
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
        } catch (Exception e) {
            // Capturar cualquier otra excepción inesperada
            log.warn("Error inesperado al sincronizar persona con servidor central para documento {}: {}", documento, e.getMessage());
            // Continuar con la información de SIFEN aunque no se haya guardado
            if (persona == null) {
                persona = new Persona();
                persona.setDocumento(documento);
            }
            String nombreProcesado = formatearRazonSocial(respuesta.getRazonSocial());
            if (nombreProcesado != null && !nombreProcesado.isEmpty()) {
                persona.setNombre(nombreProcesado);
            }
            if (respuesta.getDireccion() != null && !respuesta.getDireccion().trim().isEmpty()) {
                persona.setDireccion(respuesta.getDireccion());
            }
            cliente.setPersona(persona);
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
        } catch (IllegalStateException e) {
            // Capturar específicamente errores de sincronización con servidor central
            log.warn("No se pudo guardar cliente en servidor central para documento {}: {}", documento, e.getMessage());
            // Retornar cliente con información actualizada aunque no se haya guardado
            return cliente;
        } catch (Exception e) {
            // Capturar cualquier otra excepción inesperada
            log.warn("Error inesperado al guardar cliente en servidor central para documento {}: {}", documento, e.getMessage());
            // Retornar cliente con información actualizada aunque no se haya guardado
            return cliente;
        }
    }

    private Cliente crearClienteDesdeSifen(String documento) {
        if (!sifenService.isSifenEnabled()) {
            return null;
        }

        ConsultaRucResponse respuesta = null;
        try {
            respuesta = ejecutarConsultaRuc(documento);
            if (respuesta == null) {
                // Si SIFEN está habilitado pero la consulta retorna null, puede ser error de conexión
                log.warn("No se pudo obtener respuesta de SIFEN para documento: {}", documento);
                return null; // No se puede crear cliente sin información de SIFEN
            }

            validarContribuyente(respuesta);
        } catch (GraphQLException e) {
            // Si el cliente no es contribuyente, propagar la excepción
            throw e;
        } catch (Exception e) {
            log.error("Error al consultar SIFEN para documento {}: {}", documento, e.getMessage(), e);
            return null;
        }

        Persona persona = buscarPersonaPorDocumento(documento);
        
        // Intentar sincronizar persona, pero no fallar si no se puede guardar en servidor central
        try {
            persona = sincronizarPersonaDesdeSifen(persona, documento, respuesta);
        } catch (IllegalStateException e) {
            // Capturar específicamente errores de sincronización con servidor central
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
        } catch (Exception e) {
            // Capturar cualquier otra excepción inesperada
            log.warn("Error inesperado al sincronizar persona con servidor central para documento {}: {}", documento, e.getMessage());
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
        }

        // Crear cliente con la información de SIFEN
        Cliente nuevoCliente = null;
        try {
            nuevoCliente = new Cliente();
            if (persona.getId() != null) {
                nuevoCliente.setId(persona.getId());
            }
            nuevoCliente.setPersona(persona);
            nuevoCliente.setVerificadoSet(true);
            nuevoCliente.setTributa(estadoContribuyenteActivo(respuesta));
            nuevoCliente.setCreadoEn(LocalDateTime.now());

            Integer tipoContribuyente = parseTipoContribuyente(respuesta.getCodigoEstadoContribuyente());
            nuevoCliente.setTipoContribuyente(tipoContribuyente);
        } catch (Exception e) {
            log.error("Error al crear objeto Cliente para documento {}: {}", documento, e.getMessage(), e);
            // Si falla la creación del objeto, retornar null
            return null;
        }

        // Intentar guardar cliente, pero no fallar si no se puede guardar en servidor central
        try {
            return service.save(nuevoCliente);
        } catch (IllegalStateException e) {
            // Capturar específicamente errores de sincronización con servidor central
            log.warn("No se pudo guardar cliente en servidor central para documento {}: {}", documento, e.getMessage());
            // Retornar cliente con información de SIFEN aunque no se haya guardado
            // NOTA: Este cliente no tendrá ID persistido, pero tiene la información de SIFEN
            return nuevoCliente;
        } catch (Exception e) {
            // Capturar cualquier otra excepción inesperada
            log.warn("Error inesperado al guardar cliente en servidor central para documento {}: {}", documento, e.getMessage());
            // Retornar cliente con información de SIFEN aunque no se haya guardado
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

    private ClienteResponse actualizarClienteDesdeSifenConResponse(Cliente cliente, String documento) {
        ClienteResponse.ClienteResponseBuilder responseBuilder = ClienteResponse.builder()
                .warnings(new ArrayList<>())
                .errores(new ArrayList<>());

        if (!sifenService.isSifenEnabled()) {
            responseBuilder.cliente(cliente)
                    .exito(true)
                    .warnings(Collections.singletonList("SIFEN no está habilitado, se retorna el cliente existente"));
            return responseBuilder.build();
        }

        ConsultaRucResponse respuesta = ejecutarConsultaRuc(documento);
        if (respuesta == null) {
            responseBuilder.cliente(cliente)
                    .exito(false)
                    .warnings(Collections.singletonList("No se pudo obtener respuesta de SIFEN, se retorna el cliente existente"));
            return responseBuilder.build();
        }

        try {
            validarContribuyente(respuesta);
        } catch (GraphQLException e) {
            responseBuilder.exito(false)
                    .errores(Collections.singletonList(e.getMessage()));
            return responseBuilder.build();
        }

        Persona persona = cliente.getPersona();
        if (persona == null) {
            persona = buscarPersonaPorDocumento(documento);
        }

        boolean errorSincronizacion = false;
        // Intentar sincronizar persona, pero no fallar si no se puede guardar en servidor central
        try {
            persona = sincronizarPersonaDesdeSifen(persona, documento, respuesta);
            cliente.setPersona(persona);
        } catch (IllegalStateException e) {
            errorSincronizacion = true;
            responseBuilder.warnings(Collections.singletonList("No se pudo sincronizar persona con servidor central: " + e.getMessage()));
            // Crear persona en memoria (sin guardar) con información de SIFEN
            if (persona == null) {
                persona = new Persona();
                persona.setDocumento(documento);
            }
            String nombreProcesado = formatearRazonSocial(respuesta.getRazonSocial());
            if (nombreProcesado != null && !nombreProcesado.isEmpty()) {
                persona.setNombre(nombreProcesado);
            }
            if (respuesta.getDireccion() != null && !respuesta.getDireccion().trim().isEmpty()) {
                persona.setDireccion(respuesta.getDireccion());
            }
            cliente.setPersona(persona);
        } catch (Exception e) {
            errorSincronizacion = true;
            responseBuilder.warnings(Collections.singletonList("Error inesperado al sincronizar persona: " + e.getMessage()));
            if (persona == null) {
                persona = new Persona();
                persona.setDocumento(documento);
            }
            String nombreProcesado = formatearRazonSocial(respuesta.getRazonSocial());
            if (nombreProcesado != null && !nombreProcesado.isEmpty()) {
                persona.setNombre(nombreProcesado);
            }
            if (respuesta.getDireccion() != null && !respuesta.getDireccion().trim().isEmpty()) {
                persona.setDireccion(respuesta.getDireccion());
            }
            cliente.setPersona(persona);
        }

        cliente.setVerificadoSet(true);
        cliente.setTributa(estadoContribuyenteActivo(respuesta));

        Integer tipoContribuyente = parseTipoContribuyente(respuesta.getCodigoEstadoContribuyente());
        if (tipoContribuyente != null) {
            cliente.setTipoContribuyente(tipoContribuyente);
        }

        // Intentar guardar cliente, pero no fallar si no se puede guardar en servidor central
        try {
            Cliente clienteGuardado = service.save(cliente);
            responseBuilder.cliente(clienteGuardado)
                    .exito(true);
            if (errorSincronizacion) {
                responseBuilder.warnings(Collections.singletonList("Cliente actualizado pero la persona no se sincronizó con el servidor central"));
            }
            return responseBuilder.build();
        } catch (IllegalStateException e) {
            responseBuilder.warnings(Collections.singletonList("No se pudo guardar cliente en servidor central: " + e.getMessage()));
            ClienteDatosBasicos datosBasicos = construirDatosBasicos(respuesta);
            responseBuilder.datosBasicos(datosBasicos)
                    .exito(false);
            return responseBuilder.build();
        } catch (Exception e) {
            responseBuilder.warnings(Collections.singletonList("Error inesperado al guardar cliente: " + e.getMessage()));
            ClienteDatosBasicos datosBasicos = construirDatosBasicos(respuesta);
            responseBuilder.datosBasicos(datosBasicos)
                    .exito(false);
            return responseBuilder.build();
        }
    }

    private ClienteResponse crearClienteDesdeSifenConResponse(String documento) {
        ClienteResponse.ClienteResponseBuilder responseBuilder = ClienteResponse.builder()
                .warnings(new ArrayList<>())
                .errores(new ArrayList<>());

        if (!sifenService.isSifenEnabled()) {
            responseBuilder.exito(false)
                    .warnings(Collections.singletonList("SIFEN no está habilitado, puede continuar con la carga manual"));
            return responseBuilder.build();
        }

        ConsultaRucResponse respuesta = null;
        try {
            respuesta = ejecutarConsultaRuc(documento);
            if (respuesta == null) {
                responseBuilder.exito(false)
                        .errores(Collections.singletonList("No se pudo obtener respuesta de SIFEN para el documento: " + documento));
                return responseBuilder.build();
            }

            validarContribuyente(respuesta);
        } catch (GraphQLException e) {
            responseBuilder.exito(false)
                    .errores(Collections.singletonList(e.getMessage()));
            return responseBuilder.build();
        } catch (Exception e) {
            responseBuilder.exito(false)
                    .errores(Collections.singletonList("Error al consultar SIFEN: " + e.getMessage()));
            return responseBuilder.build();
        }

        Persona persona = buscarPersonaPorDocumento(documento);
        boolean errorSincronizacion = false;

        // Intentar sincronizar persona, pero no fallar si no se puede guardar en servidor central
        try {
            persona = sincronizarPersonaDesdeSifen(persona, documento, respuesta);
        } catch (IllegalStateException e) {
            errorSincronizacion = true;
            responseBuilder.warnings(Collections.singletonList("No se pudo sincronizar persona con servidor central: " + e.getMessage()));
            if (persona == null) {
                persona = new Persona();
                persona.setDocumento(documento);
            }
            String nombreProcesado = formatearRazonSocial(respuesta.getRazonSocial());
            if (nombreProcesado != null && !nombreProcesado.isEmpty()) {
                persona.setNombre(nombreProcesado);
            }
            if (respuesta.getDireccion() != null && !respuesta.getDireccion().trim().isEmpty()) {
                persona.setDireccion(respuesta.getDireccion());
            }
        } catch (Exception e) {
            errorSincronizacion = true;
            responseBuilder.warnings(Collections.singletonList("Error inesperado al sincronizar persona: " + e.getMessage()));
            if (persona == null) {
                persona = new Persona();
                persona.setDocumento(documento);
            }
            String nombreProcesado = formatearRazonSocial(respuesta.getRazonSocial());
            if (nombreProcesado != null && !nombreProcesado.isEmpty()) {
                persona.setNombre(nombreProcesado);
            }
            if (respuesta.getDireccion() != null && !respuesta.getDireccion().trim().isEmpty()) {
                persona.setDireccion(respuesta.getDireccion());
            }
        }

        // Crear cliente con la información de SIFEN
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
            Cliente clienteGuardado = service.save(nuevoCliente);
            responseBuilder.cliente(clienteGuardado)
                    .exito(true);
            if (errorSincronizacion) {
                responseBuilder.warnings(Collections.singletonList("Cliente creado pero la persona no se sincronizó con el servidor central"));
            }
            return responseBuilder.build();
        } catch (IllegalStateException e) {
            responseBuilder.warnings(Collections.singletonList("No se pudo guardar cliente en servidor central: " + e.getMessage()));
            ClienteDatosBasicos datosBasicos = construirDatosBasicos(respuesta);
            responseBuilder.datosBasicos(datosBasicos)
                    .exito(false);
            return responseBuilder.build();
        } catch (Exception e) {
            responseBuilder.warnings(Collections.singletonList("Error inesperado al guardar cliente: " + e.getMessage()));
            ClienteDatosBasicos datosBasicos = construirDatosBasicos(respuesta);
            responseBuilder.datosBasicos(datosBasicos)
                    .exito(false);
            return responseBuilder.build();
        }
    }

    private ClienteDatosBasicos construirDatosBasicos(ConsultaRucResponse respuesta) {
        if (respuesta == null) {
            return null;
        }

        return ClienteDatosBasicos.builder()
                .ruc(respuesta.getRuc())
                .razonSocial(respuesta.getRazonSocial())
                .direccion(respuesta.getDireccion())
                .estado(respuesta.getEstado())
                .estadoContribuyente(respuesta.getEstadoContribuyente())
                .tributa(estadoContribuyenteActivo(respuesta))
                .tipoContribuyente(parseTipoContribuyente(respuesta.getCodigoEstadoContribuyente()))
                .telefono(respuesta.getTelefono())
                .nombreFantasia(respuesta.getNombreFantasia())
                .dv(respuesta.getDv())
                .build();
    }

}
