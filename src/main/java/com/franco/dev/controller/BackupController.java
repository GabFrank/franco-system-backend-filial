package com.franco.dev.controller;

import com.franco.dev.service.utils.DatabaseBackupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/backup")
public class BackupController {

    private final DatabaseBackupService databaseBackupService;
    
    @Autowired
    public BackupController(DatabaseBackupService databaseBackupService) {
        this.databaseBackupService = databaseBackupService;
    }
    
    @PostMapping("/trigger")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> triggerBackup() {
        try {
            databaseBackupService.performBackup();
            return ResponseEntity.ok("Backup process started successfully");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error triggering backup: " + e.getMessage());
        }
    }
    
    @GetMapping("/test-pg-dump")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> testPgDump() {
        try {
            ProcessBuilder pb = new ProcessBuilder("pg_dump", "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                return ResponseEntity.ok("pg_dump is available: " + output.toString());
            } else {
                return ResponseEntity.status(500).body("pg_dump test failed with exit code " + exitCode + ": " + output.toString());
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error testing pg_dump: " + e.getMessage());
        }
    }
} 