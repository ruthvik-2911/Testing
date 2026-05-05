import { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { localStorageService } from '../services/localStorage';
import { tokenManager } from '../utils/tokenManager';
import { Bug, Copy, CheckCircle, AlertCircle } from 'lucide-react';

export default function FirebaseAuthDebugger() {
  const { user, isAuthenticated, error } = useAuth();
  const [debugInfo, setDebugInfo] = useState<any>({});
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    const collectDebugInfo = async () => {
      const info = {
        authContext: {
          user,
          isAuthenticated,
          error
        },
        localStorage: {
          authData: localStorageService.getAuthData(),
          tokens: localStorageService.getTokens(),
          userData: localStorageService.getUserData(),
          isLocallyAuthenticated: localStorageService.isLocallyAuthenticated()
        },
        tokenManager: {
          isAuthenticated: await tokenManager.isAuthenticated(),
          validFirebaseToken: await tokenManager.getValidFirebaseToken(),
          validCustomJWT: tokenManager.getValidCustomJWT()
        },
        rawLocalStorage: {
          admin_token: localStorage.getItem('admin_token'),
          admin_user: localStorage.getItem('admin_user'),
          firebase_auth: localStorage.getItem('firebase_auth'),
          auth_tokens: localStorage.getItem('auth_tokens'),
          auth_user: localStorage.getItem('auth_user')
        }
      };
      setDebugInfo(info);
    };

    collectDebugInfo();
  }, [user, isAuthenticated, error]);

  const copyToClipboard = () => {
    navigator.clipboard.writeText(JSON.stringify(debugInfo, null, 2));
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const clearAllData = () => {
    localStorage.clear();
    window.location.reload();
  };

  const formatTokenStatus = (token: string | null) => {
    if (!token) return { status: 'missing', color: 'text-red-500', icon: AlertCircle };
    
    try {
      const isExpired = tokenManager.isTokenExpired(token);
      const isValid = tokenManager.isValidTokenFormat(token);
      
      if (isExpired) return { status: 'expired', color: 'text-orange-500', icon: AlertCircle };
      if (!isValid) return { status: 'invalid', color: 'text-red-500', icon: AlertCircle };
      return { status: 'valid', color: 'text-green-500', icon: CheckCircle };
    } catch {
      return { status: 'error', color: 'text-red-500', icon: AlertCircle };
    }
  };

  return (
    <div className="fixed bottom-4 right-4 z-50">
      <div className="bg-white dark:bg-[#1C1F26] border border-gray-200 dark:border-gray-800 rounded-lg shadow-lg p-4 max-w-md max-h-96 overflow-y-auto">
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2">
            <Bug className="w-4 h-4 text-brand-500" />
            <h3 className="font-semibold text-sm">Firebase Auth Debug</h3>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={copyToClipboard}
              className="p-1 text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
              title="Copy debug info"
            >
              {copied ? <CheckCircle className="w-4 h-4 text-green-500" /> : <Copy className="w-4 h-4" />}
            </button>
            <button
              onClick={clearAllData}
              className="p-1 text-red-500 hover:text-red-700"
              title="Clear all data"
            >
              <AlertCircle className="w-4 h-4" />
            </button>
          </div>
        </div>

        <div className="space-y-3 text-xs">
          {/* Auth Context Status */}
          <div className="border-b border-gray-200 dark:border-gray-700 pb-2">
            <div className="font-medium mb-1">Auth Context:</div>
            <div className="space-y-1">
              <div className="flex justify-between">
                <span>Authenticated:</span>
                <span className={isAuthenticated ? 'text-green-500' : 'text-red-500'}>
                  {isAuthenticated ? 'Yes' : 'No'}
                </span>
              </div>
              <div className="flex justify-between">
                <span>User ID:</span>
                <span className="text-gray-600 dark:text-gray-400 truncate max-w-32">
                  {user?.uid || 'None'}
                </span>
              </div>
              {error && (
                <div className="text-red-500 text-xs">
                  Error: {error}
                </div>
              )}
            </div>
          </div>

          {/* Token Status */}
          <div className="border-b border-gray-200 dark:border-gray-700 pb-2">
            <div className="font-medium mb-1">Tokens:</div>
            <div className="space-y-1">
              <div className="flex items-center justify-between">
                <span>Firebase Token:</span>
                <div className="flex items-center gap-1">
                  {(() => {
                    const token = debugInfo.tokenManager?.validFirebaseToken;
                    const status = formatTokenStatus(token);
                    const Icon = status.icon;
                    return <Icon className={`w-3 h-3 ${status.color}`} />;
                  })()}
                </div>
              </div>
              <div className="flex items-center justify-between">
                <span>Custom JWT:</span>
                <div className="flex items-center gap-1">
                  {(() => {
                    const token = debugInfo.tokenManager?.validCustomJWT;
                    const status = formatTokenStatus(token);
                    const Icon = status.icon;
                    return <Icon className={`w-3 h-3 ${status.color}`} />;
                  })()}
                </div>
              </div>
            </div>
          </div>

          {/* Local Storage Status */}
          <div className="border-b border-gray-200 dark:border-gray-700 pb-2">
            <div className="font-medium mb-1">Local Storage:</div>
            <div className="space-y-1">
              <div className="flex justify-between">
                <span>Auth Data:</span>
                <span className={debugInfo.localStorage?.authData ? 'text-green-500' : 'text-red-500'}>
                  {debugInfo.localStorage?.authData ? 'Present' : 'Missing'}
                </span>
              </div>
              <div className="flex justify-between">
                <span>admin_token:</span>
                <span className={debugInfo.rawLocalStorage?.admin_token ? 'text-green-500' : 'text-red-500'}>
                  {debugInfo.rawLocalStorage?.admin_token ? 'Present' : 'Missing'}
                </span>
              </div>
              <div className="flex justify-between">
                <span>firebase_auth:</span>
                <span className={debugInfo.rawLocalStorage?.firebase_auth ? 'text-green-500' : 'text-red-500'}>
                  {debugInfo.rawLocalStorage?.firebase_auth ? 'Present' : 'Missing'}
                </span>
              </div>
            </div>
          </div>

          {/* Environment Info */}
          <div>
            <div className="font-medium mb-1">Environment:</div>
            <div className="space-y-1">
              <div className="flex justify-between">
                <span>Firebase API Key:</span>
                <span className={import.meta.env.VITE_FIREBASE_API_KEY ? 'text-green-500' : 'text-red-500'}>
                  {import.meta.env.VITE_FIREBASE_API_KEY ? 'Set' : 'Missing'}
                </span>
              </div>
              <div className="flex justify-between">
                <span>JWT Secret:</span>
                <span className={import.meta.env.VITE_JWT_SECRET ? 'text-green-500' : 'text-red-500'}>
                  {import.meta.env.VITE_JWT_SECRET ? 'Set' : 'Missing'}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
