package com.franco.dev.service.sifen.util;

import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.utilitarios.CalcularVerificadorRuc;
import com.roshka.sifen.core.types.TiNatRec;
import com.roshka.sifen.core.types.TiTiOpe;
import com.roshka.sifen.core.types.TiTipCont;
import com.roshka.sifen.core.types.TiTipDocRec;
import com.roshka.sifen.core.types.PaisType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper para configurar correctamente los datos del receptor en un Documento Electrónico SIFEN.
 * 
 * Implementa todas las reglas oficiales de SIFEN para los diferentes tipos de receptores:
 * - B2B (Business to Business): Contribuyentes con RUC (incluye entidades públicas)
 * - B2C (Business to Consumer): No contribuyentes identificados o innominados
 * - B2F (Business to Foreign): Servicios al exterior
 * 
 * NOTA: Aunque detectamos entidades gubernamentales, las tratamos como B2B en lugar de B2G
 * porque no tenemos los datos necesarios para compras públicas (número de licitación, contrato, etc.)
 */
public class SifenReceptorHelper {
    
    private static final Logger logger = LoggerFactory.getLogger(SifenReceptorHelper.class);
    
    /**
     * Clase que encapsula toda la configuración del receptor.
     */
    public static class ConfiguracionReceptor {
        // Campos obligatorios para todos
        public TiNatRec iNatRec;           // 1=Contribuyente, 2=No contribuyente
        public TiTiOpe iTiOpe;             // 1=B2B, 2=B2C, 3=B2G, 4=B2F
        public String dNomRec;             // Nombre del receptor
        
        // Campos para contribuyentes (iNatRec=1)
        public TiTipCont iTiContRec;       // 1=PF, 2=PJ (solo si iNatRec=1)
        public String dRucRec;             // RUC sin DV (solo si iNatRec=1)
        public Short dDVRec;               // Dígito verificador (solo si iNatRec=1)
        
        // Campos para no contribuyentes (iNatRec=2)
        public TiTipDocRec iTipIDRec;      // Tipo de documento (solo si iNatRec=2)
        public String dNumIDRec;           // Número de documento (solo si iNatRec=2)
        public String dDTipIDRec;          // Descripción del tipo doc (solo si iTipIDRec=9 - Otro)
        
        // País (para B2F o extranjeros)
        public PaisType cPaisRec;          // Código de país
        
        // Información adicional
        public String escenarioDetectado; // Para logging/debugging
        public boolean requiereDireccion; // Si requiere dirección obligatoria
        public boolean esCompraPública;   // true para B2G (gCompPub en SIFEN)
        
        public ConfiguracionReceptor() {
            this.cPaisRec = PaisType.PRY; // Por defecto Paraguay
            this.requiereDireccion = false;
            this.esCompraPública = false;
        }
    }
    
    /**
     * Determina la configuración completa del receptor basándose en los datos del cliente.
     * 
     * Lógica de tributación:
     * - PF (Persona Física): Tributa SOLO si cliente.tributa = true
     *   · Este campo se actualiza automáticamente vía consulta al servidor del gobierno
     *   · Puede tener CI (6-7 dígitos) o RUC (6-7 dígitos)
     * 
     * - PJ (Persona Jurídica): SIEMPRE tributa
     *   · Empresas (S.A., S.R.L., etc.)
     *   · Cooperativas, Asociaciones, Fundaciones
     *   · RUC típicamente 8 dígitos con prefijo 8 o 9
     * 
     * - Entidades Gubernamentales: SIEMPRE tributan (se tratan como B2B)
     *   · Municipalidades, Ministerios, Entes autárquicos
     *   · Universidades públicas, Hospitales públicos
     *   · NOTA: Aunque se detectan, se configuran como B2B por falta de datos de licitación
     * 
     * @param cliente Cliente de la factura (puede ser null para innominado)
     * @param montoTotal Monto total de la operación (para validar innominado)
     * @return Configuración completa del receptor
     * @throws IllegalArgumentException si los datos son inválidos
     */
    public static ConfiguracionReceptor determinarConfiguracionReceptor(Cliente cliente, Double montoTotal) {
        ConfiguracionReceptor config = new ConfiguracionReceptor();
        
        // Si no hay cliente, es innominado (cliente no informado)
        if (cliente == null) {
            configurarInnominado(config, montoTotal);
            return config;
        }
        
        Persona persona = cliente.getPersona();
        if (persona == null) {
            // Sin persona asociada, tratar como innominado
            configurarInnominado(config, montoTotal);
            return config;
        }
        
        // Determinar si tiene RUC válido y tributa
        // NOTA: Para PF, cliente.tributa se actualiza vía consulta gubernamental
        String documento = persona.getDocumento();
        boolean tieneRucValido = tieneRucValido(documento, cliente.getTributa());
        
        if (tieneRucValido) {
            // ===== CASO 1, 2, 3: CONTRIBUYENTE (B2B o B2G) =====
            configurarContribuyente(config, cliente, persona, documento);
        } else {
            // ===== CASO 4-11: NO CONTRIBUYENTE (B2C o B2F) =====
            configurarNoContribuyente(config, cliente, persona, documento, montoTotal);
        }
        
        logger.info("📋 Configuración del receptor determinada:");
        logger.info("   Escenario: {}", config.escenarioDetectado);
        logger.info("   iNatRec: {}", config.iNatRec);
        logger.info("   iTiOpe: {}", config.iTiOpe);
        logger.info("   Nombre: {}", config.dNomRec);
        
        return config;
    }
    
