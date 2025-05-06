package com.franco.dev.service.configuracion;

import org.springframework.context.ApplicationContext;

import java.io.IOException;

public class MacApplicationRestarter {

    public MacApplicationRestarter(ApplicationContext context) {
        restartApplication(context);
    }

    public void restartApplication(ApplicationContext context) {
        try {
            System.out.println("Parando...");
            ProcessBuilder processBuilder = new ProcessBuilder("launchctl", "stop", "com.franco.frc", "&&", "launchctl", "start", "com.franco.frc");
            processBuilder.inheritIO();
            Process process = processBuilder.start();
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
