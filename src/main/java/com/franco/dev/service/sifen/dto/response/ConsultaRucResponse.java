package com.franco.dev.service.sifen.dto.response;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConsultaRucResponse extends SifenResponseBase {
    private String ruc;
    private String razonSocial;
    private String estadoContribuyente;
    private String codigoEstadoContribuyente; // From dCodEstCons
    private String esFacturadorElectronico;   // From dRUCFactElec ('S' or 'N')

    // Campos adicionales para alinear con el schema GraphQL
    private String mensajeProcesamiento; // Alias de mensajeRespuesta para GraphQL
    private String dv;                   // Dígito verificador del RUC
    private String estado;               // Alias de estadoContribuyente
    private String nombre;               // Alias de razonSocial
    private String nombreFantasia;
    private String telefono;
    private String direccion;
    private Integer codigoEstablecimiento;
    private Boolean validacionCorrecta;  // Puede reflejar procesamientoCorrecto
    private String mensajeValidacion;    // Alias de mensajeRespuesta
}
