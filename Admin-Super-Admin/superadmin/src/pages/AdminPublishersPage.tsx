import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { 
  fetchPublishersByAdminId,
  type Publisher, 
  FetchPublishersResult 
} from '../services/publishers';

interface AdminPublishersPageProps {}

export default function AdminPublishersPage(props: AdminPublishersPageProps) {
  const { adminId } = useParams<{ adminId: string }>();
  const navigate = useNavigate();
  const [publishers, setPublishers] = useState<Publisher[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (adminId) {
      loadPublishersByAdminId(adminId);
    }
  }, [adminId]);

  const loadPublishersByAdminId = async (id: string) => {
    try {
      setLoading(true);
      setError('');
      
      const result: FetchPublishersResult = await fetchPublishersByAdminId(id);
      setPublishers(result.data);
    } catch (err) {
      console.error('Error fetching publishers by admin:', err);
      setError('Failed to load publishers');
    } finally {
      setLoading(false);
    }
  };

  const handleStatusToggle = async (publisherId: string) => {
    try {
      // TODO: Implement status toggle functionality
      console.log('Toggle status for publisher:', publisherId);
    } catch (err) {
      console.error('Error toggling publisher status:', err);
    }
  };

  const handleViewDetails = (publisherId: string) => {
    navigate(`/superadmin/publishers/${publisherId}`);
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-center">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-gray-200"></div>
          <p className="mt-4 text-gray-600">Loading publishers...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-center">
          <div className="text-red-500 text-lg font-medium">Error</div>
          <p className="mt-2 text-gray-600">{error}</p>
          <button 
            onClick={() => loadPublishersByAdminId(adminId)}
            className="mt-4 px-4 py-2 bg-blue-500 text-white rounded-md hover:bg-blue-600"
          >
            Retry
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="p-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">
          Publishers by Admin
        </h1>
        <p className="text-gray-600">
          Showing all publishers created by this admin
        </p>
      </div>

      <div className="bg-white rounded-lg shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Publisher Name
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Email
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Company
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Registered
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Status
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {publishers.map((publisher) => (
                <tr key={publisher.id} className="hover:bg-gray-50">
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="text-sm font-medium text-gray-900">
                      {publisher.name}
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="text-sm text-gray-500">
                      {publisher.email}
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="text-sm text-gray-500">
                      {publisher.company || 'N/A'}
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="text-sm text-gray-500">
                      {publisher.registered || 'N/A'}
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span className={`inline-flex px-2 py-1 text-xs font-semibold rounded-full ${
                      publisher.status === 'ACTIVE' 
                        ? 'bg-green-100 text-green-800'
                        : 'bg-red-100 text-red-800'
                    }`}>
                      {publisher.status}
                    </span>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium">
                    <div className="flex space-x-2">
                      <button
                        onClick={() => handleViewDetails(publisher.id)}
                        className="text-blue-600 hover:text-blue-800"
                      >
                        View
                      </button>
                      <button
                        onClick={() => handleStatusToggle(publisher.id)}
                        className={`${
                          publisher.status === 'ACTIVE'
                            ? 'text-red-600 hover:text-red-800'
                            : 'text-green-600 hover:text-green-800'
                        }`}
                      >
                        {publisher.status === 'ACTIVE' ? 'Suspend' : 'Activate'}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {publishers.length === 0 && !loading && (
        <div className="text-center py-12">
          <div className="text-gray-500 text-lg">
            No publishers found for this admin
          </div>
        </div>
      )}
    </div>
  );
}
