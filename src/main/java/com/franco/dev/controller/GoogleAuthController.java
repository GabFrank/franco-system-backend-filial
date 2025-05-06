package com.franco.dev.controller;

import com.franco.dev.service.utils.GoogleDriveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/google")
public class GoogleAuthController {
    
    private final GoogleDriveService googleDriveService;
    
    @Autowired
    public GoogleAuthController(GoogleDriveService googleDriveService) {
        this.googleDriveService = googleDriveService;
    }
    
    @GetMapping("/init")
    public ResponseEntity<String> initAuth() {
        try {
            googleDriveService.initDriveService();
            return ResponseEntity.ok("Google Drive service initialized successfully");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
    
    @GetMapping("/force-init")
    public ResponseEntity<String> forceInitAuth() {
        try {
            googleDriveService.initDriveService(true);
            return ResponseEntity.ok("Google Drive service initialized with forced re-authentication");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
    
    @GetMapping("/clear-tokens")
    public ResponseEntity<String> clearTokens() {
        try {
            googleDriveService.clearTokens();
            return ResponseEntity.ok("Google Drive tokens cleared successfully");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
} 