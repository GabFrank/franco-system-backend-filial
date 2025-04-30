package com.franco.dev.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "backup")
public class BackupConfig {
    
    private boolean enabled;
    private String localPath;
    private int maxFiles;
    private GoogleDriveConfig googleDrive = new GoogleDriveConfig();
    
    public static class GoogleDriveConfig {
        private String folderId;
        private String clientId;
        private String clientSecret;
        
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
} 