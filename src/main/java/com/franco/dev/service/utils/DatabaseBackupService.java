package com.franco.dev.service.utils;

import com.franco.dev.config.BackupConfig;
import com.google.api.services.drive.model.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Service
public class DatabaseBackupService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseBackupService.class);
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
    
    private final BackupConfig backupConfig;
    private final GoogleDriveService googleDriveService;
    private final Environment env;
    
    @Value("${spring.datasource.url}")
    private String dbUrl;
    
    @Value("${spring.datasource.username}")
    private String dbUsername;
    
    @Value("${spring.datasource.password}")
    private String dbPassword;
    
    @Autowired
    public DatabaseBackupService(BackupConfig backupConfig, GoogleDriveService googleDriveService, Environment env) {
        this.backupConfig = backupConfig;
        this.googleDriveService = googleDriveService;
        this.env = env;
    }
    
    @Scheduled(cron = "0 0 2 * * ?") // Run at 2:00 AM every day
    public void performBackup() {
        if (!backupConfig.isEnabled()) {
            log.info("Database backup is disabled");
            return;
        }
        
        try {
            log.info("Starting database backup...");
            
            // Create local directory if it doesn't exist
            Path localBackupDir = Paths.get(backupConfig.getLocalPath());
            if (!Files.exists(localBackupDir)) {
                Files.createDirectories(localBackupDir);
            }
            
            // Extract database name from connection URL
            String dbName = extractDatabaseName(dbUrl);
            if (dbName == null || dbName.isEmpty()) {
                log.error("Could not extract database name from connection URL");
                return;
            }
            
            // Generate backup file name with timestamp
            String timestamp = dateFormat.format(new Date());
            String backupFileName = String.format("%s_backup_%s.sql", dbName, timestamp);
            Path backupFilePath = Paths.get(backupConfig.getLocalPath(), backupFileName);
            
            // Execute pg_dump command to create backup
            boolean success = executeBackup(dbName, backupFilePath.toString());
            
            if (success) {
                log.info("Database backup created successfully: {}", backupFilePath);
                
                // Upload to Google Drive
                uploadToGoogleDrive(backupFilePath.toFile());
            } else {
                log.error("Failed to create database backup");
            }
        } catch (Exception e) {
            log.error("Error during database backup process", e);
        }
    }
    
    private String extractDatabaseName(String jdbcUrl) {
        // Extract database name from JDBC URL (postgresql://host:port/dbname)
        if (jdbcUrl != null && jdbcUrl.contains("/")) {
            String[] parts = jdbcUrl.split("/");
            if (parts.length > 0) {
                String dbName = parts[parts.length - 1];
                // Remove any parameters
                if (dbName.contains("?")) {
                    dbName = dbName.substring(0, dbName.indexOf("?"));
                }
                return dbName;
            }
        }
        return null;
    }
    
    private boolean executeBackup(String dbName, String outputFilePath) {
        try {
            ProcessBuilder pb;
            
            // Build pg_dump command
            if (dbPassword != null && !dbPassword.isEmpty()) {
                // Using environment variable for password
                pb = new ProcessBuilder(
                        "pg_dump",
                        "-h", extractHost(dbUrl),
                        "-p", extractPort(dbUrl),
                        "-U", dbUsername,
                        "-F", "p", // plain text format
                        "-f", outputFilePath,
                        dbName);
                
                pb.environment().put("PGPASSWORD", dbPassword);
            } else {
                pb = new ProcessBuilder(
                        "pg_dump",
                        "-h", extractHost(dbUrl),
                        "-p", extractPort(dbUrl),
                        "-U", dbUsername,
                        "-F", "p", // plain text format
                        "-f", outputFilePath,
                        dbName);
            }
            
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            log.error("Error executing pg_dump", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }
    
    private String extractHost(String jdbcUrl) {
        // Extract host from JDBC URL (postgresql://host:port/dbname)
        if (jdbcUrl != null && jdbcUrl.contains("://") && jdbcUrl.contains(":")) {
            String hostPart = jdbcUrl.split("://")[1].split(":")[0];
            return hostPart.equals("localhost") ? "localhost" : hostPart;
        }
        return "localhost";
    }
    
    private String extractPort(String jdbcUrl) {
        // Extract port from JDBC URL
        if (jdbcUrl != null && jdbcUrl.contains(":")) {
            String[] parts = jdbcUrl.split(":");
            if (parts.length > 2) {
                String portPart = parts[2];
                if (portPart.contains("/")) {
                    return portPart.substring(0, portPart.indexOf("/"));
                }
                return portPart;
            }
        }
        return "5432"; // Default PostgreSQL port
    }
    
    private void uploadToGoogleDrive(java.io.File backupFile) {
        try {
            log.info("Uploading backup file to Google Drive: {}", backupFile.getName());
            
            // Upload file to Google Drive
            File uploadedFile = googleDriveService.uploadFile(backupFile, "application/sql");
            log.info("File uploaded to Google Drive with ID: {}", uploadedFile.getId());
            
            // Check if we need to delete old backups
            cleanupOldBackups();
            
        } catch (IOException e) {
            log.error("Failed to upload backup to Google Drive", e);
        }
    }
    
    private void cleanupOldBackups() throws IOException {
        // Get list of backup files in Google Drive
        List<File> files = googleDriveService.listFiles();
        
        // If we have more than the max allowed, delete the oldest ones
        if (files.size() > backupConfig.getMaxFiles()) {
            int filesToDelete = files.size() - backupConfig.getMaxFiles();
            log.info("Found {} files, will delete {} oldest backups", files.size(), filesToDelete);
            
            for (int i = 0; i < filesToDelete; i++) {
                File fileToDelete = files.get(i); // Files are ordered by creation time (oldest first)
                log.info("Deleting old backup file: {}", fileToDelete.getName());
                googleDriveService.deleteFile(fileToDelete.getId());
            }
        }
    }
} 