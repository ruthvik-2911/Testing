# Firebase Project Setup Guide

## Quick Setup Steps:

### 1. Create Firebase Project
1. Go to https://console.firebase.google.com
2. Click "Add project"
3. Enter project name: "keliri-admin"
4. Continue with setup options

### 2. Enable Authentication
1. In Firebase Console, go to "Authentication"
2. Click "Get Started"
3. Enable "Email/Password" sign-in method
4. Enable "Phone" authentication (for OTP)

### 3. Get Configuration
1. Go to Project Settings (gear icon)
2. Under "Your apps", click "Web app"
3. Copy the Firebase configuration
4. Update your `.env` file with real values

### 4. Set Up Firestore (Optional)
1. Go to "Firestore Database" in Firebase Console
2. Create database
3. Start in test mode (for development)
4. Update security rules if needed

## Real Firebase Config Example:
```bash
VITE_FIREBASE_API_KEY=AIzaSyActualRealApiKeyFromFirebase
VITE_FIREBASE_AUTH_DOMAIN=keliri-admin.firebaseapp.com
VITE_FIREBASE_PROJECT_ID=keliri-admin-12345
VITE_FIREBASE_STORAGE_BUCKET=keliri-admin-12345.appspot.com
VITE_FIREBASE_MESSAGING_SENDER_ID=123456789012
VITE_FIREBASE_APP_ID=1:123456789012:web:abcdef123456789
```

## Test Users in Firebase:
1. Go to Authentication → Users
2. Add test user: admin@keliri.com with password123
3. This will allow Firebase authentication to work

## Benefits of Real Firebase:
- ✅ Actual authentication flows
- ✅ Real token generation
- ✅ Phone OTP functionality
- ✅ Firestore user data storage
- ✅ Mobile app integration ready
