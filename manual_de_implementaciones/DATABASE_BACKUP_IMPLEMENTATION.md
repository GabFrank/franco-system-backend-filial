# PostgreSQL Database Backup to Google Drive Implementation Guide

This document outlines the step-by-step process to implement an automated PostgreSQL database backup system that uploads backups to Google Drive and maintains a maximum number of files (cleanup mechanism).

## Overview

The implementation consists of the following components:
- Scheduled backup task that runs at a specified time (configurable)
- PostgreSQL database backup using `pg_dump`
- Google Drive integration for cloud storage
- Token-based authentication with Google APIs
- File cleanup to maintain a maximum number of backup files

## Dependencies

Add these dependencies to your `pom.xml`:

```xml
<!-- Google Drive API - Using consistent versions -->
<dependency>
    <groupId>com.google.api-client</groupId>
    <artifactId>google-api-client</artifactId>
    <version>1.33.0</version>
</dependency>
<dependency>
    <groupId>com.google.oauth-client</groupId>
    <artifactId>google-oauth-client-jetty</artifactId>
    <version>1.33.0</version>
</dependency>
<dependency>
    <groupId>com.google.apis</groupId>
    <artifactId>google-api-services-drive</artifactId>
    <version>v3-rev20211107-1.32.1</version>
</dependency>
<dependency>
    <groupId>com.google.api-client</groupId>
    <artifactId>google-api-client-gson</artifactId>
    <version>1.33.0</version>
</dependency>
```

> **Note**: It's important to use compatible versions of Google libraries to avoid conflicts.

## Prerequisites

1. PostgreSQL client tools (`pg_dump`) installed on the server
2. Google Cloud Project with the Drive API enabled
3. OAuth 2.0 Client ID and Client Secret
4. A folder in Google Drive to store backups

## Configuration

### 1. Google Cloud Console Setup

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or use an existing one
3. Enable the Google Drive API
4. Create OAuth credentials:
   - Go to "APIs & Services" > "Credentials"
   - Create an OAuth client ID (Web application)
   - Add `http://localhost:8888/Callback` to the authorized redirect URIs
   - Note the Client ID and Client Secret

### 2. Application Properties

Add these properties to your `application.properties` file:

```properties
# Database Backup Configuration
backup.enabled=true
backup.local-path=/tmp/backups
backup.google-drive.folder-id=YOUR_GOOGLE_DRIVE_FOLDER_ID
backup.google-drive.client-id=YOUR_CLIENT_ID
backup.google-drive.client-secret=YOUR_CLIENT_SECRET
backup.max-files=5
backup.backup-hour=9
```

Replace:
- `YOUR_GOOGLE_DRIVE_FOLDER_ID` with your Google Drive folder ID
- `YOUR_CLIENT_ID` with your OAuth client ID
- `YOUR_CLIENT_SECRET` with your OAuth client secret
- `backup.backup-hour` with the hour of the day (0-23) when you want the backup to run (default is 9 for 9:00 AM)

## Implementation Steps

### 1. Create a Configuration Class

Create `BackupConfig.java` to hold configuration properties:

```java
package com.franco.dev.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "backup")
public class BackupConfig {
    
    private boolean enabled;
    private String localPath;
    private int maxFiles;
    private int backupHour = 9; // Default to 9 AM if not specified
    private GoogleDriveConfig googleDrive = new GoogleDriveConfig();
    
    public static class GoogleDriveConfig {
        private String folderId;
        private String clientId;
        private String clientSecret;
        
        // getters and setters
        public String getFolderId() {
            return folderId;
        }
        
        public void setFolderId(String folderId) {
            this.folderId = folderId;
        }
        
        public String getClientId() {
            return clientId;
        }
        
        public void setClientId(String clientId) {
            this.clientId = clientId;
        }
        
        public String getClientSecret() {
            return clientSecret;
        }
        
        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }
    }
    
    // getters and setters
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getLocalPath() {
        return localPath;
    }
    
    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }
    
    public GoogleDriveConfig getGoogleDrive() {
        return googleDrive;
    }
    
    public void setGoogleDrive(GoogleDriveConfig googleDrive) {
        this.googleDrive = googleDrive;
    }
    
    public int getMaxFiles() {
        return maxFiles;
    }
    
    public void setMaxFiles(int maxFiles) {
        this.maxFiles = maxFiles;
    }
    
    public int getBackupHour() {
        return backupHour;
    }
    
    public void setBackupHour(int backupHour) {
        if (backupHour >= 0 && backupHour <= 23) {
            this.backupHour = backupHour;
        }
    }
}
```

### 2. Create a Backup Scheduling Configuration

Create `BackupSchedulingConfig.java` to handle dynamic scheduling:

```java
package com.franco.dev.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BackupSchedulingConfig {

    private final BackupConfig backupConfig;
    
    public BackupSchedulingConfig(BackupConfig backupConfig) {
        this.backupConfig = backupConfig;
    }
    
    @Bean
    public String backupCronExpression() {
        // Format: seconds minutes hours day-of-month month day-of-week
        // This creates a cron expression to run at the configured hour (0 minutes, 0 seconds) every day
        return String.format("0 0 %d * * ?", backupConfig.getBackupHour());
    }
}
```

