# Firebase Web App Setup Instructions

## 🔧 **Current Status:**
✅ Firebase Admin SDK credentials configured  
⚠️ Web App credentials needed for client-side authentication

## 📋 **What's Missing:**
You need to get the Firebase Web App credentials from the Firebase Console to complete the setup.

## 🔧 **Steps to Get Web App Credentials:**

### 1. Go to Firebase Console
- Visit: https://console.firebase.google.com
- Select project: `consultancy-platform-2d615`

### 2. Get Web App Configuration
1. Click **⚙️ Project Settings** (gear icon)
2. Scroll to **"Your apps"** section
3. Click **Web app** (or "Add app" → "Web")
4. Copy the configuration details

### 3. Update .env File
Replace these lines in your `.env` file:

```bash
# Replace with actual values from Firebase Console
VITE_FIREBASE_API_KEY=your-actual-api-key
VITE_FIREBASE_APP_ID=your-actual-app-id
VITE_FIREBASE_MESSAGING_SENDER_ID=your-actual-sender-id
```

### 4. Enable Authentication
1. Go to **Authentication** → **Sign-in method**
2. Enable **Email/Password** authentication
3. (Optional) Enable **Phone** authentication for OTP

## 🧪 **Testing Options:**

### Option 1: Complete Firebase Setup (Recommended)
- Follow the steps above
- Get real web app credentials
- Test full Firebase authentication

### Option 2: Test with Backend Only (Current)
- The system will fall back to your existing backend
- Firebase authentication will fail gracefully
- All other features work normally

## 🎯 **Current Authentication Flow:**

1. **Primary**: Firebase authentication (needs web app credentials)
2. **Fallback**: Existing backend authentication (working now)

## 📱 **Mobile Integration Ready:**
- Firebase Admin SDK credentials configured
- JWT token generation implemented
- Local storage for mobile access
- Export/import functionality for mobile apps

## 🚀 **Next Steps:**
1. Get web app credentials from Firebase Console
2. Update `.env` file
3. Test authentication with `admin@keliri.com` / `password123`
4. Verify dashboard and advertisement access

## 🔍 **Debug Information:**
The Firebase Auth Debugger (visible in development mode) will show:
- Authentication attempts
- Token status
- Configuration validation
- Error details
