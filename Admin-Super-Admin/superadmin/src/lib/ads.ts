import { API_BASE_URL, AuthError, getAuthSession } from './auth'

export interface AdvertisementRecord {
  id: string
  title: string
  description?: string | null
  type: string
  adminId: string
  adminName: string
  publisherId: string
  publisherName: string
  createdDate: string
  status: string
  impressions?: number | null
  clicks?: number | null
  ctr?: number | null
  startDate: string
  endDate: string
  location: string
  radius: string
  image?: string | null
}

function authHeaders() {
  const session = getAuthSession()
  return session?.token
    ? {
      Authorization: `Bearer ${session.token}`,
    }
    : undefined
}

async function handleJsonResponse<T>(response: Response, fallbackMessage: string): Promise<T> {
  const payload = await response.json().catch(() => null) as T | { error?: string; message?: string } | null

  if (!response.ok) {
    const errorMessage =
      (payload && typeof payload === 'object' && 'message' in payload && payload.message) ||
      (payload && typeof payload === 'object' && 'error' in payload && payload.error) ||
      fallbackMessage
    throw new AuthError(String(errorMessage), response.status)
  }

  return payload as T
}

export const AD_MOBILE_API_URL = import.meta.env.VITE_AD_MOBILE_BACKEND_URL ?? 'http://ec2-15-206-186-192.ap-south-1.compute.amazonaws.com:3000'
export const AD_MOBILE_TOKEN = import.meta.env.VITE_AD_MOBILE_TOKEN ?? 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOiI2NGZiMTE3YS1mNTMwLTRmOTgtYTVkMy0yMWY3ZmVlYzkwNDciLCJfaWQiOiI2NGVkODZkMjMyMDBlMWQ2YzUyMDYwYmMiLCJpYXQiOjE3NzY5MTQ0MzksImV4cCI6MTgwODQ1MDQzOX0.jYRhILMRhluttAgns-OGDgGdji0DKhok6QBcUB7qdPg'

function mobileHeaders() {
  return {
    'Authorization': `Bearer ${AD_MOBILE_TOKEN}`,
    'Content-Type': 'application/json'
  }
}

export async function fetchAdvertisements(): Promise<AdvertisementRecord[]> {
  // Fetch from our backend which has production data from Mobilize API
  const session = getAuthSession()
  
  const response = await fetch(`${API_BASE_URL}/api/superadmin/analytics/advertisements`, {
    headers: session?.token
      ? {
        'Authorization': `Bearer ${session.token}`,
      }
      : undefined,
  }).catch(() => null)

  const payload = await response?.json().catch(() => null)

  if (!response?.ok || !payload) {
    console.warn("Could not fetch advertisements from backend API. Returning empty.")
    return []
  }

  // Map advertisements to AdvertisementRecord for SuperAdmin UI
  return Array.isArray(payload) ? payload.map((ad: any, index: number) => {
    return {
      id: ad.id || ad._id || `ad-${index}`,
      title: ad.title || "Untitled Advertisement",
      description: ad.description || "No description",
      type: ad.adType || "Banner",
      adminId: "N/A",
      adminName: "System Admin",
      publisherId: ad.company || "N/A",
      publisherName: ad.company || "Unknown",
      createdDate: new Date(ad.createdAt || Date.now()).toLocaleDateString(),
      status: ad.paymentStatus === 'paid' ? 'Active' : 'Pending',
      impressions: ad.impressions || 0,
      clicks: ad.clicks || 0,
      ctr: ad.ctr || 0,
      startDate: ad.startDate || "N/A",
      endDate: ad.endDate || "N/A",
      location: ad.location || "Unknown",
      radius: ad.radius || "N/A",
      image: ad.thumbnail || null
    };
  }) : [];
}

export async function suspendAdvertisement(campaignId: string): Promise<AdvertisementRecord> {
  // Mobile API suspend endpoint 
  // (Assuming /update/:uid to "PAUSED" or "INACTIVE" based on schema)
  const response = await fetch(`${AD_MOBILE_API_URL}/v1/ad-campaigns/update/${campaignId}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders()
    },
    body: JSON.stringify({ compaignsStatus: "PAUSED" })
  })

  return handleJsonResponse<AdvertisementRecord>(response, 'Unable to suspend advertisement')
}
