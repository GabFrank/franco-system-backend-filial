package com.franco.dev.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class VersionController {

    @Value("${app.version:unknown}")
    private String appVersion;

    @Value("${spring.application.name:frc-server}")
    private String appName;

    @GetMapping("/api/version")
    public Map<String, String> getVersion() {
        Map<String, String> info = new HashMap<>();
        info.put("name", appName);
        info.put("version", appVersion);
        return info;
    }
}
