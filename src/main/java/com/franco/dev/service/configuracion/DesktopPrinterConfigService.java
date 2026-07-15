package com.franco.dev.service.configuracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lee la configuracion de impresora del desktop Electron (config-backup.json),
 * la misma fuente que usa ConfiguracionService en frc-sistemas-integrados-angular.
 */
@Service
public class DesktopPrinterConfigService {

    private static final Logger log = LoggerFactory.getLogger(DesktopPrinterConfigService.class);
    private static final String CONFIG_FILE_NAME = "config-backup.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Optional<String> getTicketPrinterName() {
        return readConfig().map(DesktopPrinterConfig::getTicketPrinter);
    }

    public Optional<String> getLocalName() {
        return readConfig().map(DesktopPrinterConfig::getLocal);
    }

    public Optional<DesktopPrinterConfig> readConfig() {
        for (Path path : resolveCandidatePaths()) {
            if (!Files.isRegularFile(path)) {
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(path.toFile());
                String ticket = textOrNull(root.path("printers").path("ticket"));
                String local = textOrNull(root.path("local"));
                if (ticket != null || local != null) {
                    log.info("[DesktopConfig] Config leida desde {} -> printer={}, local={}", path, ticket, local);
                    return Optional.of(new DesktopPrinterConfig(ticket, local, path.toString()));
                }
                log.warn("[DesktopConfig] Archivo encontrado pero sin printers.ticket/local: {}", path);
            } catch (IOException e) {
                log.warn("[DesktopConfig] No se pudo leer {}: {}", path, e.getMessage());
            }
        }
        log.warn("[DesktopConfig] No se encontro config de impresora del desktop en rutas conocidas");
        return Optional.empty();
    }

    private List<Path> resolveCandidatePaths() {
        List<Path> paths = new ArrayList<>();
        String home = System.getProperty("user.home");
        String explicitPath = System.getenv("FRC_DESKTOP_CONFIG_PATH");
        if (explicitPath != null && !explicitPath.trim().isEmpty()) {
            paths.add(Paths.get(explicitPath.trim()));
        }
        if (home != null && !home.isEmpty()) {
            paths.add(Paths.get(home, ".config", "frc-sistemas", "config", CONFIG_FILE_NAME));
            paths.add(Paths.get(home, ".frc-sistemas", CONFIG_FILE_NAME));
            paths.add(Paths.get(home, CONFIG_FILE_NAME));
            paths.add(Paths.get(home, "Documents", CONFIG_FILE_NAME));
        }
        return paths;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value != null && !value.trim().isEmpty() ? value.trim() : null;
    }

    public static final class DesktopPrinterConfig {
        private final String ticketPrinter;
        private final String local;
        private final String sourcePath;

        public DesktopPrinterConfig(String ticketPrinter, String local, String sourcePath) {
            this.ticketPrinter = ticketPrinter;
            this.local = local;
            this.sourcePath = sourcePath;
        }

        public String getTicketPrinter() {
            return ticketPrinter;
        }

        public String getLocal() {
            return local;
        }

        public String getSourcePath() {
            return sourcePath;
        }
    }
}
