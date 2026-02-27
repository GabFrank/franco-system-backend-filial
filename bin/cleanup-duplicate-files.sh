#!/bin/bash

# Script to find and remove duplicate files with " 2." in their names
# These are likely created during build processes

echo "Scanning for duplicate files with ' 2.' pattern..."

# Find all files with " 2." in their names (indicating duplicates)
duplicate_files=$(find ./src -type f -name "* 2.*")

# Count how many files were found
file_count=$(echo "$duplicate_files" | grep -v "^$" | wc -l)

if [ "$file_count" -eq 0 ]; then
    echo "No duplicate files found."
    exit 0
fi

echo "Found $file_count duplicate files:"
echo "$duplicate_files"

# Ask for confirmation before deleting
read -p "Do you want to delete these $file_count duplicate files? (y/n): " confirm

if [ "$confirm" = "y" ] || [ "$confirm" = "Y" ]; then
    # Delete each duplicate file
    echo "$duplicate_files" | while read file; do
        if [ -n "$file" ]; then
            echo "Removing: $file"
            rm "$file"
        fi
    done
    echo "All duplicate files removed successfully."
else
    echo "Operation cancelled. No files were deleted."
fi

echo "Process complete." 