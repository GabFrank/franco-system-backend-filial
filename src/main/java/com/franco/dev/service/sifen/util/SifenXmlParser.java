package com.franco.dev.service.sifen.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilidad para extraer valores específicos del XML de SIFEN.
 * Permite extraer valores de tags sin necesidad de parsear todo el XML a JSON.
 */
public class SifenXmlParser {
    
    private static final Logger logger = LoggerFactory.getLogger(SifenXmlParser.class);

    /**
     * Extrae el valor de un tag XML específico.
     * 
     * @param xml El XML completo como String
     * @param tagName El nombre del tag a buscar (sin < >)
     * @return El valor del tag, o null si no se encuentra
     * 
     * Ejemplo:
     * extractTagValue(xml, "dCarQR") -> "https://ekuatia.set.gov.py/consultas/qr?..."
     */
    public static String extractTagValue(String xml, String tagName) {
        if (xml == null || xml.isEmpty() || tagName == null || tagName.isEmpty()) {
            return null;
        }

        try {
            String openTag = "<" + tagName + ">";
            String closeTag = "</" + tagName + ">";
            
            int startIndex = xml.indexOf(openTag);
            int endIndex = xml.indexOf(closeTag);
            
            if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                String value = xml.substring(startIndex + openTag.length(), endIndex);
                
                // Decodificar entidades HTML comunes
                value = decodeHtmlEntities(value);
                
                logger.debug("✅ Tag '{}' extraído: {} caracteres", tagName, value.length());
                return value;
            } else {
                logger.warn("⚠️  Tag '{}' no encontrado en el XML", tagName);
                return null;
            }
        } catch (Exception e) {
            logger.error("❌ Error al extraer tag '{}': {}", tagName, e.getMessage());
            return null;
        }
    }

    /**
     * Extrae el valor de un tag XML específico con un namespace/prefijo.
     * 
     * @param xml El XML completo como String
     * @param tagName El nombre del tag a buscar (sin < >)
     * @param prefix El prefijo del namespace (ej: "ns2", "env")
     * @return El valor del tag, o null si no se encuentra
     * 
     * Ejemplo:
     * extractTagValue(xml, "dEstRes", "ns2") -> "Aprobado"
     */
    public static String extractTagValueWithPrefix(String xml, String tagName, String prefix) {
        if (xml == null || xml.isEmpty() || tagName == null || tagName.isEmpty()) {
            return null;
        }

        String fullTagName = prefix != null && !prefix.isEmpty() 
            ? prefix + ":" + tagName 
            : tagName;
        
        return extractTagValue(xml, fullTagName);
    }

    /**
     * Extrae la URL del QR desde el XML de SIFEN.
     * Ubicación: gCamFuFD > dCarQR
     * 
     * @param xml El XML completo del DE
     * @return La URL del QR, o null si no se encuentra
     */
    public static String extractUrlQr(String xml) {
        return extractTagValue(xml, "dCarQR");
    }

    /**
     * Extrae el CDC (Código de Control) desde el XML de SIFEN.
     * Ubicación: rDE > DE > _Id
     * 
     * @param xml El XML completo del DE
     * @return El CDC, o null si no se encuentra
     */
    public static String extractCdc(String xml) {
        // El CDC puede estar en diferentes formatos:
        // 1. Como atributo Id="..." en el tag DE
        // 2. Como tag <Id>...</Id>
        
        // Intentar extraer como atributo primero
        String cdc = extractAttributeValue(xml, "DE", "Id");
        
        // Si no se encuentra, intentar como tag
        if (cdc == null) {
            cdc = extractTagValue(xml, "Id");
        }
        
        return cdc;
    }

    /**
     * Extrae el valor de un atributo de un tag XML.
     * 
     * @param xml El XML completo como String
     * @param tagName El nombre del tag que contiene el atributo
     * @param attributeName El nombre del atributo
     * @return El valor del atributo, o null si no se encuentra
     * 
     * Ejemplo:
     * extractAttributeValue(xml, "DE", "Id") -> "01800994825001001000004422025100217524820275"
     */
    public static String extractAttributeValue(String xml, String tagName, String attributeName) {
        if (xml == null || xml.isEmpty() || tagName == null || attributeName == null) {
            return null;
        }

        try {
            // Buscar el tag de apertura
            String openTag = "<" + tagName;
            int tagStart = xml.indexOf(openTag);
            
            if (tagStart == -1) {
                return null;
            }
            
            // Buscar el cierre del tag de apertura (puede ser > o />)
            int tagEnd = xml.indexOf(">", tagStart);
            if (tagEnd == -1) {
                return null;
            }
            
            // Extraer la porción del tag de apertura
            String tagContent = xml.substring(tagStart, tagEnd + 1);
            
            // Buscar el atributo dentro del tag
            String attrPattern = attributeName + "=\"";
            int attrStart = tagContent.indexOf(attrPattern);
            
            if (attrStart == -1) {
                return null;
            }
            
            // Extraer el valor del atributo
            int valueStart = attrStart + attrPattern.length();
            int valueEnd = tagContent.indexOf("\"", valueStart);
            
            if (valueEnd == -1) {
                return null;
            }
            
            String value = tagContent.substring(valueStart, valueEnd);
            logger.debug("✅ Atributo '{}' del tag '{}' extraído: {}", attributeName, tagName, value);
            return value;
            
        } catch (Exception e) {
            logger.error("❌ Error al extraer atributo '{}' del tag '{}': {}", attributeName, tagName, e.getMessage());
            return null;
        }
    }

    /**
     * Extrae el DigestValue desde el XML de SIFEN (usado para el QR).
     * Ubicación: Signature > SignedInfo > Reference > DigestValue
     * 
     * @param xml El XML completo del DE
     * @return El DigestValue en Base64, o null si no se encuentra
     */
    public static String extractDigestValue(String xml) {
        return extractTagValue(xml, "DigestValue");
    }

    /**
     * Extrae la fecha de emisión del DE.
     * Ubicación: gDatGralOpe > dFeEmiDE
     * 
     * @param xml El XML completo del DE
     * @return La fecha de emisión en formato ISO, o null si no se encuentra
     */
    public static String extractFechaEmision(String xml) {
        return extractTagValue(xml, "dFeEmiDE");
    }

    /**
     * Extrae el total general de la operación.
     * Ubicación: gTotSub > dTotGralOpe
     * 
     * @param xml El XML completo del DE
     * @return El total general como String, o null si no se encuentra
     */
    public static String extractTotalGeneral(String xml) {
        return extractTagValue(xml, "dTotGralOpe");
    }

    /**
     * Extrae el total del IVA.
     * Ubicación: gTotSub > dTotIVA
     * 
     * @param xml El XML completo del DE
     * @return El total del IVA como String, o null si no se encuentra
     */
    public static String extractTotalIva(String xml) {
        return extractTagValue(xml, "dTotIVA");
    }

    /**
     * Decodifica entidades HTML comunes en un String.
     * 
     * @param text El texto con entidades HTML
     * @return El texto decodificado
     */
    private static String decodeHtmlEntities(String text) {
        if (text == null) {
            return null;
        }
        
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'");
    }

    /**
     * Verifica si un tag específico existe en el XML.
     * 
     * @param xml El XML completo como String
     * @param tagName El nombre del tag a buscar
     * @return true si el tag existe, false en caso contrario
     */
    public static boolean hasTag(String xml, String tagName) {
        if (xml == null || xml.isEmpty() || tagName == null || tagName.isEmpty()) {
            return false;
        }
        
        String openTag = "<" + tagName + ">";
        String closeTag = "</" + tagName + ">";
        
        return xml.contains(openTag) && xml.contains(closeTag);
    }

    /**
     * Extrae la porción completa de un tag XML incluyendo sus tags de apertura y cierre.
     * Útil para extraer secciones completas del XML.
     * 
     * @param xml El XML completo como String
     * @param tagName El nombre del tag a extraer
     * @return El contenido completo del tag (incluyendo los tags de apertura/cierre), o null si no se encuentra
     */
    public static String extractFullTag(String xml, String tagName) {
        if (xml == null || xml.isEmpty() || tagName == null || tagName.isEmpty()) {
            return null;
        }

        try {
            String openTag = "<" + tagName + ">";
            String closeTag = "</" + tagName + ">";
            
            int startIndex = xml.indexOf(openTag);
            int endIndex = xml.indexOf(closeTag);
            
            if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                return xml.substring(startIndex, endIndex + closeTag.length());
            }
            
            return null;
        } catch (Exception e) {
            logger.error("❌ Error al extraer tag completo '{}': {}", tagName, e.getMessage());
            return null;
        }
    }
}