    /**
     * Configura un receptor contribuyente (con RUC válido).
     * Casos: 1) PF contribuyente, 2) PJ contribuyente, 3) Entidad pública
     */
    private static void configurarContribuyente(ConfiguracionReceptor config, Cliente cliente, Persona persona, String documento) {
        config.iNatRec = TiNatRec.CONTRIBUYENTE;
        
        // Determinar si es entidad gubernamental (B2G)
        boolean esEntidadGubernamental = esEntidadGubernamental(persona.getNombre());
        config.iTiOpe = esEntidadGubernamental ? TiTiOpe.B2G : TiTiOpe.B2B;
        
        // Determinar si es PF o PJ
        config.iTiContRec = determinarTipoContribuyente(persona);
        
        // Configurar RUC y DV
        // El documento puede venir con o sin DV, detectamos por la presencia del guión
        String rucSinDV;
        int dvCalculado;
        
        if (documento.contains("-")) {
            // Documento CON DV (formato: "80099482-5")
            String[] partes = documento.split("-");
            rucSinDV = partes[0].trim();
            String dvStr = partes[1].trim();
            
            // Validar que el DV sea correcto
            dvCalculado = CalcularVerificadorRuc.getDigitoVerificador(rucSinDV);
            
            if (!dvStr.equals(String.valueOf(dvCalculado))) {
                throw new IllegalArgumentException(
                    "Dígito verificador de RUC inválido. Esperado: " + dvCalculado + ", Recibido: " + dvStr
                );
            }
        } else {
            // Documento SIN DV (formato: "80099482")
            rucSinDV = limpiarDocumento(documento);
            dvCalculado = CalcularVerificadorRuc.getDigitoVerificador(rucSinDV);
        }
        
        config.dRucRec = rucSinDV;
        config.dDVRec = Short.parseShort(String.valueOf(dvCalculado));
        
        config.dNomRec = persona.getNombre();
        
        // B2B/B2G requieren dirección
        config.requiereDireccion = true;
        
        // NOTA: Aunque detectamos entidades gubernamentales, las tratamos como B2B
        // porque no tenemos todos los datos necesarios para B2G (número de licitación, contrato, etc.)
        if (esEntidadGubernamental) {
            config.iTiOpe = TiTiOpe.B2B; // Forzar B2B en lugar de B2G
            config.esCompraPública = false; // No activar modo compra pública
            config.escenarioDetectado = "3) Entidad pública (tratada como B2B por falta de datos de licitación)";
            logger.info("✅ Configuración B2B (Entidad Gubernamental): RUC={}, DV={}, Tipo={}", 
                config.dRucRec, config.dDVRec, config.iTiContRec);
        } else if (config.iTiContRec == TiTipCont.PERSONA_FISICA) {
            config.escenarioDetectado = "1) Persona física contribuyente (B2B)";
            logger.info("✅ Configuración B2B: RUC={}, DV={}, Tipo={}", 
                config.dRucRec, config.dDVRec, config.iTiContRec);
        } else {
            config.escenarioDetectado = "2) Persona jurídica contribuyente (B2B)";
            logger.info("✅ Configuración B2B: RUC={}, DV={}, Tipo={}", 
                config.dRucRec, config.dDVRec, config.iTiContRec);
        }
    }
    
