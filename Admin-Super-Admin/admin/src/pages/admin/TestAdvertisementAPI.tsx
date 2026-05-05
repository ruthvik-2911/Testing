import React, { useState } from 'react';
import { Loader2, CheckCircle, XCircle, AlertCircle } from 'lucide-react';
import toast, { Toaster } from 'react-hot-toast';
import { adMobileApi } from '../../services/api';

interface TestResult {
  name: string;
  status: 'pending' | 'success' | 'error' | 'warning';
  message: string;
  data?: any;
  duration?: number;
}

export default function TestAdvertisementAPI() {
  const [testing, setTesting] = useState(false);
  const [results, setResults] = useState<TestResult[]>([]);
  const [jwtToken, setJwtToken] = useState('');

  React.useEffect(() => {
    // Auto-load JWT token from localStorage
    const token = localStorage.getItem('admin_token') || 
                  localStorage.getItem('ad_mobile_token') ||
                  '';
    setJwtToken(token);
  }, []);

  const addResult = (result: TestResult) => {
    setResults(prev => [...prev, result]);
  };

  const updateLastResult = (updates: Partial<TestResult>) => {
    setResults(prev => {
      const newResults = [...prev];
      const lastIndex = newResults.length - 1;
      if (lastIndex >= 0) {
        newResults[lastIndex] = { ...newResults[lastIndex], ...updates };
      }
      return newResults;
    });
  };

  const runTests = async () => {
    if (!jwtToken) {
      toast.error('JWT Token is required. Please login first.');
      return;
    }

    setTesting(true);
    setResults([]);

    // Test 1: Fetch All Advertisements
    try {
      addResult({
        name: 'Fetch All Advertisements',
        status: 'pending',
        message: 'Testing GET /v1/advertisements...'
      });

      const startTime = Date.now();
      const response = await adMobileApi.get('/advertisements', {
        headers: {
          'Authorization': `Bearer ${jwtToken}`
        }
      });
      const duration = Date.now() - startTime;

      updateLastResult({
        status: 'success',
        message: `✓ Successfully fetched ${response.data?.data?.length || 0} advertisements`,
        data: response.data,
        duration
      });
    } catch (error: any) {
      updateLastResult({
        status: 'error',
        message: `✗ Error: ${error.response?.data?.message || error.message}`,
        data: error.response?.data
      });
    }

    // Test 2: Fetch with Pagination
    try {
      addResult({
        name: 'Fetch Advertisements (Paginated)',
        status: 'pending',
        message: 'Testing GET /v1/advertisements?page=1&limit=5...'
      });

      const startTime = Date.now();
      const response = await adMobileApi.get('/advertisements', {
        params: { page: 1, limit: 5 },
        headers: {
          'Authorization': `Bearer ${jwtToken}`
        }
      });
      const duration = Date.now() - startTime;

      updateLastResult({
        status: 'success',
        message: `✓ Successfully fetched paginated results (${response.data?.data?.length || 0} items)`,
        data: response.data,
        duration
      });
    } catch (error: any) {
      updateLastResult({
        status: 'error',
        message: `✗ Error: ${error.response?.data?.message || error.message}`,
        data: error.response?.data
      });
    }

    // Test 3: Get Current User's Advertisements
    try {
      addResult({
        name: 'Get Current User Advertisements',
        status: 'pending',
        message: 'Testing GET /v1/advertisements/user/me...'
      });

      const startTime = Date.now();
      const response = await adMobileApi.get('/advertisements/user/me', {
        headers: {
          'Authorization': `Bearer ${jwtToken}`
        }
      });
      const duration = Date.now() - startTime;

      const adCount = response.data?.data?.length || 0;
      updateLastResult({
        status: adCount > 0 ? 'success' : 'warning',
        message: adCount > 0 
          ? `✓ Found ${adCount} advertisements for current user`
          : '⚠ No advertisements found for current user',
        data: response.data,
        duration
      });

      // Test 4: Get Specific Advertisement Details (if we have ads)
      if (adCount > 0) {
        const firstAdUid = response.data.data[0].uid;
        
        try {
          addResult({
            name: 'Get Advertisement Details',
            status: 'pending',
            message: `Testing GET /v1/advertisements/${firstAdUid}...`
          });

          const startTime2 = Date.now();
          const detailResponse = await adMobileApi.get(`/advertisements/${firstAdUid}`, {
            headers: {
              'Authorization': `Bearer ${jwtToken}`
            }
          });
          const duration2 = Date.now() - startTime2;

          updateLastResult({
            status: 'success',
            message: `✓ Successfully fetched details for ad: ${detailResponse.data?.data?.title || firstAdUid}`,
            data: detailResponse.data,
            duration: duration2
          });
        } catch (error: any) {
          updateLastResult({
            status: 'error',
            message: `✗ Error: ${error.response?.data?.message || error.message}`,
            data: error.response?.data
          });
        }
      }
    } catch (error: any) {
      updateLastResult({
        status: 'error',
        message: `✗ Error: ${error.response?.data?.message || error.message}`,
        data: error.response?.data
      });
    }

    // Test 5: Check Authentication
    try {
      addResult({
        name: 'Authentication Check',
        status: 'pending',
        message: 'Verifying JWT token validity...'
      });

      const tokenParts = jwtToken.split('.');
      if (tokenParts.length === 3) {
        try {
          const payload = JSON.parse(atob(tokenParts[1]));
          const exp = payload.exp ? new Date(payload.exp * 1000) : null;
          const isExpired = exp ? exp < new Date() : false;

          updateLastResult({
            status: isExpired ? 'error' : 'success',
            message: isExpired 
              ? '✗ JWT token is expired'
              : `✓ JWT token is valid (expires: ${exp?.toLocaleString()})`,
            data: { payload, isExpired }
          });
        } catch {
          updateLastResult({
            status: 'warning',
            message: '⚠ Could not decode JWT token payload'
          });
        }
      } else {
        updateLastResult({
          status: 'error',
          message: '✗ Invalid JWT token format'
        });
      }
    } catch (error: any) {
      updateLastResult({
        status: 'error',
        message: `✗ Error: ${error.message}`
      });
    }

    setTesting(false);
    toast.success('All tests completed!');
  };

  const getStatusIcon = (status: TestResult['status']) => {
    switch (status) {
      case 'success':
        return <CheckCircle className="w-5 h-5 text-green-500" />;
      case 'error':
        return <XCircle className="w-5 h-5 text-red-500" />;
      case 'warning':
        return <AlertCircle className="w-5 h-5 text-yellow-500" />;
      case 'pending':
        return <Loader2 className="w-5 h-5 text-blue-500 animate-spin" />;
    }
  };

  const getStatusColor = (status: TestResult['status']) => {
    switch (status) {
      case 'success':
        return 'bg-green-50 dark:bg-green-900/20 border-green-200 dark:border-green-800';
      case 'error':
        return 'bg-red-50 dark:bg-red-900/20 border-red-200 dark:border-red-800';
      case 'warning':
        return 'bg-yellow-50 dark:bg-yellow-900/20 border-yellow-200 dark:border-yellow-800';
      case 'pending':
        return 'bg-blue-50 dark:bg-blue-900/20 border-blue-200 dark:border-blue-800';
    }
  };

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-[#0E1117] p-8">
      <Toaster position="top-right" />
      
      <div className="max-w-4xl mx-auto">
        <div className="bg-white dark:bg-[#1C1F26] rounded-xl shadow-lg p-8 mb-6">
          <h1 className="text-3xl font-bold text-gray-900 dark:text-white mb-2">
            Advertisement API Test Suite
          </h1>
          <p className="text-gray-600 dark:text-gray-400 mb-6">
            Test all advertisement endpoints with JWT authentication
          </p>

          {/* JWT Token Input */}
          <div className="mb-6">
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
              JWT Token
            </label>
            <input
              type="text"
              value={jwtToken}
              onChange={(e) => setJwtToken(e.target.value)}
              placeholder="Enter your JWT token or login to auto-populate"
              className="w-full px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-[#0E1117] text-gray-900 dark:text-white focus:ring-2 focus:ring-brand-500 focus:border-transparent"
            />
            <p className="mt-2 text-sm text-gray-500 dark:text-gray-400">
              Token auto-loaded from localStorage. If empty, please login first.
            </p>
          </div>

          {/* Run Tests Button */}
          <button
            onClick={runTests}
            disabled={testing || !jwtToken}
            className="w-full flex items-center justify-center gap-2 px-6 py-3 bg-brand-500 hover:bg-brand-600 disabled:bg-gray-400 text-white font-semibold rounded-lg shadow-sm transition-all disabled:cursor-not-allowed"
          >
            {testing ? (
              <>
                <Loader2 className="w-5 h-5 animate-spin" />
                Running Tests...
              </>
            ) : (
              'Run All Tests'
            )}
          </button>
        </div>

        {/* Test Results */}
        {results.length > 0 && (
          <div className="space-y-4">
            <h2 className="text-xl font-bold text-gray-900 dark:text-white">
              Test Results
            </h2>
            
            {results.map((result, index) => (
              <div
                key={index}
                className={`border rounded-lg p-4 ${getStatusColor(result.status)}`}
              >
                <div className="flex items-start gap-3">
                  <div className="flex-shrink-0 mt-0.5">
                    {getStatusIcon(result.status)}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between mb-1">
                      <h3 className="text-sm font-semibold text-gray-900 dark:text-white">
                        {result.name}
                      </h3>
                      {result.duration && (
                        <span className="text-xs text-gray-500 dark:text-gray-400">
                          {result.duration}ms
                        </span>
                      )}
                    </div>
                    <p className="text-sm text-gray-700 dark:text-gray-300 mb-2">
                      {result.message}
                    </p>
                    
                    {result.data && (
                      <details className="mt-2">
                        <summary className="text-xs text-gray-600 dark:text-gray-400 cursor-pointer hover:text-gray-900 dark:hover:text-gray-200">
                          View Response Data
                        </summary>
                        <pre className="mt-2 p-3 bg-gray-900 dark:bg-black text-green-400 text-xs rounded overflow-x-auto">
                          {JSON.stringify(result.data, null, 2)}
                        </pre>
                      </details>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* API Documentation */}
        <div className="mt-8 bg-white dark:bg-[#1C1F26] rounded-xl shadow-lg p-8">
          <h2 className="text-xl font-bold text-gray-900 dark:text-white mb-4">
            API Endpoints Reference
          </h2>
          
          <div className="space-y-4 text-sm">
            <div className="border-l-4 border-brand-500 pl-4">
              <code className="text-brand-600 dark:text-brand-400 font-mono">
                GET /v1/advertisements
              </code>
              <p className="text-gray-600 dark:text-gray-400 mt-1">
                Fetch all advertisements for current user
              </p>
            </div>

            <div className="border-l-4 border-brand-500 pl-4">
              <code className="text-brand-600 dark:text-brand-400 font-mono">
                GET /v1/advertisements/:uid
              </code>
              <p className="text-gray-600 dark:text-gray-400 mt-1">
                Get specific advertisement details by UID
              </p>
            </div>

            <div className="border-l-4 border-brand-500 pl-4">
              <code className="text-brand-600 dark:text-brand-400 font-mono">
                GET /v1/advertisements/user/me
              </code>
              <p className="text-gray-600 dark:text-gray-400 mt-1">
                Get ads published by current user
              </p>
            </div>

            <div className="border-l-4 border-brand-500 pl-4">
              <code className="text-brand-600 dark:text-brand-400 font-mono">
                GET /v1/advertisements/user-uid/:uid
              </code>
              <p className="text-gray-600 dark:text-gray-400 mt-1">
                Get ads published by specific user (admin view)
              </p>
            </div>

            <div className="border-l-4 border-brand-500 pl-4">
              <code className="text-brand-600 dark:text-brand-400 font-mono">
                GET /v1/advertisements/copy-ad/:uid
              </code>
              <p className="text-gray-600 dark:text-gray-400 mt-1">
                Copy advertisement template
              </p>
            </div>
          </div>

          <div className="mt-6 p-4 bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg">
            <p className="text-sm text-blue-800 dark:text-blue-300">
              <strong>Note:</strong> All endpoints require JWT authentication via the Authorization header.
              Base URL: <code className="font-mono">http://localhost:3000/v1</code>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
