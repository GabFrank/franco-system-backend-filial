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