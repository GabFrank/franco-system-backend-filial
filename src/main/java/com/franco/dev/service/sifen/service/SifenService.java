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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.enums.TipoCliente;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.sifen.dto.response.ConsultaRucResponse;
import com.franco.dev.utilitarios.CalcularVerificadorCDC;
import com.roshka.sifen.Sifen;
import com.roshka.sifen.core.beans.DocumentoElectronico;
import com.roshka.sifen.core.beans.response.RespuestaConsultaRUC;
import com.roshka.sifen.core.beans.response.RespuestaRecepcionDE;
import com.roshka.sifen.core.exceptions.SifenException;
import com.roshka.sifen.core.fields.request.de.*;
import com.roshka.sifen.core.types.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import lombok.Data;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class SifenService {

    @Value("${tipoContribuyenteEmisor:2}")
    private Integer tipoContribuyenteEmisor;

    private final ClienteService clienteService;

    private static final List<String> PALABRAS_CLAVE_PERSONA_JURIDICA = Arrays.asList(
        " S.A", " S.R.L", " COOP", "COOPERATIVA", "ASOCIACION", "FUNDACION", 
        "EMPRESA", "COMPAÑIA", "INDUSTRIAS", "COMERCIAL", "AGRO", "IMPORT", "EXPORT", " EAS"
    );

    public SifenService(@Lazy ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    /**
     * Consulta la información de un contribuyente en SIFEN a partir de su RUC.
     *
     * @param ruc El RUC del contribuyente, puede contener guion y dígito verificador.
     * @return Un objeto {@link ConsultaRucResponse} con la información obtenida.
     */
    public ConsultaRucResponse consultaRuc(String ruc) {
        try {
            String rucSinDv = cleanRuc(ruc);
            RespuestaConsultaRUC respuestaSifen = Sifen.consultaRUC(rucSinDv);
            return mapToConsultaRucResponse(respuestaSifen, ruc);
        } catch (SifenException e) {
            log.error("Error al consultar RUC en SIFEN: {}", e.getMessage(), e);
            return createErrorResponse(e);
        }
    }

    // ===================== Facturación electrónica (Documento Electrónico) =====================

    /**
     * Genera un documento electrónico completo utilizando la librería SIFEN.
     * Este método crea el documento, genera el CDC automáticamente, firma el XML y obtiene la URL QR.
     *
     * @param facturaLegal La factura legal para la cual generar el documento electrónico
     * @return RespuestaRecepcionDE con la información del documento electrónico generado
     */
    public DocumentoElectronicoInfo generarDocumentoElectronico(com.franco.dev.domain.financiero.FacturaLegal facturaLegal, List<FacturaLegalItem> facturaLegalItemList) {
        try {
            // 1. Crear el objeto DocumentoElectronico
            DocumentoElectronico de = crearDocumentoElectronicoSifen(facturaLegal, facturaLegalItemList);

            // 2. Enviar el DE a SIFEN
            RespuestaRecepcionDE respuesta = Sifen.recepcionDE(de);

            // 3. Procesar la respuesta y devolver toda la info
            return procesarRespuestaSifen(respuesta, de);

        } catch (SifenException e) {
            log.error("Error de SIFEN al generar o enviar el documento electrónico: {}", e.getMessage(), e);
            throw new RuntimeException("Error de SIFEN: " + e.getMessage(), e);
        }
    }

    private DocumentoElectronico crearDocumentoElectronicoSifen(com.franco.dev.domain.financiero.FacturaLegal facturaLegal, List<FacturaLegalItem> facturaLegalItemList) throws SifenException {
        LocalDateTime currentDate = LocalDateTime.now();

        // Grupo A
        DocumentoElectronico de = new DocumentoElectronico();
        de.setdFecFirma(currentDate);
        de.setdSisFact((short) 1);

        // Grupo B
        TgOpeDE gOpeDE = new TgOpeDE();
        gOpeDE.setiTipEmi(TTipEmi.NORMAL);
        de.setgOpeDE(gOpeDE);

        // Grupo C
        TgTimb gTimb = new TgTimb();
        gTimb.setiTiDE(TTiDE.FACTURA_ELECTRONICA);
        gTimb.setdNumTim(Integer.parseInt(facturaLegal.getTimbradoDetalle().getTimbrado().getNumero()));
        gTimb.setdEst(facturaLegal.getTimbradoDetalle().getSucursal().getCodigoEstablecimientoFactura());
        gTimb.setdPunExp(facturaLegal.getTimbradoDetalle().getPuntoExpedicion());
        gTimb.setdNumDoc(String.format("%07d", facturaLegal.getNumeroFactura()));
        gTimb.setdFeIniT(facturaLegal.getTimbradoDetalle().getTimbrado().getFechaInicio().toLocalDate());
        // gTimb.setdFeVenT(facturaLegal.getTimbradoDetalle().getTimbrado().getFechaFin().toLocalDate()); // Campo no disponible en la versión actual
        de.setgTimb(gTimb);

        // Grupo D
        TdDatGralOpe dDatGralOpe = new TdDatGralOpe();
        dDatGralOpe.setdFeEmiDE(facturaLegal.getFecha());

        TgOpeCom gOpeCom = new TgOpeCom();
        gOpeCom.setiTipTra(TTipTra.VENTA_MERCADERIA); // O el que corresponda
        gOpeCom.setiTImp(TTImp.IVA);
        gOpeCom.setcMoneOpe(CMondT.PYG);
        dDatGralOpe.setgOpeCom(gOpeCom);

        TgEmis gEmis = new TgEmis();
        String rucEmisor = facturaLegal.getTimbradoDetalle().getTimbrado().getRuc();
        String[] rucParts = rucEmisor.split("-");
        gEmis.setdRucEm(rucParts[0]);
        gEmis.setdDVEmi(rucParts[1]);
        TiTipCont tipCont = tipoContribuyenteEmisor == 1 ? TiTipCont.PERSONA_FISICA : TiTipCont.PERSONA_JURIDICA;
        gEmis.setiTipCont(tipCont);
        // TODO: Cargar estos datos desde la configuración de la empresa
        gEmis.setdNomEmi(facturaLegal.getTimbradoDetalle().getTimbrado().getRazonSocial());
        gEmis.setdDirEmi(facturaLegal.getTimbradoDetalle().getSucursal().getDireccion());
        gEmis.setdNumCas("000"); // Temporal, hasta que se agregue a la sucursal
        // agregar este dato en timbrado en un futuro
        gEmis.setcDepEmi(TDepartamento.CANINDEYU);
        gEmis.setcCiuEmi(207);
        if (facturaLegal.getTimbradoDetalle().getSucursal().getCiudad() != null) {
            gEmis.setdDesCiuEmi(facturaLegal.getTimbradoDetalle().getSucursal().getCiudad().getDescripcion());
        }
        if (facturaLegal.getTimbradoDetalle().getSucursal().getNroDelivery() != null) {
            gEmis.setdTelEmi(facturaLegal.getTimbradoDetalle().getSucursal().getNroDelivery());
        }
        
        if (facturaLegal.getTimbradoDetalle().getTimbrado().getEmail() != null) {
        gEmis.setdEmailE(facturaLegal.getTimbradoDetalle().getTimbrado().getEmail());
        }

        TgActEco gActEco = new TgActEco();
        // Usar valores por defecto si no están configurados
        String codActividad = facturaLegal.getTimbradoDetalle().getTimbrado().getCodActividadEconomicaPrincipal();
        String descActividad = facturaLegal.getTimbradoDetalle().getTimbrado().getDescActividadEconomicaPrincipal();
        
        gActEco.setcActEco(codActividad != null ? codActividad : "471110"); // Código por defecto para comercio al por menor
        gActEco.setdDesActEco(descActividad != null ? descActividad : "Comercio al por menor de productos alimenticios");
        
        gEmis.setgActEcoList(Collections.singletonList(gActEco));
        dDatGralOpe.setgEmis(gEmis);

        TgDatRec gDatRec = new TgDatRec();
        if (facturaLegal.getCliente() != null && facturaLegal.getCliente().getPersona() != null && facturaLegal.getCliente().getId() != 0) {
            gDatRec.setiNatRec(TiNatRec.CONTRIBUYENTE);

            // Asignar Tipo de Contribuyente del Receptor (OBLIGATORIO) con lógica de verificación
            gDatRec.setiTiContRec(determinarTipoContribuyenteReceptor(facturaLegal));

            gDatRec.setiTiOpe(TiTiOpe.B2B);
            gDatRec.setcPaisRec(PaisType.PRY);
            gDatRec.setiTipIDRec(TiTipDocRec.CEDULA_PARAGUAYA);

            String documentoCompletoCliente = facturaLegal.getCliente().getPersona().getDocumento();
            String rucSinDvCliente = documentoCompletoCliente.contains("-") ? documentoCompletoCliente.split("-")[0] : documentoCompletoCliente;
            String dvCliente = calcularDigitoVerificadorRuc(rucSinDvCliente);

            gDatRec.setdNumIDRec(rucSinDvCliente);
            gDatRec.setdRucRec(rucSinDvCliente);
            gDatRec.setdDVRec(Short.parseShort(dvCliente));
            gDatRec.setdNomRec(facturaLegal.getCliente().getPersona().getNombre());
        } else {
            gDatRec.setiNatRec(TiNatRec.NO_CONTRIBUYENTE);
            gDatRec.setiTiOpe(TiTiOpe.B2C);
            gDatRec.setcPaisRec(PaisType.PRY);
            gDatRec.setiTipIDRec(TiTipDocRec.INNOMINADO);
            gDatRec.setdNumIDRec("X");
            gDatRec.setdNomRec("SIN NOMBRE");
        }
        dDatGralOpe.setgDatRec(gDatRec);
        de.setgDatGralOpe(dDatGralOpe);

        // Grupo E
        TgDtipDE gDtipDE = new TgDtipDE();
        TgCamFE gCamFE = new TgCamFE();
        gCamFE.setiIndPres(TiIndPres.OPERACION_PRESENCIAL);
        gDtipDE.setgCamFE(gCamFE);
        
        TgCamCond gCamCond = new TgCamCond();
        gCamCond.setiCondOpe(facturaLegal.getCredito() ? TiCondOpe.CREDITO : TiCondOpe.CONTADO);
        if (facturaLegal.getCredito()) {
            TgPagCred gPagCred = new TgPagCred();
            gPagCred.setiCondCred(TiCondCred.PLAZO); // O CUOTAS, dependiendo del caso de uso
            // TODO: Obtener el plazo del crédito de la factura legal o venta asociada
            gPagCred.setdPlazoCre("30 días");
            gCamCond.setgPagCred(gPagCred);
        } else {
            gCamCond.setgPaConEIniList(new ArrayList<>());
        }
        gDtipDE.setgCamCond(gCamCond);
        
        List<TgCamItem> gCamItemList = new ArrayList<>();
        for (FacturaLegalItem itemFactura : facturaLegalItemList) {
            VentaItem ventaItem = itemFactura.getVentaItem();
            TgCamItem gCamItem = new TgCamItem();
            gCamItem.setdCodInt(ventaItem.getProducto().getId().toString());
            gCamItem.setdDesProSer(itemFactura.getDescripcion());
            Boolean balanza = ventaItem.getProducto().getBalanza();
            if (balanza == null) {
                balanza = false;
            }
            gCamItem.setcUniMed(balanza ? TcUniMed.Km : TcUniMed.UNI);
            gCamItem.setdCantProSer(BigDecimal.valueOf(itemFactura.getCantidad()));

            TgValorItem gValorItem = new TgValorItem();
            gValorItem.setdPUniProSer(BigDecimal.valueOf(itemFactura.getPrecioUnitario()));

            // Se inicializa gValorRestaItem aunque no haya descuentos, como buena práctica
            TgValorRestaItem gValorRestaItem = new TgValorRestaItem();
            gValorItem.setgValorRestaItem(gValorRestaItem);

            gCamItem.setgValorItem(gValorItem);

            TgCamIVA gCamIVA = new TgCamIVA();
            Integer ivaPorcentaje = ventaItem.getProducto().getIva();
            if (ivaPorcentaje != null && ivaPorcentaje > 0) {
                gCamIVA.setiAfecIVA(TiAfecIVA.GRAVADO);
                gCamIVA.setdPropIVA(BigDecimal.valueOf(100));
                gCamIVA.setdTasaIVA(BigDecimal.valueOf(ivaPorcentaje));
            } else {
                gCamIVA.setiAfecIVA(TiAfecIVA.EXENTO);
            }
            gCamItem.setgCamIVA(gCamIVA);
            gCamItemList.add(gCamItem);
        }
        gDtipDE.setgCamItemList(gCamItemList);
        de.setgDtipDE(gDtipDE);

        // Grupo F - Totales (Restaurado y con validaciones)
        // La librería SIFEN se encarga de calcular los totales a partir de los ítems.
        // Solo necesitamos asegurarnos de que el objeto gTotSub esté presente.
        de.setgTotSub(new TgTotSub());

        // Asignar código de seguridad (Restaurado)
        gOpeDE.setdCodSeg(generarCodigoSeguridad(facturaLegal.getNumeroFactura().toString()));
        

        return de;
    }

    /**
     * Determina el tipo de contribuyente del receptor con múltiples capas de verificación.
     * Capa 0: Dato local. Capa 1: Consulta SIFEN. Capa 2: Heurística.
     * Además, actualiza el cliente en la base de datos si se descubre el tipo.
     */
    private TiTipCont determinarTipoContribuyenteReceptor(com.franco.dev.domain.financiero.FacturaLegal facturaLegal) {
        Cliente cliente = facturaLegal.getCliente();
        Integer tipoContribuyenteLocal = cliente.getTipoContribuyente();

        // Capa 0: Usar el dato si ya existe en la base de datos.
        if (tipoContribuyenteLocal != null) {
            return tipoContribuyenteLocal == 2 ? TiTipCont.PERSONA_JURIDICA : TiTipCont.PERSONA_FISICA;
        }
        
        Integer tipoDeterminado;
        String fuente = "Heurística";
        String rucCliente = cliente.getPersona().getDocumento();
        String rucSinDv = cleanRuc(rucCliente);
        String nombreCliente = cliente.getPersona().getNombre().toUpperCase();

        // Lógica heurística mejorada:
        if ((rucSinDv.startsWith("80") && rucSinDv.length() == 8) || 
            PALABRAS_CLAVE_PERSONA_JURIDICA.stream().anyMatch(nombreCliente::contains)) {
            tipoDeterminado = 2; // Persona Jurídica
        } else {
            tipoDeterminado = 1; // Persona Física (por defecto)
        }
        // Actualizar el cliente en la base de datos con el nuevo dato encontrado.
        cliente.setTipoContribuyente(tipoDeterminado);
        clienteService.save(cliente);

        return tipoDeterminado == 2 ? TiTipCont.PERSONA_JURIDICA : TiTipCont.PERSONA_FISICA;
    }

    /**
     * Procesa la respuesta de SIFEN y extrae la información relevante.
     */
    private DocumentoElectronicoInfo procesarRespuestaSifen(RespuestaRecepcionDE respuesta, DocumentoElectronico de) {
        DocumentoElectronicoInfo info = new DocumentoElectronicoInfo();
        info.setProcesamientoCorrecto(false);

        try {
            if (respuesta != null) {
                info.setCodigoRespuesta(respuesta.getdCodRes());
                info.setMensajeRespuesta(respuesta.getdMsgRes());
                info.setUrlQr(de.getEnlaceQR());
                info.setXmlFirmado(respuesta.getRequestSent());
                info.setCdc(de.getId());

                if ("0300".equals(respuesta.getdCodRes())) { // 0300 = Lote recibido con éxito
                    info.setProcesamientoCorrecto(true);
                    info.setEstadoDocumento("PROCESADO");
                    info.setXmlFirmado(respuesta.getRespuestaBruta());

                    if (de != null) {
                        info.setCdc(de.getId());
                        info.setUrlQr(de.getEnlaceQR());
                    }
                } else {
                    info.setEstadoDocumento("RECHAZADO");
                }
            } else {
                info.setCodigoRespuesta("999");
                info.setMensajeRespuesta("Respuesta nula de SIFEN");
                info.setEstadoDocumento("ERROR");
            }
        } catch (Exception e) {
            log.error("Error al procesar la respuesta de SIFEN: {}", e.getMessage(), e);
            info.setCodigoRespuesta("999");
            info.setMensajeRespuesta("Error interno al procesar respuesta: " + e.getMessage());
            info.setEstadoDocumento("ERROR");
        }
        return info;
    }

    /**
     * Clase para encapsular la información del documento electrónico procesado.
     */
    public static class DocumentoElectronicoInfo {
        private String cdc;
        private String urlQr;
        private String estadoDocumento;
        private String codigoRespuesta;
        private String mensajeRespuesta;
        private boolean procesamientoCorrecto;
        private String xmlFirmado; // Nuevo campo para el XML firmado

        // Getters y setters
        public String getCdc() { return cdc; }
        public void setCdc(String cdc) { this.cdc = cdc; }

        public String getUrlQr() { return urlQr; }
        public void setUrlQr(String urlQr) { this.urlQr = urlQr; }

        public String getEstadoDocumento() { return estadoDocumento; }
        public void setEstadoDocumento(String estadoDocumento) { this.estadoDocumento = estadoDocumento; }

        public String getCodigoRespuesta() { return codigoRespuesta; }
        public void setCodigoRespuesta(String codigoRespuesta) { this.codigoRespuesta = codigoRespuesta; }

        public String getMensajeRespuesta() { return mensajeRespuesta; }
        public void setMensajeRespuesta(String mensajeRespuesta) { this.mensajeRespuesta = mensajeRespuesta; }

        public boolean isProcesamientoCorrecto() { return procesamientoCorrecto; }
        public void setProcesamientoCorrecto(boolean procesamientoCorrecto) { this.procesamientoCorrecto = procesamientoCorrecto; }

        public String getXmlFirmado() { return xmlFirmado; }
        public void setXmlFirmado(String xmlFirmado) { this.xmlFirmado = xmlFirmado; }
    }
    
    private String calcularDigitoVerificadorRuc(String ruc) {
        if (ruc == null || ruc.isEmpty()) {
            return "0";
        }
        String rucSinDv = ruc.contains("-") ? ruc.split("-")[0] : ruc;
        return com.franco.dev.utilitarios.CalcularVerificadorRuc.getDigitoVerificadorString(rucSinDv);
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

    /**
     * Mapea la respuesta de la librería SIFEN a nuestro DTO local.
     * (Restaurado)
     */
    private ConsultaRucResponse mapToConsultaRucResponse(RespuestaConsultaRUC respuestaSifen, String rucOriginal) {
        ConsultaRucResponse dto = new ConsultaRucResponse();
        dto.setProcesamientoCorrecto("0502".equals(respuestaSifen.getdCodRes()));
        dto.setCodigoRespuesta(respuestaSifen.getdCodRes());
        dto.setMensajeRespuesta(respuestaSifen.getdMsgRes());
        dto.setRuc(rucOriginal);

        if (respuestaSifen.getxContRUC() != null) {
            dto.setRazonSocial(respuestaSifen.getxContRUC().getdRazCons());
            dto.setEstadoContribuyente(respuestaSifen.getxContRUC().getdDesEstCons());
            dto.setCodigoEstadoContribuyente(respuestaSifen.getxContRUC().getdCodEstCons());
            dto.setEsFacturadorElectronico(respuestaSifen.getxContRUC().getdRUCFactElec());
        }

        return dto;
    }

    /**
     * Crea una respuesta de error estandarizada para la consulta de RUC.
     * (Restaurado)
     */
    private ConsultaRucResponse createErrorResponse(SifenException e) {
        ConsultaRucResponse dto = new ConsultaRucResponse();
        dto.setProcesamientoCorrecto(false);
        dto.setCodigoRespuesta("999");
        dto.setMensajeRespuesta("Error de comunicación con SIFEN: " + e.getMessage());
        return dto;
    }

}

