import { 
  signInWithPhoneNumber,
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  signOut as firebaseSignOut
} from 'firebase/auth';
import type { User, UserCredential } from 'firebase/auth';
import { doc, setDoc, getDoc, updateDoc } from 'firebase/firestore';
import { auth, db, setupRecaptcha } from '../config/firebase';
import { KJUR } from 'jsrsasign';
import { localStorageService } from './localStorage';

// JWT Secret - in production, this should be stored securely in environment variables
const JWT_SECRET = import.meta.env.VITE_JWT_SECRET || 'your-super-secret-jwt-key-change-in-production';

export interface FirebaseUser {
  uid: string;
  email?: string | null;
  phoneNumber?: string | null;
  displayName?: string | null;
  photoURL?: string | null;
  emailVerified: boolean;
  customClaims?: Record<string, any>;
}

export interface AuthTokens {
  firebaseToken: string;    // Firebase ID token
  customJwt: string;        // Custom JWT for your backend
  refreshToken?: string;
}

export interface StoredUserData {
  uid: string;
  email?: string;
  phoneNumber?: string;
  displayName?: string;
  companyUID?: string;
  role?: string;
  status: 'active' | 'inactive' | 'pending';
  tokens: AuthTokens;
  createdAt: string;
  lastLoginAt: string;
}

class FirebaseAuthService {
  private recaptchaVerifier: any = null;

  // Initialize reCAPTCHA
  initializeRecaptcha(containerId: string) {
    this.recaptchaVerifier = setupRecaptcha(containerId);
    return this.recaptchaVerifier;
  }

  // Generate custom JWT token for backend integration
  private generateCustomJWT(user: User | FirebaseUser): string {
    const header = { alg: 'HS256', typ: 'JWT' };
    const payload = {
      uid: user.uid,
      email: user.email || undefined,
      phoneNumber: user.phoneNumber || undefined,
      role: 'user',
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + (24 * 60 * 60) // 24 hours
    };

    return KJUR.jws.JWS.sign('HS256', header, payload, JWT_SECRET);
  }

  // Store user data in Firestore
  private async storeUserData(user: User | FirebaseUser, tokens: AuthTokens): Promise<void> {
    const userRef = doc(db, 'users', user.uid);
    const userData: StoredUserData = {
      uid: user.uid,
      email: user.email || undefined,
      phoneNumber: user.phoneNumber || undefined,
      displayName: user.displayName || undefined,
      status: 'active',
      tokens,
      createdAt: new Date().toISOString(),
      lastLoginAt: new Date().toISOString()
    };

    await setDoc(userRef, userData, { merge: true });
  }

  // Store tokens in localStorage for mobile access
  private storeTokensLocally(tokens: AuthTokens, user: User | FirebaseUser): void {
    const userData: StoredUserData = {
      uid: user.uid,
      email: user.email || undefined,
      phoneNumber: user.phoneNumber || undefined,
      displayName: user.displayName || undefined,
      status: 'active',
      tokens,
      createdAt: new Date().toISOString(),
      lastLoginAt: new Date().toISOString()
    };

    // Use the localStorageService for proper storage
    localStorageService.storeAuthData(userData, tokens);
  }

  // Phone Number Sign In
  async signInWithPhoneNumber(phoneNumber: string, containerId: string): Promise<string> {
    try {
      if (!this.recaptchaVerifier) {
        this.recaptchaVerifier = this.initializeRecaptcha(containerId);
      }

      const confirmation = await signInWithPhoneNumber(auth, phoneNumber, this.recaptchaVerifier);
      return confirmation.verificationId;
    } catch (error: any) {
      console.error('Phone sign-in error:', error);
      throw new Error(error.message || 'Failed to send OTP');
    }
  }

  // Verify OTP and complete phone sign-in
  async verifyOTP(_verificationId: string, _otp: string): Promise<StoredUserData> {
    try {
      // Note: In a real implementation, you'd need to import PhoneAuthProvider
      // and use confirmationResult.confirm(otp)
      // For now, this is a placeholder structure
      throw new Error('OTP verification requires PhoneAuthProvider implementation');
    } catch (error: any) {
      console.error('OTP verification error:', error);
      throw new Error(error.message || 'Failed to verify OTP');
    }
  }

