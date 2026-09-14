#!/bin/bash

# MicUp RVC Build & Sign Script
# Requirements: Android SDK, NDK, Java 17, Gradle

set -e

echo "🔨 Building MicUp RVC APK..."

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
APP_NAME="MicUp-RVC"
BUILD_TYPE="release"  # debug or release
OUT_DIR="build/outputs/apk"

echo -e "${YELLOW}Step 1: Cleaning previous builds...${NC}"
./gradlew clean

echo -e "${YELLOW}Step 2: Building ${BUILD_TYPE} APK...${NC}"
./gradlew assemble${BUILD_TYPE^}

echo -e "${YELLOW}Step 3: Locating unsigned APK...${NC}"
UNSIGNED_APK=$(find ${OUT_DIR}/${BUILD_TYPE} -name "*.apk" | head -1)

if [ -z "$UNSIGNED_APK" ]; then
    echo -e "${RED}❌ APK not found! Build failed.${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Found: $UNSIGNED_APK${NC}"

# Check if keystore exists
if [ ! -f "micup.keystore" ]; then
    echo -e "${YELLOW}Creating new keystore...${NC}"
    keytool -genkey -v -keystore micup.keystore -keyalg RSA -keysize 2048 \
        -validity 10000 -alias micup-key \
        -storepass "micup123" -keypass "micup123" \
        -dname "CN=MicUp, O=MicUp, L=Earth, C=US"
    echo -e "${GREEN}✅ Keystore created: micup.keystore${NC}"
else
    echo -e "${GREEN}✅ Keystore found: micup.keystore${NC}"
fi

echo -e "${YELLOW}Step 4: Signing APK...${NC}"
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 \
    -keystore micup.keystore \
    -storepass "micup123" -keypass "micup123" \
    "$UNSIGNED_APK" micup-key

echo -e "${YELLOW}Step 5: Verifying signature...${NC}"
jarsigner -verify -verbose -certs "$UNSIGNED_APK"

echo -e "${YELLOW}Step 6: Zipaligning APK...${NC}"
ZIPALIGNED_APK="${UNSIGNED_APK%.apk}-aligned.apk"
${ANDROID_HOME}/build-tools/34.0.0/zipalign -v 4 "$UNSIGNED_APK" "$ZIPALIGNED_APK"

echo -e "${GREEN}✅ Build complete!${NC}"
echo -e "${GREEN}📦 APK ready: $ZIPALIGNED_APK${NC}"

# Copy to root for easy access
cp "$ZIPALIGNED_APK" "${APP_NAME}.apk"
echo -e "${GREEN}📦 Copied to: $(pwd)/${APP_NAME}.apk${NC}"

echo -e "${YELLOW}Step 7: Install on device (optional)${NC}"
echo "Run: adb install -r ${APP_NAME}.apk"
