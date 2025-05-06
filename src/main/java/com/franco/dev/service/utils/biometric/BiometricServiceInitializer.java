package com.franco.dev.service.utils.biometric;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class BiometricServiceInitializer implements ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    private BiometricService biometricService;

    @Autowired
    private Environment env;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        initService();
    }

    @PostConstruct
    public void initService() {
        String ip = env.getProperty("biometricIp");
        String portStr = env.getProperty("biometricPort");
        if (ip != null && portStr != null) {
            int port = Integer.parseInt(portStr);
            biometricService.getRealTimeLogs(ip, String.valueOf(port)); // Adjust to handle exceptions or async execution as needed
        }
    }


}
