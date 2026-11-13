#!/usr/bin/env bash
set -e

echo "============================================="
echo "   VideoCalls - Firebase Project Setup"
echo "============================================="

# 1. Check Firebase CLI login
if ! firebase login:list 2>/dev/null | grep -q "@"; then
    echo "Opening browser to log into your Google/Firebase account..."
    firebase login
fi

# 2. Show projects and prompt for Project ID
echo ""
echo "Your available Firebase projects:"
firebase projects:list

echo ""
read -p "Enter your Firebase Project ID to connect: " PROJECT_ID

if [ -z "$PROJECT_ID" ]; then
    echo "Error: Project ID cannot be empty."
    exit 1
fi

# 3. Associate project in .firebaserc
firebase use "$PROJECT_ID"

# 4. Check or create Android App com.videocalls.app
echo ""
echo "Checking Android apps for com.videocalls.app..."
APP_LINE=$(firebase apps:list ANDROID --project "$PROJECT_ID" 2>/dev/null | grep "com.videocalls.app" || true)

if [ -z "$APP_LINE" ]; then
    echo "Creating Android app com.videocalls.app in project '$PROJECT_ID'..."
    firebase apps:create ANDROID com.videocalls.app --project "$PROJECT_ID"
    APP_LINE=$(firebase apps:list ANDROID --project "$PROJECT_ID" | grep "com.videocalls.app")
fi

APP_ID=$(echo "$APP_LINE" | awk '{print $4}')
echo "Found Android App ID: $APP_ID"

# 5. Fetch fresh google-services.json
echo "Downloading google-services.json into app/..."
firebase apps:sdkconfig ANDROID "$APP_ID" -o app/google-services.json --project "$PROJECT_ID"

# 6. Deploy Firestore Rules & Indexes
echo "Deploying Firestore security rules and composite indexes..."
firebase deploy --only firestore --project "$PROJECT_ID"

echo ""
echo "============================================="
echo " Successfully connected to Firebase!"
echo " - google-services.json updated"
echo " - Firestore security rules deployed"
echo " - Indexes deployed"
echo "============================================="
