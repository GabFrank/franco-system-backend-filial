package com.franco.dev.service.sifen.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.service.sifen.dto.response.ConsultaRucResponse;
import com.franco.dev.utilitarios.CalcularVerificadorCDC;
import com.roshka.sifen.Sifen;
import com.roshka.sifen.core.SifenConfig;
import com.roshka.sifen.core.beans.response.RespuestaConsultaRUC;
import com.roshka.sifen.core.exceptions.SifenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Slf4j
@Service
public class SifenService {

    @Value("${tipoContribuyenteEmisor:2}")
    private Integer tipoContribuyenteEmisor;

    /**
     * Consulta la información de un contribuyente en SIFEN a partir de su RUC.
     *
     * @param ruc El RUC del contribuyente, puede contener guion y dígito verificador.
     * @return Un objeto {@link ConsultaRucResponse} con la información obtenida.
     */
    public ConsultaRucResponse consultaRuc(String ruc) {
        try {
            // La librería espera el RUC sin el dígito verificador.
            String rucSinDv = cleanRuc(ruc);
            log.info("Consultando RUC: {} (enviado como: {})", ruc, rucSinDv);

            RespuestaConsultaRUC respuestaSifen = Sifen.consultaRUC(rucSinDv);
            log.debug("Respuesta recibida de SIFEN: {}", respuestaSifen);

            // parsear respuestra bruta usando object mapper
            ObjectMapper objectMapper = new ObjectMapper();
            String json;
            try {
                json = objectMapper.writeValueAsString(respuestaSifen);
                log.info("Respuesta en formato JSON: {}", json);
            } catch (JsonProcessingException e) {
                log.error("Error al parsear respuesta en formato JSON: {}", e.getMessage(), e);
                e.printStackTrace();
            }
            // String json = respuestaSifen.getRespuestaBruta().toString();

            return mapToConsultaRucResponse(respuestaSifen, ruc);

        } catch (SifenException e) {
            log.error("Error al consultar RUC en SIFEN: {}", e.getMessage(), e);
            return createErrorResponse(e);
        }
    }

    // ===================== Facturación electrónica (CDC) =====================

    public String generarCdc(String ruc,
                             String codEstablecimiento,
                             String puntoExpedicion,
                             String numeroFactura,
                             java.time.LocalDateTime fecha) {
        StringBuilder cdc = new StringBuilder();
        try {
            cdc.append("01");

            String rucSinDv = ruc.contains("-") ? ruc.split("-")[0] : ruc;
            String dvRuc = ruc.contains("-") ? ruc.split("-")[1] : com.franco.dev.utilitarios.CalcularVerificadorRuc.getDigitoVerificadorString(rucSinDv);


            String rucNormalizado = normalizarRuc(rucSinDv);
            cdc.append(rucNormalizado);
            
            cdc.append(dvRuc);

            cdc.append(codEstablecimiento);
            cdc.append(puntoExpedicion);

            String numFacturaNormalizado = normalizarNumeroFactura(numeroFactura);
            cdc.append(numFacturaNormalizado);

            Integer tipo = tipoContribuyenteEmisor != null ? tipoContribuyenteEmisor : 2;
            cdc.append(tipo);

            String fechaNormalizada = normalizarFecha(com.franco.dev.utilitarios.DateUtils.dateToStringShort(fecha));
            cdc.append(fechaNormalizada);

            cdc.append("1");

            String codigoSeguridad = generarCodigoSeguridad(numeroFactura);
            cdc.append(codigoSeguridad);

            String cdcSinDV = cdc.toString();
            int dvCalculado = CalcularVerificadorCDC.calcularDigitoVerificador(cdcSinDV);
            cdc.append(dvCalculado);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return cdc.toString();
    }

    private static String normalizarNumeroFactura(String numeroFactura) {
        StringBuilder numeroFacturaString = new StringBuilder();
        int cerosNecesarios = 7 - numeroFactura.toString().length();
        for (int i = 0; i < cerosNecesarios; i++) {
            numeroFacturaString.append("0");
        }
        numeroFacturaString.append(numeroFactura.toString());
        return numeroFacturaString.toString();
    }

    private static String normalizarRuc(String ruc) {
        if (ruc.length() < 8) {
            StringBuilder rucString = new StringBuilder();
            for (int i = 8 - ruc.length(); i > 0; i--) {
                rucString.append("0");
            }
            rucString.append(ruc);
            return rucString.toString();
        } else {
            return ruc;
        }
    }

    private static String normalizarFecha(String fecha) {
        return fecha.replace("-", "").replace("/", "");
    }

    /**
     * Genera un código de seguridad para documentos electrónicos según las reglas de SIFEN.
     * 
     * Reglas:
     * - Debe ser un número positivo de 9 dígitos
     * - Aleatorio y no secuencial
     * - Rango entre 000000001 y 999999999
     * - No debe ser igual al número de documento
     * - No tener relación con información específica del DE o emisor
     * 
     * @param numeroDocumento El número de documento para evitar duplicación
     * @return Código de seguridad de 9 dígitos como String
     */
    private String generarCodigoSeguridad(String numeroDocumento) {
        SecureRandom random = new SecureRandom();
        String codigoSeguridad;
        
        do {
            // Generar número aleatorio entre 1 y 999999999
            int numeroAleatorio = random.nextInt(999999999) + 1;
            // Formatear con ceros a la izquierda para asegurar 9 dígitos
            codigoSeguridad = String.format("%09d", numeroAleatorio);
        } while (codigoSeguridad.equals(numeroDocumento));
        
        log.debug("Código de seguridad generado: {}", codigoSeguridad);
        return codigoSeguridad;
    }

    private String cleanRuc(String ruc) {
        if (ruc == null) return "";
        String cleanRuc = ruc.replaceAll("[^0-9]", "");
        if (cleanRuc.length() > 1) {
            return cleanRuc.substring(0, cleanRuc.length() - 1);
        }
        return cleanRuc;
    }

    private ConsultaRucResponse mapToConsultaRucResponse(RespuestaConsultaRUC respuestaSifen, String rucOriginal) {
        ConsultaRucResponse dto = new ConsultaRucResponse();
        dto.setProcesamientoCorrecto("0502".equals(respuestaSifen.getdCodRes()));
        dto.setCodigoRespuesta(respuestaSifen.getdCodRes());
        dto.setMensajeRespuesta(respuestaSifen.getdMsgRes());
        dto.setRuc(rucOriginal); // Devolvemos el RUC original para consistencia

        // Mapeo desde el objeto anidado xContRUC
        if (respuestaSifen.getxContRUC() != null) {
            dto.setRazonSocial(respuestaSifen.getxContRUC().getdRazCons());
            dto.setEstadoContribuyente(respuestaSifen.getxContRUC().getdDesEstCons());
            dto.setCodigoEstadoContribuyente(respuestaSifen.getxContRUC().getdCodEstCons());
            dto.setEsFacturadorElectronico(respuestaSifen.getxContRUC().getdRUCFactElec());
        }

        return dto;
    }

    private ConsultaRucResponse createErrorResponse(SifenException e) {
        ConsultaRucResponse dto = new ConsultaRucResponse();
        dto.setProcesamientoCorrecto(false);
        // El código de error de la librería suele ser más técnico, usamos uno genérico.
        dto.setCodigoRespuesta("999");
        dto.setMensajeRespuesta("Error de comunicación con SIFEN: " + e.getMessage());
        return dto;
    }
}
