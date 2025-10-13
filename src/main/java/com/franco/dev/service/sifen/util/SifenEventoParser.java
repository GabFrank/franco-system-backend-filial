package com.franco.dev.service.sifen.util;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Utilidad para parsear eventos desde respuestas XML de SIFEN.
 * 
 * Extrae información de eventos de cancelación, inutilización y nominación
 * desde el XML de respuesta de consultas de DE.
 */
@Slf4j
public class SifenEventoParser {

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
    };

    /**
     * Extrae todos los eventos de cancelación desde una respuesta XML de consulta DE.
     */
    public static List<EventoCancelacion> extraerEventosCancelacion(String xmlRespuesta) {
        List<EventoCancelacion> eventos = new ArrayList<>();
        
        if (xmlRespuesta == null || xmlRespuesta.isEmpty()) {
            return eventos;
        }

        try {
            // Buscar el contenedor de eventos (con o sin namespace)
            // Puede ser <xContEv> o <ns2:xContEv>
            int inicioContEv = buscarTag(xmlRespuesta, "xContEv", true);
            if (inicioContEv == -1) {
                log.debug("No se encontró contenedor xContEv en la respuesta");
                return eventos;
            }

            int finContEv = buscarTag(xmlRespuesta, "xContEv", false, inicioContEv);
            if (finContEv == -1) return eventos;

            String bloqueEventos = xmlRespuesta.substring(inicioContEv, finContEv + 20); // +20 para incluir </xContEv> o </ns2:xContEv>

            log.debug("Bloque de eventos encontrado, buscando rGesEve...");

            // Buscar eventos de cancelación (rGeVeCan)
            int indiceInicio = 0;
            while (indiceInicio < bloqueEventos.length()) {
                int inicioGesEve = buscarTag(bloqueEventos, "rGesEve", true, indiceInicio);
                if (inicioGesEve == -1) break;

                int finGesEve = buscarTag(bloqueEventos, "rGesEve", false, inicioGesEve);
                if (finGesEve == -1) break;

                String bloqueGesEve = bloqueEventos.substring(inicioGesEve, finGesEve + 15); // +15 para incluir </rGesEve>

                // Verificar si es un evento de cancelación
                if (bloqueGesEve.contains("rGeVeCan")) {
                    log.debug("Encontrado evento de cancelación, extrayendo datos...");
                    EventoCancelacion evento = extraerDatosCancelacion(bloqueGesEve, bloqueEventos);
                    if (evento != null) {
                        eventos.add(evento);
                        log.info("✅ Evento de cancelación extraído - ID: {}, CDC: {}, Estado: {}", 
                            evento.getEventoId(), evento.getCdcDocumento(), evento.getEstadoResultado());
                    }
                }

                indiceInicio = finGesEve + 15;
            }

        } catch (Exception e) {
            log.error("Error al extraer eventos de cancelación desde XML: {}", e.getMessage(), e);
        }

        log.info("Total de eventos de cancelación encontrados: {}", eventos.size());
        return eventos;
    }
    
    /**
     * Busca un tag XML con o sin namespace.
     * @param xml El XML donde buscar
     * @param tagName El nombre del tag (sin < ni >)
     * @param isOpenTag true para tag de apertura, false para cierre
     * @return índice donde comienza el tag, o -1 si no se encuentra
     */
    private static int buscarTag(String xml, String tagName, boolean isOpenTag) {
        return buscarTag(xml, tagName, isOpenTag, 0);
    }
    
    /**
     * Busca un tag XML con o sin namespace, desde un índice específico.
     */
    private static int buscarTag(String xml, String tagName, boolean isOpenTag, int fromIndex) {
        // Intentar con namespace común (ns2:, ns3:, etc)
        String[] prefijos = {"", "ns2:", "ns3:", "ns:", "rshk:"};
        
        for (String prefijo : prefijos) {
            String tag = isOpenTag ? "<" + prefijo + tagName : "</" + prefijo + tagName;
            int index = xml.indexOf(tag, fromIndex);
            if (index != -1) {
                return index;
            }
        }
        
        return -1;
    }

    /**
     * Extrae los datos de un evento de cancelación desde un bloque XML.
     */
    private static EventoCancelacion extraerDatosCancelacion(String bloqueGesEve, String bloqueCompleto) {
        try {
            EventoCancelacion evento = new EventoCancelacion();

            // Datos del evento (rEve) - extraer ID del atributo
            // El ID puede venir como <rEve _Id="1"> o <ns2:rEve _Id="1">
            String eventoId = extraerValorXML(bloqueGesEve, "rEve", "_Id=\"", "\"");
            
            // Fallback: si no encuentra _Id, intentar con Id (sin guión bajo)
            if (eventoId == null || eventoId.isEmpty()) {
                eventoId = extraerValorXML(bloqueGesEve, "rEve", "Id=\"", "\"");
            }
            
            // Si aún no hay ID, buscar en el CDC del documento para generar uno único
            if (eventoId == null || eventoId.isEmpty()) {
                // Extraer CDC del documento para usarlo como parte del ID
                String cdcTemp = extraerValorXML(bloqueGesEve, "Id>", "</Id>");
                if (cdcTemp != null && !cdcTemp.isEmpty()) {
                    // Generar ID único basado en timestamp y CDC
                    eventoId = "EVT-" + System.currentTimeMillis() + "-" + cdcTemp.substring(0, Math.min(8, cdcTemp.length()));
                    log.warn("Evento sin _Id explícito, generando ID único: {}", eventoId);
                } else {
                    eventoId = "EVT-" + System.currentTimeMillis();
                    log.warn("Evento sin _Id ni CDC, generando ID genérico: {}", eventoId);
                }
            }
            
            evento.setEventoId(eventoId);
            log.debug("Evento ID extraído/generado: {}", eventoId);

            String fechaFirma = extraerValorXML(bloqueGesEve, "dFecFirma>", "</dFecFirma>");
            if (fechaFirma == null) {
                fechaFirma = extraerValorXML(bloqueGesEve, "<dFecFirma>", "</dFecFirma>");
            }
            evento.setFechaFirma(parsearFecha(fechaFirma));

            // Datos de cancelación (rGeVeCan) - el CDC del documento cancelado
            String cdcDocumento = extraerValorXML(bloqueGesEve, "Id>", "</Id>");
            if (cdcDocumento == null) {
                cdcDocumento = extraerValorXML(bloqueGesEve, "<Id>", "</Id>");
            }
            evento.setCdcDocumento(cdcDocumento);
            log.debug("CDC Documento extraído: {}", cdcDocumento);

            String motivo = extraerValorXML(bloqueGesEve, "mOtEve>", "</mOtEve>");
            if (motivo == null) {
                motivo = extraerValorXML(bloqueGesEve, "<mOtEve>", "</mOtEve>");
            }
            evento.setMotivoCancelacion(motivo);
            log.debug("Motivo extraído: {}", motivo);

            // Buscar la respuesta del evento (rResEnviEventoDe)
            // Esto está fuera de rGesEve, en el mismo nivel de bloqueCompleto
            int inicioResEvento = buscarTag(bloqueCompleto, "rResEnviEventoDe", true);
            if (inicioResEvento != -1) {
                int finResEvento = buscarTag(bloqueCompleto, "rResEnviEventoDe", false, inicioResEvento);
                if (finResEvento != -1) {
                    String bloqueRespuesta = bloqueCompleto.substring(inicioResEvento, finResEvento + 25);
                    log.debug("Bloque respuesta evento encontrado");

                    String fechaProcesamiento = extraerValorXML(bloqueRespuesta, "dFecProc>", "</dFecProc>");
                    if (fechaProcesamiento == null) {
                        fechaProcesamiento = extraerValorXML(bloqueRespuesta, "<dFecProc>", "</dFecProc>");
                    }
                    evento.setFechaProcesamiento(parsearFecha(fechaProcesamiento));

                    String estadoResultado = extraerValorXML(bloqueRespuesta, "dEstRes>", "</dEstRes>");
                    if (estadoResultado == null) {
                        estadoResultado = extraerValorXML(bloqueRespuesta, "<dEstRes>", "</dEstRes>");
                    }
                    evento.setEstadoResultado(estadoResultado);
                    log.debug("Estado resultado extraído: {}", estadoResultado);

                    String protocolo = extraerValorXML(bloqueRespuesta, "dProtAut>", "</dProtAut>");
                    if (protocolo == null) {
                        protocolo = extraerValorXML(bloqueRespuesta, "<dProtAut>", "</dProtAut>");
                    }
                    evento.setProtocoloAutorizacion(protocolo);
                    log.debug("Protocolo extraído: {}", protocolo);

                    String codigoRespuesta = extraerValorXML(bloqueRespuesta, "dCodRes>", "</dCodRes>");
                    if (codigoRespuesta == null) {
                        codigoRespuesta = extraerValorXML(bloqueRespuesta, "<dCodRes>", "</dCodRes>");
                    }
                    evento.setCodigoRespuesta(codigoRespuesta);

                    String mensajeRespuesta = extraerValorXML(bloqueRespuesta, "dMsgRes>", "</dMsgRes>");
                    if (mensajeRespuesta == null) {
                        mensajeRespuesta = extraerValorXML(bloqueRespuesta, "<dMsgRes>", "</dMsgRes>");
                    }
                    evento.setMensajeRespuesta(mensajeRespuesta);
                }
            } else {
                log.debug("No se encontró bloque rResEnviEventoDe en respuesta");
            }

            return evento;

        } catch (Exception e) {
            log.error("Error al extraer datos de cancelación: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Extrae un valor entre dos tags XML.
     * Maneja tags con o sin namespace.
     */
    private static String extraerValorXML(String xml, String tagInicio, String tagFin) {
        try {
            // Si el tag ya tiene < y >, usarlo directamente
            if (tagInicio.startsWith("<")) {
                int inicio = xml.indexOf(tagInicio);
                if (inicio == -1) return null;

                inicio += tagInicio.length();
                int fin = xml.indexOf(tagFin, inicio);
                if (fin == -1) return null;

                return xml.substring(inicio, fin).trim();
            }
            
            // Si no, intentar con diferentes namespaces
            String[] prefijos = {"<", "<ns2:", "<ns3:", "<ns:"};
            
            for (String prefijo : prefijos) {
                String tagCompletoInicio = prefijo + tagInicio;
                int inicio = xml.indexOf(tagCompletoInicio);
                
                if (inicio != -1) {
                    inicio += tagCompletoInicio.length();
                    
                    // Buscar cierre con mismo namespace
                    String tagCompletoFin = tagFin.replace("<", "<" + prefijo.substring(1));
                    int fin = xml.indexOf(tagCompletoFin, inicio);
                    
                    if (fin != -1) {
                        return xml.substring(inicio, fin).trim();
                    }
                }
            }
            
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extrae un valor de un atributo XML.
     * Busca el contexto (tag) con o sin namespace, y luego extrae el atributo.
     */
    private static String extraerValorXML(String xml, String contexto, String atributoInicio, String atributoFin) {
        try {
            // Intentar con diferentes namespaces para el contexto
            String[] prefijos = {"<", "<ns2:", "<ns3:", "<ns:"};
            
            for (String prefijo : prefijos) {
                String tagContexto = prefijo + contexto.replace("<", "");
                int inicioContexto = xml.indexOf(tagContexto);
                
                if (inicioContexto != -1) {
                    // Encontrar el final del tag de apertura (hasta el > o />)
                    int finTagApertura = xml.indexOf('>', inicioContexto);
                    if (finTagApertura == -1) continue;
                    
                    // Buscar el atributo dentro del tag de apertura
                    String tagCompleto = xml.substring(inicioContexto, finTagApertura + 1);
                    
                    int inicioAtributo = tagCompleto.indexOf(atributoInicio);
                    if (inicioAtributo == -1) continue;
                    
                    inicioAtributo += atributoInicio.length();
                    int finAtributo = tagCompleto.indexOf(atributoFin, inicioAtributo);
                    if (finAtributo == -1) continue;
                    
                    return tagCompleto.substring(inicioAtributo, finAtributo).trim();
                }
            }
            
            return null;
        } catch (Exception e) {
            log.error("Error al extraer atributo XML: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parsea una fecha en varios formatos posibles.
     */
    private static LocalDateTime parsearFecha(String fechaStr) {
        if (fechaStr == null || fechaStr.isEmpty()) {
            return null;
        }

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDateTime.parse(fechaStr, formatter);
            } catch (DateTimeParseException e) {
                // Intentar con el siguiente formato
            }
        }

        log.warn("No se pudo parsear fecha: {}", fechaStr);
        return null;
    }

    /**
     * Extrae todos los eventos de nominación desde una respuesta XML de consulta DE.
     */
    public static List<EventoNominacion> extraerEventosNominacion(String xmlRespuesta) {
        List<EventoNominacion> eventos = new ArrayList<>();
        
        if (xmlRespuesta == null || xmlRespuesta.isEmpty()) {
            return eventos;
        }

        try {
            // Buscar el contenedor de eventos
            int inicioContEv = buscarTag(xmlRespuesta, "xContEv", true);
            if (inicioContEv == -1) {
                log.debug("No se encontró contenedor xContEv en la respuesta");
                return eventos;
            }

            int finContEv = buscarTag(xmlRespuesta, "xContEv", false, inicioContEv);
            if (finContEv == -1) return eventos;

            String bloqueEventos = xmlRespuesta.substring(inicioContEv, finContEv + 20);

            log.debug("Bloque de eventos encontrado, buscando eventos de nominación...");

            // Buscar eventos de nominación (rGeVeNotRec)
            int indiceInicio = 0;
            while (indiceInicio < bloqueEventos.length()) {
                int inicioGesEve = buscarTag(bloqueEventos, "rGesEve", true, indiceInicio);
                if (inicioGesEve == -1) break;

                int finGesEve = buscarTag(bloqueEventos, "rGesEve", false, inicioGesEve);
                if (finGesEve == -1) break;

                String bloqueGesEve = bloqueEventos.substring(inicioGesEve, finGesEve + 15);

                // Verificar si es un evento de nominación
                if (bloqueGesEve.contains("rGeVeNotRec")) {
                    log.debug("Encontrado evento de nominación, extrayendo datos...");
                    EventoNominacion evento = extraerDatosNominacion(bloqueGesEve, bloqueEventos);
                    if (evento != null) {
                        eventos.add(evento);
                        log.info("✅ Evento de nominación extraído - ID: {}, CDC: {}, Receptor: {}, Estado: {}", 
                            evento.getEventoId(), evento.getCdcDocumento(), evento.getNombreReceptor(), evento.getEstadoResultado());
                    }
                }

                indiceInicio = finGesEve + 15;
            }

        } catch (Exception e) {
            log.error("Error al extraer eventos de nominación desde XML: {}", e.getMessage(), e);
        }

        log.info("Total de eventos de nominación encontrados: {}", eventos.size());
        return eventos;
    }

    /**
     * Extrae los datos de un evento de nominación desde un bloque XML.
     */
    private static EventoNominacion extraerDatosNominacion(String bloqueGesEve, String bloqueCompleto) {
        try {
            EventoNominacion evento = new EventoNominacion();

            // Datos del evento (rEve) - extraer ID del atributo
            String eventoId = extraerValorXML(bloqueGesEve, "rEve", "Id=\"", "\"");
            if (eventoId == null || eventoId.isEmpty()) {
                eventoId = extraerValorXML(bloqueGesEve, "rEve", "_Id=\"", "\"");
            }
            
            if (eventoId == null || eventoId.isEmpty()) {
                String cdcTemp = extraerValorXML(bloqueGesEve, "Id>", "</Id>");
                if (cdcTemp != null && !cdcTemp.isEmpty()) {
                    eventoId = "NOM-" + System.currentTimeMillis() + "-" + cdcTemp.substring(0, Math.min(8, cdcTemp.length()));
                } else {
                    eventoId = "NOM-" + System.currentTimeMillis();
                }
                log.warn("Evento de nominación sin Id explícito, generando: {}", eventoId);
            }
            
            evento.setEventoId(eventoId);

            String fechaFirma = extraerValorXML(bloqueGesEve, "dFecFirma>", "</dFecFirma>");
            if (fechaFirma == null) {
                fechaFirma = extraerValorXML(bloqueGesEve, "<dFecFirma>", "</dFecFirma>");
            }
            evento.setFechaFirma(parsearFecha(fechaFirma));

            // Datos de nominación (rGeVeNotRec)
            String cdcDocumento = extraerValorXML(bloqueGesEve, "Id>", "</Id>");
            if (cdcDocumento == null) {
                cdcDocumento = extraerValorXML(bloqueGesEve, "<Id>", "</Id>");
            }
            evento.setCdcDocumento(cdcDocumento);

            String nombreReceptor = extraerValorXML(bloqueGesEve, "dNomRec>", "</dNomRec>");
            if (nombreReceptor == null) {
                nombreReceptor = extraerValorXML(bloqueGesEve, "<dNomRec>", "</dNomRec>");
            }
            evento.setNombreReceptor(nombreReceptor);

            String fechaEmision = extraerValorXML(bloqueGesEve, "dFecEmi>", "</dFecEmi>");
            if (fechaEmision == null) {
                fechaEmision = extraerValorXML(bloqueGesEve, "<dFecEmi>", "</dFecEmi>");
            }
            evento.setFechaEmision(parsearFecha(fechaEmision));

            String fechaRecepcion = extraerValorXML(bloqueGesEve, "dFecRecep>", "</dFecRecep>");
            if (fechaRecepcion == null) {
                fechaRecepcion = extraerValorXML(bloqueGesEve, "<dFecRecep>", "</dFecRecep>");
            }
            evento.setFechaRecepcion(parsearFecha(fechaRecepcion));

            String tipoReceptor = extraerValorXML(bloqueGesEve, "iTipRec>", "</iTipRec>");
            if (tipoReceptor == null) {
                tipoReceptor = extraerValorXML(bloqueGesEve, "<iTipRec>", "</iTipRec>");
            }
            evento.setTipoReceptor("1".equals(tipoReceptor) ? "CONTRIBUYENTE" : "NO_CONTRIBUYENTE");

            // Para contribuyentes: RUC y DV
            String rucReceptor = extraerValorXML(bloqueGesEve, "dRucRec>", "</dRucRec>");
            if (rucReceptor == null) {
                rucReceptor = extraerValorXML(bloqueGesEve, "<dRucRec>", "</dRucRec>");
            }
            evento.setRucReceptor(rucReceptor);

            String dvReceptor = extraerValorXML(bloqueGesEve, "dDVRec>", "</dDVRec>");
            if (dvReceptor == null) {
                dvReceptor = extraerValorXML(bloqueGesEve, "<dDVRec>", "</dDVRec>");
            }
            evento.setDvReceptor(dvReceptor);

            // Para no contribuyentes: tipo y número de documento
            String tipoDocumento = extraerValorXML(bloqueGesEve, "dTipIDRec>", "</dTipIDRec>");
            if (tipoDocumento == null) {
                tipoDocumento = extraerValorXML(bloqueGesEve, "<dTipIDRec>", "</dTipIDRec>");
            }
            evento.setTipoDocumento(tipoDocumento);

            String numeroDocumento = extraerValorXML(bloqueGesEve, "dNumID>", "</dNumID>");
            if (numeroDocumento == null) {
                numeroDocumento = extraerValorXML(bloqueGesEve, "<dNumID>", "</dNumID>");
            }
            evento.setNumeroDocumento(numeroDocumento);

            String totalStr = extraerValorXML(bloqueGesEve, "dTotalGs>", "</dTotalGs>");
            if (totalStr == null) {
                totalStr = extraerValorXML(bloqueGesEve, "<dTotalGs>", "</dTotalGs>");
            }
            if (totalStr != null) {
                try {
                    evento.setTotalFactura(new java.math.BigDecimal(totalStr));
                } catch (NumberFormatException e) {
                    log.warn("No se pudo parsear total: {}", totalStr);
                }
            }

            // Buscar la respuesta del evento
            int inicioResEvento = buscarTag(bloqueCompleto, "rResEnviEventoDe", true);
            if (inicioResEvento != -1) {
                int finResEvento = buscarTag(bloqueCompleto, "rResEnviEventoDe", false, inicioResEvento);
                if (finResEvento != -1) {
                    String bloqueRespuesta = bloqueCompleto.substring(inicioResEvento, finResEvento + 25);

                    String fechaProcesamiento = extraerValorXML(bloqueRespuesta, "dFecProc>", "</dFecProc>");
                    if (fechaProcesamiento == null) {
                        fechaProcesamiento = extraerValorXML(bloqueRespuesta, "<dFecProc>", "</dFecProc>");
                    }
                    evento.setFechaProcesamiento(parsearFecha(fechaProcesamiento));

                    String estadoResultado = extraerValorXML(bloqueRespuesta, "dEstRes>", "</dEstRes>");
                    if (estadoResultado == null) {
                        estadoResultado = extraerValorXML(bloqueRespuesta, "<dEstRes>", "</dEstRes>");
                    }
                    evento.setEstadoResultado(estadoResultado);

                    String protocolo = extraerValorXML(bloqueRespuesta, "dProtAut>", "</dProtAut>");
                    if (protocolo == null) {
                        protocolo = extraerValorXML(bloqueRespuesta, "<dProtAut>", "</dProtAut>");
                    }
                    evento.setProtocoloAutorizacion(protocolo);

                    String codigoRespuesta = extraerValorXML(bloqueRespuesta, "dCodRes>", "</dCodRes>");
                    if (codigoRespuesta == null) {
                        codigoRespuesta = extraerValorXML(bloqueRespuesta, "<dCodRes>", "</dCodRes>");
                    }
                    evento.setCodigoRespuesta(codigoRespuesta);

                    String mensajeRespuesta = extraerValorXML(bloqueRespuesta, "dMsgRes>", "</dMsgRes>");
                    if (mensajeRespuesta == null) {
                        mensajeRespuesta = extraerValorXML(bloqueRespuesta, "<dMsgRes>", "</dMsgRes>");
                    }
                    evento.setMensajeRespuesta(mensajeRespuesta);
                }
            }

            return evento;

        } catch (Exception e) {
            log.error("Error al extraer datos de nominación: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * DTO que contiene los datos de un evento de cancelación extraído del XML.
     */
    @Data
    public static class EventoCancelacion {
        private String eventoId;
        private LocalDateTime fechaFirma;
        private String cdcDocumento;
        private String motivoCancelacion;
        
        // Respuesta de SIFEN
        private LocalDateTime fechaProcesamiento;
        private String estadoResultado;        // "Aprobado" o "Rechazado"
        private String protocoloAutorizacion;
        private String codigoRespuesta;
        private String mensajeRespuesta;
    }
    
    /**
     * DTO que contiene los datos de un evento de nominación extraído del XML.
     */
    @Data
    public static class EventoNominacion {
        private String eventoId;
        private LocalDateTime fechaFirma;
        private String cdcDocumento;
        
        // Datos del receptor nominado
        private String nombreReceptor;
        private String tipoReceptor;          // "CONTRIBUYENTE" o "NO_CONTRIBUYENTE"
        private String rucReceptor;           // Para contribuyentes
        private String dvReceptor;            // Para contribuyentes
        private String tipoDocumento;         // Para no contribuyentes
        private String numeroDocumento;       // Para no contribuyentes
        
        private java.math.BigDecimal totalFactura;
        private LocalDateTime fechaEmision;
        private LocalDateTime fechaRecepcion;
        
        // Respuesta de SIFEN
        private LocalDateTime fechaProcesamiento;
        private String estadoResultado;        // "Aprobado" o "Rechazado"
        private String protocoloAutorizacion;
        private String codigoRespuesta;
        private String mensajeRespuesta;
    }
}

