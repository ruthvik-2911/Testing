# Firebase Authentication Integration Guide

## Overview
This document explains the Firebase authentication integration implemented in the KELIRI admin panel, which provides JWT token generation and storage in both mobile and local databases.

## Features Implemented

### 1. Firebase Authentication Service (`src/services/firebaseAuth.ts`)
- **Email/Password Authentication**: Sign in users with Firebase email authentication
- **Phone OTP Authentication**: Send and verify OTPs via Firebase phone authentication
- **JWT Token Generation**: Generate custom JWT tokens for backend integration
- **Firestore Storage**: Store user data and tokens in Firestore
- **Local Storage**: Maintain tokens in localStorage for offline access

### 2. Local Storage Service (`src/services/localStorage.ts`)
- **Token Management**: Store and retrieve Firebase tokens and custom JWTs
- **User Data Storage**: Persist user information locally
- **Mobile Data Sync**: Export/import authentication data for mobile apps
- **Fallback Support**: Works when Firestore is unavailable

### 3. Phone OTP Component (`src/components/PhoneOTPAuth.tsx`)
- **OTP Input Interface**: 6-digit OTP input with auto-focus
- **Resend Timer**: 60-second countdown for OTP resend
- **Error Handling**: Comprehensive error display and retry logic
- **reCAPTCHA Integration**: Invisible reCAPTCHA for security

### 4. Updated Login Component (`src/pages/admin/Login.tsx`)
- **Dual Authentication**: Firebase first, fallback to existing backend
- **Seamless Integration**: Maintains existing API compatibility
- **Company UID Resolution**: Integrates with Ad Mobile API for company data

## Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Login Page    │───▶│ Firebase Auth    │───▶│   Firestore     │
│                 │    │   Service        │    │   Database      │
└─────────────────┘    └──────────────────┘    └─────────────────┘
         │                       │                       │
         │                       ▼                       │
         │              ┌──────────────────┐              │
         │              │   Local Storage  │              │
         │              │   Service        │              │
         │              └──────────────────┘              │
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Mobile App    │    │   Backend API    │    │   Admin Panel   │
│   Access        │    │   Integration    │    │   Navigation    │
└─────────────────┘    └──────────────────┘    └─────────────────┘
```

## Token Flow

1. **User Authentication**: Firebase authenticates user
2. **Token Generation**: 
   - Firebase ID token (for Firebase services)
   - Custom JWT (for your backend APIs)
3. **Storage**: Tokens stored in both Firestore and localStorage
4. **Access**: Mobile apps can access tokens via localStorage or API

## Setup Instructions

### 1. Firebase Configuration
Create a `.env` file based on `.env.example`:

```bash
# Firebase Configuration
VITE_FIREBASE_API_KEY=your-api-key-here
VITE_FIREBASE_AUTH_DOMAIN=your-project.firebaseapp.com
VITE_FIREBASE_PROJECT_ID=your-project-id
VITE_FIREBASE_STORAGE_BUCKET=your-project.appspot.com
VITE_FIREBASE_MESSAGING_SENDER_ID=123456789
VITE_FIREBASE_APP_ID=your-app-id

# JWT Secret for custom token generation
VITE_JWT_SECRET=your-super-secret-jwt-key-change-in-production
```

### 2. Firebase Project Setup
1. Create a Firebase project at https://console.firebase.google.com
2. Enable Authentication providers:
   - Email/Password
   - Phone (requires phone number verification)
3. Set up Firestore database
4. Configure reCAPTCHA for phone authentication

### 3. Dependencies
The following packages are already installed:
- `firebase` - Firebase SDK
- `jsonwebtoken` - JWT token generation
- `@types/jsonwebtoken` - TypeScript types

## Usage Examples

### Email/Password Authentication
```typescript
import { firebaseAuthService } from '../services/firebaseAuth';

try {
  const userData = await firebaseAuthService.signInWithEmail(email, password);
  console.log('User authenticated:', userData);
  // Navigate to dashboard
} catch (error) {
  console.error('Authentication failed:', error);
}
```

### Phone OTP Authentication
```typescript
// The PhoneOTPAuth component handles this automatically
// Or use the service directly:
const verificationId = await firebaseAuthService.signInWithPhoneNumber(phoneNumber, 'recaptcha-container');
const userData = await firebaseAuthService.verifyOTP(verificationId, otp);
```

### Token Access
```typescript
import { localStorageService } from '../services/localStorage';

// Get stored tokens
const tokens = localStorageService.getTokens();
const customJwt = localStorageService.getCustomJWT();

// Check authentication status
const isAuthenticated = localStorageService.isLocallyAuthenticated();
```

## Mobile Integration

### Export Authentication Data
```typescript
const mobileData = localStorageService.exportForMobile();
// Send this data to your mobile app
```

### Import in Mobile App
```typescript
// In your mobile app
localStorageService.importFromMobile(mobileData);
const tokens = localStorageService.getTokens();
```

## Security Considerations

1. **JWT Secret**: Use a strong, randomly generated secret in production
2. **Token Expiration**: Custom JWTs expire in 24 hours
3. **Firebase Security Rules**: Configure proper Firestore security rules
4. **reCAPTCHA**: Phone authentication includes reCAPTCHA verification
5. **Environment Variables**: Never commit sensitive credentials to git

## API Compatibility

The integration maintains full compatibility with existing APIs:
- `admin_token` in localStorage for existing admin API calls
- `admin_user` in localStorage for user data
- Fallback to original backend authentication if Firebase fails

## Error Handling

The system includes comprehensive error handling:
- Firebase authentication errors
- Network connectivity issues
- Token expiration handling
- Graceful fallback to existing authentication

## Testing

The development server is running at http://127.0.0.1:5176

To test:
1. Configure Firebase credentials in `.env`
2. Try email/password authentication
3. Test phone OTP (requires Firebase phone auth setup)
4. Verify token storage in localStorage
5. Check Firestore database for user records

## Future Enhancements

1. **Social Login**: Add Google, Facebook, etc.
2. **Multi-Factor Auth**: Implement 2FA
3. **Session Management**: Add session timeout handling
4. **Biometric Auth**: Mobile biometric authentication
5. **SSO Integration**: SAML/OIDC support

## Troubleshooting

### Common Issues
1. **Firebase Config Error**: Verify `.env` variables are correct
2. **reCAPTCHA Issues**: Check domain configuration in Firebase console
3. **Token Errors**: Ensure JWT secret is set properly
4. **Firestore Permissions**: Verify security rules allow read/write

### Debug Mode
Enable console logging to debug authentication flow:
```javascript
// In browser console
localStorage.getItem('firebase_auth');
localStorage.getItem('admin_token');
```

## Support

For issues with Firebase integration:
1. Check Firebase console configuration
2. Verify environment variables
3. Review browser console errors
4. Test with Firebase emulator if needed
