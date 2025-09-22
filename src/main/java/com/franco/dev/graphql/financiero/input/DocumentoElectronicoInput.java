package com.franco.dev.graphql.financiero.input;

import lombok.Data;

import java.io.Serializable;

@Data
public class DocumentoElectronicoInput implements Serializable {
    private Long id;
    private Long sucursalId;
    private Long facturaLegalId;
    private String cdc;
    private String urlQr;
    private String xmlFirmado;
    private String xmlOriginal;
    private String estadoDocumentoElectronico;
    private String codigoRespuestaSifen;
    private String mensajeRespuestaSifen;
    private String numeroDocumento;
    private String tipoDocumento;
    private String fechaEmision;
    private String fechaRecepcionSifen;
    private Boolean activo;
    private Long usuarioId;
}
