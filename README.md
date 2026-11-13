# VideoCalls - Minimalist Android WebRTC Video Calling App

A modern, minimalist, battery-efficient Android video calling app built with **Jetpack Compose**, **WebRTC** (with public Google STUN servers and full mesh peer-to-peer for groups up to 8 participants), **Firebase Cloud Firestore** (for low-latency real-time signaling and contact discovery), and **Firebase Cloud Messaging (FCM)** (for high-priority incoming call alerts).

---

## Features

- **1-on-1 HD Video & Audio Calling**:
  - Full-screen remote video stream + Picture-in-Picture (PIP) local camera preview.
  - Crystal-clear audio with hardware echo cancellation, noise suppression, and automatic gain control.
  - Floating frosted glass controls: Mute/Unmute Mic, Flip Camera (Front/Back), Video On/Off, Speakerphone/Earpiece toggle, and End Call.
- **Group Calls (Mesh P2P up to 8 Participants)**:
  - Create groups and add contacts.
  - Dynamic responsive grid layout (2, 4, 6, 8 tiles) showing all participants in real time.
  - Active call indicator in groups with instant "Join Live Call" functionality.
- **Device Contact Matching**:
  - Automatically queries device contacts and normalizes phone numbers to standard E.164.
  - Segregates contacts into **"Available on VideoCalls"** (registered users with direct 1-tap call actions) and **"Other Contacts"** (with 1-tap SMS invite).
- **Incoming Call Alerts & Background Calling**:
  - Full-screen incoming call wake lock & notification with ringtone, vibration, Accept, and Decline actions.
  - Foreground Service (`CallService`) keeps calls active when switching apps or locking the device.
- **Minimalist Aesthetic & Theme Support**:
  - Built with Material 3.
  - Full **Light and Dark theme** support.

---

## Architecture & Technology Stack

- **UI Layer**: Jetpack Compose, Material 3, Navigation Compose.
- **WebRTC**: `io.getstream:stream-webrtc-android` (modern, actively maintained WebRTC engine with precompiled native ABIs).
- **Public STUN Servers**:
  - `stun:stun.l.google.com:19302`
  - `stun:stun1.l.google.com:19302`
  - `stun:stun2.l.google.com:19302`
  - `stun:stun3.l.google.com:19302`
  - `stun:stun4.l.google.com:19302`
- **Signaling Layer**: Firebase Cloud Firestore (real-time snapshot listeners for SDP Offers, Answers, and ICE candidates).
- **Push Notifications**: Firebase Cloud Messaging (FCM v1).
- **Contact Sync**: Android `ContactsContract` + Google `libphonenumber` for international E.164 normalization.

---

## Setup & Running the App

### 1. Firebase Project Setup

1. Go to the [Firebase Console](https://console.firebase.google.com/) and create a new project.
2. Add an Android app with package name: `com.videocalls.app`.
3. Download `google-services.json` and replace `app/google-services.json`.
4. Enable **Phone Authentication** under *Authentication > Sign-in method*.
5. Enable **Cloud Firestore** under *Firestore Database*.

### 2. Firestore Security Rules

Deploy the following rules in your Firebase Console under *Firestore Database > Rules*:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // User profile matching & discovery
    match /users/{userId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && request.auth.uid == userId;
    }
    
    // Groups
    match /groups/{groupId} {
      allow read: if request.auth != null && request.auth.uid in resource.data.memberUids;
      allow create: if request.auth != null;
      allow update, delete: if request.auth != null && request.auth.uid in resource.data.memberUids;
    }
    
    // Call signaling & rooms
    match /calls/{callId} {
      allow read, write: if request.auth != null;
      
      match /signals/{signalId} {
        allow read, write: if request.auth != null;
      }
      
      match /candidates/{candidateId} {
        allow read, write: if request.auth != null;
      }
    }
  }
}
```

### 3. Building and Running

You can open the project in **Android Studio** or build from the command line:

```bash
# Build Debug APK
./gradlew assembleDebug

# Install on connected device or emulator
./gradlew installDebug
```
The APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.
