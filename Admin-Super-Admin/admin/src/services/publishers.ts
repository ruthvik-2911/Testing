import { api, adMobileApi } from './api';

// ─────────────────────────────────────────
// Types
// ─────────────────────────────────────────

export interface Publisher {
  id: string
  name: string
  contactPerson: string
  location: string
  status: "Active" | "Inactive"
  lastActive: string
  mobile: string
  email: string
  address?: string
}

export interface AdCampaign {
  id: string
  title: string
  status: string
  startDate: string
  endDate: string
  impressions: number
  clicks: number
  ctr: number
}

export interface PublisherAnalytics {
  publisher: Publisher
  stats: {
    totalAds: number
    activeCampaigns: number
    impressions: number
    clicks: number
    ctr: number
  }
  trends: {
    date: string
    impressions: number
    clicks: number
  }[]
  campaigns: AdCampaign[]
}

export interface FetchPublishersArgs {
  page: number
  limit: number
  search?: string
  status?: string
}

export interface FetchPublishersResult {
  data: Publisher[]
  totalItems: number
  totalPages: number
}

// ─────────────────────────────────────────
// Helper: map backend publisher to frontend type
// ─────────────────────────────────────────
const mapPublisher = (p: any): Publisher => ({
  id: p.id,
  name: p.name,
  contactPerson: p.contactPerson || '',
  location: p.location || 'Unknown',
  status: p.status === 'ACTIVE' ? 'Active' : 'Inactive',
  lastActive: p.createdAt
    ? new Date(p.createdAt).toLocaleDateString('en-IN')
    : new Date().toLocaleDateString('en-IN'),
  mobile: p.mobile || '',
  email: p.email || '',
  address: p.address || '',
})

// ─────────────────────────────────────────
// Fetch All Publishers (with client-side search/filter/pagination)
// ─────────────────────────────────────────
export const fetchPublishers = async ({
  page,
  limit,
  search,
  status,
}: FetchPublishersArgs): Promise<FetchPublishersResult> => {
  try {
    const response = await api.get('/api/admin/publishers')
    const rawData = response.data?.publishers || []

    // Map the payload from Admin Backend
    let publishers: Publisher[] = rawData.map((p: any) => ({
      id: p.id,
      name: p.name || 'Unknown',
      contactPerson: p.contactPerson || 'N/A',
      location: p.location || 'Unknown',
      status: p.status === 'INACTIVE' ? 'Inactive' : 'Active',
      lastActive: p.createdAt
        ? new Date(p.createdAt).toLocaleDateString('en-IN')
        : new Date().toLocaleDateString('en-IN'),
      mobile: p.mobile || '',
      email: p.email || '',
      address: p.address || '',
    }))

    if (search) {
      const s = search.toLowerCase()
      publishers = publishers.filter(
        (p) =>
          p.name.toLowerCase().includes(s) ||
          p.location.toLowerCase().includes(s) ||
          p.contactPerson.toLowerCase().includes(s)
      )
    }

    if (status && status !== 'All') {
      publishers = publishers.filter((p) => p.status === status)
    }

    const totalItems = publishers.length
    const totalPages = Math.ceil(totalItems / limit) || 1
    const startIndex = (page - 1) * limit
    const paginatedData = publishers.slice(startIndex, startIndex + limit)

    return { data: paginatedData, totalItems, totalPages }
  } catch (error) {
    console.error('Error fetching publishers:', error)
    return { data: [], totalItems: 0, totalPages: 0 }
  }
}

// ─────────────────────────────────────────
// Get Publisher by ID
// ─────────────────────────────────────────
export const getPublisherById = async (id: string): Promise<Publisher> => {
  const response = await api.get(`/api/admin/publishers/${id}`)
  return mapPublisher(response.data.publisher)
}

