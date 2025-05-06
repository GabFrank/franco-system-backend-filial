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