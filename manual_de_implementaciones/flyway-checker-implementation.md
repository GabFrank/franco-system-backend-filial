# Flyway Duplicate Migration Prevention

This document explains how this project implements a solution to prevent Flyway errors related to duplicate migration versions, such as:

```
org.flywaydb.core.api.FlywayException: Found more than one migration with version 4
```

## Problem Overview

Flyway uses versioned SQL migration files (typically named like `V1__description.sql`, `V2__description.sql`, etc.) to manage database schema changes. Each migration version must be unique - if Flyway finds multiple migrations with the same version number, it throws an exception.

This can happen in various scenarios:
- When merging code from different branches where developers created migrations with the same version
- When using build tools that copy resources to multiple locations
- When modifying an existing migration and not properly removing the old one

## Solution Implementation

This project implements a dual-layer approach to prevent duplicate migration errors:

### 1. Java-based Duplicate Detection (Runtime)

The project includes a custom `FlywayConfig` class that detects and resolves duplicate migrations before Flyway executes:

```java
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
                // Clean up duplicates before Flyway starts
                cleanupDuplicateMigrations();
            } catch (Exception e) {
                logger.error("Error cleaning up duplicate migrations", e);
            }
        };
    }
    
    /**
     * Scans the classpath for duplicate Flyway migration files and removes them
     */
    private void cleanupDuplicateMigrations() throws IOException {
        String targetDir = "target/classes/db/migration";
        
        logger.info("Scanning for duplicate migrations in: {}", targetDir);
        
        Path migrationPath = Paths.get(targetDir);
        if (!Files.exists(migrationPath)) {
            logger.info("Migration directory not found: {}", targetDir);
            return;
        }
        
        // Find all SQL migration files
        List<Path> migrationFiles = Files.walk(migrationPath)
            .filter(path -> path.toString().endsWith(".sql"))
            .filter(path -> {
                String filename = path.getFileName().toString();
                return filename.matches("V[0-9]+__.*\\.sql");
            })
            .collect(Collectors.toList());
        
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
        
        logger.info("Duplicate migration cleanup complete");
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
```

This implementation:
1. Scans the `target/classes/db/migration` directory for SQL migration files
2. Groups files by their version number
3. If multiple files share the same version, it keeps only the newest file (by last modified time) and deletes the rest
4. Logs all actions for auditing purposes

### 2. Maven Build-time Cleanup Script

In addition to runtime checking, the project also includes a build-time cleanup mechanism using a shell script and Maven's exec-maven-plugin:

```xml
<!-- Maven configuration in pom.xml -->
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <version>3.0.0</version>
    <executions>
        <execution>
            <id>cleanup-duplicate-migrations</id>
            <phase>process-resources</phase>
            <goals>
                <goal>exec</goal>
            </goals>
            <configuration>
                <executable>${project.basedir}/cleanup-duplicate-migrations.sh</executable>
            </configuration>
        </execution>
    </executions>
</plugin>
```

This executes a shell script (`cleanup-duplicate-migrations.sh`) during the build process:

```bash
#!/bin/bash

# Script to detect and remove duplicate Flyway migration files
# This script searches for files with the pattern V[number]__ and ensures only one copy exists

TARGET_DIR="./target/classes/db/migration"

if [ ! -d "$TARGET_DIR" ]; then
    echo "No migration directory found at $TARGET_DIR. Exiting."
    exit 0
fi

echo "Scanning for duplicate migration files in $TARGET_DIR..."

# Get all migration version numbers (extract number after V and before __)
versions=$(find "$TARGET_DIR" -type f -name "V*__*.sql" | sed -E 's/.*V([0-9]+)__.*/\1/' | sort | uniq)

for version in $versions; do
    # Find all files with this version number
    files=$(find "$TARGET_DIR" -type f -name "V${version}__*.sql")
    count=$(echo "$files" | wc -l | xargs)
    
    if [ "$count" -gt 1 ]; then
        echo "Found $count files with version V$version:"
        echo "$files"
        
        # Keep the newest file (by modification time) and remove others
        newest=$(ls -t $files | head -1)
        echo "Keeping newest file: $newest"
        
        for file in $files; do
            if [ "$file" != "$newest" ]; then
                echo "Removing duplicate: $file"
                rm "$file"
            fi
        done
    fi
done

echo "Duplicate migration cleanup complete."
```

The shell script provides similar functionality to the Java implementation but runs during the Maven build process.

## Flyway Configuration

The project configures Flyway with the following settings in `application.properties`:

```properties
#flyway
flyway.url=${spring.datasource.url}
flyway.user=${spring.datasource.username}
flyway.password=${spring.datasource.password}
spring.flyway.baseline-on-migrate=true
spring.flyway.enabled=true
```

Additionally, a custom `FlywayMigrationStrategy` bean is defined in the main application class:

```java
@Bean
public FlywayMigrationStrategy cleanMigrateStrategy() {
    return flyway -> {
        flyway.migrate();
    };
}
```

## How it Works

1. When the application is built, the Maven exec plugin executes `cleanup-duplicate-migrations.sh` during the `process-resources` phase
2. The script checks for and removes duplicate migration files
3. When the application starts, `FlywayConfig.flywayConfigurationCustomizer()` is called
4. This performs a second check for duplicates using the Java implementation
5. With duplicates removed, Flyway can execute migrations without encountering the duplicate version error

## Best Practices to Avoid Duplicates

Despite these safeguards, it's still recommended to:

1. Coordinate migration version numbers across teams
2. Consider using timestamps instead of sequential numbers for versions (e.g., `V20230401123045__description.sql`)
3. Use a centralized way to determine the next available version number
4. Review migration files during code reviews

## Conclusion

This dual-layer approach ensures that duplicate migration files are detected and removed before Flyway attempts to validate them, preventing the common "Found more than one migration with version X" error. 