import type { ReactNode } from 'react';
import React, { createContext, useContext, useReducer, useEffect } from 'react';
import { useFirebaseAuth } from '../hooks/useFirebaseAuth';
import type { StoredUserData } from '../services/firebaseAuth';

interface AuthState {
  user: StoredUserData | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  error: string | null;
  isInitialized: boolean;
}

type AuthAction =
  | { type: 'SET_USER'; payload: StoredUserData }
  | { type: 'SET_LOADING'; payload: boolean }
  | { type: 'SET_ERROR'; payload: string | null }
  | { type: 'SET_INITIALIZED'; payload: boolean }
  | { type: 'SIGN_OUT' };

const initialState: AuthState = {
  user: null,
  isLoading: true,
  isAuthenticated: false,
  error: null,
  isInitialized: false
};

function authReducer(state: AuthState, action: AuthAction): AuthState {
  switch (action.type) {
    case 'SET_USER':
      return {
        ...state,
        user: action.payload,
        isAuthenticated: !!action.payload,
        isLoading: false,
        error: null,
        isInitialized: true
      };
    case 'SET_LOADING':
      return {
        ...state,
        isLoading: action.payload
      };
    case 'SET_ERROR':
      return {
        ...state,
        error: action.payload,
        isLoading: false
      };
    case 'SET_INITIALIZED':
      return {
        ...state,
        isInitialized: action.payload,
        isLoading: false
      };
    case 'SIGN_OUT':
      return {
        ...state,
        user: null,
        isAuthenticated: false,
        isLoading: false,
        error: null
      };
    default:
      return state;
  }
}

interface AuthContextType extends AuthState {
  signInWithEmail: (email: string, password: string) => Promise<StoredUserData>;
  signInWithPhone: (phoneNumber: string) => Promise<string>;
  verifyOTP: (verificationId: string, otp: string) => Promise<StoredUserData>;
  signOut: () => Promise<void>;
  refreshTokens: () => Promise<void>;
  clearError: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

interface AuthProviderProps {
  children: ReactNode;
}

export function AuthProvider({ children }: AuthProviderProps) {
  const [state, dispatch] = useReducer(authReducer, initialState);
  const firebaseAuth = useFirebaseAuth();

  // Sync Firebase auth state with context
  useEffect(() => {
    if (firebaseAuth.user !== state.user) {
      if (firebaseAuth.user) {
        dispatch({ type: 'SET_USER', payload: firebaseAuth.user });
      } else if (firebaseAuth.isAuthenticated === false && !firebaseAuth.isLoading) {
        dispatch({ type: 'SIGN_OUT' });
      }
    }

    if (firebaseAuth.isLoading !== state.isLoading) {
      dispatch({ type: 'SET_LOADING', payload: firebaseAuth.isLoading });
    }

    if (firebaseAuth.error !== state.error) {
      dispatch({ type: 'SET_ERROR', payload: firebaseAuth.error });
    }

    if (!state.isInitialized && !firebaseAuth.isLoading) {
      dispatch({ type: 'SET_INITIALIZED', payload: true });
    }
  }, [firebaseAuth, state]);

  const contextValue: AuthContextType = {
    ...state,
    signInWithEmail: firebaseAuth.signInWithEmail,
    signInWithPhone: firebaseAuth.signInWithPhone,
    verifyOTP: firebaseAuth.verifyOTP,
    signOut: firebaseAuth.signOut,
    refreshTokens: firebaseAuth.refreshTokens,
    clearError: firebaseAuth.clearError
  };

  return (
    <AuthContext.Provider value={contextValue}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}

// Higher-order component for protected routes
export function withAuth<P extends object>(Component: React.ComponentType<P>) {
  return function AuthenticatedComponent(props: P) {
    const { isAuthenticated, isLoading } = useAuth();

    if (isLoading) {
      return (
        <div className="flex items-center justify-center min-h-screen">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-brand-500"></div>
        </div>
      );
    }

    if (!isAuthenticated) {
      return (
        <div className="flex items-center justify-center min-h-screen">
          <div className="text-center">
            <h2 className="text-2xl font-bold text-gray-900 mb-4">Authentication Required</h2>
            <p className="text-gray-600 mb-4">Please sign in to access this page.</p>
            <a 
              href="/admin/login" 
              className="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-brand-600 hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-brand-500"
            >
              Sign In
            </a>
          </div>
        </div>
      );
    }

    return <Component {...props} />;
  };
}
