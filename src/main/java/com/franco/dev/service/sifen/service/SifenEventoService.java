package com.franco.dev.service.sifen.service;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.EventoCancelacionDE;
import com.franco.dev.domain.financiero.EventoNominacionDE;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.domain.financiero.enums.EstadoEvento;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.EventoCancelacionDEService;
import com.franco.dev.service.financiero.EventoNominacionDEService;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.sifen.util.SifenReceptorHelper;
import com.roshka.sifen.Sifen;
import com.roshka.sifen.core.beans.EventosDE;
import com.roshka.sifen.core.beans.response.RespuestaRecepcionEvento;
import com.roshka.sifen.core.exceptions.SifenException;
import com.roshka.sifen.core.fields.request.event.TgGroupTiEvt;
import com.roshka.sifen.core.fields.request.event.TrGeVeCan;
import com.roshka.sifen.core.fields.request.event.TrGeVeInu;
import com.roshka.sifen.core.fields.request.event.TrGeVeNotRec;
import com.roshka.sifen.core.fields.request.event.TrGesEve;
import com.roshka.sifen.core.types.TiNatRec;
import com.roshka.sifen.core.types.TTiDE;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Servicio para gestionar eventos SIFEN del emisor.
 * 
 * Eventos soportados:
 * 1. Cancelación de DE - Dejar sin efecto un DE aprobado
 * 2. Inutilización de números - Declarar números/rangos como inutilizados
 * 3. Nominación - Identificar receptor real de factura innominada
 */
@Slf4j
@Service
public class SifenEventoService {

    private final DocumentoElectronicoService documentoElectronicoService;
    private final EventoCancelacionDEService eventoCancelacionDEService;
    private final EventoNominacionDEService eventoNominacionDEService;
    private final FacturaLegalService facturaLegalService;
    private final SucursalService sucursalService;

    public SifenEventoService(
            DocumentoElectronicoService documentoElectronicoService,
            EventoCancelacionDEService eventoCancelacionDEService,
            EventoNominacionDEService eventoNominacionDEService,
            FacturaLegalService facturaLegalService,
            SucursalService sucursalService) {
        this.documentoElectronicoService = documentoElectronicoService;
        this.eventoCancelacionDEService = eventoCancelacionDEService;
        this.eventoNominacionDEService = eventoNominacionDEService;
        this.facturaLegalService = facturaLegalService;
        this.sucursalService = sucursalService;
    }

    // ===================== CANCELACIÓN DE DE =====================