// ─────────────────────────────────────────
// Create Publisher
// ─────────────────────────────────────────
export const createPublisher = async (data: any): Promise<Publisher> => {
  // Clean phone number extracting last 10 digits
  const cleanPhone = data.mobile.replace(/[^0-9]/g, '').slice(-10) || "0000000000";

  // 1. Create Company Profile (to map to physical locations / Ad Manager table)
  const companyPayload = {
    name: data.name,
    email: data.email,
    companyType: "PRODUCTS_SERVICES",
    phoneNumber: {
      countryCode: "+91",
      dialNumber: cleanPhone
    },
    billingAddress: {
      addressLine1: data.address || "Publisher Headquarters",
      city: "Unknown",
      state: "Unknown",
      zipCode: "000000",
      country: "India"
    },
    primaryContact: {
      name: data.contactPerson || data.name,
      email: data.email,
      isSameAsBilling: true,
      phoneNumber: {
        countryCode: "+91",
        dialNumber: cleanPhone
      }
    }
  }

  const companyResponse = await adMobileApi.post('/v1/company', companyPayload)
  const createdCompany = companyResponse.data?.data || {}

  // 2. Create User Profile (so the publisher can actually log into their system)
  const userPayload = {
    name: data.name,
    email: data.email,
    phone: data.mobile,
    password: "Secure_Temp_Password!123", // Auto-generated temporary password
    bio: data.address || `${data.name} Publisher`,
    avatar: ""
  }

  try {
    await adMobileApi.post('/v1/user/create/PUBLISHER', userPayload)
  } catch (error) {
    console.warn("User credential creation failed. Company profile still exists.", error)
  }

  // 3. Save publisher to admin backend database
  const adminPayload = {
    name: data.name,
    contactPerson: data.contactPerson || data.name,
    mobile: data.mobile,
    email: data.email,
    address: data.address || '',
    location: data.location || data.address || '',
  }

  let adminPublisherResponse
  try {
    adminPublisherResponse = await api.post('/api/admin/publishers', adminPayload)
  } catch (error) {
    console.error("Failed to save publisher to admin backend:", error)
    throw error
  }

  const adminPublisher = adminPublisherResponse.data?.publisher

  // Return mapped object so UI table updates correctly immediately
  return {
    id: adminPublisher?.id || createdCompany.uid || createdCompany._id || Date.now().toString(),
    name: adminPublisher?.name || createdCompany.name || data.name,
    contactPerson: adminPublisher?.contactPerson || data.contactPerson || data.name,
    location: adminPublisher?.location || data.address || 'Unknown',
    status: adminPublisher?.status === 'INACTIVE' ? 'Inactive' : 'Active',
    lastActive: adminPublisher?.createdAt
      ? new Date(adminPublisher.createdAt).toLocaleDateString('en-IN')
      : new Date().toLocaleDateString('en-IN'),
    mobile: adminPublisher?.mobile || createdCompany.phoneNumber?.dialNumber || data.mobile,
    email: adminPublisher?.email || createdCompany.email || data.email,
    address: adminPublisher?.address || data.address,
  }
}

// ─────────────────────────────────────────
// Update Publisher
// ─────────────────────────────────────────
export const updatePublisher = async (id: string, data: any): Promise<Publisher> => {
  const payload = {
    name: data.name,
    contactPerson: data.contactPerson,
    mobile: data.mobile,
    email: data.email,
    address: data.address || '',
    location:
      data.latitude && data.longitude
        ? `${data.latitude}, ${data.longitude}`
        : data.location || '',
  }
  const response = await api.put(`/api/admin/publishers/${id}`, payload)
  return mapPublisher(response.data.publisher)
}

// ─────────────────────────────────────────
// Toggle Publisher Status (ACTIVE ↔ INACTIVE)
// ─────────────────────────────────────────
export const togglePublisherStatus = async (id: string): Promise<Publisher> => {
  const response = await api.patch(`/api/admin/publishers/${id}/status`)
  return mapPublisher(response.data.publisher)
}

// ─────────────────────────────────────────
// Fetch Publisher Analytics (Real Data)
// ─────────────────────────────────────────
export const fetchPublisherAnalytics = async (id: string): Promise<PublisherAnalytics> => {
  const response = await api.get(`/api/admin/publishers/${id}/analytics`)
  const { publisher: rawPub, analytics } = response.data

  const publisher = mapPublisher(rawPub)

  return {
    publisher,
    stats: {
      totalAds: analytics.stats.totalAds ?? 0,
      activeCampaigns: analytics.stats.activeCampaigns ?? 0,
      impressions: analytics.stats.impressions ?? 0,
      clicks: analytics.stats.clicks ?? 0,
      ctr: analytics.stats.ctr ?? 0,
    },
    trends: (analytics.trends ?? []).map((t: any) => ({
      date: t.date,
      impressions: t.impressions,
      clicks: t.clicks,
    })),
    campaigns: (analytics.campaigns ?? []).map((c: any) => ({
      id: c.id,
      title: c.title,
      status: c.status === 'ACTIVE' ? 'Active' : c.status === 'EXPIRED' ? 'Expired' : c.status ?? 'Draft',
      startDate: c.startDate ?? '-',
      endDate: c.endDate ?? '-',
      impressions: c.impressions ?? 0,
      clicks: c.clicks ?? 0,
      ctr: c.ctr ?? 0,
    })),
  }
}
