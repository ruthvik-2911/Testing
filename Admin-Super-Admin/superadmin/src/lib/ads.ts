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
  const query = new URLSearchParams()
  query.set('page', '1')
  query.set('limit', '1000')

  const response = await fetch(`${AD_MOBILE_API_URL}/v1/ad-campaigns?${query.toString()}`, {
    headers: mobileHeaders(),
  }).catch(() => null)

  const payload = await response?.json().catch(() => null)

  if (!response?.ok || !payload) {
    console.warn("Could not fetch advertisements from Mobile API. Returning empty.")
    return []
  }

  // Map campaign records to AdvertisementRecord for SuperAdmin UI
  const rawAds = payload.data || [];
  return rawAds.map((ad: any) => {
    // Determine the type string safely
    let typeStr = "AD";
    const adType = ad.advertisementId?.adType;
    if (typeof adType === 'string') {
      typeStr = adType;
    } else if (adType && typeof adType === 'object') {
      typeStr = adType.name || adType.title || adType.code || "AD";
    }

    return {
      id: ad.uid || ad.advertisementId?.uid || ad._id,
      title: ad.advertisementId?.title || "Untitled Ad",
      description: ad.advertisementId?.description,
      type: typeStr,
      adminId: "N/A",
      adminName: "Automated",
      publisherId: ad.advertisementId?.company?.uid || ad.advertisementId?.company || "N/A",
      publisherName: ad.advertisementId?.company?.name || "Unassigned",
      createdDate: new Date(ad.createdAt || Date.now()).toLocaleDateString(),
      status: ad.compaignsStatus === 'ACTIVE' ? 'Active' : (ad.compaignsStatus || 'Pending'),
      impressions: ad.reachedPublishingCount || 0,
      clicks: ad.clicks || 0,
      ctr: ad.ctr || 0,
      startDate: ad.dateRange?.fromDate || "N/A",
      endDate: ad.dateRange?.toDate || "N/A",
      location: ad.location?.locationName || "Unknown",
      radius: (ad.location?.range ? `${ad.location.range}m` : undefined) || "N/A",
      image: ad.advertisementId?.thumbnail?.url || ad.advertisementId?.thumbnail || null
    };
  })
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
