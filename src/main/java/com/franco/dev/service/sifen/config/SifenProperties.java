package com.franco.dev.service.sifen.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@Validated
@ConfigurationProperties(prefix = "sifen")
public class SifenProperties {

    private boolean enabled = true;

    @NotBlank
    private String ambiente = "DEV";

    @NotNull
    private Certificado certificado = new Certificado();

    @NotBlank
    private String csc;

    @NotBlank
    private String cscId;

    private boolean habilitarNotaTecnica13 = false;

    @Data
    public static class Certificado {
        private boolean usar = true;

        @NotBlank
        private String tipo = "PFX";

        @NotBlank
        private String archivo;

        @NotBlank
        private String contrasena;
    }
}
