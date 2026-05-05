import { useAuth } from '../context/AuthContext';
import { LogOut, User, Shield } from 'lucide-react';

export default function AuthStatus() {
  const { user, isAuthenticated, isLoading, signOut } = useAuth();

  if (isLoading) {
    return (
      <div className="flex items-center gap-2 text-sm text-gray-500">
        <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-brand-500"></div>
        <span>Loading...</span>
      </div>
    );
  }

  if (!isAuthenticated || !user) {
    return (
      <div className="flex items-center gap-2 text-sm text-gray-500">
        <Shield className="w-4 h-4" />
        <span>Not authenticated</span>
      </div>
    );
  }

  const handleSignOut = async () => {
    try {
      await signOut();
      window.location.href = '/admin/login';
    } catch (error) {
      console.error('Sign out error:', error);
    }
  };

  return (
    <div className="flex items-center gap-4">
      <div className="flex items-center gap-2 text-sm">
        <User className="w-4 h-4 text-brand-500" />
        <span className="font-medium text-gray-700">
          {user.displayName || user.email || 'Admin User'}
        </span>
        <span className="text-xs text-gray-500">
          ({user.role || 'admin'})
        </span>
      </div>
      
      <button
        onClick={handleSignOut}
        className="flex items-center gap-2 px-3 py-1.5 text-sm text-red-600 hover:text-red-700 hover:bg-red-50 rounded-lg transition-colors"
        title="Sign out"
      >
        <LogOut className="w-4 h-4" />
        <span>Sign Out</span>
      </button>
    </div>
  );
}
