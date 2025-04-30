package com.franco.dev.controller;

import com.franco.dev.service.utils.DatabaseBackupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
} 