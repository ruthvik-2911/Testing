import { api } from './api';

export interface AdminPublisher {
  id: string;
  name: string;
  email: string;
  contactPerson: string | null;
  mobile: string | null;
  address: string | null;
  location: string | null;
  adminId: string;
  status: string;
  createdAt: string;
}

export interface AdminPublishersResponse {
  success: boolean;
  data: AdminPublisher[];
  total: number;
  message: string;
}

export const fetchPublishersByAdminId = async (adminId: string): Promise<AdminPublishersResponse> => {
  try {
    const response = await api.get(`/api/admin/publishers/by-admin/${adminId}`);
    return response.data;
  } catch (error) {
    console.error('Error fetching publishers by admin ID:', error);
    throw error;
  }
};