  // Email/Password Sign In
  async signInWithEmail(email: string, password: string): Promise<StoredUserData> {
    try {
      const userCredential: UserCredential = await signInWithEmailAndPassword(auth, email, password);
      const user = userCredential.user;

      // Get Firebase ID token
      const firebaseToken = await user.getIdToken();
      
      // Generate custom JWT
      const customJwt = this.generateCustomJWT(user);

      const tokens: AuthTokens = {
        firebaseToken,
        customJwt
      };

      // Store in Firestore
      await this.storeUserData(user, tokens);

      // Store locally
      this.storeTokensLocally(tokens, user);

      // Return stored user data
      return await this.getUserData(user.uid);
    } catch (error: any) {
      console.error('Email sign-in error:', error);
      throw new Error(error.message || 'Failed to sign in with email');
    }
  }

  // Create new user with email/password
  async createUser(email: string, password: string, displayName?: string): Promise<StoredUserData> {
    try {
      const userCredential: UserCredential = await createUserWithEmailAndPassword(auth, email, password);
      const user = userCredential.user;

      // Update display name if provided
      if (displayName) {
        // Note: updateProfile is not available in the current Firebase auth version
        // This would need to be handled differently or removed
        console.log('Display name update not implemented in this version');
      }

      // Get Firebase ID token
      const firebaseToken = await user.getIdToken();
      
      // Generate custom JWT
      const customJwt = this.generateCustomJWT(user);

      const tokens: AuthTokens = {
        firebaseToken,
        customJwt
      };

      // Store in Firestore
      await this.storeUserData(user, tokens);

      // Store locally
      this.storeTokensLocally(tokens, user);

      return await this.getUserData(user.uid);
    } catch (error: any) {
      console.error('User creation error:', error);
      throw new Error(error.message || 'Failed to create user');
    }
  }

  // Get user data from Firestore
  async getUserData(uid: string): Promise<StoredUserData> {
    try {
      const userRef = doc(db, 'users', uid);
      const userDoc = await getDoc(userRef);
      
      if (!userDoc.exists()) {
        throw new Error('User not found in database');
      }

      return userDoc.data() as StoredUserData;
    } catch (error: any) {
      console.error('Get user data error:', error);
      throw new Error(error.message || 'Failed to get user data');
    }
  }

  // Update user data
  async updateUserData(uid: string, updates: Partial<StoredUserData>): Promise<void> {
    try {
      const userRef = doc(db, 'users', uid);
      await updateDoc(userRef, {
        ...updates,
        lastLoginAt: new Date().toISOString()
      });
    } catch (error: any) {
      console.error('Update user data error:', error);
      throw new Error(error.message || 'Failed to update user data');
    }
  }

  // Sign out
  async signOut(): Promise<void> {
    try {
      await firebaseSignOut(auth);
      localStorageService.clearAuthData();
    } catch (error: any) {
      console.error('Sign out error:', error);
      throw new Error(error.message || 'Failed to sign out');
    }
  }

  // Get current authenticated user
  getCurrentUser(): User | null {
    return auth.currentUser;
  }

  // Check if user is authenticated
  isAuthenticated(): boolean {
    return auth.currentUser !== null;
  }

  // Get stored tokens from localStorage
  getStoredTokens(): { tokens: AuthTokens; user: FirebaseUser } | null {
    try {
      const authData = localStorageService.getAuthData();
      if (!authData) return null;

      return {
        tokens: authData.tokens,
        user: {
          uid: authData.user.uid,
          email: authData.user.email,
          phoneNumber: authData.user.phoneNumber,
          displayName: authData.user.displayName,
          emailVerified: true
        }
      };
    } catch (error) {
      console.error('Error reading stored tokens:', error);
      return null;
    }
  }

  // Refresh Firebase token
  async refreshFirebaseToken(): Promise<string> {
    try {
      const user = auth.currentUser;
      if (!user) {
        throw new Error('No authenticated user');
      }

      return await user.getIdToken(true);
    } catch (error: any) {
      console.error('Token refresh error:', error);
      throw new Error(error.message || 'Failed to refresh token');
    }
  }
}

export const firebaseAuthService = new FirebaseAuthService();