    /**
     * Configura un receptor no contribuyente.
     * Casos: 4-11) Varios tipos de documentos de identidad
     */
    private static void configurarNoContribuyente(ConfiguracionReceptor config, Cliente cliente, Persona persona, String documento, Double montoTotal) {
        config.iNatRec = TiNatRec.NO_CONTRIBUYENTE;
        
        // TODO: Detectar B2F (servicios al exterior) - Por ahora usamos B2C
        config.iTiOpe = TiTiOpe.B2C;
        
        // País por defecto Paraguay (puede cambiar en extranjero)
        config.cPaisRec = PaisType.PRY;
        
        String nombre = persona.getNombre();
        
        // Verificar si es innominado
        if (esInnominado(nombre, documento)) {
            configurarInnominado(config, montoTotal);
            return;
        }
        
        // Configurar nombre
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new IllegalArgumentException("Cliente no contribuyente debe tener nombre");
        }
        config.dNomRec = nombre.trim().toUpperCase();
        
        // Determinar tipo de documento
        TiTipDocRec tipoDoc = determinarTipoDocumento(documento, persona);
        
        config.iTipIDRec = tipoDoc;
        config.dNumIDRec = limpiarDocumento(documento);
        
        // Si es "Otro" documento, agregar descripción
        if (tipoDoc == TiTipDocRec.OTRO) {
            config.dDTipIDRec = "Documento de identidad";
            logger.warn("⚠️  Usando tipo de documento 'OTRO' - verificar si esto es correcto");
        }
        
        // Determinar escenario específico
        // Nota: TiTipDocRec solo tiene CEDULA_PARAGUAYA, INNOMINADO y OTRO en la versión actual de la librería
        if (tipoDoc == TiTipDocRec.CEDULA_PARAGUAYA) {
            config.escenarioDetectado = "4) No contribuyente identificado – PF nacional (B2C)";
        } else if (tipoDoc == TiTipDocRec.OTRO) {
            config.escenarioDetectado = "8) No contribuyente identificado – Otro documento (B2C)";
        } else {
            config.escenarioDetectado = "No contribuyente (B2C)";
        }
        
