package com.franco.dev.service.sifen.service;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
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

    public SifenEventoService(DocumentoElectronicoService documentoElectronicoService) {
        this.documentoElectronicoService = documentoElectronicoService;
    }

    // ===================== CANCELACIÓN DE DE =====================

    /**
     * Cancela un Documento Electrónico aprobado.
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
        
        // 1. Validar que el DE existe y está aprobado
        DocumentoElectronico de = documentoElectronicoService.findByCdc(cdc)
            .orElseThrow(() -> new IllegalArgumentException("No se encontró DE con CDC: " + cdc));
        
        // 2. Crear evento de cancelación
        TrGeVeCan cancelacion = new TrGeVeCan();
        cancelacion.setId(cdc); // El CDC del documento a cancelar
        cancelacion.setmOtEve(motivo);
        
        // 3. Crear contenedor de tipo de evento
        TgGroupTiEvt tipoEvento = new TgGroupTiEvt();
        tipoEvento.setrGeVeCan(cancelacion);
        
        // 4. Crear gestión de evento
        TrGesEve gestionEvento = new TrGesEve();
        gestionEvento.setId(cdc); // ID del evento (usamos el CDC)
        gestionEvento.setdFecFirma(LocalDateTime.now());
        gestionEvento.setgGroupTiEvt(tipoEvento);
        
        // 5. Crear lista de eventos
        List<TrGesEve> listaEventos = new ArrayList<>();
        listaEventos.add(gestionEvento);
        
        // 6. Crear objeto EventosDE
        EventosDE eventosDE = new EventosDE();
        eventosDE.setrGesEveList(listaEventos);
        
        // 7. Enviar a SIFEN
        log.info("   📤 Enviando evento de cancelación a SIFEN...");
        RespuestaRecepcionEvento respuesta = Sifen.recepcionEvento(eventosDE);
        
        log.info("   📥 Respuesta recibida - Código: {}", respuesta.getdCodRes());
        // log respuesta bruta
        log.info("   📥 Respuesta bruta: {}", respuesta.getRespuestaBruta());
        
        // 8. Actualizar estado en BD si la cancelación fue exitosa
        if ("0300".equals(respuesta.getdCodRes())) {
            de.setEstado(com.franco.dev.domain.financiero.enums.EstadoDE.CANCELADO);
            de.setCodigoRespuestaSifen(respuesta.getdCodRes());
            de.setMensajeRespuestaSifen(respuesta.getdMsgRes());
            documentoElectronicoService.save(de);
            log.info("✅ Evento de cancelación exitoso - DE actualizado a estado CANCELADO");
        } else {
            log.error("❌ Error en cancelación - Código: {} - {}", respuesta.getdCodRes(), respuesta.getdMsgRes());
        }
        
        return respuesta;
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
        log.info("   Cliente: {} (ID: {})", 
            cliente != null ? cliente.getPersona().getNombre() : "null", 
            cliente != null ? cliente.getId() : "null");
        
        // 1. Validar que el DE existe
        DocumentoElectronico de = documentoElectronicoService.findByCdc(cdc)
            .orElseThrow(() -> new IllegalArgumentException("No se encontró DE con CDC: " + cdc));
        
        FacturaLegal factura = de.getFacturaLegal();
        if (factura == null) {
            throw new IllegalArgumentException("DE sin factura asociada");
        }
        
        // 2. Obtener total automáticamente desde la factura
        BigDecimal totalFactura = BigDecimal.valueOf(factura.getTotalFinal());
        log.info("   Total: {} (obtenido desde factura ID: {})", totalFactura, factura.getId());
        
        // 3. Usar SifenReceptorHelper para determinar configuración del receptor
        SifenReceptorHelper.ConfiguracionReceptor config = 
            SifenReceptorHelper.determinarConfiguracionReceptor(cliente, factura.getTotalFinal());
        
        // 4. Crear evento de nominación
        TrGeVeNotRec nominacion = new TrGeVeNotRec();
        nominacion.setId(cdc);
        nominacion.setdFecEmi(factura.getFecha());
        nominacion.setdFecRecep(LocalDateTime.now());
        nominacion.setdTotalGs(totalFactura);
        nominacion.setdNomRec(config.dNomRec);
        
        // 5. Configurar datos del receptor según configuración determinada por SifenReceptorHelper
        if (config.iNatRec == TiNatRec.CONTRIBUYENTE) {
            nominacion.setiTipRec(TiNatRec.CONTRIBUYENTE);
            nominacion.setdRucRec(config.dRucRec);
            nominacion.setdDVRec(String.valueOf(config.dDVRec));
            log.info("   Tipo: Contribuyente - RUC: {}-{}", config.dRucRec, config.dDVRec);
        } else {
            nominacion.setiTipRec(TiNatRec.NO_CONTRIBUYENTE);
            nominacion.setdTipIDRec(config.iTipIDRec);
            nominacion.setdNumID(config.dNumIDRec);
            log.info("   Tipo: No Contribuyente - Doc: {} ({})", config.dNumIDRec, config.iTipIDRec);
        }
        
        // 6. Crear contenedor de tipo de evento
        TgGroupTiEvt tipoEvento = new TgGroupTiEvt();
        tipoEvento.setrGeVeNotRec(nominacion);
        
        // 7. Crear gestión de evento
        TrGesEve gestionEvento = new TrGesEve();
        gestionEvento.setId(cdc + "-NOM"); // ID único para el evento de nominación
        gestionEvento.setdFecFirma(LocalDateTime.now());
        gestionEvento.setgGroupTiEvt(tipoEvento);
        
        // 8. Crear lista de eventos
        List<TrGesEve> listaEventos = new ArrayList<>();
        listaEventos.add(gestionEvento);
        
        // 9. Crear objeto EventosDE
        EventosDE eventosDE = new EventosDE();
        eventosDE.setrGesEveList(listaEventos);
        
        // 10. Enviar a SIFEN
        log.info("   📤 Enviando evento de nominación a SIFEN...");
        RespuestaRecepcionEvento respuesta = Sifen.recepcionEvento(eventosDE);
        
        log.info("   📥 Respuesta recibida - Código: {}", respuesta.getdCodRes());
        
        // 11. Actualizar información en BD si la nominación fue exitosa
        if ("0300".equals(respuesta.getdCodRes())) {
            de.setCodigoRespuestaSifen(respuesta.getdCodRes());
            de.setMensajeRespuestaSifen(respuesta.getdMsgRes());
            documentoElectronicoService.save(de);
            log.info("✅ Evento de nominación exitoso - DE actualizado con respuesta de SIFEN");
        } else {
            log.error("❌ Error en nominación - Código: {} - {}", respuesta.getdCodRes(), respuesta.getdMsgRes());
        }
        
        return respuesta;
    }
}

