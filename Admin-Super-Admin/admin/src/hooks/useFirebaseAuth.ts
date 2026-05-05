import { useState, useEffect, useCallback } from 'react';
import { onAuthStateChanged } from 'firebase/auth';
import { auth } from '../config/firebase';
import { firebaseAuthService } from '../services/firebaseAuth';
import { localStorageService } from '../services/localStorage';
import type { StoredUserData } from '../services/firebaseAuth';

export interface UseFirebaseAuthReturn {
  user: StoredUserData | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  error: string | null;
  signInWithEmail: (email: string, password: string) => Promise<StoredUserData>;
  signInWithPhone: (phoneNumber: string) => Promise<string>;
  verifyOTP: (verificationId: string, otp: string) => Promise<StoredUserData>;
  signOut: () => Promise<void>;
  refreshTokens: () => Promise<void>;
  clearError: () => void;
}

export function useFirebaseAuth(): UseFirebaseAuthReturn {
  const [user, setUser] = useState<StoredUserData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Clear error function
  const clearError = useCallback(() => {
    setError(null);
  }, []);

  // Initialize auth state
  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, async (firebaseUser) => {
      setIsLoading(true);
      
      try {
        if (firebaseUser) {
          // User is authenticated with Firebase
          // Try to get stored data first
          const storedData = localStorageService.getAuthData();
          
          if (storedData) {
            setUser(storedData.user);
          } else {
            // If no stored data, get from Firestore
            try {
              const userData = await firebaseAuthService.getUserData(firebaseUser.uid);
              setUser(userData);
            } catch (firestoreError) {
              console.warn('Could not get user data from Firestore:', firestoreError);
              // Create minimal user data from Firebase user
              const minimalUser: StoredUserData = {
                uid: firebaseUser.uid,
                email: firebaseUser.email || undefined,
                phoneNumber: firebaseUser.phoneNumber || undefined,
                displayName: firebaseUser.displayName || undefined,
                status: 'active',
                tokens: {
                  firebaseToken: await firebaseUser.getIdToken(),
                  customJwt: '' // Will be generated if needed
                },
                createdAt: new Date().toISOString(),
                lastLoginAt: new Date().toISOString()
              };
              setUser(minimalUser);
            }
          }
        } else {
          // User is not authenticated
          setUser(null);
        }
      } catch (error) {
        console.error('Auth state change error:', error);
        setError('Failed to initialize authentication');
        setUser(null);
      } finally {
        setIsLoading(false);
      }
    });

    return unsubscribe;
  }, []);

  // Sign in with email and password
  const signInWithEmail = useCallback(async (email: string, password: string): Promise<StoredUserData> => {
    setIsLoading(true);
    setError(null);
    
    try {
      const userData = await firebaseAuthService.signInWithEmail(email, password);
      setUser(userData);
      return userData;
    } catch (error: any) {
      const errorMessage = error.message || 'Failed to sign in with email';
      setError(errorMessage);
      throw error;
    } finally {
      setIsLoading(false);
    }
  }, []);

  // Sign in with phone number
  const signInWithPhone = useCallback(async (phoneNumber: string): Promise<string> => {
    setIsLoading(true);
    setError(null);
    
    try {
      const verificationId = await firebaseAuthService.signInWithPhoneNumber(phoneNumber, 'recaptcha-container');
      return verificationId;
    } catch (error: any) {
      const errorMessage = error.message || 'Failed to send OTP';
      setError(errorMessage);
      throw error;
    } finally {
      setIsLoading(false);
    }
  }, []);

  // Verify OTP
  const verifyOTP = useCallback(async (verificationId: string, otp: string): Promise<StoredUserData> => {
    setIsLoading(true);
    setError(null);
    
    try {
      const userData = await firebaseAuthService.verifyOTP(verificationId, otp);
      setUser(userData);
      return userData;
    } catch (error: any) {
      const errorMessage = error.message || 'Failed to verify OTP';
      setError(errorMessage);
      throw error;
    } finally {
      setIsLoading(false);
    }
  }, []);

  // Sign out
  const signOut = useCallback(async (): Promise<void> => {
    setIsLoading(true);
    setError(null);
    
    try {
      await firebaseAuthService.signOut();
      setUser(null);
    } catch (error: any) {
      const errorMessage = error.message || 'Failed to sign out';
      setError(errorMessage);
      throw error;
    } finally {
      setIsLoading(false);
    }
  }, []);

  // Refresh tokens
  const refreshTokens = useCallback(async (): Promise<void> => {
    if (!user) return;
    
    try {
      const newFirebaseToken = await firebaseAuthService.refreshFirebaseToken();
      const updatedTokens = { ...user.tokens, firebaseToken: newFirebaseToken };
      
      // Update localStorage
      localStorageService.updateTokens(updatedTokens);
      
      // Update user state
      setUser({ ...user, tokens: updatedTokens });
    } catch (error: any) {
      console.error('Token refresh error:', error);
      setError('Failed to refresh tokens');
    }
  }, [user]);

  // Check if user is authenticated
  const isAuthenticated = !!user;

  return {
    user,
    isLoading,
    isAuthenticated,
    error,
    signInWithEmail,
    signInWithPhone,
    verifyOTP,
    signOut,
    refreshTokens,
    clearError
  };
}
