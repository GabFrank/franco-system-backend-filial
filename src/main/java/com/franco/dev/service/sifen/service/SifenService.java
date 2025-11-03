/*
* Copyright 2023 Roshka S.A.
*
* Permission is hereby granted, free of charge, to any person obtaining a copy of
* this software and associated documentation files (the "Software"), to deal in
* the Software without restriction, including without limitation the rights to
* use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
* the Software, and to permit persons to whom the Software is furnished to do so,
* subject to the following conditions:
*
* The above copyright notice and this permission notice shall be included in all
* copies or substantial portions of the Software.
*
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
* IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
* FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
* COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
* IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
* CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/
package com.franco.dev.service.sifen.service;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.EventoCancelacionDE;
import com.franco.dev.domain.financiero.EventoNominacionDE;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.domain.financiero.LoteDE;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.domain.financiero.enums.EstadoEvento;
import com.franco.dev.domain.financiero.enums.EstadoLoteDE;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.EventoCancelacionDEService;
import com.franco.dev.service.financiero.EventoNominacionDEService;
import com.franco.dev.service.financiero.FacturaLegalItemService;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.financiero.LoteDEService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.sifen.util.SifenEventoParser;
import com.franco.dev.service.sifen.util.SifenReceptorHelper;
import com.roshka.sifen.Sifen;
import com.roshka.sifen.core.beans.response.RespuestaConsultaDE;
import com.roshka.sifen.core.beans.response.RespuestaConsultaLoteDE;
import com.roshka.sifen.core.beans.response.RespuestaRecepcionLoteDE;
import com.roshka.sifen.core.exceptions.SifenException;
import com.roshka.sifen.core.fields.request.de.*;
import com.roshka.sifen.core.types.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Servicio para integración con SIFEN (Sistema Integrado de Facturación Electrónica Nacional).
 * 
 * Proporciona métodos granulares para:
 * - Crear Documentos Electrónicos (DE)
 * - Crear y gestionar Lotes de DEs
 * - Enviar lotes a SIFEN
 * - Consultar estados de lotes y DEs
 * 
 * Todos los métodos están probados y validados en SifenFlujoCompletoDELote.java
 */
@Slf4j
@Service
public class SifenService {

    private final DocumentoElectronicoService documentoElectronicoService;
    private final LoteDEService loteDEService;
    private final FacturaLegalItemService facturaLegalItemService;
    private final ClienteService clienteService;
    private final EventoCancelacionDEService eventoCancelacionDEService;
    private final EventoNominacionDEService eventoNominacionDEService;
    private final FacturaLegalService facturaLegalService;
    private final SucursalService sucursalService;
    private final com.roshka.sifen.core.SifenConfig sifenConfig;
    private final boolean sifenEnabled;

    @Value("${tipoContribuyenteEmisor:2}")
    private Integer tipoContribuyenteEmisor;

    public SifenService(
            DocumentoElectronicoService documentoElectronicoService,
            LoteDEService loteDEService,
            FacturaLegalItemService facturaLegalItemService,
            ClienteService clienteService,
            EventoCancelacionDEService eventoCancelacionDEService,
            EventoNominacionDEService eventoNominacionDEService,
            @Lazy FacturaLegalService facturaLegalService,
            SucursalService sucursalService,
            @Autowired(required = false) com.roshka.sifen.core.SifenConfig sifenConfig) {
        this.documentoElectronicoService = documentoElectronicoService;
        this.loteDEService = loteDEService;
        this.facturaLegalItemService = facturaLegalItemService;
        this.clienteService = clienteService;
        this.eventoCancelacionDEService = eventoCancelacionDEService;
        this.eventoNominacionDEService = eventoNominacionDEService;
        this.facturaLegalService = facturaLegalService;
        this.sucursalService = sucursalService;
        this.sifenConfig = sifenConfig;
        this.sifenEnabled = (this.sifenConfig != null);
        if (!sifenEnabled) {
            log.warn("SIFEN Service inicializado, pero SIFEN está DESHABILITADO. No se procesarán operaciones SIFEN.");
        }
    }

    private void verificarSifenHabilitado() {
        if (!sifenEnabled) {
            throw new IllegalStateException("SIFEN está deshabilitado. No se puede ejecutar la operación.");
        }
    }

    // ===================== CREACIÓN DE DOCUMENTOS ELECTRÓNICOS =====================

    /**
     * Crea un Documento Electrónico para una factura y lo persiste en BD con estado PENDIENTE.
     * Genera el XML original y la URL QR para uso posterior.
     * 
     * @param factura La factura legal para la cual crear el DE
     * @return El DocumentoElectronico persistido con CDC y XML original
     * @throws Exception Si hay error en la generación
     */
    @Transactional
    public com.franco.dev.domain.financiero.DocumentoElectronico crearDocumentoElectronico(
            FacturaLegal factura) throws Exception {
        verificarSifenHabilitado();
        log.info("📝 Creando Documento Electrónico para factura ID: {}", factura.getId());
        
        // 1. Crear el objeto DE de la BD
        com.franco.dev.domain.financiero.DocumentoElectronico de = 
            documentoElectronicoService.createFromFacturaLegal(factura);
        
        // 2. Obtener items de la factura
        List<FacturaLegalItem> items = facturaLegalItemService.findByFacturaLegalId(factura.getId());
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Factura sin items - no se puede crear DE");
        }
        
        // 3. Generar el objeto DE de SIFEN
        com.roshka.sifen.core.beans.DocumentoElectronico deSifen = 
            generarDEDesdeFacturaDatosReales(factura, items);
        
        // 4. Asignar CDC
        String cdc = deSifen.obtenerCDC();
        de.setCdc(cdc);
        log.info("   CDC generado: {}", cdc);
        
        // 5. Generar y guardar XML original (CRÍTICO para reconstrucción exacta)
        try {
            com.roshka.sifen.internal.ctx.GenerationCtx ctx = 
                com.roshka.sifen.internal.ctx.GenerationCtx.getDefaultFromConfig(sifenConfig);
            String xmlOriginal = deSifen.generarXml(ctx);
            de.setXmlOriginal(xmlOriginal);
            log.info("   XML original generado ({} caracteres)", xmlOriginal.length());
            
            // 6. Extraer URL QR del XML
            String urlQr = com.franco.dev.service.sifen.util.SifenXmlParser.extractUrlQr(xmlOriginal);
            if (urlQr != null) {
                de.setUrlQr(urlQr);
                log.info("   URL QR extraída del XML");
            } else {
                log.warn("   URL QR no encontrada en XML - continuando sin URL QR");
            }
        } catch (Exception e) {
            log.error("   Error al generar XML original: {}", e.getMessage());
            throw new RuntimeException("Error al generar XML original del DE", e);
        }
        
        // 7. Establecer estado PENDIENTE
        de.setEstado(EstadoDE.PENDIENTE);
        
        // 8. Guardar en BD
        com.franco.dev.domain.financiero.DocumentoElectronico deGuardado = 
            documentoElectronicoService.save(de);
        
