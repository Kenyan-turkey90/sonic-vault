#!/bin/bash
# Bump versionCode and versionName in app/build.gradle.kts
# Usage: ./bump-version.sh [major|minor|patch]

set -euo pipefail

BUILD_FILE="app/build.gradle.kts"

if [ ! -f "$BUILD_FILE" ]; then
    echo "Error: $BUILD_FILE not found"
    exit 1
fi

# Extract current version
CURRENT_VERSION_NAME=$(grep -oP 'versionName\s*=\s*"\K[^"]+' "$BUILD_FILE")
CURRENT_VERSION_CODE=$(grep -oP 'versionCode\s*=\s*\K\d+' "$BUILD_FILE")

if [ -z "$CURRENT_VERSION_NAME" ] || [ -z "$CURRENT_VERSION_CODE" ]; then
    echo "Error: Could not extract current version from $BUILD_FILE"
    exit 1
fi

echo "Current version: $CURRENT_VERSION_NAME (code: $CURRENT_VERSION_CODE)"

# Parse version parts
IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT_VERSION_NAME"

# Bump based on argument
BUMP_TYPE="${1:-patch}"
case "$BUMP_TYPE" in
    major)
        MAJOR=$((MAJOR + 1))
        MINOR=0
        PATCH=0
        ;;
    minor)
        MINOR=$((MINOR + 1))
        PATCH=0
        ;;
    patch)
        PATCH=$((PATCH + 1))
        ;;
    *)
        echo "Usage: $0 [major|minor|patch]"
        exit 1
        ;;
esac

NEW_VERSION_NAME="$MAJOR.$MINOR.$PATCH"
NEW_VERSION_CODE=$((CURRENT_VERSION_CODE + 1))

echo "New version: $NEW_VERSION_NAME (code: $NEW_VERSION_CODE)"

# Update versionName
sed -i "s/versionName = \"$CURRENT_VERSION_NAME\"/versionName = \"$NEW_VERSION_NAME\"/" "$BUILD_FILE"

# Update versionCode
sed -i "s/versionCode = $CURRENT_VERSION_CODE/versionCode = $NEW_VERSION_CODE/" "$BUILD_FILE"

echo "Updated $BUILD_FILE"
echo "  versionName: $CURRENT_VERSION_NAME -> $NEW_VERSION_NAME"
echo "  versionCode: $CURRENT_VERSION_CODE -> $NEW_VERSION_CODE"
