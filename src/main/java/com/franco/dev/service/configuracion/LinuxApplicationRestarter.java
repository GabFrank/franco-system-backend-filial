package com.franco.dev.service.configuracion;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;

import java.io.IOException;

public class LinuxApplicationRestarter {
    public LinuxApplicationRestarter(ApplicationContext context) {
        restartApplication(context);
    }

    public void restartApplication(ApplicationContext context) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("sudo", "systemctl", "restart", "frc.service");
            processBuilder.inheritIO();
            Process process = processBuilder.start();
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
