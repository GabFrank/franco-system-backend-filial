package com.franco.dev.service.configuracion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * #226: la impresora del balance del mobile sale de la config del desktop. El desktop actual la guarda
 * en userData (appData/FRC/config); si el filial no la encuentra, el balance no se imprime.
 */
class DesktopPrinterConfigServiceTest {

    private String homeOriginal;
    private Path home;

    @BeforeEach
    void setUp() throws Exception {
        homeOriginal = System.getProperty("user.home");
        home = Files.createTempDirectory("frc-home");
        System.setProperty("user.home", home.toString());
    }

    @AfterEach
    void tearDown() {
        System.setProperty("user.home", homeOriginal);
    }

    private void config(String carpeta, String impresora) throws Exception {
        Path dir = home.resolve(carpeta);
        Files.createDirectories(dir);
        Files.write(dir.resolve("config-backup.json"),
                ("{\"printers\":{\"ticket\":\"" + impresora + "\"},\"local\":\"CAJA 1\"}").getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void leeLaConfigDelDesktopLinux() throws Exception {
        config(".config/FRC/config", "frc-ticket");
        assertEquals(Optional.of("frc-ticket"), new DesktopPrinterConfigService().getTicketPrinterName());
    }

    @Test
    void leeLaConfigDelDesktopWindows() throws Exception {
        config("AppData/Roaming/FRC/config", "TICKET58");
        assertEquals(Optional.of("TICKET58"), new DesktopPrinterConfigService().getTicketPrinterName());
    }

    @Test
    void prefiereLaDelDesktopActualAUnaCopiaVieja() throws Exception {
        config(".config/frc-sistemas/config", "ticket_soporte");
        config(".config/FRC/config", "frc-ticket");
        assertEquals(Optional.of("frc-ticket"), new DesktopPrinterConfigService().getTicketPrinterName());
    }
}