### 3. Create the Google Drive Service

Create `GoogleDriveService.java` to handle Google Drive operations:

```java
package com.franco.dev.service.utils;

import com.franco.dev.config.BackupConfig;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

@Service
public class GoogleDriveService {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveService.class);
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE_FILE);
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    
    private final BackupConfig backupConfig;
    private Drive driveService;
    
    @Autowired
    public GoogleDriveService(BackupConfig backupConfig) {
        this.backupConfig = backupConfig;
    }
    
    @PostConstruct
    public void initializeOnStartup() {
        if (backupConfig.isEnabled()) {
            log.info("Database backup is enabled. Initializing Google Drive service at startup...");
            try {
                initDriveService();
                log.info("Google Drive service initialized successfully");
            } catch (Exception e) {
                log.warn("Failed to initialize Google Drive service at startup. You'll need to authenticate manually: {}", e.getMessage());
                log.info("To authenticate manually, access: http://localhost:8082/auth/google/init");
            }
        } else {
            log.info("Database backup is disabled. Google Drive service will not be initialized at startup.");
        }
    }
    
    public synchronized void initDriveService() throws GeneralSecurityException, IOException {
        initDriveService(false);
    }
    
    public synchronized void initDriveService(boolean forceAuth) throws GeneralSecurityException, IOException {
        if (driveService != null && !forceAuth) {
            return; // Already initialized and no force auth requested
        }
        
        // If force auth is requested, clear the tokens directory
        if (forceAuth) {
            clearTokens();
            driveService = null; // Reset the service
        }
        
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        driveService = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName("Database Backup")
                .build();
    }
    
    /**
     * Clear stored tokens to force re-authentication
     */
    public void clearTokens() {
        log.info("Clearing stored tokens to force re-authentication");
        java.io.File tokensDir = new java.io.File(TOKENS_DIRECTORY_PATH);
        if (tokensDir.exists() && tokensDir.isDirectory()) {
            java.io.File[] files = tokensDir.listFiles();
            if (files != null) {
                for (java.io.File file : files) {
                    boolean deleted = file.delete();
                    log.info("Deleted token file {}: {}", file.getName(), deleted);
                }
            }
            boolean deletedDir = tokensDir.delete();
            log.info("Deleted tokens directory: {}", deletedDir);
        } else {
            log.info("No tokens directory found at {}", tokensDir.getAbsolutePath());
        }
    }
    
    private Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        // Create client secrets from config
        GoogleClientSecrets.Details details = new GoogleClientSecrets.Details()
                .setClientId(backupConfig.getGoogleDrive().getClientId())
                .setClientSecret(backupConfig.getGoogleDrive().getClientSecret())
                .setAuthUri("https://accounts.google.com/o/oauth2/auth")
                .setTokenUri("https://oauth2.googleapis.com/token")
                .setRedirectUris(Collections.singletonList("http://localhost:8888/Callback"));
        
        GoogleClientSecrets clientSecrets = new GoogleClientSecrets().setInstalled(details);
        
        // Build flow and trigger user authorization request
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        
        // Set up the LocalServerReceiver
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        
        // Create a custom AuthorizationCodeInstalledApp that ensures URL is displayed in logs
        AuthorizationCodeInstalledApp app = new AuthorizationCodeInstalledApp(flow, receiver) {
            @Override
            protected void onAuthorization(AuthorizationCodeRequestUrl authorizationUrl) throws IOException {
                // Always log the authorization URL clearly
                log.info("***************************************************************");
                log.info("Please open the following URL in a browser on any machine:");
                log.info("{}", authorizationUrl);
                log.info("***************************************************************");
                
                // Call the parent implementation which may attempt to open browser if not headless
                super.onAuthorization(authorizationUrl);
            }
        };
        
        return app.authorize("user");
    }
    
    public File uploadFile(java.io.File fileToUpload, String mimeType) throws IOException {
        try {
            initDriveService();
        } catch (Exception e) {
            log.error("Failed to initialize Google Drive service", e);
            throw new IOException("Drive service not available", e);
        }
        
        File fileMetadata = new File();
        fileMetadata.setName(fileToUpload.getName());
        
        if (backupConfig.getGoogleDrive().getFolderId() != null && !backupConfig.getGoogleDrive().getFolderId().isEmpty()) {
            fileMetadata.setParents(Collections.singletonList(backupConfig.getGoogleDrive().getFolderId()));
        }
        
        FileContent mediaContent = new FileContent(mimeType, fileToUpload);
        
        try {
            return driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id, name, createdTime")
                    .execute();
        } catch (IOException e) {
            log.error("Failed to upload file to Google Drive", e);
            throw e;
        }
    }
    
    public List<File> listFiles() throws IOException {
        try {
            initDriveService();
        } catch (Exception e) {
            log.error("Failed to initialize Google Drive service", e);
            throw new IOException("Drive service not available", e);
        }
        
        String folderId = backupConfig.getGoogleDrive().getFolderId();
        String query = folderId != null && !folderId.isEmpty() ? 
                String.format("'%s' in parents", folderId) : "";
        
        FileList result = driveService.files().list()
                .setQ(query)
                .setOrderBy("createdTime")
                .setFields("files(id, name, createdTime)")
                .execute();
                
        return result.getFiles();
    }
    
    public void deleteFile(String fileId) throws IOException {
        try {
            initDriveService();
        } catch (Exception e) {
            log.error("Failed to initialize Google Drive service", e);
            throw new IOException("Drive service not available", e);
        }
        
        try {
            driveService.files().delete(fileId).execute();
        } catch (IOException e) {
            log.error("Failed to delete file from Google Drive: " + fileId, e);
            throw e;
        }
    }
}
```

