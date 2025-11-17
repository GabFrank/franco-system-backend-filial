package com.franco.dev.service.sifen.config;

import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.repository.financiero.TimbradoRepository;
import com.roshka.sifen.Sifen;
import com.roshka.sifen.core.SifenConfig;
import com.roshka.sifen.core.SifenConfig.TipoAmbiente;
import com.roshka.sifen.core.SifenConfig.TipoCertificadoCliente;
import com.roshka.sifen.core.exceptions.SifenException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.lang.Nullable;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Slf4j
@Configuration
@EnableConfigurationProperties(SifenProperties.class)
public class SifenConfiguration {

    @Bean
    @Nullable
    @DependsOn("entityManagerFactory")
    public SifenConfig sifenConfig(SifenProperties properties, TimbradoRepository timbradoRepository) 
            throws SifenException, FileNotFoundException {
        if (!properties.isEnabled()) {
            log.warn("SIFEN está deshabilitado (sifen.enabled=false). El bean SifenConfig no se creará.");
            return null;
        }

        log.info("SIFEN está habilitado. Intentando configurar SifenConfig...");
        
        // Buscar timbrado activo y electrónico
        Timbrado timbrado = timbradoRepository.findFirstByActivoTrueAndIsElectronicoTrueOrderByIdDesc()
                .orElseThrow(() -> {
                    String errorMsg = "No se encontró un timbrado activo y electrónico. " +
                            "Es necesario configurar al menos un timbrado con activo=true e isElectronico=true para usar SIFEN.";
                    log.error(errorMsg);
                    return new IllegalStateException(errorMsg);
                });

        // Validar que el timbrado tenga CSC configurado
        if (timbrado.getCsc() == null || timbrado.getCsc().trim().isEmpty()) {
            String errorMsg = String.format("El timbrado ID %d no tiene configurado el CSC (Código de Seguridad del Contribuyente).", 
                    timbrado.getId());
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        // Obtener CSC ID, usando "0001" como valor por defecto si es null o vacío
        String cscId = timbrado.getCscId();
        if (cscId == null || cscId.trim().isEmpty()) {
            cscId = "0001";
            log.warn("El timbrado ID {} no tiene configurado el CSC ID. Se utilizará el valor por defecto: {}", 
                    timbrado.getId(), cscId);
        }

        log.info("Usando timbrado ID {} para configuración de SIFEN. Número de timbrado: {}, CSC ID: {}", 
                timbrado.getId(), timbrado.getNumero(), cscId);

        String certPath = properties.getCertificado().getArchivo();
        if (certPath == null || Files.notExists(Paths.get(certPath))) {
            log.error("Archivo de certificado SIFEN no encontrado en la ruta: {}", certPath);
            throw new FileNotFoundException("No se encontró el archivo de certificado SIFEN: " + certPath);
        }

        try {
            SifenConfig config = new SifenConfig(
                TipoAmbiente.valueOf(properties.getAmbiente().toUpperCase()),
                cscId,
                timbrado.getCsc(),
                TipoCertificadoCliente.valueOf(properties.getCertificado().getTipo().toUpperCase()),
                certPath,
                properties.getCertificado().getContrasena()
            );

            config.setHabilitarNotaTecnica13(properties.isHabilitarNotaTecnica13());
            Sifen.setSifenConfig(config);
            log.info("SIFEN Config inicializado correctamente para el ambiente: {} usando timbrado ID: {}", 
                    properties.getAmbiente(), timbrado.getId());
            return config;
        } catch (Exception e) {
            log.error("Error fatal al inicializar la configuración de SIFEN: {}", e.getMessage(), e);
            throw new RuntimeException("Error al inicializar SifenConfig: " + e.getMessage());
        }
    }
}
