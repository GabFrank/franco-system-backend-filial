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

import javax.annotation.PostConstruct;

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
    
    // Add these new properties to extract parts of the JDBC URL
    private String dbHost;
    private String dbPort;
    private String dbName;
    
    // Add a flag to check if this is first run after startup
    private boolean isFirstRun = true;
    
    @Autowired
    public DatabaseBackupService(BackupConfig backupConfig, GoogleDriveService googleDriveService, Environment env) {
        this.backupConfig = backupConfig;
        this.googleDriveService = googleDriveService;
        this.env = env;
    }
    
    @PostConstruct
    public void init() {
        // Parse the JDBC URL once during initialization
        if (dbUrl != null && dbUrl.contains("jdbc:postgresql://")) {
            // Extract host and port
            String urlWithoutPrefix = dbUrl.replace("jdbc:postgresql://", "");
            String[] hostPortPart = urlWithoutPrefix.split("/")[0].split(":");
            if (hostPortPart.length >= 1) {
                dbHost = hostPortPart[0];
                log.info("Database host: {}", dbHost);
            }
            if (hostPortPart.length >= 2) {
                dbPort = hostPortPart[1];
                log.info("Database port: {}", dbPort);
            } else {
                dbPort = "5432"; // Default PostgreSQL port
                log.info("Using default database port: {}", dbPort);
            }
            
            // Extract database name
            String[] parts = urlWithoutPrefix.split("/");
            if (parts.length > 1) {
                dbName = parts[1];
                // Remove parameters from dbName if any
                if (dbName.contains("?")) {
                    dbName = dbName.substring(0, dbName.indexOf("?"));
                }
                log.info("Database name: {}", dbName);
            } else {
                log.warn("Could not extract database name from JDBC URL");
            }
        } else {
            log.warn("JDBC URL not in expected format: {}", dbUrl);
        }
        
        log.info("Database backup scheduled to run at {}:00 every day", backupConfig.getBackupHour());
    }
    
    // @Scheduled(fixedRate = 120000) // For testing (2 minutes)
    // @Scheduled(cron = "0 0 9 * * ?") // Run at 9:00 AM every day
    @Scheduled(cron = "#{@backupCronExpression}")
    public void performBackup() {
        // Skip the first execution after application startup
        if (isFirstRun) {
            log.info("Skipping backup execution at application startup");
            isFirstRun = false;
            return;
        }
        
        if (!backupConfig.isEnabled()) {
            log.info("Database backup is disabled");
            return;
        }
        
        if (dbName == null || dbHost == null || dbPort == null) {
            log.error("Database connection information is incomplete. Host: {}, Port: {}, Name: {}", 
                      dbHost, dbPort, dbName);
            return;
        }
        
        try {
            log.info("Starting database backup...");
            
            // Create local directory if it doesn't exist
            Path localBackupDir = Paths.get(backupConfig.getLocalPath());
            if (!Files.exists(localBackupDir)) {
                Files.createDirectories(localBackupDir);
                log.info("Created backup directory: {}", localBackupDir);
            }
            
            // Generate backup file name with timestamp
            String timestamp = dateFormat.format(new Date());
            String backupFileName = String.format("%s_backup_%s.sql", dbName, timestamp);
            Path backupFilePath = Paths.get(backupConfig.getLocalPath(), backupFileName);
            log.info("Backup file path: {}", backupFilePath);
            
            // Execute pg_dump command to create backup
            log.info("Executing pg_dump command for backup...");
            log.info("Database host: {}, port: {}, username: {}", dbHost, dbPort, dbUsername);
            boolean success = executeBackup(backupFilePath.toString());
            
            if (success) {
                log.info("Database backup created successfully: {}", backupFilePath);
                
                // Upload to Google Drive
                uploadToGoogleDrive(backupFilePath.toFile());
            } else {
                log.error("Failed to create database backup. pg_dump execution failed.");
            }
        } catch (Exception e) {
            log.error("Error during database backup process", e);
        }
    }
    
    private boolean executeBackup(String outputFilePath) {
        try {
            ProcessBuilder pb;
            
            // Build pg_dump command
            if (dbPassword != null && !dbPassword.isEmpty()) {
                // Using environment variable for password
                pb = new ProcessBuilder(
                        "pg_dump",
                        "-h", dbHost,
                        "-p", dbPort,
                        "-U", dbUsername,
                        "-F", "p", // plain text format
                        "-f", outputFilePath,
                        dbName);
                
                pb.environment().put("PGPASSWORD", dbPassword);
            } else {
                pb = new ProcessBuilder(
                        "pg_dump",
                        "-h", dbHost,
                        "-p", dbPort,
                        "-U", dbUsername,
                        "-F", "p", // plain text format
                        "-f", outputFilePath,
                        dbName);
            }
            
            // Capture process output for logging
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // Log the process output
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                StringBuilder output = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                if (output.length() > 0) {
                    log.info("pg_dump output: {}", output);
                }
            }
            
            int exitCode = process.waitFor();
            log.info("pg_dump exit code: {}", exitCode);
            
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            log.error("Error executing pg_dump", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
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