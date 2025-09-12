package com.franco.dev.service.sifen.config;

import com.roshka.sifen.Sifen;
import com.roshka.sifen.core.SifenConfig;
import com.roshka.sifen.core.SifenConfig.TipoAmbiente;
import com.roshka.sifen.core.SifenConfig.TipoCertificadoCliente;
import com.roshka.sifen.core.exceptions.SifenException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Slf4j
@Configuration
@EnableConfigurationProperties(SifenProperties.class)
@ConditionalOnProperty(name = "sifen.enabled", havingValue = "true", matchIfMissing = true)
public class SifenConfiguration {

    @Bean
    public SifenConfig sifenConfig(SifenProperties properties) throws FileNotFoundException {
        // Validar que el archivo de certificado exista
        String certPath = properties.getCertificado().getArchivo();
        if (Files.notExists(Paths.get(certPath))) {
            log.error("****************************************************************************");
            log.error("ARCHIVO DE CERTIFICADO SIFEN NO ENCONTRADO EN: {}", certPath);
            log.error("La aplicación no se iniciará. Verifique la ruta en `application.properties`");
            log.error("o la variable de entorno `SIFEN_CERT_PATH`.");
            log.error("****************************************************************************");
            throw new FileNotFoundException("No se encontró el archivo de certificado SIFEN: " + certPath);
        }

        log.info("Inicializando configuración de SIFEN para el ambiente: {}", properties.getAmbiente());

        SifenConfig config = new SifenConfig(
            TipoAmbiente.valueOf(properties.getAmbiente().toUpperCase()),
            properties.getCscId(),
            properties.getCsc(),
            TipoCertificadoCliente.valueOf(properties.getCertificado().getTipo().toUpperCase()),
            properties.getCertificado().getArchivo(),
            properties.getCertificado().getContrasena()
        );

        config.setHabilitarNotaTecnica13(properties.isHabilitarNotaTecnica13());

        // Inicializa la configuración global de la librería
        try {
            Sifen.setSifenConfig(config);
        } catch (SifenException e) {
            log.error("Error al inicializar la configuración de SIFEN: {}", e.getMessage());
            e.printStackTrace();
        }

        log.info("Configuración de SIFEN inicializada correctamente.");
        return config;
    }
}
