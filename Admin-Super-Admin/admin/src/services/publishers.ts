import { api } from "./api";

export interface Publisher {
  id: string;
  name: string;
  email?: string;
  mobile?: string;
  address?: string;
  location?: string;
  contactPerson?: string;
  status?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface FetchPublishersArgs {
  page?: number;
  limit?: number;
  search?: string;
  status?: string;
  companyUID?: string;
}

export interface FetchPublishersResult {
  data: Publisher[];
  totalItems: number;
  totalPages: number;
}

// eslint-disable-next-line @typescript-eslint/no-unused-vars
const mapPublisher = (raw: any): Publisher => ({
  id: raw.id || '',
  name: raw.name || '',
  email: raw.email,
  mobile: raw.mobile,
  address: raw.address,
  location: raw.location,
  contactPerson: raw.contactPerson,
  status: raw.status,
  createdAt: raw.createdAt,
  updatedAt: raw.updatedAt,
});

// ─────────────────────────────────────────
// Fetch Publishers
// ─────────────────────────────────────────
export const fetchPublishers = async (args: FetchPublishersArgs): Promise<FetchPublishersResult> => {
  const { page = 1, limit = 10, search = '', status = '', companyUID } = args;
  try {
    console.log('🔧 Fetching publishers with authentication');
    
    // Build query params
    const params: any = { page, limit };
    if (search) params.search = search;
    if (status && status !== 'All') params.status = status;
    if (companyUID) params.companyUID = companyUID;
    
    // Use the configured api instance which includes auth token
    const response = await api.get('/api/admin/publishers', { params });
    
    const rawData = response.data?.publishers || response.data?.data || [];
    const publishers: Publisher[] = rawData.map(mapPublisher);

    const totalItems = response.data?.totalItems || publishers.length;
    const totalPages = response.data?.totalPages || Math.ceil(totalItems / limit) || 1;

    console.log(`✅ Successfully fetched ${totalItems} publishers`);
    return { data: publishers, totalItems, totalPages };
  } catch (error) {
    console.error('Error fetching publishers:', error);
    return { data: [], totalItems: 0, totalPages: 0 };
  }
};

// ─────────────────────────────────────────
// Get Publisher by ID
// ─────────────────────────────────────────
export const getPublisherById = async (id: string): Promise<Publisher> => {
  const response = await api.get(`/api/admin/publishers/${id}`);
  return mapPublisher(response.data.publisher);
};

// ─────────────────────────────────────────
// Create Publisher
// ─────────────────────────────────────────
export const createPublisher = async (_data: any): Promise<Publisher> => {
  console.log('🔧 Publisher creation temporarily disabled for testing');
  throw new Error('Publisher creation temporarily disabled');
};

// ─────────────────────────────────────────
// Update Publisher
// ─────────────────────────────────────────
export const updatePublisher = async (id: string, _data: any): Promise<Publisher> => {
  console.log('🔧 Publisher update temporarily disabled for testing');
  const { name, contactPerson, mobile, email, address, latitude, longitude, location } = _data;
  const payload = {
    name,
    contactPerson,
    mobile,
    email,
    address: address || '',
    location:
      latitude && longitude
        ? `${latitude}, ${longitude}`
        : location || '',
  };
  const response = await api.put(`/api/admin/publishers/${id}`, payload);
  return mapPublisher(response.data.publisher);
};

// ─────────────────────────────────────────
// Toggle Publisher Status
// ─────────────────────────────────────────
export const togglePublisherStatus = async (id: string): Promise<Publisher> => {
  const response = await api.put(`/api/admin/publishers/${id}/toggle-status`);
  return mapPublisher(response.data.publisher);
};

// ─────────────────────────────────────────
// Publisher Analytics
// ─────────────────────────────────────────
export interface PublisherAnalytics {
  totalAds: number;
  activeAds: number;
  totalRevenue: number;
  avgEngagement: number;
}

export const fetchPublisherAnalytics = async (id: string): Promise<PublisherAnalytics> => {
  const response = await api.get(`/api/admin/publishers/${id}/analytics`);
  return response.data;
};