        log.info("✅ DE creado exitosamente - ID: {}, CDC: {}", deGuardado.getId(), deGuardado.getCdc());
        return deGuardado;
    }

    // ===================== GESTIÓN DE LOTES =====================

    /**
     * Crea un lote vacío en BD con estado PENDIENTE_ENVIO.
     * 
     * @return El lote creado
     */
    @Transactional
    public LoteDE crearLote() {
        log.info("📦 Creando lote vacío...");
        
        LoteDE lote = new LoteDE();
        lote.setEstado(EstadoLoteDE.PENDIENTE_ENVIO);
        lote.setFechaUltimoIntento(LocalDateTime.now());
        lote.setIntentos(0);
        lote.setCreadoEn(LocalDateTime.now());
        
        // Asignar sucursal actual
        Sucursal sucursalActual = sucursalService.sucursalActual();
        if (sucursalActual != null) {
            lote.setSucursal(sucursalActual);
            log.info("   Sucursal asignada: {} (ID: {})", sucursalActual.getNombre(), sucursalActual.getId());
        } else {
            log.warn("   No se pudo obtener la sucursal actual");
        }
        
        LoteDE loteGuardado = loteDEService.save(lote);
        log.info("✅ Lote creado - ID: {}", loteGuardado.getId());
        
        return loteGuardado;
    }

    /**
     * Vincula documentos electrónicos a un lote y actualiza sus estados a EN_LOTE.
     * 
     * @param lote El lote al cual vincular los documentos
     * @param documentos Lista de documentos a vincular
     */
    @Transactional
    public void vincularDocumentosALote(LoteDE lote, 
            List<com.franco.dev.domain.financiero.DocumentoElectronico> documentos) {
        log.info("🔗 Vinculando {} documentos al lote ID: {}", documentos.size(), lote.getId());
        
        for (com.franco.dev.domain.financiero.DocumentoElectronico documento : documentos) {
            documento.setLoteDe(lote);
            documento.setEstado(EstadoDE.EN_LOTE);
            documentoElectronicoService.save(documento);
            log.debug("   ✓ Documento ID {} vinculado", documento.getId());
        }
        
        log.info("✅ {} documentos vinculados exitosamente", documentos.size());
    }

    /**
     * Envía un lote a SIFEN usando el método nativo de lotes.
     * Reconstruye los DEs desde el XML original guardado.
     * 
     * @param lote El lote a enviar
     * @throws SifenException Si hay error en el envío
     */
    @Transactional
    public void enviarLote(LoteDE lote) throws SifenException {
        verificarSifenHabilitado();
        log.info("📤 Enviando lote ID: {} a SIFEN...", lote.getId());
        
        // 1. Obtener documentos del lote
        List<com.franco.dev.domain.financiero.DocumentoElectronico> documentos = 
            documentoElectronicoService.findByLoteDe(lote);
            
            if (documentos.isEmpty()) {
            throw new IllegalArgumentException("El lote " + lote.getId() + " no tiene documentos asociados");
        }
        
        log.info("   Lote contiene {} documentos", documentos.size());
        
        // 2. Reconstruir DEs de SIFEN desde XML original
        List<com.roshka.sifen.core.beans.DocumentoElectronico> deSifenLote = new ArrayList<>();
        
        for (com.franco.dev.domain.financiero.DocumentoElectronico de : documentos) {
            log.debug("   Procesando DE ID: {}, CDC: {}", de.getId(), de.getCdc());
            
            com.roshka.sifen.core.beans.DocumentoElectronico deSifen;
            
            // ESTRATEGIA ÓPTIMA: Reconstruir desde XML guardado
            if (de.getXmlOriginal() != null && !de.getXmlOriginal().isEmpty()) {
                try {
                    deSifen = new com.roshka.sifen.core.beans.DocumentoElectronico(de.getXmlOriginal());
                    log.debug("      ✓ DE reconstruido desde XML original");
                    
                    // Verificar CDC
                    if (!de.getCdc().equals(deSifen.obtenerCDC())) {
                        log.error("      ✗ CDC no coincide! Esperado: {}, Obtenido: {}", 
                            de.getCdc(), deSifen.obtenerCDC());
                    }
        } catch (Exception e) {
                    log.warn("      ⚠ Error al reconstruir desde XML: {}", e.getMessage());
                    deSifen = reconstruirDEDesdeFactura(de);
                }
            } else {
                log.warn("      ⚠ XML original no disponible - regenerando desde factura");
                deSifen = reconstruirDEDesdeFactura(de);
            }
            
            // Asegurar URL QR
            if (de.getUrlQr() != null) {
                deSifen.setEnlaceQR(de.getUrlQr());
            }
            
            deSifenLote.add(deSifen);
        }
        
        // 3. Enviar lote a SIFEN
        log.info("   ⏳ Enviando {} DEs a SIFEN...", deSifenLote.size());
        RespuestaRecepcionLoteDE respuesta = Sifen.recepcionLoteDE(deSifenLote);
        
        // 4. Actualizar lote con respuesta
        lote.setFechaUltimoIntento(LocalDateTime.now());
        lote.setRespuestaSifen(respuesta.getRespuestaBruta());
        lote.setIntentos(lote.getIntentos() + 1);
        
        String codigoRespuesta = respuesta.getdCodRes();
        log.info("   📥 Respuesta recibida - Código: {}, Mensaje: {}", 
            codigoRespuesta, respuesta.getdMsgRes());
        
        // 5. Determinar estado del lote según respuesta
        if ("0300".equals(codigoRespuesta)) {
                lote.setEstado(EstadoLoteDE.EN_PROCESO);
            String protocolo = extraerProtocoloDeRespuesta(respuesta.getRespuestaBruta());
            lote.setProtocolo(protocolo);
            log.info("✅ Lote {} enviado exitosamente. Protocolo: {}", lote.getId(), protocolo);
        } else {
            lote.setEstado(EstadoLoteDE.ERROR_ENVIO);
            log.error("❌ Error al enviar lote {}: {} - {}", 
                        lote.getId(), codigoRespuesta, respuesta.getdMsgRes());
        }
        
        loteDEService.save(lote);
    }

    /**
     * Consulta el estado de un lote en SIFEN y actualiza los documentos según la respuesta.
     * 
     * @param lote El lote a consultar
     * @throws SifenException Si hay error en la consulta
     */
    @Transactional
    public void consultarLote(LoteDE lote) throws SifenException {
        verificarSifenHabilitado();
        log.info("🔍 Consultando lote ID: {} con protocolo: {}", lote.getId(), lote.getProtocolo());
        
            if (lote.getProtocolo() == null || lote.getProtocolo().isEmpty()) {
            throw new IllegalArgumentException("El lote " + lote.getId() + " no tiene protocolo para consultar");
            }
            
        // 1. Consultar estado en SIFEN
            RespuestaConsultaLoteDE respuesta = Sifen.consultaLoteDE(lote.getProtocolo());
            
        log.info("   📥 Respuesta recibida - Código: {}, Mensaje: {}", 
            respuesta.getdCodResLot(), respuesta.getdMsgResLot());
        
        // 2. Actualizar lote con respuesta
        lote.setFechaUltimoIntento(LocalDateTime.now());
        lote.setRespuestaSifen(respuesta.getRespuestaBruta());
        
        String codigoRespuesta = respuesta.getdCodResLot();
        
        // 3. Procesar según código de respuesta
        switch (codigoRespuesta) {
            case "0360": // Lote no existe
                log.error("❌ Lote {} no existe en SIFEN", lote.getId());
                lote.setEstado(EstadoLoteDE.ERROR_PERMANENTE);
                actualizarDocumentosLote(lote, EstadoDE.RECHAZADO, codigoRespuesta, respuesta.getdMsgResLot());
                break;
                
            case "0361": // Lote en procesamiento
                log.info("⏳ Lote {} aún en procesamiento", lote.getId());
                lote.setEstado(EstadoLoteDE.EN_PROCESO);
                break;
                
            case "0362": // Procesamiento concluido
                log.info("✅ Lote {} procesamiento concluido", lote.getId());
                procesarRespuestaLoteConcluido(lote, respuesta);
                break;
                
            default:
                log.warn("⚠️ Código de respuesta inesperado: {}", codigoRespuesta);
                lote.setEstado(EstadoLoteDE.ERROR_PERMANENTE);
                break;
        }
        
        loteDEService.save(lote);
    }

    /**
     * Consulta el estado de un documento electrónico individual en SIFEN.
     * Actualiza el estado del DE en BD basándose en la respuesta.
     * Extrae y actualiza eventos asociados (cancelación, etc.) si existen.
     * 
     * TRANSACCIÓN:
     * - Usa REQUIRES_NEW para que cada consulta sea independiente
     * - Si una consulta falla, no afecta a otras consultas ni a la transacción padre
     * - Permite procesar múltiples DEs sin que un error bloquee el resto
     * 
     * CÓDIGOS DE RESPUESTA MANEJADOS:
     * - 0422: CDC encontrado → Actualiza estado según respuesta (APROBADO/RECHAZADO)
     * - 0420: Documento no existe o rechazado → Marca como RECHAZADO
     * - 0421: Error en CDC (formato inválido) → Marca como RECHAZADO
     * 
     * ORDEN DE PROCESAMIENTO (importante para estados):
     * 1. Actualiza estado del DE según XML principal (APROBADO/RECHAZADO)
     * 2. Procesa eventos asociados (cancelación, etc.)
     * 3. Si existe evento de cancelación APROBADO, sobrescribe estado a CANCELADO
     * 
     * EJEMPLO:
     * - XML dice: DE está "Aprobado" (protocolo 2658109949)
     * - XML también contiene: Evento de cancelación "Aprobado" (protocolo 40975537)
     * - Estado final del DE: CANCELADO (porque el evento prevalece sobre el estado original)
     * 
     * RETRY:
     * - Implementa reintentos automáticos ante errores intermitentes de SIFEN
     * - Usa backoff exponencial (1s, 2s, 4s)
     * - Máximo 3 intentos por defecto
     * 
     * @param cdc El CDC del documento a consultar
     * @return Información del estado del documento
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public RespuestaConsultaDE consultarDE(String cdc) {
        verificarSifenHabilitado();
        return consultarDEConRetry(cdc, 3, 1000);
    }
    
    /**
     * Consulta DE con mecanismo de retry configurable.
     * 
     * @param cdc El CDC del documento a consultar
     * @param maxReintentos Número máximo de intentos (por defecto 3)
     * @param delayInicialMs Delay inicial en milisegundos (por defecto 1000)
     * @return Información del estado del documento
     */
    private RespuestaConsultaDE consultarDEConRetry(String cdc, int maxReintentos, long delayInicialMs) {
        verificarSifenHabilitado();
        log.info("🔍 Consultando DE con CDC: {}", cdc);
        
        int intento = 0;
        Exception ultimaException = null;
        
        while (intento < maxReintentos) {
            intento++;
            
            try {
                if (intento > 1) {
                    log.info("   🔄 Reintento {}/{} para CDC: {}", intento, maxReintentos, cdc);
                }
                
                RespuestaConsultaDE respuesta = Sifen.consultaDE(cdc);
                
                // Verificar si la respuesta es válida (no null)
                if (respuesta.getdCodRes() == null && respuesta.getdMsgRes() == null) {
                    throw new RuntimeException("Respuesta SOAP inválida - probablemente HTML en lugar de XML");
                }
                
                log.info("   📥 Respuesta recibida - Código: {}, Mensaje: {}", 
                    respuesta.getdCodRes(), respuesta.getdMsgRes());
                
                // Procesar respuesta según código
                String codigoRespuesta = respuesta.getdCodRes();
                
                switch (codigoRespuesta) {
                    case "0422": // CDC encontrado - Éxito
                        // Paso 1: Actualizar estado base del DE
                        actualizarEstadoDesdeRespuesta(cdc, respuesta.getRespuestaBruta());
                        
                        // Paso 2: Procesar eventos de cancelación (puede sobrescribir el estado si hay cancelación aprobada)
                        procesarEventosAsociados(cdc, respuesta.getRespuestaBruta());
                        
                        // Paso 3: Procesar eventos de nominación (actualiza cliente en factura si hay nominación aprobada)
                        procesarEventosNominacion(cdc, respuesta.getRespuestaBruta());
                        break;
                        
                    case "0420": // Documento no existe o fue rechazado
                        log.warn("   ⚠️ DE no encontrado o rechazado en SIFEN");
                        actualizarEstadoDENoEncontrado(cdc, codigoRespuesta, respuesta.getdMsgRes());
                        break;
                        
                    case "0421": // Error en CDC (formato inválido)
                        log.error("   ❌ CDC inválido según SIFEN");
                        actualizarEstadoDENoEncontrado(cdc, codigoRespuesta, respuesta.getdMsgRes());
                        break;
                        
                    default:
                        log.warn("   ⚠️ Código de respuesta no esperado: {}", codigoRespuesta);
                        break;
                }
                
                // Éxito - retornar respuesta
                if (intento > 1) {
                    log.info("   ✅ Consulta exitosa después de {} intento(s)", intento);
                }
                return respuesta;
                
            } catch (SifenException e) {
                ultimaException = e;
                log.warn("   ⚠️ Intento {}/{} falló: {}", intento, maxReintentos, e.getMessage());
                
            } catch (RuntimeException e) {
                ultimaException = e;
                // Errores de parsing SOAP (HTML en lugar de XML)
                if (e.getMessage() != null && 
                    (e.getMessage().contains("envelope") || 
                     e.getMessage().contains("SOAP") ||
                     e.getMessage().contains("inválida"))) {
                    log.warn("   ⚠️ Intento {}/{} falló - Error de parsing SOAP (probablemente HTML en respuesta)", 
                        intento, maxReintentos);
                } else {
                    log.warn("   ⚠️ Intento {}/{} falló: {}", intento, maxReintentos, e.getMessage());
                }
                
            } catch (Exception e) {
                ultimaException = e;
                log.warn("   ⚠️ Intento {}/{} falló - Error inesperado: {}", 
                    intento, maxReintentos, e.getMessage());
            }
            
            // Si no es el último intento, esperar antes de reintentar (backoff exponencial)
            if (intento < maxReintentos) {
                long delay = delayInicialMs * (long) Math.pow(2, intento - 1);
                log.info("   ⏱️ Esperando {}ms antes del siguiente intento...", delay);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("   ❌ Retry interrumpido");
                    break;
                }
            }
        }
        
        // Todos los intentos fallaron
        log.error("❌ Error al consultar DE {} después de {} intentos", cdc, maxReintentos);
        throw new RuntimeException(
            String.format("Error al consultar DE en SIFEN después de %d intentos. CDC: %s", 
                maxReintentos, cdc), 
            ultimaException
        );
    }
    
    /**
     * Actualiza el estado de un DE que no fue encontrado o fue rechazado en SIFEN.
     * 
     * Códigos manejados:
     * - 0420: Documento no existe en SIFEN o ha sido rechazado
     * - 0421: Error en CDC (formato inválido)
     * 
     * @param cdc CDC del documento
     * @param codigoRespuesta Código de respuesta de SIFEN
     * @param mensajeRespuesta Mensaje de respuesta de SIFEN
     */
    private void actualizarEstadoDENoEncontrado(String cdc, String codigoRespuesta, String mensajeRespuesta) {
        log.info("   🔄 Actualizando estado del DE no encontrado/rechazado...");
        
        try {
            // Buscar el DE en la base de datos
            com.franco.dev.domain.financiero.DocumentoElectronico de = 
                documentoElectronicoService.findByCdc(cdc).orElse(null);
            
            if (de == null) {
                log.warn("   ⚠️ DE no encontrado en BD local - no se actualizará");
                return;
            }
            
            EstadoDE estadoAnterior = de.getEstado();
            log.info("   📊 Estado actual en BD: {}", estadoAnterior);
            
            // Solo actualizar si el estado actual indica que debería estar en SIFEN
            // No actualizar si ya está en PENDIENTE, ERROR, etc.
            if (estadoAnterior == EstadoDE.APROBADO || 
                estadoAnterior == EstadoDE.EN_LOTE ||
                estadoAnterior == EstadoDE.CANCELADO) {
                
                // Código 0420: Documento no existe o fue rechazado
                // Esto puede significar:
                // 1. Nunca fue enviado a SIFEN (aunque nuestro registro dice que sí)
                // 2. Fue rechazado por SIFEN
                // 3. Fue cancelado y eliminado de SIFEN
                
                de.setEstado(EstadoDE.RECHAZADO);
                de.setCodigoRespuestaSifen(codigoRespuesta);
                de.setMensajeRespuestaSifen(mensajeRespuesta);
                de.setFechaRecepcionSifen(LocalDateTime.now());
                
                documentoElectronicoService.save(de);
                
                log.warn("   ⚠️ Estado actualizado: {} → RECHAZADO", estadoAnterior);
                log.warn("   📝 Motivo: {} - {}", codigoRespuesta, mensajeRespuesta);
                
            } else {
                log.info("   ℹ️ Estado {} no requiere actualización para código {}", 
                    estadoAnterior, codigoRespuesta);
            }
            
        } catch (Exception e) {
            log.error("   ❌ Error al actualizar estado de DE no encontrado: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Actualiza el estado del DocumentoElectronico basándose en la respuesta de SIFEN.
     * 
     * Estados posibles según SIFEN:
     * - "Aprobado" -> APROBADO
     * - "Aprobado con observación" -> APROBADO (ej: extemporáneo)
     * - "Rechazado" -> RECHAZADO
     */
    private void actualizarEstadoDesdeRespuesta(String cdc, String xmlRespuesta) {
        log.info("   🔄 Actualizando estado del DE desde respuesta SIFEN...");
        
        try {
            // Buscar el DE en la base de datos
            com.franco.dev.domain.financiero.DocumentoElectronico de = 
                documentoElectronicoService.findByCdc(cdc).orElse(null);
            
            if (de == null) {
                log.warn("   ⚠️ DE no encontrado en BD local - no se actualizará estado");
                return;
            }
            
            // Extraer protocolo de autorización si existe
            String protocoloAutorizacion = extraerValorXML(xmlRespuesta, "<dProtAut>", "</dProtAut>");
            if (protocoloAutorizacion != null && !protocoloAutorizacion.isEmpty()) {
                log.info("   📋 Protocolo de autorización: {}", protocoloAutorizacion);
            }
            
            // Extraer estado del resultado (dEstRes) del DE
            // Este campo indica si el documento fue Aprobado, Aprobado con observación, o Rechazado
            String estadoResultado = extraerEstadoResultadoDE(xmlRespuesta);
            
            if (estadoResultado == null) {
                log.warn("   ⚠️ No se pudo determinar estado del DE desde respuesta");
                return;
            }
            
            log.info("   📊 Estado en SIFEN: {}", estadoResultado);
            
            // Mapear estado de SIFEN a estado local
            EstadoDE nuevoEstado = null;
            boolean actualizar = false;
            
            if ("Aprobado".equalsIgnoreCase(estadoResultado) || 
                estadoResultado.toLowerCase().contains("aprobado con observación") ||
                estadoResultado.toLowerCase().contains("aprobado con observacion")) {
                
                // Solo actualizar a APROBADO si no está ya en un estado final más específico
                if (de.getEstado() != EstadoDE.APROBADO && 
                    de.getEstado() != EstadoDE.CANCELADO) {
                    nuevoEstado = EstadoDE.APROBADO;
                    actualizar = true;
                    log.info("   ✅ DE aprobado por SIFEN");
                }
                
            } else if ("Rechazado".equalsIgnoreCase(estadoResultado)) {
                
                // Actualizar a RECHAZADO si no está ya en ese estado
                if (de.getEstado() != EstadoDE.RECHAZADO) {
                    nuevoEstado = EstadoDE.RECHAZADO;
                    actualizar = true;
                    log.info("   ❌ DE rechazado por SIFEN");
                }
            }
            
            // Actualizar en BD si corresponde
            if (actualizar && nuevoEstado != null) {
                de.setEstado(nuevoEstado);
                de.setFechaRecepcionSifen(LocalDateTime.now());
                documentoElectronicoService.save(de);
                log.info("   💾 Estado del DE actualizado a: {}", nuevoEstado);
            } else {
                log.info("   ℹ️ Estado del DE no requiere actualización (actual: {})", de.getEstado());
            }
            
        } catch (Exception e) {
            log.error("   ❌ Error al actualizar estado del DE: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Extrae el estado del resultado (dEstRes) desde el XML de respuesta.
     * Este campo indica si el documento fue "Aprobado", "Aprobado con observación", o "Rechazado".
     * 
     * Nota: En respuestas de consulta individual, el estado puede venir en diferentes partes del XML.
     * Para consulta de lote viene en gResProcLote/dEstRes.
     * Para consulta individual puede venir implícito en la presencia de protocolo y ausencia de errores.
     */
    private String extraerEstadoResultadoDE(String xmlRespuesta) {
        if (xmlRespuesta == null) {
            return null;
        }
        
        try {
            // Intentar extraer de gResProcLote (respuesta de lote)
            String estadoLote = extraerValorXML(xmlRespuesta, "<ns2:dEstRes>", "</ns2:dEstRes>");
            if (estadoLote != null && !estadoLote.isEmpty()) {
                return estadoLote;
            }
            
            // Intentar sin namespace
            estadoLote = extraerValorXML(xmlRespuesta, "<dEstRes>", "</dEstRes>");
            if (estadoLote != null && !estadoLote.isEmpty()) {
                return estadoLote;
            }
            
            // En consultas individuales exitosas (0422), si hay protocolo de autorización
            // y no hay códigos de error, el documento está aprobado
            String protocolo = extraerValorXML(xmlRespuesta, "<dProtAut>", "</dProtAut>");
            if (protocolo != null && !protocolo.isEmpty() && !protocolo.equals("0000000000")) {
                log.debug("   🔍 Estado inferido como 'Aprobado' por presencia de protocolo válido");
                return "Aprobado";
            }
            
            // Si no se puede determinar, retornar null
            return null;
            
        } catch (Exception e) {
            log.error("   ❌ Error al extraer estado resultado: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Procesa eventos de cancelación asociados a un DE desde la respuesta de consulta.
     */
    private void procesarEventosAsociados(String cdc, String xmlRespuesta) {
        log.info("   🔍 Buscando eventos de cancelación asociados al DE...");
        
        try {
            // Buscar el DE en la base de datos
            com.franco.dev.domain.financiero.DocumentoElectronico de = 
                documentoElectronicoService.findByCdc(cdc).orElse(null);
            
            if (de == null) {
                log.warn("   ⚠️ DE no encontrado en BD local - no se actualizarán eventos");
                return;
            }
            
            // Extraer eventos de cancelación
            List<SifenEventoParser.EventoCancelacion> eventosCancelacion = 
                SifenEventoParser.extraerEventosCancelacion(xmlRespuesta);
            
            if (eventosCancelacion.isEmpty()) {
                log.info("   ℹ️ No se encontraron eventos de cancelación asociados");
                return;
            }
            
            log.info("   📋 Se encontraron {} evento(s) de cancelación", eventosCancelacion.size());
            
            // Procesar cada evento
            for (SifenEventoParser.EventoCancelacion eventoParsed : eventosCancelacion) {
                procesarEventoCancelacion(de, eventoParsed);
            }
            
            // Si hay eventos aprobados, actualizar estado del DE
            if (eventoCancelacionDEService.tieneCancelacionAprobada(de.getId())) {
                de.setEstado(EstadoDE.CANCELADO);
                documentoElectronicoService.save(de);
                log.info("   ✅ DE actualizado a estado CANCELADO por evento aprobado");
                
                // ✅ ACTUALIZAR FACTURA LEGAL - MARCAR COMO INACTIVA
                FacturaLegal factura = de.getFacturaLegal();
                if (factura != null) {
                    factura.setActivo(false);
                    facturaLegalService.save(factura);
                    log.info("   📄 Factura Legal ID {} marcada como INACTIVA", factura.getId());
                }
            }
            
        } catch (Exception e) {
            log.error("   ❌ Error al procesar eventos de cancelación: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Procesa eventos de nominación asociados a un DE desde la respuesta de consulta.
     * 
     * Este método es crucial para recuperación de datos en caso de que:
     * - Se haya enviado exitosamente una nominación a SIFEN
     * - Pero hubo un error de BD local que impidió registrar el evento
     * 
     * Al consultar el DE posteriormente, si detecta un evento de nominación aprobado
     * sin registro local, lo crea automáticamente y actualiza la factura.
     */
    private void procesarEventosNominacion(String cdc, String xmlRespuesta) {
        log.info("   🔍 Buscando eventos de nominación asociados al DE...");
        
        try {
            // Buscar el DE en la base de datos
            com.franco.dev.domain.financiero.DocumentoElectronico de = 
                documentoElectronicoService.findByCdc(cdc).orElse(null);
            
            if (de == null) {
                log.warn("   ⚠️ DE no encontrado en BD local - no se procesarán eventos de nominación");
                return;
            }
            
            // Extraer eventos de nominación
            List<SifenEventoParser.EventoNominacion> eventosNominacion = 
                SifenEventoParser.extraerEventosNominacion(xmlRespuesta);
            
            if (eventosNominacion.isEmpty()) {
                log.info("   ℹ️ No se encontraron eventos de nominación asociados");
                return;
            }
            
            log.info("   📋 Se encontraron {} evento(s) de nominación", eventosNominacion.size());
            
            // Procesar cada evento
            for (SifenEventoParser.EventoNominacion eventoParsed : eventosNominacion) {
                procesarEventoNominacion(de, eventoParsed);
            }
            
        } catch (Exception e) {
            log.error("   ❌ Error al procesar eventos de nominación: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Procesa un evento de cancelación individual.
     */
    private void procesarEventoCancelacion(
            com.franco.dev.domain.financiero.DocumentoElectronico de, 
            SifenEventoParser.EventoCancelacion eventoParsed) {
        
        try {
            // Buscar si el evento ya existe en BD
            EventoCancelacionDE eventoExistente = 
                eventoCancelacionDEService.findByEventoId(eventoParsed.getEventoId()).orElse(null);
            
            if (eventoExistente != null) {
                // Actualizar evento existente con datos de SIFEN
                log.info("      🔄 Actualizando evento existente ID: {}", eventoExistente.getId());
                
                if (eventoParsed.getFechaProcesamiento() != null) {
                    eventoExistente.setFechaProcesamiento(eventoParsed.getFechaProcesamiento());
                }
                if (eventoParsed.getProtocoloAutorizacion() != null) {
                    eventoExistente.setProtocoloAutorizacion(eventoParsed.getProtocoloAutorizacion());
                }
                if (eventoParsed.getCodigoRespuesta() != null) {
                    eventoExistente.setCodigoRespuesta(eventoParsed.getCodigoRespuesta());
                }
                if (eventoParsed.getMensajeRespuesta() != null) {
                    eventoExistente.setMensajeRespuesta(eventoParsed.getMensajeRespuesta());
                }
                
                // Actualizar estado según resultado
                if ("Aprobado".equalsIgnoreCase(eventoParsed.getEstadoResultado())) {
                    eventoExistente.setEstado(EstadoEvento.APROBADO);
                    log.info("      ✅ Evento APROBADO - Protocolo: {}", eventoParsed.getProtocoloAutorizacion());
                } else if ("Rechazado".equalsIgnoreCase(eventoParsed.getEstadoResultado())) {
                    eventoExistente.setEstado(EstadoEvento.RECHAZADO);
                    log.info("      ❌ Evento RECHAZADO - Motivo: {}", eventoParsed.getMensajeRespuesta());
                }
                
                eventoCancelacionDEService.save(eventoExistente);
                
            } else {
                // Crear nuevo evento si no existe (caso raro, pero posible si se perdió el registro)
                log.info("      ➕ Creando nuevo registro de evento desde respuesta SIFEN");
                
                EventoCancelacionDE nuevoEvento = new EventoCancelacionDE();
                nuevoEvento.setDocumentoElectronico(de);
                nuevoEvento.setEventoId(eventoParsed.getEventoId());
                nuevoEvento.setFechaFirma(eventoParsed.getFechaFirma());
                nuevoEvento.setCdcDocumento(eventoParsed.getCdcDocumento());
                nuevoEvento.setMotivoCancelacion(eventoParsed.getMotivoCancelacion());
                nuevoEvento.setFechaProcesamiento(eventoParsed.getFechaProcesamiento());
                nuevoEvento.setProtocoloAutorizacion(eventoParsed.getProtocoloAutorizacion());
                nuevoEvento.setCodigoRespuesta(eventoParsed.getCodigoRespuesta());
                nuevoEvento.setMensajeRespuesta(eventoParsed.getMensajeRespuesta());
                nuevoEvento.setActivo(true);
                
                // Asignar sucursal actual
                Sucursal sucursalActual = sucursalService.sucursalActual();
                if (sucursalActual != null) {
                    nuevoEvento.setSucursal(sucursalActual);
                }
                
                // Determinar estado
                if ("Aprobado".equalsIgnoreCase(eventoParsed.getEstadoResultado())) {
                    nuevoEvento.setEstado(EstadoEvento.APROBADO);
                } else if ("Rechazado".equalsIgnoreCase(eventoParsed.getEstadoResultado())) {
                    nuevoEvento.setEstado(EstadoEvento.RECHAZADO);
                } else {
                    nuevoEvento.setEstado(EstadoEvento.PENDIENTE);
                }
                
                eventoCancelacionDEService.save(nuevoEvento);
                log.info("      ✅ Nuevo evento creado - ID: {}", nuevoEvento.getId());
            }
            
        } catch (Exception e) {
            log.error("      ❌ Error al procesar evento individual: {}", e.getMessage());
        }
    }
    
    /**
     * Procesa un evento de nominación individual.
     * 
     * Si el evento no existe en BD, lo crea automáticamente.
     * Si el evento fue APROBADO, busca o crea el cliente y actualiza la factura.
     */
    private void procesarEventoNominacion(
            com.franco.dev.domain.financiero.DocumentoElectronico de, 
            SifenEventoParser.EventoNominacion eventoParsed) {
        
        try {
            // Buscar si el evento ya existe en BD
            EventoNominacionDE eventoExistente = 
                eventoNominacionDEService.findByEventoId(eventoParsed.getEventoId()).orElse(null);
            
            if (eventoExistente != null) {
                // Actualizar evento existente con datos de SIFEN
                log.info("      🔄 Evento de nominación ya existe - ID BD: {}", eventoExistente.getId());
                
                if (eventoParsed.getFechaProcesamiento() != null) {
                    eventoExistente.setFechaProcesamiento(eventoParsed.getFechaProcesamiento());
                }
                if (eventoParsed.getProtocoloAutorizacion() != null) {
                    eventoExistente.setProtocoloAutorizacion(eventoParsed.getProtocoloAutorizacion());
                }
                if (eventoParsed.getCodigoRespuesta() != null) {
                    eventoExistente.setCodigoRespuesta(eventoParsed.getCodigoRespuesta());
                }
                if (eventoParsed.getMensajeRespuesta() != null) {
                    eventoExistente.setMensajeRespuesta(eventoParsed.getMensajeRespuesta());
                }
                
                // Actualizar estado según resultado
                if ("Aprobado".equalsIgnoreCase(eventoParsed.getEstadoResultado())) {
                    eventoExistente.setEstado(EstadoEvento.APROBADO);
                    log.info("      ✅ Evento APROBADO - Protocolo: {}", eventoParsed.getProtocoloAutorizacion());
                } else if ("Rechazado".equalsIgnoreCase(eventoParsed.getEstadoResultado())) {
                    eventoExistente.setEstado(EstadoEvento.RECHAZADO);
                    log.info("      ❌ Evento RECHAZADO - Motivo: {}", eventoParsed.getMensajeRespuesta());
                }
                
                eventoNominacionDEService.save(eventoExistente);
                return; // Ya existe, no hacer más
            }
            
            // =========== CREAR NUEVO EVENTO - CASO DE RECUPERACIÓN ===========
            log.info("      ➕ Evento de nominación NO encontrado en BD - Creando desde respuesta SIFEN (recuperación)");
            
            FacturaLegal factura = de.getFacturaLegal();
            if (factura == null) {
                log.error("      ❌ DE sin factura asociada - no se puede procesar nominación");
                return;
            }
            
            // Buscar o crear el cliente basándose en los datos del evento
            Cliente cliente = buscarOCrearClienteDesdeEvento(eventoParsed);
            
            if (cliente == null) {
                log.error("      ❌ No se pudo encontrar/crear cliente para nominación");
                return;
            }
            
            log.info("      ✅ Cliente identificado: ID={}, Nombre={}", 
                cliente.getId(), 
                cliente.getPersona() != null ? cliente.getPersona().getNombre() : "N/A");
            
            // Crear nuevo evento de nominación
            EventoNominacionDE nuevoEvento = new EventoNominacionDE();
            nuevoEvento.setDocumentoElectronico(de);
            nuevoEvento.setEventoId(eventoParsed.getEventoId());
            nuevoEvento.setFechaFirma(eventoParsed.getFechaFirma());
            nuevoEvento.setCdcDocumento(eventoParsed.getCdcDocumento());
            nuevoEvento.setCliente(cliente);
            nuevoEvento.setNombreReceptor(eventoParsed.getNombreReceptor());
            
            // Documento del receptor
            String documentoReceptor = eventoParsed.getRucReceptor();
            if (documentoReceptor != null && eventoParsed.getDvReceptor() != null) {
                documentoReceptor = documentoReceptor + "-" + eventoParsed.getDvReceptor();
            } else if (eventoParsed.getNumeroDocumento() != null) {
                documentoReceptor = eventoParsed.getNumeroDocumento();
            }
            nuevoEvento.setDocumentoReceptor(documentoReceptor);
            nuevoEvento.setTipoReceptor(eventoParsed.getTipoReceptor());
            nuevoEvento.setTotalFactura(eventoParsed.getTotalFactura());
            nuevoEvento.setFechaEmision(eventoParsed.getFechaEmision());
            nuevoEvento.setFechaRecepcion(eventoParsed.getFechaRecepcion());
            nuevoEvento.setFechaProcesamiento(eventoParsed.getFechaProcesamiento());
            nuevoEvento.setProtocoloAutorizacion(eventoParsed.getProtocoloAutorizacion());
            nuevoEvento.setCodigoRespuesta(eventoParsed.getCodigoRespuesta());
            nuevoEvento.setMensajeRespuesta(eventoParsed.getMensajeRespuesta());
            nuevoEvento.setActivo(true);
            
            // Asignar sucursal actual
            Sucursal sucursalActual = sucursalService.sucursalActual();
            if (sucursalActual != null) {
                nuevoEvento.setSucursal(sucursalActual);
            }
            
            // Determinar estado
            if ("Aprobado".equalsIgnoreCase(eventoParsed.getEstadoResultado())) {
                nuevoEvento.setEstado(EstadoEvento.APROBADO);
                
                // ✅ ACTUALIZAR FACTURA CON EL CLIENTE NOMINADO
                factura.setCliente(cliente);
                facturaLegalService.save(factura);
                
                log.info("      ✅ Evento APROBADO - Factura ID {} actualizada con cliente ID {}", 
                    factura.getId(), cliente.getId());
                log.info("      📋 Protocolo: {}", eventoParsed.getProtocoloAutorizacion());
                
            } else if ("Rechazado".equalsIgnoreCase(eventoParsed.getEstadoResultado())) {
                nuevoEvento.setEstado(EstadoEvento.RECHAZADO);
                log.info("      ❌ Evento RECHAZADO - Factura mantiene cliente NULL");
            } else {
                nuevoEvento.setEstado(EstadoEvento.PENDIENTE);
            }
            
            eventoNominacionDEService.save(nuevoEvento);
            log.info("      💾 Evento de nominación guardado en BD - ID: {}, Estado: {}", 
                nuevoEvento.getId(), nuevoEvento.getEstado());
            
        } catch (Exception e) {
            log.error("      ❌ Error al procesar evento de nominación: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Busca un cliente basándose en los datos del evento de nominación.
     * 
     * IMPORTANTE: En la BD, los documentos se guardan SIN guión ni DV.
     * Ejemplos:
     * - Evento trae: RUC=4043581, DV=4
     * - En BD está guardado: "4043581" (sin guión ni DV)
     * 
     * Estrategia:
     * 1. Extraer solo el número base (sin guión ni DV)
     * 2. Buscar por ese número
     * 3. Si no existe, retornar null (no crear automáticamente)
     */
    private Cliente buscarOCrearClienteDesdeEvento(SifenEventoParser.EventoNominacion eventoParsed) {
        try {
            String documentoBusqueda = null;
            
            // Determinar documento a buscar (SOLO EL NÚMERO BASE, SIN DV)
            if ("CONTRIBUYENTE".equals(eventoParsed.getTipoReceptor())) {
                // Para contribuyente: usar solo el RUC (sin el DV)
                documentoBusqueda = eventoParsed.getRucReceptor();
                log.info("      📋 Receptor CONTRIBUYENTE - RUC: {}, DV: {}", 
                    eventoParsed.getRucReceptor(), 
                    eventoParsed.getDvReceptor());
            } else {
                // Para no contribuyente: número de documento
                documentoBusqueda = eventoParsed.getNumeroDocumento();
                log.info("      📋 Receptor NO CONTRIBUYENTE - Doc: {}", documentoBusqueda);
            }
            
            if (documentoBusqueda == null || documentoBusqueda.isEmpty()) {
                log.warn("      ⚠️ No se pudo determinar documento del receptor");
                return null;
            }
            
            // LIMPIEZA: Remover cualquier guión o carácter no numérico (por si acaso)
            // En la BD se guarda solo números: "4043581", "80099482", etc.
            String documentoLimpio = documentoBusqueda.replaceAll("[^0-9]", "");
            
            log.info("      🔍 Buscando cliente con documento limpio: '{}' (original: '{}')", 
                documentoLimpio, documentoBusqueda);
            
            // Buscar cliente por documento de persona
            Cliente clienteEncontrado = clienteService.findByPersonaDocumento(documentoLimpio);
            
            if (clienteEncontrado != null) {
                log.info("      ✅ Cliente encontrado - ID: {}, Nombre: {}", 
                    clienteEncontrado.getId(),
                    clienteEncontrado.getPersona() != null ? clienteEncontrado.getPersona().getNombre() : "N/A");
                return clienteEncontrado;
            }
            
            // Cliente no encontrado
            log.warn("      ⚠️ Cliente NO encontrado con documento '{}'", documentoLimpio);
            log.warn("      💡 Posibles causas:");
            log.warn("         - El cliente no existe en la BD");
            log.warn("         - El documento está en formato diferente");
            log.warn("         - Verificar manualmente en la tabla personas.persona");
            log.warn("      📝 Para recuperar: Buscar cliente manualmente y volver a nominar");
            
            return null;
            
        } catch (Exception e) {
            log.error("      ❌ Error al buscar cliente: {}", e.getMessage(), e);
            return null;
        }
    }

    // ===================== MÉTODOS AUXILIARES PRIVADOS =====================

    /**
     * Procesa la respuesta de un lote que ha concluido su procesamiento (código 0362).
     * Analiza individualmente cada documento en la respuesta.
     */
    private void procesarRespuestaLoteConcluido(LoteDE lote, RespuestaConsultaLoteDE respuesta) {
        log.info("   🔍 Analizando detalles individuales de documentos...");
        
        // 1. Determinar si el lote fue aprobado o rechazado
        boolean loteAprobado = determinarAprobacionLoteDesdeXML(respuesta.getRespuestaBruta());
        
        // 2. Actualizar estado del lote
        EstadoLoteDE nuevoEstadoLote = loteAprobado ? EstadoLoteDE.PROCESADO : EstadoLoteDE.RECHAZADO;
        lote.setEstado(nuevoEstadoLote);
        lote.setFechaProcesado(LocalDateTime.now());
        
        log.info("   📊 Estado del lote: {}", nuevoEstadoLote);
        
        // 3. Extraer detalles individuales de cada documento
        List<DetalleDocumentoEnLote> detallesDocumentos = 
            extraerDetallesDocumentosDesdeXML(respuesta.getRespuestaBruta());
        
        if (detallesDocumentos.isEmpty()) {
            log.warn("   ⚠️ No se encontraron detalles individuales en la respuesta");
            // Fallback: usar estado general del lote
            EstadoDE estadoGeneral = loteAprobado ? EstadoDE.APROBADO : EstadoDE.RECHAZADO;
            actualizarDocumentosLote(lote, estadoGeneral, respuesta.getdCodResLot(), respuesta.getdMsgResLot());
            return;
        }
        
        log.info("   📄 {} documentos con detalles individuales", detallesDocumentos.size());
        
        // 4. Actualizar cada documento individualmente
        List<com.franco.dev.domain.financiero.DocumentoElectronico> documentos = 
            documentoElectronicoService.findByLoteDe(lote);
        
        int aprobados = 0, rechazados = 0;
        
        for (com.franco.dev.domain.financiero.DocumentoElectronico documento : documentos) {
            // Buscar detalle individual por CDC
            DetalleDocumentoEnLote detalle = detallesDocumentos.stream()
                .filter(d -> documento.getCdc().equals(d.cdc))
                .findFirst()
                .orElse(null);
            
            if (detalle != null) {
                // Determinar estado basado en detalle individual
                EstadoDE estadoIndividual = "Aprobado".equalsIgnoreCase(detalle.estado) 
                    ? EstadoDE.APROBADO 
                    : EstadoDE.RECHAZADO;
            
            documento.setEstado(estadoIndividual);
                documento.setCodigoRespuestaSifen(detalle.codigo);
                documento.setMensajeRespuestaSifen(detalle.mensaje);
                
                if (estadoIndividual == EstadoDE.APROBADO) {
                    aprobados++;
                    log.debug("      ✓ Documento {} APROBADO", documento.getCdc());
                } else {
                    rechazados++;
                    log.debug("      ✗ Documento {} RECHAZADO: {}", documento.getCdc(), detalle.mensaje);
                }
            } else {
                // No se encontró detalle, usar estado general
                EstadoDE estadoGeneral = loteAprobado ? EstadoDE.APROBADO : EstadoDE.RECHAZADO;
                documento.setEstado(estadoGeneral);
                log.warn("      ⚠️ Documento {} sin detalle individual - usando estado general", documento.getCdc());
            }
            
            documento.setFechaRecepcionSifen(LocalDateTime.now());
            documentoElectronicoService.save(documento);
        }
        
        log.info("   📊 Resumen: {} aprobados, {} rechazados de {} totales", 
            aprobados, rechazados, documentos.size());
        
        // 5. Ajustar estado final del lote si hay errores mixtos
        if (aprobados > 0 && rechazados > 0) {
            lote.setEstado(EstadoLoteDE.PROCESADO_CON_ERRORES);
            log.info("   ⚠️ Lote con resultados mixtos → PROCESADO_CON_ERRORES");
        }
    }

    /**
     * Actualiza el estado de todos los documentos de un lote.
     */
    private void actualizarDocumentosLote(LoteDE lote, EstadoDE nuevoEstado, 
            String codigoRespuesta, String mensajeRespuesta) {
        List<com.franco.dev.domain.financiero.DocumentoElectronico> documentos = 
            documentoElectronicoService.findByLoteDe(lote);
        
        for (com.franco.dev.domain.financiero.DocumentoElectronico documento : documentos) {
            documento.setEstado(nuevoEstado);
            documento.setCodigoRespuestaSifen(codigoRespuesta);
            documento.setMensajeRespuestaSifen(mensajeRespuesta);
            documento.setFechaRecepcionSifen(LocalDateTime.now());
            documentoElectronicoService.save(documento);
        }
        
        log.info("   Actualizados {} documentos del lote {} al estado {}", 
                documentos.size(), lote.getId(), nuevoEstado);
    }

    /**
     * Determina si un lote fue aprobado analizando el XML de respuesta.
     */
    private boolean determinarAprobacionLoteDesdeXML(String xmlRespuesta) {
        if (xmlRespuesta == null || !xmlRespuesta.contains("<ns2:dEstRes>")) {
            return false;
        }
        
        int startIndex = xmlRespuesta.indexOf("<ns2:dEstRes>") + 13;
        int endIndex = xmlRespuesta.indexOf("</ns2:dEstRes>");
        
        if (startIndex > 13 && endIndex > startIndex) {
            String estadoResultado = xmlRespuesta.substring(startIndex, endIndex).trim();
            return "Aprobado".equalsIgnoreCase(estadoResultado);
        }
        
        return false;
    }

    /**
     * Extrae los detalles individuales de cada documento desde el XML de respuesta.
     */
    private List<DetalleDocumentoEnLote> extraerDetallesDocumentosDesdeXML(String xmlRespuesta) {
        List<DetalleDocumentoEnLote> detalles = new ArrayList<>();
        
        try {
            String patronGResProcLote = "<ns2:gResProcLote>";
            int indiceInicio = 0;
            
            while (indiceInicio < xmlRespuesta.length()) {
                int inicioBloque = xmlRespuesta.indexOf(patronGResProcLote, indiceInicio);
                if (inicioBloque == -1) break;
                
                int finBloque = xmlRespuesta.indexOf("</ns2:gResProcLote>", inicioBloque);
                if (finBloque == -1) break;
                
                String bloqueDocumento = xmlRespuesta.substring(
                    inicioBloque, 
                    finBloque + "</ns2:gResProcLote>".length()
                );
                
                String cdc = extraerValorXML(bloqueDocumento, "<ns2:id>", "</ns2:id>");
                String estado = extraerValorXML(bloqueDocumento, "<ns2:dEstRes>", "</ns2:dEstRes>");
                String protocolo = extraerValorXML(bloqueDocumento, "<ns2:dProtAut>", "</ns2:dProtAut>");
                String codigo = extraerValorXML(bloqueDocumento, "<ns2:dCodRes>", "</ns2:dCodRes>");
                String mensaje = extraerValorXML(bloqueDocumento, "<ns2:dMsgRes>", "</ns2:dMsgRes>");
                
                if (cdc != null && !cdc.trim().isEmpty()) {
                    detalles.add(new DetalleDocumentoEnLote(cdc, estado, codigo, mensaje, protocolo));
                }
                
                indiceInicio = finBloque + "</ns2:gResProcLote>".length();
            }
        } catch (Exception e) {
            log.error("Error al extraer detalles de documentos desde XML: {}", e.getMessage(), e);
        }
        
        return detalles;
    }

    /**
     * Extrae un valor específico de un bloque XML.
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
            return null;
        }
    }

    /**
     * Extrae el protocolo de la respuesta XML de SIFEN.
     */
    private String extraerProtocoloDeRespuesta(String respuestaXml) {
        if (respuestaXml == null) {
            return null;
        }
        
        try {
            int startProtocolo = respuestaXml.indexOf("<ns2:dProtConsLote>") + 19;
            int endProtocolo = respuestaXml.indexOf("</ns2:dProtConsLote>");
            
            if (startProtocolo > 19 && endProtocolo > startProtocolo) {
                return respuestaXml.substring(startProtocolo, endProtocolo);
            }
        } catch (Exception e) {
            log.error("Error al extraer protocolo del XML: {}", e.getMessage());
        }
        
        return null;
    }

    /**
     * Reconstruye un DE desde la factura cuando no hay XML original disponible.
     */
    private com.roshka.sifen.core.beans.DocumentoElectronico reconstruirDEDesdeFactura(
            com.franco.dev.domain.financiero.DocumentoElectronico de) throws SifenException {
        
        FacturaLegal factura = de.getFacturaLegal();
        if (factura == null) {
            throw new IllegalArgumentException("Documento sin FacturaLegal asociada");
        }
        
        List<FacturaLegalItem> items = facturaLegalItemService.findByFacturaLegalId(factura.getId());
        if (items.isEmpty()) {
            throw new IllegalArgumentException("FacturaLegal sin items");
        }
        
        com.roshka.sifen.core.beans.DocumentoElectronico deSifen = 
            generarDEDesdeFacturaDatosReales(factura, items);
        
        // Forzar CDC original si difiere
        if (!de.getCdc().equals(deSifen.obtenerCDC())) {
            log.warn("CDC regenerado difiere del original - forzando CDC original");
            deSifen.setId(de.getCdc());
        }
        
        return deSifen;
    }

    /**
     * Genera un DocumentoElectronico de SIFEN desde una FacturaLegal usando datos reales.
     * Utiliza SifenReceptorHelper para configuración correcta del receptor.
     * 
     * @throws SifenException Si hay error al generar el DE
     */
    private com.roshka.sifen.core.beans.DocumentoElectronico generarDEDesdeFacturaDatosReales(
            FacturaLegal factura, List<FacturaLegalItem> items) throws SifenException {
        
        log.debug("Generando DE desde FacturaLegal ID: {}", factura.getId());
        
        // Grupo A - Identificación del DE
        com.roshka.sifen.core.beans.DocumentoElectronico DE = 
            new com.roshka.sifen.core.beans.DocumentoElectronico();
        DE.setdFecFirma(factura.getCreadoEn() != null ? factura.getCreadoEn() : LocalDateTime.now());
        DE.setdSisFact((short) 1);

        // Grupo B - Operación del DE
        TgOpeDE gOpeDE = new TgOpeDE();
        gOpeDE.setiTipEmi(TTipEmi.NORMAL);
        DE.setgOpeDE(gOpeDE);

        // Grupo C - Timbrado
        TgTimb gTimb = new TgTimb();
        gTimb.setiTiDE(TTiDE.FACTURA_ELECTRONICA);
        gTimb.setdNumTim(Integer.parseInt(factura.getTimbradoDetalle().getTimbrado().getNumero()));
        
        // Obtener código de establecimiento desde la sucursal (requerido)
        String codigoEstablecimiento = null;
        if (factura.getTimbradoDetalle().getSucursal() != null && 
            factura.getTimbradoDetalle().getSucursal().getCodigoEstablecimientoFactura() != null &&
            !factura.getTimbradoDetalle().getSucursal().getCodigoEstablecimientoFactura().trim().isEmpty()) {
            codigoEstablecimiento = factura.getTimbradoDetalle().getSucursal().getCodigoEstablecimientoFactura().trim();
            log.debug("   Código de establecimiento obtenido de sucursal: {}", codigoEstablecimiento);
        } else {
            String mensajeError = "No se puede obtener el código de establecimiento (dEst). ";
            if (factura.getTimbradoDetalle().getSucursal() == null) {
                mensajeError += "La factura no tiene una sucursal asociada en el timbradoDetalle.";
            } else if (factura.getTimbradoDetalle().getSucursal().getCodigoEstablecimientoFactura() == null ||
                       factura.getTimbradoDetalle().getSucursal().getCodigoEstablecimientoFactura().trim().isEmpty()) {
                mensajeError += String.format("La sucursal ID %d no tiene configurado el campo 'codigoEstablecimientoFactura'.", 
                    factura.getTimbradoDetalle().getSucursal().getId());
            }
            log.error("   ❌ {}", mensajeError);
            throw new IllegalArgumentException(mensajeError);
        }
        gTimb.setdEst(codigoEstablecimiento);
        
        gTimb.setdPunExp(factura.getTimbradoDetalle().getPuntoExpedicion());
        gTimb.setdNumDoc(String.format("%07d", factura.getNumeroFactura()));
        gTimb.setdFeIniT(factura.getTimbradoDetalle().getTimbrado().getFechaInicio().toLocalDate());
        DE.setgTimb(gTimb);

        // Grupo D - Datos Generales de la Operación
        TdDatGralOpe dDatGralOpe = new TdDatGralOpe();
        dDatGralOpe.setdFeEmiDE(factura.getFecha());

        TgOpeCom gOpeCom = new TgOpeCom();
        gOpeCom.setiTipTra(TTipTra.VENTA_MERCADERIA);
        gOpeCom.setiTImp(TTImp.IVA);
        gOpeCom.setcMoneOpe(CMondT.PYG);
        dDatGralOpe.setgOpeCom(gOpeCom);

        // Datos del Emisor
        TgEmis gEmis = construirDatosEmisor(factura);
        dDatGralOpe.setgEmis(gEmis);

        // Datos del Receptor - USANDO SifenReceptorHelper
        TgDatRec gDatRec = construirDatosReceptor(factura);
        dDatGralOpe.setgDatRec(gDatRec);
        
        DE.setgDatGralOpe(dDatGralOpe);

        // Grupo E - Items y condiciones
        TgDtipDE gDtipDE = construirDatosItems(factura, items);
        DE.setgDtipDE(gDtipDE);

        // Grupo F - Totales
        DE.setgTotSub(new TgTotSub());
        aplicarFixTotalesIVA(DE.getgTotSub());
        
        return DE;
    }

    /**
     * Construye los datos del emisor desde la factura.
     */
    private TgEmis construirDatosEmisor(FacturaLegal factura) {
        TgEmis gEmis = new TgEmis();
        
        String rucCompleto = factura.getTimbradoDetalle().getTimbrado().getRuc();
        String[] rucPartes = rucCompleto.split("-");
        gEmis.setdRucEm(rucPartes[0]);
        gEmis.setdDVEmi(rucPartes.length > 1 ? rucPartes[1] : "");
        gEmis.setiTipCont(tipoContribuyenteEmisor == 1 ? TiTipCont.PERSONA_FISICA : TiTipCont.PERSONA_JURIDICA);
        gEmis.setdNomEmi(factura.getTimbradoDetalle().getTimbrado().getRazonSocial());
        gEmis.setdDirEmi(factura.getTimbradoDetalle().getDireccion());
        gEmis.setdNumCas("0");
        gEmis.setdTelEmi(factura.getTimbradoDetalle().getTelefono());
        gEmis.setdEmailE(factura.getTimbradoDetalle().getTimbrado().getEmail());
        
        // Datos geográficos
        TDepartamento tdep = mapearDepartamento(factura.getTimbradoDetalle().getDepartamento());
        gEmis.setcDepEmi(tdep);
        gEmis.setcCiuEmi(Integer.parseInt(factura.getTimbradoDetalle().getCodigoCiudad()));
        gEmis.setdDesCiuEmi(factura.getTimbradoDetalle().getCiudad());

        // Actividades económicas
        List<TgActEco> gActEcoList = construirActividadesEconomicas(factura);
        gEmis.setgActEcoList(gActEcoList);
        
        return gEmis;
    }

    /**
     * Construye los datos del receptor usando SifenReceptorHelper.
     */
    private TgDatRec construirDatosReceptor(FacturaLegal factura) {
        // Usar SifenReceptorHelper para determinar configuración correcta
        SifenReceptorHelper.ConfiguracionReceptor config = 
            SifenReceptorHelper.determinarConfiguracionReceptor(
                factura.getCliente(), 
                factura.getTotalFinal()
            );
        
        // Mapear configuración a TgDatRec
        TgDatRec gDatRec = new TgDatRec();
        gDatRec.setiNatRec(config.iNatRec);
        gDatRec.setiTiOpe(config.iTiOpe);
        gDatRec.setdNomRec(config.dNomRec);
        gDatRec.setcPaisRec(config.cPaisRec);
        
        // Contribuyente
        if (config.iNatRec == TiNatRec.CONTRIBUYENTE) {
            gDatRec.setiTiContRec(config.iTiContRec);
            gDatRec.setdRucRec(config.dRucRec);
            gDatRec.setdDVRec(config.dDVRec);
            gDatRec.setiTipIDRec(TiTipDocRec.CEDULA_PARAGUAYA);
            gDatRec.setdNumIDRec(config.dRucRec);
        } 
        // No contribuyente
        else {
            gDatRec.setiTipIDRec(config.iTipIDRec);
            gDatRec.setdNumIDRec(config.dNumIDRec);
            if (config.dDTipIDRec != null) {
                gDatRec.setdDTipIDRec(config.dDTipIDRec);
            }
        }
        
        return gDatRec;
    }

    /**
     * Construye las actividades económicas del emisor.
     */
    private List<TgActEco> construirActividadesEconomicas(FacturaLegal factura) {
        List<TgActEco> gActEcoList = new ArrayList<>();
        
        // Actividad principal
        TgActEco gActEco = new TgActEco();
        gActEco.setcActEco(factura.getTimbradoDetalle().getTimbrado().getCodActividadEconomicaPrincipal());
        gActEco.setdDesActEco(factura.getTimbradoDetalle().getTimbrado().getDescActividadEconomicaPrincipal());
        gActEcoList.add(gActEco);
        
        // Actividades secundarias
        String[] codigosSecundarios = factura.getTimbradoDetalle().getTimbrado()
            .getListCodigoActividadEconomicaSecundaria().split(",");
        String[] descripcionesSecundarias = factura.getTimbradoDetalle().getTimbrado()
            .getListDescripcionActividadEconomicaSecundaria().split(",");
        
        for (int i = 0; i < codigosSecundarios.length && i < descripcionesSecundarias.length; i++) {
            TgActEco gActEcoSec = new TgActEco();
            gActEcoSec.setcActEco(codigosSecundarios[i].trim());
            gActEcoSec.setdDesActEco(descripcionesSecundarias[i].trim());
            gActEcoList.add(gActEcoSec);
        }
        
        return gActEcoList;
    }

    /**
     * Construye los campos de factura electrónica (gCamFE).
     * 
     * NOTA: Aunque detectamos entidades gubernamentales, las tratamos como B2B
     * porque no tenemos los datos necesarios para B2G (número de licitación, contrato, etc.)
     */
    private TgCamFE construirCamposFE(FacturaLegal factura) {
        TgCamFE gCamFE = new TgCamFE();
        gCamFE.setiIndPres(TiIndPres.OPERACION_PRESENCIAL);
        
        // Verificar si requiere configuración especial para compra pública (B2G)
        SifenReceptorHelper.ConfiguracionReceptor config = 
            SifenReceptorHelper.determinarConfiguracionReceptor(
                factura.getCliente(), 
                factura.getTotalFinal()
            );
        
        // Para B2G, configurar gCompPub (actualmente deshabilitado - tratamos como B2B)
        if (config.esCompraPública) {
            TgCompPub gCompPub = new TgCompPub();
            gCompPub.setdModCont("1"); // 1 = Contratación por licitación
            gCompPub.setdEntCont(1);    // Código de entidad contratante
            gCamFE.setgCompPub(gCompPub);
            log.debug("✅ Configurado gCompPub para B2G (compra pública)");
        }
        // Como esCompraPública siempre será false, este bloque nunca se ejecutará
        
        return gCamFE;
    }

    /**
     * Construye los datos de items y condiciones de pago.
     * 
     * IMPORTANTE: Para determinar el producto asociado a cada item, se usa la siguiente prioridad:
     * 1. Producto vinculado directamente al FacturaLegalItem (nuevo campo producto_id)
     * 2. Producto a través de VentaItem (para compatibilidad con facturas vinculadas a ventas)
     * 
     * Esta lógica permite que las facturas legales independientes (no vinculadas a ventas)
     * mantengan acceso a información crítica del producto como IVA y si es pesable.
     */
    private TgDtipDE construirDatosItems(FacturaLegal factura, List<FacturaLegalItem> items) {
        TgDtipDE gDtipDE = new TgDtipDE();

        // Configuración de factura electrónica (incluyendo compra pública si aplica)
        TgCamFE gCamFE = construirCamposFE(factura);
        gDtipDE.setgCamFE(gCamFE);

        TgCamCond gCamCond = new TgCamCond();
        boolean esCredito = factura.getCredito() != null && factura.getCredito();
        gCamCond.setiCondOpe(esCredito ? TiCondOpe.CREDITO : TiCondOpe.CONTADO);

        List<TgPaConEIni> gPaConEIniList = new ArrayList<>();
        TgPaConEIni gPaConEIni = new TgPaConEIni();
        gPaConEIni.setiTiPago(TiTiPago.EFECTIVO);
        gPaConEIni.setcMoneTiPag(CMondT.PYG);
        gPaConEIni.setdMonTiPag(BigDecimal.valueOf(factura.getTotalFinal()));
        gPaConEIniList.add(gPaConEIni);
        gCamCond.setgPaConEIniList(gPaConEIniList);
        
        if (esCredito) {
            TgPagCred gPagCred = new TgPagCred();
            gPagCred.setiCondCred(TiCondCred.PLAZO);
            gPagCred.setdPlazoCre("30 días");
            gCamCond.setgPagCred(gPagCred);
        }
        
        gDtipDE.setgCamCond(gCamCond);

        // Items
        List<TgCamItem> gCamItemList = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            FacturaLegalItem item = items.get(i);
            TgCamItem gCamItem = new TgCamItem();
            gCamItem.setdCodInt(String.format("%03d", i + 1));
            gCamItem.setdDesProSer(item.getDescripcion());

            // Prioridad 1: Producto vinculado directamente al FacturaLegalItem
            Producto producto = item.getProducto();
            
            // Prioridad 2: Producto a través de VentaItem (para compatibilidad con facturas vinculadas a ventas)
            if (producto == null && item.getVentaItem() != null) {
                producto = item.getVentaItem().getProducto();
            }

            BigDecimal cantidad;
            float cantidadFloat = item.getCantidad() != null ? item.getCantidad() : 0.0f;

            // Determinar unidad de medida basándose en si el producto es pesable
            if (producto != null && producto.getBalanza() != null && producto.getBalanza()) {
                gCamItem.setcUniMed(TcUniMed.kg);
                cantidad = new BigDecimal(Float.toString(cantidadFloat)).setScale(3, RoundingMode.HALF_UP);
                log.debug("   Producto {} marcado como pesable - usando kg", producto.getDescripcion());
            } else {
                gCamItem.setcUniMed(TcUniMed.UNI);
                cantidad = new BigDecimal(Float.toString(cantidadFloat)).setScale(0, RoundingMode.HALF_UP);
                log.debug("   Producto {} no es pesable - usando unidades", 
                    producto != null ? producto.getDescripcion() : "N/A");
            }
            gCamItem.setdCantProSer(cantidad);
            TgValorItem gValorItem = new TgValorItem();
            gValorItem.setdPUniProSer(BigDecimal.valueOf(item.getPrecioUnitario().doubleValue()));

            TgValorRestaItem gValorRestaItem = new TgValorRestaItem();
            gValorItem.setgValorRestaItem(gValorRestaItem);
            gCamItem.setgValorItem(gValorItem);

            TgCamIVA gCamIVA = new TgCamIVA();
            
            // Determinar IVA usando la misma prioridad que para el producto
            Integer iva = null;
            if (producto != null && producto.getIva() != null) {
                iva = producto.getIva();
                log.debug("   IVA obtenido del producto {}: {}%", producto.getDescripcion(), iva);
            } else {
                iva = 10; // Default to 10%
                log.debug("   IVA no disponible del producto - usando default: {}%", iva);
            }

            switch (iva) {
                case 5:
                    gCamIVA.setiAfecIVA(TiAfecIVA.GRAVADO);
                    gCamIVA.setdPropIVA(BigDecimal.valueOf(100));
                    gCamIVA.setdTasaIVA(BigDecimal.valueOf(5));
                    gCamIVA.setdBasExe(BigDecimal.ZERO);
                    log.debug("   Configurado IVA 5% para producto");
                    break;
                case 0:
                    gCamIVA.setiAfecIVA(TiAfecIVA.EXENTO);
                    log.debug("   Configurado IVA EXENTO para producto");
                    break;
                case 10:
                default:
                    gCamIVA.setiAfecIVA(TiAfecIVA.GRAVADO);
                    gCamIVA.setdPropIVA(BigDecimal.valueOf(100));
                    gCamIVA.setdTasaIVA(BigDecimal.valueOf(10));
                    gCamIVA.setdBasExe(BigDecimal.ZERO);
                    log.debug("   Configurado IVA 10% para producto");
                    break;
            }
            gCamItem.setgCamIVA(gCamIVA);

            gCamItemList.add(gCamItem);
        }

        gDtipDE.setgCamItemList(gCamItemList);
        return gDtipDE;
    }

    /**
     * Aplica fix para bug de totales IVA en la librería SIFEN.
     */
    private void aplicarFixTotalesIVA(TgTotSub totales) {
        try {
            // Fix para IVA 10%
            if (totales.getdIVA10() != null && totales.getdIVA10().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal valorIVA10 = totales.getdIVA10();
                BigDecimal valorActualLiq10 = totales.getdLiqTotIVA10();
                
                if (valorActualLiq10 == null || valorActualLiq10.compareTo(BigDecimal.ZERO) == 0) {
                    Field field = TgTotSub.class.getDeclaredField("dLiqTotIVA10");
                    field.setAccessible(true);
                    field.set(totales, valorIVA10);
                }
            }
            
            // Fix para IVA 5%
            if (totales.getdIVA5() != null && totales.getdIVA5().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal valorIVA5 = totales.getdIVA5();
                BigDecimal valorActualLiq5 = totales.getdLiqTotIVA5();
                
                if (valorActualLiq5 == null || valorActualLiq5.compareTo(BigDecimal.ZERO) == 0) {
                    Field field = TgTotSub.class.getDeclaredField("dLiqTotIVA5");
                    field.setAccessible(true);
                    field.set(totales, valorIVA5);
                }
            }
        } catch (Exception e) {
            log.error("Error al aplicar workaround de totales IVA: {}", e.getMessage());
        }
    }
    
    /**
     * Mapea el nombre del departamento a su enum correspondiente.
     */
    private TDepartamento mapearDepartamento(String departamento) {
        if (departamento == null) return TDepartamento.CAPITAL;
        
        switch (departamento.toUpperCase()) {
            case "CAPITAL": return TDepartamento.CAPITAL;
            case "CONCEPCION": case "CONCEPCIÓN": return TDepartamento.CONCEPCION;
            case "SAN PEDRO": return TDepartamento.SAN_PEDRO;
            case "CORDILLERA": return TDepartamento.CORDILLERA;
            case "GUAIRA": case "GUAIRÁ": return TDepartamento.GUAIRA;
            case "CAAGUAZU": case "CAAGUAZÚ": return TDepartamento.CAAGUAZU;
            case "CAAZAPA": case "CAAZAPÁ": return TDepartamento.CAAZAPA;
            case "ITAPUA": case "ITAPÚA": return TDepartamento.ITAPUA;
            case "MISIONES": return TDepartamento.MISIONES;
            case "PARAGUARI": case "PARAGUARÍ": return TDepartamento.PARAGUARI;
            case "ALTO PARANA": case "ALTO PARANÁ": return TDepartamento.ALTO_PARANA;
            case "CENTRAL": return TDepartamento.CENTRAL;
            case "ÑEEMBUCU": case "ÑEEMBUCÚ": return TDepartamento.NEEMBUCU;
            case "AMAMBAY": return TDepartamento.AMAMBAY;
            case "CANINDEYU": case "CANINDEYÚ": return TDepartamento.CANINDEYU;
            case "PRESIDENTE HAYES": case "HAYES": return TDepartamento.PTE_HAYES;
            case "BOQUERON": case "BOQUERÓN": return TDepartamento.BOQUERON;
            case "ALTO PARAGUAY": return TDepartamento.ALTO_PARAGUAY;
            default: return TDepartamento.CAPITAL;
        }
    }

    /**
     * Clase interna para almacenar detalles de documentos en lote.
     */
    private static class DetalleDocumentoEnLote {
        String cdc;
        String estado;
        String codigo;
        String mensaje;
        String protocoloAutorizacion;
        
        public DetalleDocumentoEnLote(String cdc, String estado, String codigo, 
                String mensaje, String protocoloAutorizacion) {
            this.cdc = cdc;
            this.estado = estado;
            this.codigo = codigo;
            this.mensaje = mensaje;
            this.protocoloAutorizacion = protocoloAutorizacion;
        }
    }
}
