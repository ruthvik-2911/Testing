import { localStorageService } from '../services/localStorage';
import { firebaseAuthService } from '../services/firebaseAuth';

export class TokenManager {
  // Check if token is expired
  static isTokenExpired(token: string): boolean {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const currentTime = Math.floor(Date.now() / 1000);
      return payload.exp < currentTime;
    } catch {
      return true; // If token is invalid, consider it expired
    }
  }

  // Get valid Firebase token
  static async getValidFirebaseToken(): Promise<string | null> {
    try {
      const tokens = localStorageService.getTokens();
      if (!tokens?.firebaseToken) return null;

      // Check if Firebase token is expired
      if (this.isTokenExpired(tokens.firebaseToken)) {
        console.log('Firebase token expired, refreshing...');
        const newToken = await firebaseAuthService.refreshFirebaseToken();
        localStorageService.updateTokens({ firebaseToken: newToken });
        return newToken;
      }

      return tokens.firebaseToken;
    } catch (error) {
      console.error('Error getting valid Firebase token:', error);
      return null;
    }
  }

  // Get valid custom JWT token
  static getValidCustomJWT(): string | null {
    try {
      const tokens = localStorageService.getTokens();
      if (!tokens?.customJwt) return null;

      // Check if custom JWT is expired
      if (this.isTokenExpired(tokens.customJwt)) {
        console.log('Custom JWT expired');
        return null;
      }

      return tokens.customJwt;
    } catch (error) {
      console.error('Error getting valid custom JWT:', error);
      return null;
    }
  }

  // Get authentication headers for API calls
  static async getAuthHeaders(): Promise<Record<string, string>> {
    const headers: Record<string, string> = {};

    // Try to get Firebase token
    const firebaseToken = await this.getValidFirebaseToken();
    if (firebaseToken) {
      headers['Authorization'] = `Bearer ${firebaseToken}`;
    }

    // Fallback to custom JWT
    const customJwt = this.getValidCustomJWT();
    if (customJwt && !headers['Authorization']) {
      headers['Authorization'] = `Bearer ${customJwt}`;
    }

    return headers;
  }

  // Check if user is authenticated with valid tokens
  static async isAuthenticated(): Promise<boolean> {
    const firebaseToken = await this.getValidFirebaseToken();
    const customJwt = this.getValidCustomJWT();
    
    return !!(firebaseToken || customJwt);
  }

  // Force token refresh
  static async refreshTokens(): Promise<boolean> {
    try {
      const firebaseToken = await this.getValidFirebaseToken();
      return !!firebaseToken;
    } catch (error) {
      console.error('Token refresh failed:', error);
      return false;
    }
  }

  // Get user info from token
  static getUserFromToken(token: string): any {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return {
        uid: payload.uid,
        email: payload.email,
        role: payload.role,
        exp: payload.exp,
        iat: payload.iat
      };
    } catch (error) {
      console.error('Error parsing token:', error);
      return null;
    }
  }

  // Validate token format
  static isValidTokenFormat(token: string): boolean {
    try {
      const parts = token.split('.');
      return parts.length === 3 && parts.every(part => part.length > 0);
    } catch {
      return false;
    }
  }

  // Get token expiration time
  static getTokenExpiration(token: string): number | null {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.exp || null;
    } catch {
      return null;
    }
  }

  // Get time until token expires (in seconds)
  static getTimeUntilExpiration(token: string): number {
    const expiration = this.getTokenExpiration(token);
    if (!expiration) return 0;
    
    const currentTime = Math.floor(Date.now() / 1000);
    return Math.max(0, expiration - currentTime);
  }

  // Check if token needs refresh (expires in less than 5 minutes)
  static needsRefresh(token: string): boolean {
    const timeUntilExpiration = this.getTimeUntilExpiration(token);
    return timeUntilExpiration < 300; // 5 minutes in seconds
  }

  // Clear all tokens
  static clearTokens(): void {
    localStorageService.clearAuthData();
  }

  // Export tokens for mobile app
  static exportTokens(): string | null {
    return localStorageService.exportForMobile();
  }

  // Import tokens from mobile app
  static importTokens(mobileData: string): boolean {
    return localStorageService.importFromMobile(mobileData);
  }
}

// Export singleton instance
export const tokenManager = TokenManager;