    /**
     * Cancela un Documento Electrónico aprobado.
     * 
     * CARACTERÍSTICAS:
     * - Maneja automáticamente reintentos si hay eventos previos fallidos
     * - Marca eventos anteriores como inactivos (histórico)
     * - Previene cancelaciones duplicadas si ya existe evento aprobado
     * 
     * PLAZOS:
     * - Factura Electrónica: Hasta 48 horas desde la aprobación
     * - Otros DTE: Hasta 168 horas (7 días) desde la aprobación
     * 
     * Después del plazo, corresponde emitir Nota de Crédito/Débito.
     * 
     * @param cdc CDC del documento a cancelar
     * @param motivo Motivo de la cancelación
     * @return Respuesta de SIFEN con el resultado del evento
     * @throws SifenException Si hay error en el envío
     */
    @Transactional
    public RespuestaRecepcionEvento cancelarDE(String cdc, String motivo) throws SifenException {
        log.info("🚫 Cancelando DE con CDC: {}", cdc);
        log.info("   Motivo: {}", motivo);
        
        // 1. Validar que el DE existe
        DocumentoElectronico de = documentoElectronicoService.findByCdc(cdc)
            .orElseThrow(() -> new IllegalArgumentException("No se encontró DE con CDC: " + cdc));
        
        // 2. Verificar si ya existe un evento de cancelación APROBADO
        if (eventoCancelacionDEService.tieneCancelacionAprobada(de.getId())) {
            log.warn("⚠️ El DE ya tiene un evento de cancelación APROBADO");
            log.warn("   Estado del DE: {}", de.getEstado());
            throw new IllegalStateException("El DE ya fue cancelado exitosamente. No se puede cancelar nuevamente.");
        }
        
        // 3. Buscar eventos activos previos (fallidos o pendientes)
        List<EventoCancelacionDE> eventosActivos = 
            eventoCancelacionDEService.findActivosByCdcDocumento(cdc);
        
        if (!eventosActivos.isEmpty()) {
            log.info("   🔄 Se encontraron {} evento(s) previo(s) - realizando reintento automático", 
                eventosActivos.size());
            
            // Marcar eventos anteriores como inactivos (histórico)
            for (EventoCancelacionDE eventoAnterior : eventosActivos) {
                eventoAnterior.setActivo(false);
                eventoCancelacionDEService.save(eventoAnterior);
                log.info("      📝 Evento anterior ID {} marcado como inactivo (estado: {})", 
                    eventoAnterior.getId(), eventoAnterior.getEstado());
            }
        }
        
        // 4. Crear evento de cancelación
        TrGeVeCan cancelacion = new TrGeVeCan();
        cancelacion.setId(cdc); // El CDC del documento a cancelar
        cancelacion.setmOtEve(motivo);
        
        // 5. Crear contenedor de tipo de evento
        TgGroupTiEvt tipoEvento = new TgGroupTiEvt();
        tipoEvento.setrGeVeCan(cancelacion);
        
        // 6. Crear gestión de evento
        TrGesEve gestionEvento = new TrGesEve();
        // generar un numero random entre 1 y 999999999.
        int numeroRandom = new Random().nextInt(99999999) + 1;
        String eventoId = String.valueOf(numeroRandom);
        LocalDateTime fechaFirma = LocalDateTime.now();
        
        gestionEvento.setId(eventoId);
        gestionEvento.setdFecFirma(fechaFirma);
        gestionEvento.setgGroupTiEvt(tipoEvento);
        
        // 7. Crear lista de eventos
        List<TrGesEve> listaEventos = new ArrayList<>();
        listaEventos.add(gestionEvento);
        
        // 8. Crear objeto EventosDE
        EventosDE eventosDE = new EventosDE();
        eventosDE.setrGesEveList(listaEventos);
        
        // 9. Crear registro del evento en BD (antes de enviar)
        EventoCancelacionDE eventoCancelacion = new EventoCancelacionDE();
        eventoCancelacion.setDocumentoElectronico(de);
        eventoCancelacion.setEventoId(eventoId);
        eventoCancelacion.setFechaFirma(fechaFirma);
        eventoCancelacion.setCdcDocumento(cdc);
        eventoCancelacion.setMotivoCancelacion(motivo);
        eventoCancelacion.setEstado(EstadoEvento.PENDIENTE);
        eventoCancelacion.setActivo(true);
        
        // Asignar sucursal actual
        Sucursal sucursalActual = sucursalService.sucursalActual();
        if (sucursalActual != null) {
            eventoCancelacion.setSucursal(sucursalActual);
            log.info("   Sucursal asignada: {} (ID: {})", sucursalActual.getNombre(), sucursalActual.getId());
        }
        
        // 10. Enviar a SIFEN
        log.info("   📤 Enviando evento de cancelación a SIFEN...");
        RespuestaRecepcionEvento respuesta = Sifen.recepcionEvento(eventosDE);
        
        // 11. Extraer datos directamente del XML (la librería puede no parsear correctamente)
        String xmlRespuesta = respuesta.getRespuestaBruta();
        
        // Extraer código de respuesta del XML
        String codigoRespuesta = extraerValorXML(xmlRespuesta, "<dCodRes>", "</dCodRes>");
        if (codigoRespuesta == null) {
            codigoRespuesta = extraerValorXML(xmlRespuesta, "<ns2:dCodRes>", "</ns2:dCodRes>");
        }
        
        // Extraer mensaje de respuesta del XML
        String mensajeRespuesta = extraerValorXML(xmlRespuesta, "<dMsgRes>", "</dMsgRes>");
        if (mensajeRespuesta == null) {
            mensajeRespuesta = extraerValorXML(xmlRespuesta, "<ns2:dMsgRes>", "</ns2:dMsgRes>");
        }
        
        log.info("   📥 Respuesta recibida - Código: {}", codigoRespuesta);
        log.info("   📥 Mensaje: {}", mensajeRespuesta);
        
        // 12. Actualizar evento con respuesta de SIFEN
        eventoCancelacion.setRespuestaBruta(xmlRespuesta);
        eventoCancelacion.setCodigoRespuesta(codigoRespuesta);
        eventoCancelacion.setMensajeRespuesta(mensajeRespuesta);
        
        // 13. Procesar respuesta y extraer información del evento
        // Primero verificar el estado del resultado (dEstRes) para manejar apropiadamente
        String estadoResultado = extraerValorXML(xmlRespuesta, "<dEstRes>", "</dEstRes>");
        if (estadoResultado == null) {
            estadoResultado = extraerValorXML(xmlRespuesta, "<ns2:dEstRes>", "</ns2:dEstRes>");
        }
        
        log.info("   📊 Estado del evento en SIFEN: {}", estadoResultado);
        
        // Extraer protocolo de autorización (puede existir incluso en rechazos)
        String protocolo = extraerValorXML(xmlRespuesta, "<dProtAut>", "</dProtAut>");
        if (protocolo == null) {
            protocolo = extraerValorXML(xmlRespuesta, "<ns2:dProtAut>", "</ns2:dProtAut>");
        }
        if (protocolo != null && !protocolo.isEmpty() && !"0".equals(protocolo)) {
            eventoCancelacion.setProtocoloAutorizacion(protocolo);
            log.info("   📋 Protocolo: {}", protocolo);
        }
        
        // Procesar según el estado del resultado
        if ("Aprobado".equalsIgnoreCase(estadoResultado)) {
            // ✅ EVENTO APROBADO
            eventoCancelacion.setEstado(EstadoEvento.APROBADO);
            eventoCancelacion.setFechaProcesamiento(LocalDateTime.now());
            
            // Actualizar el DE a CANCELADO
            de.setEstado(com.franco.dev.domain.financiero.enums.EstadoDE.CANCELADO);
            de.setCodigoRespuestaSifen(codigoRespuesta);
            de.setMensajeRespuestaSifen(mensajeRespuesta);
            documentoElectronicoService.save(de);
            
            // ✅ ACTUALIZAR FACTURA LEGAL - MARCAR COMO INACTIVA
            FacturaLegal factura = de.getFacturaLegal();
            if (factura != null) {
                factura.setActivo(false);
                facturaLegalService.save(factura);
                log.info("   📄 Factura Legal ID {} marcada como INACTIVA", factura.getId());
            }
            
            log.info("   ✅ Evento APROBADO - DE actualizado a estado CANCELADO");
            log.info("   📋 Código SIFEN: {} - {}", codigoRespuesta, mensajeRespuesta);
            
        } else if ("Rechazado".equalsIgnoreCase(estadoResultado)) {
            // ❌ EVENTO RECHAZADO POR SIFEN
            eventoCancelacion.setEstado(EstadoEvento.RECHAZADO);
            eventoCancelacion.setFechaProcesamiento(LocalDateTime.now());
            
            // NO actualizar el DE a cancelado
            de.setCodigoRespuestaSifen(codigoRespuesta);
            de.setMensajeRespuestaSifen(mensajeRespuesta);
            documentoElectronicoService.save(de);
            
            log.error("   ❌ Evento RECHAZADO por SIFEN");
            log.error("   📋 Código: {} - {}", codigoRespuesta, mensajeRespuesta);
            log.error("   ℹ️ El DE mantiene su estado actual: {}", de.getEstado());
            
        } else if (estadoResultado == null || estadoResultado.isEmpty()) {
            // ⏳ RESPUESTA SIN ESTADO EXPLÍCITO
            // Verificar código de respuesta para determinar si es exitoso o error
            if ("0300".equals(codigoRespuesta)) {
                // Recepción exitosa, pendiente de procesamiento
                eventoCancelacion.setEstado(EstadoEvento.PENDIENTE);
                log.info("   ✅ Evento recibido (código 0300) - pendiente de procesamiento");
            } else if ("0600".equals(codigoRespuesta)) {
                // Este caso no debería ocurrir (0600 siempre viene con estado)
                eventoCancelacion.setEstado(EstadoEvento.PENDIENTE);
                log.info("   ✅ Evento registrado (código 0600) - estado pendiente");
            } else {
                // Error en el envío
                eventoCancelacion.setEstado(EstadoEvento.ERROR_ENVIO);
                log.error("   ❌ Error en envío - Código: {} - {}", codigoRespuesta, mensajeRespuesta);
            }
        } else {
            // ESTADO DESCONOCIDO
            eventoCancelacion.setEstado(EstadoEvento.PENDIENTE);
            log.warn("   ⚠️ Estado desconocido: {} - marcando como PENDIENTE", estadoResultado);
        }
        
        // 14. Persistir evento
        eventoCancelacionDEService.save(eventoCancelacion);
        log.info("   💾 Evento guardado en BD - ID: {}, Estado: {}", 
            eventoCancelacion.getId(), eventoCancelacion.getEstado());
        
        return respuesta;
    }
    
