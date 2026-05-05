import type { StoredUserData, AuthTokens } from './firebaseAuth';

// Local Storage Service for managing authentication data
// This provides a fallback when Firestore is not available and for mobile access

export interface LocalStorageData {
  user: StoredUserData;
  tokens: AuthTokens;
  timestamp: number;
}

class LocalStorageService {
  private readonly STORAGE_KEY = 'firebase_auth_data';
  private readonly TOKEN_KEY = 'auth_tokens';
  private readonly USER_KEY = 'auth_user';

  // Store complete authentication data
  storeAuthData(userData: StoredUserData, tokens: AuthTokens): void {
    try {
      const storageData: LocalStorageData = {
        user: userData,
        tokens,
        timestamp: Date.now()
      };

      // Store main auth data
      localStorage.setItem(this.STORAGE_KEY, JSON.stringify(storageData));
      
      // Store tokens separately for easy access
      localStorage.setItem(this.TOKEN_KEY, JSON.stringify(tokens));
      
      // Store user data separately for easy access
      localStorage.setItem(this.USER_KEY, JSON.stringify(userData));

      // Maintain compatibility with existing admin system
      localStorage.setItem('admin_token', tokens.customJwt);
      localStorage.setItem('admin_user', JSON.stringify({
        uid: userData.uid,
        email: userData.email,
        phoneNumber: userData.phoneNumber,
        displayName: userData.displayName,
        companyUID: userData.companyUID
      }));

      console.log('✅ Auth data stored locally successfully');
    } catch (error) {
      console.error('❌ Error storing auth data locally:', error);
      throw new Error('Failed to store authentication data locally');
    }
  }

  // Get complete authentication data
  getAuthData(): LocalStorageData | null {
    try {
      const stored = localStorage.getItem(this.STORAGE_KEY);
      if (!stored) return null;

      const data = JSON.parse(stored);
      
      // Check if data is expired (optional - you can implement expiration logic)
      const isExpired = this.isDataExpired(data.timestamp);
      if (isExpired) {
        this.clearAuthData();
        return null;
      }

      return data;
    } catch (error) {
      console.error('❌ Error retrieving auth data:', error);
      return null;
    }
  }

  // Get only tokens
  getTokens(): AuthTokens | null {
    try {
      const stored = localStorage.getItem(this.TOKEN_KEY);
      return stored ? JSON.parse(stored) : null;
    } catch (error) {
      console.error('❌ Error retrieving tokens:', error);
      return null;
    }
  }

  // Get only user data
  getUserData(): StoredUserData | null {
    try {
      const stored = localStorage.getItem(this.USER_KEY);
      return stored ? JSON.parse(stored) : null;
    } catch (error) {
      console.error('❌ Error retrieving user data:', error);
      return null;
    }
  }

  // Update user data
  updateUserData(updates: Partial<StoredUserData>): void {
    try {
      const currentData = this.getAuthData();
      if (!currentData) {
        throw new Error('No auth data found to update');
      }

      const updatedUserData = { ...currentData.user, ...updates };
      this.storeAuthData(updatedUserData, currentData.tokens);
    } catch (error) {
      console.error('❌ Error updating user data:', error);
      throw new Error('Failed to update user data');
    }
  }

  // Update tokens
  updateTokens(tokens: Partial<AuthTokens>): void {
    try {
      const currentData = this.getAuthData();
      if (!currentData) {
        throw new Error('No auth data found to update');
      }

      const updatedTokens = { ...currentData.tokens, ...tokens };
      this.storeAuthData(currentData.user, updatedTokens);
    } catch (error) {
      console.error('❌ Error updating tokens:', error);
      throw new Error('Failed to update tokens');
    }
  }

  // Clear all authentication data
  clearAuthData(): void {
    try {
      localStorage.removeItem(this.STORAGE_KEY);
      localStorage.removeItem(this.TOKEN_KEY);
      localStorage.removeItem(this.USER_KEY);
      
      // Clear existing admin system data
      localStorage.removeItem('admin_token');
      localStorage.removeItem('admin_user');
      localStorage.removeItem('ad_mobile_token');

      console.log('✅ Auth data cleared from local storage');
    } catch (error) {
      console.error('❌ Error clearing auth data:', error);
    }
  }

  // Check if stored data is expired (24 hours by default)
  private isDataExpired(timestamp: number, maxAge: number = 24 * 60 * 60 * 1000): boolean {
    return Date.now() - timestamp > maxAge;
  }

  // Check if user is authenticated based on local storage
  isLocallyAuthenticated(): boolean {
    const authData = this.getAuthData();
    const tokens = this.getTokens();
    
    return !!(authData && tokens && tokens.customJwt);
  }

  // Get Firebase token for API calls
  getFirebaseToken(): string | null {
    const tokens = this.getTokens();
    return tokens?.firebaseToken || null;
  }

  // Get custom JWT token for backend API calls
  getCustomJWT(): string | null {
    const tokens = this.getTokens();
    return tokens?.customJwt || null;
  }

  // Sync with existing admin system
  syncWithAdminSystem(): void {
    try {
      const adminToken = localStorage.getItem('admin_token');
      const adminUser = localStorage.getItem('admin_user');

      if (adminToken && adminUser) {
        // If admin system has data but Firebase storage doesn't, migrate it
        if (!this.getAuthData()) {
          const user = JSON.parse(adminUser);
          const tokens: AuthTokens = {
            firebaseToken: '',
            customJwt: adminToken
          };

          const userData: StoredUserData = {
            uid: user.uid || user.id || '',
            email: user.email,
            phoneNumber: user.phoneNumber,
            displayName: user.displayName || user.name,
            companyUID: user.companyUID,
            role: user.role || 'admin',
            status: 'active',
            tokens,
            createdAt: new Date().toISOString(),
            lastLoginAt: new Date().toISOString()
          };

          this.storeAuthData(userData, tokens);
          console.log('✅ Migrated existing admin data to Firebase storage');
        }
      }
    } catch (error) {
      console.error('❌ Error syncing with admin system:', error);
    }
  }

  // Export data for mobile app usage
  exportForMobile(): string | null {
    try {
      const authData = this.getAuthData();
      if (!authData) return null;

      return JSON.stringify({
        user: authData.user,
        tokens: authData.tokens,
        exportedAt: Date.now()
      });
    } catch (error) {
      console.error('❌ Error exporting data for mobile:', error);
      return null;
    }
  }

  // Import data from mobile app
  importFromMobile(mobileData: string): boolean {
    try {
      const data = JSON.parse(mobileData);
      
      if (data.user && data.tokens) {
        this.storeAuthData(data.user, data.tokens);
        console.log('✅ Mobile data imported successfully');
        return true;
      }
      
      return false;
    } catch (error) {
      console.error('❌ Error importing mobile data:', error);
      return false;
    }
  }
}

export const localStorageService = new LocalStorageService();
