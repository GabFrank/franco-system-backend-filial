package com.franco.dev.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Configuration class to prevent Flyway duplicate migration errors
 */
@Configuration
public class FlywayConfig {

    private static final Logger logger = LoggerFactory.getLogger(FlywayConfig.class);

    /**
     * Configure Flyway to clean up duplicate migrations before running
     */
    @Bean
    public FlywayConfigurationCustomizer flywayConfigurationCustomizer() {
        return configuration -> {
            try {
                // Log runtime environment information to help debug duplication issues
                logEnvironmentInfo();
                
                // Clean up duplicates in source directory first
                cleanupDuplicateMigrationsInSourceDir();
                
                // Clean up duplicates in target directory
                cleanupDuplicateMigrationsInTargetDir();
            } catch (Exception e) {
                logger.error("Error cleaning up duplicate migrations", e);
            }
        };
    }
    
    /**
     * Log information about the runtime environment to help debug duplication issues
     */
    private void logEnvironmentInfo() {
        logger.info("==== Environment Information ====");
        logger.info("OS Name: {}", System.getProperty("os.name"));
        logger.info("OS Version: {}", System.getProperty("os.version"));
        logger.info("Java Version: {}", System.getProperty("java.version"));
        logger.info("File Encoding: {}", System.getProperty("file.encoding"));
        logger.info("User Dir: {}", System.getProperty("user.dir"));
        logger.info("================================");
    }
    
    /**
     * Scans the source directory for duplicate Flyway migration files and removes them
     */
    private void cleanupDuplicateMigrationsInSourceDir() throws IOException {
        String sourceDir = "src/main/resources/db/migration";
        
        logger.info("Scanning for duplicate migrations in source directory: {}", sourceDir);
        
        Path migrationPath = Paths.get(sourceDir);
        if (!Files.exists(migrationPath)) {
            logger.info("Source migration directory not found: {}", sourceDir);
            return;
        }
        
        cleanupDuplicatesInDirectory(migrationPath);
    }
    
    /**
     * Scans the classpath for duplicate Flyway migration files and removes them
     */
    private void cleanupDuplicateMigrationsInTargetDir() throws IOException {
        String targetDir = "target/classes/db/migration";
        
        logger.info("Scanning for duplicate migrations in target directory: {}", targetDir);
        
        Path migrationPath = Paths.get(targetDir);
        if (!Files.exists(migrationPath)) {
            logger.info("Target migration directory not found: {}", targetDir);
            return;
        }
        
        cleanupDuplicatesInDirectory(migrationPath);
    }
    
    /**
     * Common logic to clean up duplicates in a directory
     */
    private void cleanupDuplicatesInDirectory(Path migrationPath) throws IOException {
        // Find all SQL migration files
        List<Path> migrationFiles = Files.walk(migrationPath)
            .filter(path -> path.toString().endsWith(".sql"))
            .filter(path -> {
                String filename = path.getFileName().toString();
                return filename.matches("V[0-9]+__.*\\.sql");
            })
            .collect(Collectors.toList());
        
        logger.info("Found {} migration files in {}", migrationFiles.size(), migrationPath);
        
        // Find all files with " 2." pattern (duplicates created by file system)
        List<Path> dupFiles = Files.walk(migrationPath)
            .filter(path -> path.getFileName().toString().contains(" 2."))
            .collect(Collectors.toList());
        
        if (!dupFiles.isEmpty()) {
            logger.warn("Found {} files with ' 2.' in their name in {}", dupFiles.size(), migrationPath);
            for (Path dupFile : dupFiles) {
                logger.info("Deleting duplicate with ' 2.' pattern: {}", dupFile);
                try {
                    Files.delete(dupFile);
                } catch (IOException e) {
                    logger.error("Failed to delete duplicate file: {}", dupFile, e);
                }
            }
        }
        
        // Group files by migration version
        Map<Integer, List<Path>> versionToPathsMap = new HashMap<>();
        
        for (Path path : migrationFiles) {
            String filename = path.getFileName().toString();
            int version = extractVersionNumber(filename);
            
            versionToPathsMap.computeIfAbsent(version, k -> new ArrayList<>())
                            .add(path);
        }
        
        // Delete duplicates, keeping only the newest file for each version
        for (Map.Entry<Integer, List<Path>> entry : versionToPathsMap.entrySet()) {
            Integer version = entry.getKey();
            List<Path> paths = entry.getValue();
            
            if (paths.size() > 1) {
                logger.warn("Found {} duplicate migrations for version V{}", paths.size(), version);
                
                // Log all duplicates for troubleshooting
                for (Path path : paths) {
                    logger.info("Duplicate for V{}: {} (Last modified: {})", 
                              version, path, Files.getLastModifiedTime(path));
                }
                
                // Sort by last modified time (newest first)
                paths.sort((path1, path2) -> {
                    try {
                        FileTime time1 = Files.getLastModifiedTime(path1);
                        FileTime time2 = Files.getLastModifiedTime(path2);
                        return time2.compareTo(time1); // Newest first
                    } catch (IOException e) {
                        return 0;
                    }
                });
                
                // Keep the newest, delete the rest
                Path newestFile = paths.get(0);
                logger.info("Keeping newest file: {}", newestFile);
                
                for (int i = 1; i < paths.size(); i++) {
                    Path duplicateFile = paths.get(i);
                    logger.info("Deleting duplicate: {}", duplicateFile);
                    try {
                        Files.delete(duplicateFile);
                    } catch (IOException e) {
                        logger.error("Failed to delete duplicate file: {}", duplicateFile, e);
                    }
                }
            }
        }
        
        logger.info("Duplicate migration cleanup complete for {}", migrationPath);
    }
    
    private int extractVersionNumber(String filename) {
        // Extract version number from filename (e.g., V10__create_stock_por_producto.sql -> 10)
        int underscoreIndex = filename.indexOf("__");
        if (underscoreIndex > 1) {
            String versionStr = filename.substring(1, underscoreIndex);
            try {
                return Integer.parseInt(versionStr);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }
} 