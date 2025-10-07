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

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.domain.financiero.LoteDE;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.domain.financiero.enums.EstadoLoteDE;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.FacturaLegalItemService;
import com.franco.dev.service.financiero.LoteDEService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.sifen.util.SifenReceptorHelper;
import com.roshka.sifen.Sifen;
import com.roshka.sifen.core.beans.response.RespuestaConsultaDE;
import com.roshka.sifen.core.beans.response.RespuestaConsultaLoteDE;
import com.roshka.sifen.core.beans.response.RespuestaRecepcionLoteDE;
import com.roshka.sifen.core.exceptions.SifenException;
import com.roshka.sifen.core.fields.request.de.*;
import com.roshka.sifen.core.types.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
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
    private final com.roshka.sifen.core.SifenConfig sifenConfig;

    @Value("${tipoContribuyenteEmisor:2}")
    private Integer tipoContribuyenteEmisor;

    public SifenService(
            DocumentoElectronicoService documentoElectronicoService,
            LoteDEService loteDEService,
            FacturaLegalItemService facturaLegalItemService,
            ClienteService clienteService,
            com.roshka.sifen.core.SifenConfig sifenConfig) {
        this.documentoElectronicoService = documentoElectronicoService;
        this.loteDEService = loteDEService;
        this.facturaLegalItemService = facturaLegalItemService;
        this.clienteService = clienteService;
        this.sifenConfig = sifenConfig;
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
     * 
     * @param cdc El CDC del documento a consultar
     * @return Información del estado del documento
     */
    public RespuestaConsultaDE consultarDE(String cdc) {
        log.info("🔍 Consultando DE con CDC: {}", cdc);
        
        try {
            RespuestaConsultaDE respuesta = Sifen.consultaDE(cdc);
            log.info("   📥 Respuesta recibida - Código: {}, Mensaje: {}", 
                respuesta.getdCodRes(), respuesta.getdMsgRes());
            return respuesta;
        } catch (SifenException e) {
            log.error("❌ Error al consultar DE {}: {}", cdc, e.getMessage());
            throw new RuntimeException("Error al consultar DE en SIFEN", e);
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
        gTimb.setdEst("001");
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
            gCamItem.setcUniMed(TcUniMed.UNI);
            gCamItem.setdCantProSer(BigDecimal.valueOf(item.getCantidad().doubleValue()));

            TgValorItem gValorItem = new TgValorItem();
            gValorItem.setdPUniProSer(BigDecimal.valueOf(item.getPrecioUnitario().doubleValue()));

            TgValorRestaItem gValorRestaItem = new TgValorRestaItem();
            gValorItem.setgValorRestaItem(gValorRestaItem);
            gCamItem.setgValorItem(gValorItem);

            TgCamIVA gCamIVA = new TgCamIVA();
            gCamIVA.setiAfecIVA(TiAfecIVA.GRAVADO);
            gCamIVA.setdPropIVA(BigDecimal.valueOf(100));
            gCamIVA.setdTasaIVA(BigDecimal.valueOf(10));
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
