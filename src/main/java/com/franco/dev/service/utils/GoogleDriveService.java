package com.franco.dev.service.utils;

import com.franco.dev.config.BackupConfig;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
        try {
            initDriveService();
        } catch (Exception e) {
            log.error("Failed to initialize Google Drive service", e);
        }
    }
    
    private void initDriveService() throws GeneralSecurityException, IOException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        driveService = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName("Database Backup")
                .build();
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
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }
    
    public File uploadFile(java.io.File fileToUpload, String mimeType) throws IOException {
        if (driveService == null) {
            try {
                initDriveService();
            } catch (Exception e) {
                log.error("Failed to initialize Google Drive service", e);
                throw new IOException("Drive service not available", e);
            }
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
        if (driveService == null) {
            try {
                initDriveService();
            } catch (Exception e) {
                log.error("Failed to initialize Google Drive service", e);
                throw new IOException("Drive service not available", e);
            }
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
        if (driveService == null) {
            try {
                initDriveService();
            } catch (Exception e) {
                log.error("Failed to initialize Google Drive service", e);
                throw new IOException("Drive service not available", e);
            }
        }
        
        try {
            driveService.files().delete(fileId).execute();
        } catch (IOException e) {
            log.error("Failed to delete file from Google Drive: " + fileId, e);
            throw e;
        }
    }
} 