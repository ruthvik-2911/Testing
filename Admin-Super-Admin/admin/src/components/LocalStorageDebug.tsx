import { useEffect, useState } from 'react';

export default function LocalStorageDebug() {
  const [storage, setStorage] = useState<any>({});

  useEffect(() => {
    const checkStorage = () => {
      const data = {
        admin_token: localStorage.getItem('admin_token'),
        admin_user: localStorage.getItem('admin_user'),
        firebase_auth: localStorage.getItem('firebase_auth_data'),
        auth_tokens: localStorage.getItem('auth_tokens'),
        auth_user: localStorage.getItem('auth_user'),
      };
      setStorage(data);
      console.log('🔍 LocalStorage Debug:', data);
    };

    checkStorage();
    const interval = setInterval(checkStorage, 2000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="fixed top-4 right-4 bg-black text-white p-4 rounded-lg text-xs max-w-md z-50">
      <h4 className="font-bold mb-2">LocalStorage Debug</h4>
      {Object.entries(storage).map(([key, value]) => (
        <div key={key} className="mb-1">
          <strong>{key}:</strong> {value ? (typeof value === 'string' ? value.substring(0, 50) + '...' : JSON.stringify(value).substring(0, 50) + '...') : 'null'}
        </div>
      ))}
    </div>
  );
}
