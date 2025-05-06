package com.franco.dev.service.configuracion;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;

import java.io.IOException;

public class WindowsApplicationRestarter {

    public WindowsApplicationRestarter(ApplicationContext context){
        restartApplication(context);
    }

    public void restartApplication(ApplicationContext context) {
        // Stop the current application
        SpringApplication.exit(context);

        // Stop and start the service using WinSW
        try {
            String serviceName = "frc";
            String stopCmd = "net stop " + serviceName;
            String startCmd = "net start " + serviceName;

            // Stop the service
            Process stopProcess = Runtime.getRuntime().exec(stopCmd);
            stopProcess.waitFor();

            // Start the service
            Runtime.getRuntime().exec(startCmd);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

}
