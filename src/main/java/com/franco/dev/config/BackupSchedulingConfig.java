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