    /**
     * Obtiene el historial completo de eventos de cancelación para un DE.
     * Útil para auditoría y debugging.
     * 
     * @param cdcDocumento CDC del documento
     * @return Lista de todos los eventos (activos e inactivos), ordenados por fecha descendente
     */
    public List<EventoCancelacionDE> obtenerHistorialCancelaciones(String cdcDocumento) {
        DocumentoElectronico de = documentoElectronicoService.findByCdc(cdcDocumento).orElse(null);
        if (de == null) {
            return new ArrayList<>();
        }
        return eventoCancelacionDEService.findByDocumentoElectronicoId(de.getId());
    }
    
    /**
     * Obtiene el último evento de cancelación para un DE (activo o no).
     * 
     * @param cdcDocumento CDC del documento
     * @return Evento más reciente o null si no hay eventos
     */
    public EventoCancelacionDE obtenerUltimoEventoCancelacion(String cdcDocumento) {
        return eventoCancelacionDEService.findActivosByCdcDocumento(cdcDocumento)
            .stream()
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Método auxiliar para extraer un valor entre dos tags XML.
     */
    private String extraerValorXML(String xml, String tagInicio, String tagFin) {
        try {
            int inicio = xml.indexOf(tagInicio);
            if (inicio == -1) return null;
            
            inicio += tagInicio.length();
            int fin = xml.indexOf(tagFin, inicio);
            if (fin == -1) return null;
            
            return xml.substring(inicio, fin).trim();
        } catch (Exception e) {
            log.error("Error al extraer valor XML: {}", e.getMessage());
            return null;
        }
    }

    // ===================== INUTILIZACIÓN DE NÚMEROS =====================

    /**
     * Inutiliza un rango de números de documentos electrónicos.
     * 
     * Usado cuando hay saltos, daños o errores de numeración ANTES de que exista un DTE aprobado.
     * Los números quedan marcados como inutilizados en SIFEN.
     * 
     * @param timbrado Timbrado que contiene los datos de establecimiento y punto de expedición
     * @param numeroInicio Número inicial del rango a inutilizar
     * @param numeroFin Número final del rango a inutilizar
     * @param tipoDE Tipo de documento electrónico (FACTURA_ELECTRONICA, etc.)
     * @param motivo Motivo de la inutilización
     * @return Respuesta de SIFEN con el resultado del evento
     * @throws SifenException Si hay error en el envío
     */
    @Transactional
    public RespuestaRecepcionEvento inutilizarNumeros(
            Timbrado timbrado,
            String establecimiento,
            String puntoExpedicion,
            int numeroInicio,
            int numeroFin,
            TTiDE tipoDE,
            String motivo) throws SifenException {
        
        log.info("📝 Inutilizando números de documentos");
        log.info("   Timbrado: {}", timbrado.getNumero());
        log.info("   Establecimiento: {}", establecimiento);
        log.info("   Punto Expedición: {}", puntoExpedicion);
        log.info("   Rango: {} - {}", numeroInicio, numeroFin);
        log.info("   Tipo DE: {}", tipoDE);
        log.info("   Motivo: {}", motivo);
        
        // 1. Validar rango
        if (numeroInicio > numeroFin) {
            throw new IllegalArgumentException(
                "Número inicial (" + numeroInicio + ") no puede ser mayor que número final (" + numeroFin + ")"
            );
        }
        
        // 2. Crear evento de inutilización
        TrGeVeInu inutilizacion = new TrGeVeInu();
        inutilizacion.setdNumTim(Integer.parseInt(timbrado.getNumero()));
        inutilizacion.setdEst(establecimiento);
        inutilizacion.setdPunExp(puntoExpedicion);
        inutilizacion.setdNumIn(String.valueOf(numeroInicio)); // Se formatea automáticamente con padding
        inutilizacion.setdNumFin(String.valueOf(numeroFin));   // Se formatea automáticamente con padding
        inutilizacion.setiTiDE(tipoDE);
        inutilizacion.setmOtEve(motivo);
        
        // 3. Crear contenedor de tipo de evento
        TgGroupTiEvt tipoEvento = new TgGroupTiEvt();
        tipoEvento.setrGeVeInu(inutilizacion);
        
        // 4. Generar ID único para el evento (combinación de datos)
        String eventoId = String.format("INU-%s-%s-%s-%07d",
            timbrado.getNumero(),
            establecimiento,
            puntoExpedicion,
            numeroInicio
        );
        
        // 5. Crear gestión de evento
        TrGesEve gestionEvento = new TrGesEve();
        gestionEvento.setId(eventoId);
        gestionEvento.setdFecFirma(LocalDateTime.now());
        gestionEvento.setgGroupTiEvt(tipoEvento);
        
        // 6. Crear lista de eventos
        List<TrGesEve> listaEventos = new ArrayList<>();
        listaEventos.add(gestionEvento);
        
        // 7. Crear objeto EventosDE
        EventosDE eventosDE = new EventosDE();
        eventosDE.setrGesEveList(listaEventos);
        
        // 8. Enviar a SIFEN
        log.info("   📤 Enviando evento de inutilización a SIFEN...");
        RespuestaRecepcionEvento respuesta = Sifen.recepcionEvento(eventosDE);
        
        log.info("   📥 Respuesta recibida - Código: {}", respuesta.getdCodRes());
        log.info("✅ Evento de inutilización procesado");
        
        return respuesta;
    }

    // ===================== NOMINACIÓN DE RECEPTOR =====================

    /**
     * Nomina (identifica) al receptor real de una factura que fue emitida como innominada.
     * 
     * CARACTERÍSTICAS:
     * - Maneja automáticamente reintentos si hay eventos previos fallidos
     * - Crea registro del evento en BD para seguimiento
     * - Actualiza la FacturaLegal con el cliente nominado si el evento es aprobado
     * - Previene nominaciones duplicadas si ya existe evento aprobado
     * 
     * Solo aplica para facturas electrónicas que fueron emitidas sin identificar al receptor
     * y posteriormente se necesita registrar quién fue el receptor real.
     * 
     * El total de la factura se obtiene automáticamente desde la factura asociada al DE.
     * 
     * @param cdc CDC del documento innominado a nominar
     * @param cliente Cliente que será el receptor nominado
     * @return Respuesta de SIFEN con el resultado del evento
     * @throws SifenException Si hay error en el envío
     */
    @Transactional
    public RespuestaRecepcionEvento nominarReceptor(String cdc, Cliente cliente) throws SifenException {
        
        log.info("👤 Nominando receptor para DE con CDC: {}", cdc);
        
        // Validar que el cliente no sea null
        if (cliente == null) {
            throw new IllegalArgumentException("Cliente no puede ser null para nominación");
        }
        
        log.info("   Cliente: {} (ID: {})", 
            cliente.getPersona() != null ? cliente.getPersona().getNombre() : "null", 
            cliente.getId());
        
        // 1. Validar que el DE existe
        DocumentoElectronico de = documentoElectronicoService.findByCdc(cdc)
            .orElseThrow(() -> new IllegalArgumentException("No se encontró DE con CDC: " + cdc));
        
        FacturaLegal factura = de.getFacturaLegal();
        if (factura == null) {
            throw new IllegalArgumentException("DE sin factura asociada");
        }
        
        // 2. Verificar si ya existe un evento de nominación APROBADO
        if (eventoNominacionDEService.tieneNominacionAprobada(de.getId())) {
            log.warn("⚠️ El DE ya tiene un evento de nominación APROBADO");
            throw new IllegalStateException("El DE ya fue nominado exitosamente. No se puede nominar nuevamente.");
        }
        
        // 3. Buscar eventos activos previos (fallidos o pendientes)
        List<EventoNominacionDE> eventosActivos = 
            eventoNominacionDEService.findActivosByCdcDocumento(cdc);
        
        if (!eventosActivos.isEmpty()) {
            log.info("   🔄 Se encontraron {} evento(s) previo(s) - realizando reintento automático", 
                eventosActivos.size());
            
            // Marcar eventos anteriores como inactivos (histórico)
            for (EventoNominacionDE eventoAnterior : eventosActivos) {
                eventoAnterior.setActivo(false);
                eventoNominacionDEService.save(eventoAnterior);
                log.info("      📝 Evento anterior ID {} marcado como inactivo (estado: {})", 
                    eventoAnterior.getId(), eventoAnterior.getEstado());
            }
        }
        
        // 4. Obtener total automáticamente desde la factura
        BigDecimal totalFactura = BigDecimal.valueOf(factura.getTotalFinal());
        LocalDateTime fechaFirma = LocalDateTime.now();
        LocalDateTime fechaRecepcion = LocalDateTime.now();
        
        log.info("   Total: {} (obtenido desde factura ID: {})", totalFactura, factura.getId());
        
        // 5. Usar SifenReceptorHelper para determinar configuración del receptor
        SifenReceptorHelper.ConfiguracionReceptor config = 
            SifenReceptorHelper.determinarConfiguracionReceptor(cliente, factura.getTotalFinal());
        
        // 6. Crear evento de nominación
        TrGeVeNotRec nominacion = new TrGeVeNotRec();
        nominacion.setId(cdc);
        nominacion.setdFecEmi(factura.getFecha());
        nominacion.setdFecRecep(fechaRecepcion);
        nominacion.setdTotalGs(totalFactura);
        nominacion.setdNomRec(config.dNomRec);
        
        // 7. Configurar datos del receptor según configuración determinada por SifenReceptorHelper
        String tipoReceptor;
        String documentoReceptor;
        
        if (config.iNatRec == TiNatRec.CONTRIBUYENTE) {
            nominacion.setiTipRec(TiNatRec.CONTRIBUYENTE);
            nominacion.setdRucRec(config.dRucRec);
            nominacion.setdDVRec(String.valueOf(config.dDVRec));
            tipoReceptor = "CONTRIBUYENTE";
            documentoReceptor = config.dRucRec + "-" + config.dDVRec;
            log.info("   Tipo: Contribuyente - RUC: {}-{}", config.dRucRec, config.dDVRec);
        } else {
            nominacion.setiTipRec(TiNatRec.NO_CONTRIBUYENTE);
            nominacion.setdTipIDRec(config.iTipIDRec);
            nominacion.setdNumID(config.dNumIDRec);
            tipoReceptor = "NO_CONTRIBUYENTE";
            documentoReceptor = config.dNumIDRec;
            log.info("   Tipo: No Contribuyente - Doc: {} ({})", config.dNumIDRec, config.iTipIDRec);
        }
        
        // 8. Crear contenedor de tipo de evento
        TgGroupTiEvt tipoEvento = new TgGroupTiEvt();
        tipoEvento.setrGeVeNotRec(nominacion);
        
        // 9. Crear gestión de evento
        TrGesEve gestionEvento = new TrGesEve();
        int numeroRandom = new Random().nextInt(99999999) + 1;
        String eventoId = String.valueOf(numeroRandom);
        gestionEvento.setId(eventoId);
        gestionEvento.setdFecFirma(fechaFirma);
        gestionEvento.setgGroupTiEvt(tipoEvento);
        
        // 10. Crear lista de eventos
        List<TrGesEve> listaEventos = new ArrayList<>();
        listaEventos.add(gestionEvento);
        
        // 11. Crear objeto EventosDE
        EventosDE eventosDE = new EventosDE();
        eventosDE.setrGesEveList(listaEventos);
        
        // 12. Crear registro del evento en BD (antes de enviar)
        EventoNominacionDE eventoNominacion = new EventoNominacionDE();
        eventoNominacion.setDocumentoElectronico(de);
        eventoNominacion.setEventoId(eventoId);
        eventoNominacion.setFechaFirma(fechaFirma);
        eventoNominacion.setCdcDocumento(cdc);
        eventoNominacion.setCliente(cliente);
        eventoNominacion.setNombreReceptor(config.dNomRec);
        eventoNominacion.setDocumentoReceptor(documentoReceptor);
        eventoNominacion.setTipoReceptor(tipoReceptor);
        eventoNominacion.setTotalFactura(totalFactura);
        eventoNominacion.setFechaEmision(factura.getFecha());
        eventoNominacion.setFechaRecepcion(fechaRecepcion);
        eventoNominacion.setEstado(EstadoEvento.PENDIENTE);
        eventoNominacion.setActivo(true);
        
        // Asignar sucursal actual
        Sucursal sucursalActual = sucursalService.sucursalActual();
        if (sucursalActual != null) {
            eventoNominacion.setSucursal(sucursalActual);
            log.info("   Sucursal asignada: {} (ID: {})", sucursalActual.getNombre(), sucursalActual.getId());
        }
        
        // 13. Enviar a SIFEN
        log.info("   📤 Enviando evento de nominación a SIFEN...");
        RespuestaRecepcionEvento respuesta = Sifen.recepcionEvento(eventosDE);
        
        // 14. Extraer datos directamente del XML (la librería puede no parsear correctamente)
        String xmlRespuesta = respuesta.getRespuestaBruta();
        
        // Extraer código de respuesta del XML
        String codigoRespuesta = extraerValorXML(xmlRespuesta, "<dCodRes>", "</dCodRes>");
        if (codigoRespuesta == null) {
            codigoRespuesta = extraerValorXML(xmlRespuesta, "<ns2:dCodRes>", "</ns2:dCodRes>");
        }
        
        // Extraer mensaje de respuesta del XML
        String mensajeRespuesta = extraerValorXML(xmlRespuesta, "<dMsgRes>", "</dMsgRes>");
        if (mensajeRespuesta == null) {
            mensajeRespuesta = extraerValorXML(xmlRespuesta, "<ns2:dMsgRes>", "</ns2:dMsgRes>");
        }
        
        log.info("   📥 Respuesta recibida - Código: {}", codigoRespuesta);
        log.info("   📥 Mensaje: {}", mensajeRespuesta);
        
        // 15. Actualizar evento con respuesta de SIFEN
        eventoNominacion.setRespuestaBruta(xmlRespuesta);
        eventoNominacion.setCodigoRespuesta(codigoRespuesta);
        eventoNominacion.setMensajeRespuesta(mensajeRespuesta);
        
        // 16. Procesar respuesta y extraer información del evento
        String estadoResultado = extraerValorXML(xmlRespuesta, "<dEstRes>", "</dEstRes>");
        if (estadoResultado == null) {
            estadoResultado = extraerValorXML(xmlRespuesta, "<ns2:dEstRes>", "</ns2:dEstRes>");
        }
        
        log.info("   📊 Estado del evento en SIFEN: {}", estadoResultado);
        
        // Extraer protocolo de autorización
        String protocolo = extraerValorXML(xmlRespuesta, "<dProtAut>", "</dProtAut>");
        if (protocolo == null) {
            protocolo = extraerValorXML(xmlRespuesta, "<ns2:dProtAut>", "</ns2:dProtAut>");
        }
        if (protocolo != null && !protocolo.isEmpty() && !"0".equals(protocolo)) {
            eventoNominacion.setProtocoloAutorizacion(protocolo);
            log.info("   📋 Protocolo: {}", protocolo);
        }
        
        // 17. Procesar según el estado del resultado
        if ("Aprobado".equalsIgnoreCase(estadoResultado)) {
            // ✅ EVENTO APROBADO
            eventoNominacion.setEstado(EstadoEvento.APROBADO);
            eventoNominacion.setFechaProcesamiento(LocalDateTime.now());
            
            // Actualizar el DE con la respuesta
            de.setCodigoRespuestaSifen(codigoRespuesta);
            de.setMensajeRespuestaSifen(mensajeRespuesta);
            documentoElectronicoService.save(de);
            
            // ✅ ACTUALIZAR FACTURA LEGAL CON EL CLIENTE NOMINADO
            factura.setCliente(cliente);
            facturaLegalService.save(factura);
            
            log.info("   ✅ Evento APROBADO - Factura actualizada con cliente nominado");
            log.info("   📋 Código SIFEN: {} - {}", codigoRespuesta, mensajeRespuesta);
            log.info("   👤 Factura ID {} ahora tiene cliente ID {}", factura.getId(), cliente.getId());
            
        } else if ("Rechazado".equalsIgnoreCase(estadoResultado)) {
            // ❌ EVENTO RECHAZADO POR SIFEN
            eventoNominacion.setEstado(EstadoEvento.RECHAZADO);
            eventoNominacion.setFechaProcesamiento(LocalDateTime.now());
            
            // NO actualizar la factura
            de.setCodigoRespuestaSifen(codigoRespuesta);
            de.setMensajeRespuestaSifen(mensajeRespuesta);
            documentoElectronicoService.save(de);
            
            log.error("   ❌ Evento RECHAZADO por SIFEN");
            log.error("   📋 Código: {} - {}", codigoRespuesta, mensajeRespuesta);
            log.error("   ℹ️ La factura mantiene cliente NULL (innominada)");
            
        } else if (estadoResultado == null || estadoResultado.isEmpty()) {
            // ⏳ RESPUESTA SIN ESTADO EXPLÍCITO
            if ("0300".equals(codigoRespuesta)) {
                eventoNominacion.setEstado(EstadoEvento.PENDIENTE);
                log.info("   ✅ Evento recibido (código 0300) - pendiente de procesamiento");
            } else if ("0600".equals(codigoRespuesta)) {
                // Código 0600 = "Evento registrado correctamente"
                // En algunos casos SIFEN puede retornar este código con estado "Aprobado"
                // Verificar si hay protocolo de autorización
                if (protocolo != null && !protocolo.isEmpty() && !"0".equals(protocolo)) {
                    eventoNominacion.setEstado(EstadoEvento.APROBADO);
                    eventoNominacion.setFechaProcesamiento(LocalDateTime.now());
                    
                    // Actualizar factura con cliente nominado
                    factura.setCliente(cliente);
                    facturaLegalService.save(factura);
                    
                    log.info("   ✅ Evento APROBADO (código 0600 + protocolo) - Factura actualizada");
                    log.info("   👤 Factura ID {} ahora tiene cliente ID {}", factura.getId(), cliente.getId());
                } else {
                    eventoNominacion.setEstado(EstadoEvento.PENDIENTE);
                    log.info("   ✅ Evento registrado (código 0600) - estado pendiente");
                }
            } else {
                eventoNominacion.setEstado(EstadoEvento.ERROR_ENVIO);
                log.error("   ❌ Error en envío - Código: {} - {}", codigoRespuesta, mensajeRespuesta);
            }
        } else {
            // ESTADO DESCONOCIDO
            eventoNominacion.setEstado(EstadoEvento.PENDIENTE);
            log.warn("   ⚠️ Estado desconocido: {} - marcando como PENDIENTE", estadoResultado);
        }
        
        // 18. Persistir evento
        eventoNominacionDEService.save(eventoNominacion);
        log.info("   💾 Evento guardado en BD - ID: {}, Estado: {}", 
            eventoNominacion.getId(), eventoNominacion.getEstado());
        
        return respuesta;
    }
    
    /**
     * Obtiene el historial completo de eventos de nominación para un DE.
     * 
     * @param cdcDocumento CDC del documento
     * @return Lista de todos los eventos (activos e inactivos), ordenados por fecha descendente
     */
    public List<EventoNominacionDE> obtenerHistorialNominaciones(String cdcDocumento) {
        DocumentoElectronico de = documentoElectronicoService.findByCdc(cdcDocumento).orElse(null);
        if (de == null) {
            return new ArrayList<>();
        }
        return eventoNominacionDEService.findByDocumentoElectronicoId(de.getId());
    }
    
    /**
     * Obtiene el último evento de nominación para un DE (activo o no).
     * 
     * @param cdcDocumento CDC del documento
     * @return Evento más reciente o null si no hay eventos
     */
    public EventoNominacionDE obtenerUltimoEventoNominacion(String cdcDocumento) {
        return eventoNominacionDEService.findActivosByCdcDocumento(cdcDocumento)
            .stream()
            .findFirst()
            .orElse(null);
    }
}