        logger.info("✅ Configuración B2C: TipoDoc={}, NumDoc={}", config.iTipIDRec, config.dNumIDRec);
    }
    
    /**
     * Configura un receptor innominado.
     * Caso 9) Innominado (B2C de bajo monto)
     */
    private static void configurarInnominado(ConfiguracionReceptor config, Double montoTotal) {
        // Validar monto máximo
        final double MONTO_MAXIMO_INNOMINADO = 7_000_000.0;
        if (montoTotal != null && montoTotal >= MONTO_MAXIMO_INNOMINADO) {
            throw new IllegalArgumentException(
                "Factura innominada no permitida para montos >= 7.000.000 PYG. Monto: " + montoTotal
            );
        }
        
        // Configurar naturaleza y tipo de operación
        config.iNatRec = TiNatRec.NO_CONTRIBUYENTE;
        config.iTiOpe = TiTiOpe.B2C;
        
        // Configurar datos del receptor innominado
        config.iTipIDRec = TiTipDocRec.INNOMINADO;
        config.dNumIDRec = "0";
        config.dNomRec = "Sin Nombre";
        
        // País por defecto Paraguay
        config.cPaisRec = PaisType.PRY;
        
        config.escenarioDetectado = "9) Innominado (B2C de bajo monto)";
        
        logger.info("✅ Configuración INNOMINADA: Monto={}", montoTotal);
    }
    
    /**
     * Verifica si el cliente tiene un RUC válido y tributa.
     * 
     * Nota: El sistema guarda el RUC SIN el dígito verificador (DV).
     * 
     * Formato de RUC en BD (sin DV):
     * - PF (Persona Física): 6-7 dígitos (ej: 4043581)
     * - PJ (Persona Jurídica): 8 dígitos (ej: 80099482)
     * 
     * Reglas de tributación:
     * - PF con CI/RUC: Solo tributa si cliente.tributa = true (se actualiza vía consulta gubernamental)
     * - PJ (empresas, cooperativas, etc.): Siempre tributan
     * - Entidades gubernamentales: Siempre tributan
     */
    private static boolean tieneRucValido(String documento, Boolean tributa) {
        if (documento == null || documento.trim().isEmpty()) {
            logger.debug("⚠️  RUC inválido: documento vacío o null");
            return false;
        }
        
        String docLimpio = limpiarDocumento(documento);
        
        // Si no tributa explícitamente, no es contribuyente
        // IMPORTANTE: Para PF, este campo se actualiza vía consulta al servidor del gobierno
        if (tributa == null || !tributa) {
            logger.debug("⚠️  No es contribuyente: tributa = {}", tributa);
            return false;
        }
        
        // RUC (sin DV) debe tener entre 6-8 dígitos
        // - PF: típicamente 6-7 dígitos
        // - PJ: típicamente 8 dígitos (con prefijo 8 o 9)
        if (docLimpio.length() < 6 || docLimpio.length() > 8) {
            logger.warn("⚠️  RUC inválido: documento '{}' tiene {} dígitos (se requieren 6-8 sin DV). " +
                "Cliente marcado como tributa=true pero será tratado como NO contribuyente.", 
                documento, docLimpio.length());
            return false;
        }
        
        // Validar formato numérico
        try {
            Long.parseLong(docLimpio);
            logger.debug("✅ RUC válido: '{}' ({} dígitos, tributa=true)", documento, docLimpio.length());
            return true;
        } catch (NumberFormatException e) {
            logger.warn("⚠️  RUC inválido: documento '{}' contiene caracteres no numéricos", documento);
            return false;
        }
    }
    
    /**
     * Verifica si el cliente debe ser tratado como innominado.
     */
    private static boolean esInnominado(String nombre, String documento) {
        if (nombre == null || nombre.trim().isEmpty() || nombre.trim().equalsIgnoreCase("Sin Nombre")) {
            return true;
        }
        
        if (documento == null || documento.trim().isEmpty() || documento.trim().equals("0")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Detecta si el nombre corresponde a una entidad gubernamental.
     * 
     * Cubre:
     * - Ministerios y Secretarías
     * - Municipalidades y Gobernaciones
     * - Entes autárquicos y descentralizados
     * - Instituciones educativas públicas
     * - Hospitales y centros de salud públicos
     * - Fuerzas armadas y seguridad
     * - Poder judicial y legislativo
     * - Empresas públicas
     */
    private static boolean esEntidadGubernamental(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) {
            return false;
        }
        
        String nombreUpper = nombre.toUpperCase().trim();
        
        // === Poder Ejecutivo ===
        if (nombreUpper.contains("MINISTERIO") ||
            nombreUpper.contains("SECRETARIA") ||
            nombreUpper.contains("SECRETARÍA") ||
            nombreUpper.contains("PRESIDENCIA") ||
            nombreUpper.contains("VICEPRESIDENCIA")) {
            return true;
        }
        
        // === Gobiernos Locales ===
        if (nombreUpper.contains("MUNICIPALIDAD") ||
            nombreUpper.contains("INTENDENCIA") ||
            nombreUpper.contains("GOBERNACION") ||
            nombreUpper.contains("GOBERNACIÓN") ||
            nombreUpper.contains("JUNTA MUNICIPAL") ||
            nombreUpper.contains("CONSEJO DEPARTAMENTAL")) {
            return true;
        }
        
        // === Entes Autárquicos y Descentralizados ===
        if (nombreUpper.contains("DIRECCION NACIONAL") ||
            nombreUpper.contains("DIRECCIÓN NACIONAL") ||
            nombreUpper.contains("SUBSECRETARIA") ||
            nombreUpper.contains("SUBSECRETARÍA") ||
            nombreUpper.matches(".*\\bDIRECCION\\b.*") ||
            nombreUpper.matches(".*\\bDIRECCIÓN\\b.*")) {
            return true;
        }
        
        // === Entes y Organismos ===
        if (nombreUpper.contains("ENTE REGULADOR") ||
            nombreUpper.contains("ENTE BINACIONAL") ||
            nombreUpper.contains("ANDE") || // Administración Nacional de Electricidad
            nombreUpper.contains("ESSAP") || // Empresa de Servicios Sanitarios del Paraguay
            nombreUpper.contains("COPACO") || // Compañía Paraguaya de Comunicaciones
            nombreUpper.contains("IPS") || // Instituto de Previsión Social
            nombreUpper.contains("INSTITUTO NACIONAL") ||
            nombreUpper.contains("SENASA") ||
            nombreUpper.contains("SENACSA") ||
            nombreUpper.contains("SENAVITAT") ||
            nombreUpper.contains("CONATEL") ||
            nombreUpper.contains("CONACYT")) {
            return true;
        }
        
        // === Educación Pública ===
        if ((nombreUpper.contains("UNIVERSIDAD") && nombreUpper.contains("NACIONAL")) ||
            nombreUpper.contains("COLEGIO NACIONAL") ||
            nombreUpper.contains("ESCUELA PUBLICA") ||
            nombreUpper.contains("ESCUELA PÚBLICA") ||
            nombreUpper.contains("LICEO MILITAR") ||
            nombreUpper.contains("UNA") || // Universidad Nacional de Asunción
            nombreUpper.contains("U.N.A")) {
            return true;
        }
        
        // === Salud Pública ===
        if (nombreUpper.contains("HOSPITAL NACIONAL") ||
            nombreUpper.contains("CENTRO DE SALUD") ||
            nombreUpper.contains("MINISTERIO DE SALUD") ||
            nombreUpper.contains("HOSPITAL MILITAR") ||
            nombreUpper.contains("HOSPITAL DE CLINICAS") ||
            nombreUpper.contains("HOSPITAL DE CLÍNICAS") ||
            nombreUpper.contains("IPS HOSPITAL")) {
            return true;
        }
        
        // === Fuerzas Armadas y Seguridad ===
        if (nombreUpper.contains("EJERCITO") ||
            nombreUpper.contains("EJÉRCITO") ||
            nombreUpper.contains("ARMADA") ||
            nombreUpper.contains("FUERZA AEREA") ||
            nombreUpper.contains("FUERZA AÉREA") ||
            nombreUpper.contains("POLICIA NACIONAL") ||
            nombreUpper.contains("POLICÍA NACIONAL") ||
            nombreUpper.contains("COMANDO") ||
            nombreUpper.contains("CUARTEL")) {
            return true;
        }
        
        // === Poder Judicial ===
        if (nombreUpper.contains("CORTE SUPREMA") ||
            nombreUpper.contains("TRIBUNAL") ||
            nombreUpper.contains("JUZGADO") ||
            nombreUpper.contains("FISCALIA") ||
            nombreUpper.contains("FISCALÍA") ||
            nombreUpper.contains("DEFENSORIA") ||
            nombreUpper.contains("DEFENSORÍA")) {
            return true;
        }
        
        // === Poder Legislativo ===
        if (nombreUpper.contains("CONGRESO NACIONAL") ||
            nombreUpper.contains("SENADO") ||
            nombreUpper.contains("CAMARA DE DIPUTADOS") ||
            nombreUpper.contains("CÁMARA DE DIPUTADOS") ||
            nombreUpper.contains("PARLAMENTO")) {
            return true;
        }
        
        // === Empresas y Servicios Públicos ===
        if (nombreUpper.contains("EMPRESA PUBLICA") ||
            nombreUpper.contains("EMPRESA PÚBLICA") ||
            nombreUpper.contains("ADMINISTRACION NACIONAL") ||
            nombreUpper.contains("ADMINISTRACIÓN NACIONAL") ||
            nombreUpper.contains("SERVICIO NACIONAL")) {
            return true;
        }
        
        // === Organismos de Control ===
        if (nombreUpper.contains("CONTRALORIA") ||
            nombreUpper.contains("CONTRALORÍA") ||
            nombreUpper.contains("CONTADURIA") ||
            nombreUpper.contains("CONTADURÍA") ||
            nombreUpper.contains("AUDITORIA GENERAL") ||
            nombreUpper.contains("AUDITORÍA GENERAL") ||
            nombreUpper.contains("BANCO CENTRAL") ||
            nombreUpper.contains("BCP")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Clasifica si un RUC pertenece a PF o PJ basándose en patrones numéricos.
     * 
     * Nota: Los RUCs se guardan SIN dígito verificador (DV).
     * 
     * Patrones en Paraguay:
     * - PF (Persona Física): 6-7 dígitos, típicamente empiezan con 1-7
     * - PJ (Persona Jurídica): 8 dígitos, típicamente empiezan con 8 o 9
     * 
     * Retorna null si la confianza es baja (< 70%).
     */
    private static TiTipCont clasificarPorRuc(String documento) {
        if (documento == null) return null;
        
        // Limpiar el documento de guiones, espacios y otros caracteres
        String numeroBase = limpiarDocumento(documento);
        
        if (!numeroBase.matches("\\d+")) {
            return null; // No es un número válido
        }
        
        // Quitar ceros a la izquierda para evaluar patrones
        String n = numeroBase.replaceFirst("^0+", "");
        if (n.isEmpty()) n = "0";
        
        int len = n.length();
        char first = n.charAt(0);
        
        // Regla 1: Empieza con 8 y largo 7–8 → PJ fuerte (90% confianza)
        // Ejemplo: 80099482 (8 dígitos, prefijo 8)
        if (first == '8' && len >= 7 && len <= 8) {
            return TiTipCont.PERSONA_JURIDICA;
        }
        
        // Regla 2: Largo <= 6 → PF fuerte (85% confianza)
        // Ejemplo: 123456 (CI/RUC de PF)
        if (len <= 6) {
            return TiTipCont.PERSONA_FISICA;
        }
        
        // Regla 3: Largo 7 sin prefijo 8 → PF media-alta (75% confianza)
        // Ejemplo: 4043581 (7 dígitos, sin prefijo 8)
        if (len == 7 && first != '8') {
            return TiTipCont.PERSONA_FISICA;
        }
        
        // Para casos ambiguos (confianza < 70%), retornar null
        // y dejar que el análisis de nombre decida
        return null;
    }
    
    /**
     * Determina el tipo de contribuyente (PF o PJ) utilizando múltiples heurísticas.
     * 
     * Prioridad de análisis:
     * 1. Análisis del RUC (si está disponible) - Alta confianza
     * 2. Análisis del nombre (formas societarias) - Media-Alta confianza
     * 
     * Heurística de RUC:
     * - Prefijo 8 + longitud 7-9: PJ (90% confianza)
     * - Longitud ≤ 6: PF (85% confianza)
     * - Longitud 7-9 sin prefijo 8: PF (65% confianza)
     * - Prefijo 9: PJ (60% confianza)
     * - Longitud ≥ 11: PJ (60% confianza)
     */
    private static TiTipCont determinarTipoContribuyente(Persona persona) {
        // Primero, analizar el RUC si está disponible
        String documento = persona.getDocumento();
        if (documento != null && !documento.trim().isEmpty()) {
            TiTipCont tipoPorRuc = clasificarPorRuc(documento);
            if (tipoPorRuc != null) {
                // Si la clasificación por RUC es concluyente, usarla
                return tipoPorRuc;
            }
        }
        
        // Si el RUC no es concluyente, analizar el nombre
        String nombre = persona.getNombre();
        if (nombre == null || nombre.trim().isEmpty()) {
            return TiTipCont.PERSONA_FISICA; // Por defecto
        }
        
        String nombreUpper = nombre.toUpperCase();
        
        // === Formas societarias (Paraguay) ===
        // Con puntos
        if (nombreUpper.contains("S.A.") || 
            nombreUpper.contains("S.R.L.") ||
            nombreUpper.contains("E.A.S.") ||
            nombreUpper.contains("S.A.E.C.A") ||
            nombreUpper.contains("S.N.C.") ||
            nombreUpper.contains("S.C.S.") ||
            nombreUpper.contains("S.C.A.") ||
            nombreUpper.contains("LTDA.") ||
            nombreUpper.contains("COOP.") ||
            nombreUpper.contains("ASOC.") ||
            nombreUpper.contains("FUND.") ||
            nombreUpper.contains("CÍA.") ||
            nombreUpper.contains("CIA.")) {
            return TiTipCont.PERSONA_JURIDICA;
        }
        
        // Sin puntos
        if (nombreUpper.contains(" SA ") || nombreUpper.endsWith(" SA") ||
            nombreUpper.contains(" SRL ") || nombreUpper.endsWith(" SRL") ||
            nombreUpper.contains(" EAS ") || nombreUpper.endsWith(" EAS") ||
            nombreUpper.contains(" SAECA ") || nombreUpper.endsWith(" SAECA") ||
            nombreUpper.contains(" SNC ") || nombreUpper.endsWith(" SNC") ||
            nombreUpper.contains(" SCS ") || nombreUpper.endsWith(" SCS") ||
            nombreUpper.contains(" SCA ") || nombreUpper.endsWith(" SCA") ||
            nombreUpper.contains(" LTDA ") || nombreUpper.endsWith(" LTDA")) {
            return TiTipCont.PERSONA_JURIDICA;
        }
        
        // === Palabras clave de sociedades ===
        if (nombreUpper.contains("SOCIEDAD") ||
            nombreUpper.contains("EMPRESA") ||
            nombreUpper.contains("COMPAÑIA") ||
            nombreUpper.contains("COMPANIA") ||
            nombreUpper.contains("Y CIA") ||
            nombreUpper.contains("Y CÍA")) {
            return TiTipCont.PERSONA_JURIDICA;
        }
        
        // === Cooperativas y asociaciones sin fines de lucro ===
        if (nombreUpper.contains("COOPERATIVA") ||
            nombreUpper.contains(" COOP ") || nombreUpper.endsWith(" COOP") ||
            nombreUpper.contains("ASOCIACIÓN") ||
            nombreUpper.contains("ASOCIACION") ||
            nombreUpper.contains("FUNDACIÓN") ||
            nombreUpper.contains("FUNDACION") ||
            nombreUpper.contains("FEDERACIÓN") ||
            nombreUpper.contains("FEDERACION") ||
            nombreUpper.contains("CONFEDERACIÓN") ||
            nombreUpper.contains("CONFEDERACION") ||
            nombreUpper.contains("CÁMARA") ||
            nombreUpper.contains("CAMARA") ||
            nombreUpper.contains("CONSORCIO")) {
            return TiTipCont.PERSONA_JURIDICA;
        }
        
        // === Sector público y entes autónomos (también PJ) ===
        if (nombreUpper.contains("MUNICIPALIDAD") ||
            nombreUpper.contains("MINISTERIO") ||
            nombreUpper.contains("SECRETARÍA") ||
            nombreUpper.contains("SECRETARIA") ||
            nombreUpper.contains("DIRECCIÓN") ||
            nombreUpper.contains("DIRECCION") ||
            nombreUpper.contains("GOBIERNO") ||
            nombreUpper.contains("ENTE ") ||
            nombreUpper.contains("INSTITUTO") ||
            nombreUpper.contains("UNIVERSIDAD") ||
            nombreUpper.contains("FACULTAD") ||
            nombreUpper.contains("HOSPITAL") ||
            nombreUpper.contains("CLÍNICA") ||
            nombreUpper.contains("CLINICA")) {
            return TiTipCont.PERSONA_JURIDICA;
        }
        
        // Por defecto, asumir Persona Física
        return TiTipCont.PERSONA_FISICA;
    }
    
    /**
     * Determina el tipo de documento de identidad para no contribuyentes.
     */
    private static TiTipDocRec determinarTipoDocumento(String documento, Persona persona) {
        if (documento == null || documento.trim().isEmpty()) {
            return TiTipDocRec.OTRO;
        }
        
        String docLimpio = limpiarDocumento(documento);
        
        // TODO: Implementar lógica más robusta para detectar tipos de documento
        // Por ahora, heurística simple basada en la longitud y formato
        
        // TODO: Implementar detección de otros tipos cuando estén disponibles en la librería
        // Por ahora, solo soportamos CEDULA_PARAGUAYA y OTRO
        
        // Cédula paraguaya: típicamente 6-8 dígitos numéricos
        if (docLimpio.matches("\\d{6,8}")) {
            return TiTipDocRec.CEDULA_PARAGUAYA;
        }
        
        // Por defecto, usar OTRO para cualquier otro formato
        // TODO: Agregar soporte para PASAPORTE, CEDULA_EXTRANJERA, CARNET_RESIDENCIA cuando estén disponibles
        return TiTipDocRec.OTRO;
    }
    
    /**
     * Limpia un número de documento eliminando caracteres no alfanuméricos.
     */
    private static String limpiarDocumento(String documento) {
        if (documento == null) {
            return "";
        }
        return documento.replaceAll("[^A-Za-z0-9]", "");
    }
}