### 4. Create the Database Backup Service

Create `DatabaseBackupService.java` to handle scheduled backups:

```java
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

import javax.annotation.PostConstruct;
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
    
    // Using a dynamic cron expression based on the configured backup hour
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
```

### 5. Create Controller for Manual Operations

Create `BackupController.java` for manual backup triggering:

```java
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
```

### 6. Create an Authentication Controller

Create `GoogleAuthController.java` for Google authentication management:

```java
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
```

### 7. Enable Scheduling in Your Application

Make sure `@EnableScheduling` is added to your main application class:

```java
package com.franco.dev;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@EnableScheduling
@SpringBootApplication
public class FrancoSystemsApplication {
    // ...
}
```

## Using the Backup System

### Initial Authentication

When the application starts, the Google Drive authentication process begins automatically if `backup.enabled=true` in your properties. The authentication URL will be clearly displayed in the logs:

```
***************************************************************
Please open the following URL in a browser on any machine:
https://accounts.google.com/o/oauth2/auth?access_type=offline&client_id=YOUR_CLIENT_ID&redirect_uri=http://localhost:8888/Callback&response_type=code&scope=https://www.googleapis.com/auth/drive.file
***************************************************************
```

### Headless Server Authentication

For servers without a graphical interface (headless):

1. Start your application
2. Look for the authentication URL in the logs (with the clearly marked section above)
3. Copy this URL and open it in a browser on any machine with a graphical interface
4. Complete the Google authentication flow
5. The authentication will complete on the server

The implementation handles both headless and GUI environments. On headless servers, it will display the authentication URL prominently in the logs, allowing you to authenticate from any other machine.

### Changing Google Credentials

If you need to change Google credentials:

1. Update properties in `application.properties` with new credentials
2. Access `http://localhost:8082/auth/google/force-init` to force re-authentication
3. Complete the authentication flow with the new credentials

### Manual Backup Trigger

To manually trigger a backup (requires ADMIN role):
- Send a POST request to `http://localhost:8082/api/backup/trigger`

### Configuring Backup Time

You can configure when the backup will run by setting the `backup.backup-hour` property in application.properties:

- Use 24-hour format (0-23)
- For example:
  - `backup.backup-hour=0` - Run at midnight
  - `backup.backup-hour=9` - Run at 9:00 AM
  - `backup.backup-hour=15` - Run at 3:00 PM
  - `backup.backup-hour=23` - Run at 11:00 PM

## Troubleshooting

### Common Issues

1. **pg_dump Not Found**:
   - Ensure PostgreSQL client tools are installed
   - Verify command availability with `http://localhost:8082/api/backup/test-pg-dump`

2. **Authentication Issues**:
   - Ensure redirect URI is registered in Google Cloud Console
   - Clear tokens with `http://localhost:8082/auth/google/clear-tokens`
   - Check client ID and secret in application.properties
   - Verify the authentication URL is accessible from a browser

3. **No Authentication URL in Logs**:
   - Ensure `backup.enabled=true` in your application.properties
   - Check application logs carefully for the URL (surrounded by asterisks)
   - Manually trigger authentication via `http://localhost:8082/auth/google/init`

4. **Permission Issues**:
   - Ensure the backup directory is writable
   - Verify Google Drive folder permissions

5. **Dependency Conflicts**:
   - Use compatible versions of Google libraries
   - Avoid duplicate declarations in pom.xml

## Best Practices

1. **Security**:
   - Store sensitive information in a secure vault rather than application.properties
   - Use environment variables for credentials in production

2. **Backup Strategy**:
   - Consider compression for large databases
   - Implement backup verification
   - Monitor backup success/failures

3. **Performance**:
   - For very large databases, consider incremental backups
   - Schedule backups during low-traffic periods

## Conclusion

This implementation provides an automated PostgreSQL database backup solution that securely stores backups in Google Drive while maintaining a clean backup history by automatically removing older files when necessary.

The system is designed to be configurable, secure, and robust, with proper error handling and logging to help diagnose issues. It works well in both GUI and headless environments, making it suitable for all types of servers.

The enhanced version includes:
- Automatic initialization at application startup
- Clear display of authentication URLs in logs for headless environments
- Configurable backup time using a 24-hour format (0-23)
- Support for authentication from any machine with a browser

The system is designed to be configurable, secure, and robust, with proper error handling and logging to help diagnose issues. 