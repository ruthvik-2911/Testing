import { adMobileApi, api } from './api';
import {
  AD_TYPE_UIDS,
  CTA_UIDS,
  CTA_LABEL_TO_CODE,
  ENDPOINTS,
  buildCreateAdPayload,
  buildCreateCampaignPayload,
} from '../config/constants';

// Re-export UIDs so existing imports don't break
export { AD_TYPE_UIDS, CTA_UIDS };

// ─────────────────────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────────────────────

export type AdStatus = 'Draft' | 'Pending' | 'Active' | 'Expired' | 'Suspended';
export type PaymentStatus = 'Paid' | 'Pending' | 'Failed';

export interface Advertisement {
  id: string;
  title: string;
  publishers: string[];
  status: AdStatus;
  startDate: string;
  endDate: string;
  impressions: number;
  clicks: number;
  ctr: number;
  paymentStatus: PaymentStatus;
  adTypeUID?: string;
  imageAdUID?: string; // was "thumbnailUID" — renamed to match frontend field
}

export interface Company {
  uid: string;
  name: string;
  logoUrl?: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// Media Upload
// POST /v1/media/upload  (multipart/form-data, field: "file")
// Response: { data: { uid: "<media-uid>" } }
// ─────────────────────────────────────────────────────────────────────────────

export const uploadMedia = async (file: File): Promise<string> => {
  console.log('📤 Uploading media:', { fileName: file.name, mimeType: file.type });
  try {
    const formData = new FormData();
    formData.append('file', file);
    const response = await adMobileApi.post(ENDPOINTS.mediaUpload, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });

    let responseData = response.data;
    if (typeof responseData === 'string') {
      try {
        responseData = JSON.parse(responseData);
      } catch (e) {
        console.warn('Could not parse response data as JSON:', responseData);
      }
    }

    console.log('📦 Media upload response:', responseData);

    // Safely get the uid. Some backend versions might return it directly, or nested in data
    const uid = responseData?.data?.uid || responseData?.uid;

    if (!uid) {
      console.error('❌ Failed to extract uid from response:', responseData);
      throw new Error('No UID returned from media upload');
    }

    return uid;
  } catch (error) {
    console.error('❌ Media Upload API Error:', error);
    throw error;
  }
};

// ─────────────────────────────────────────────────────────────────────────────
// Company API
// GET /v1/company/PRODUCTS_SERVICES?all=yes
// Response: { success: true, data: [{ uid, name, companyLogo: { url } }] }
// ─────────────────────────────────────────────────────────────────────────────

export const fetchCompanies = async (): Promise<Company[]> => {
  const response = await adMobileApi.get(ENDPOINTS.companyList, {
    params: { all: 'yes' },
  });
  const list: any[] = response.data.data ?? [];
  return list.map((c) => ({
    uid: c.uid,
    name: c.name,
    logoUrl: c.companyLogo?.url,
  }));
};

// ─────────────────────────────────────────────────────────────────────────────
// Fetch Advertisements
// GET /v1/advertisements?page=N&limit=5
// ─────────────────────────────────────────────────────────────────────────────

export interface FetchAdsArgs {
  page: number;
  limit: number;
  search?: string;
  status?: string;
  publisher?: string;
  companyUID?: string;
  dateRange?: { start?: string; end?: string };
}

export interface FetchAdsResult {
  data: Advertisement[];
  totalItems: number;
  totalPages: number;
  uniquePublishers: string[];
}

// Fetch the set of ad IDs that have been paid for, from the Spring Boot backend.
// This is the permanent, isolated source of truth for payment status.
async function fetchPaidAdIds(): Promise<Set<string>> {
  try {
    const token = localStorage.getItem('admin_token');
    const res = await api.get('/api/admin/payments/paid-ads', {
      headers: token ? { Authorization: `Bearer ${token}` } : {}
    });
    const ids: string[] = res.data?.paidAdIds ?? [];
    return new Set(ids);
  } catch {
    return new Set();
  }
}

export const fetchAds = async ({
  page,
  limit,
  search,
  status,
  companyUID,
}: FetchAdsArgs): Promise<FetchAdsResult> => {
  // Fetch a large batch of campaigns AND advertisements
  const params: Record<string, any> = { page: 1, limit: 1000 };
  if (companyUID) params.companyUID = companyUID;

  // Fetch campaigns, advertisements, and paid ad IDs in parallel
  const [campaignsRes, adsRes, paidAdIds] = await Promise.all([
    adMobileApi.get(ENDPOINTS.campaignsList, { params }).catch(() => ({ data: { data: [] } })),
    adMobileApi.get(ENDPOINTS.adsList, { params }).catch(() => ({ data: { data: [] } })),
    fetchPaidAdIds()
  ]);

  const rawCampaigns = campaignsRes.data?.data ?? [];
  let rawAds = adsRes.data?.data ?? [];

  // Frontend Fallback Filter: ensure an admin only sees their company's ads
  if (companyUID) {
    rawAds = rawAds.filter((ad: any) => {
      const adCompanyId = ad.company?.uid;
      return adCompanyId === companyUID;
    });
  }

  // Deduplicate campaigns by advertisementId (to get the most relevant metrics/status)
  const campaignMap = new Map<string, any>();
  for (const camp of rawCampaigns) {
    const adUid = camp.advertisementId?.uid || camp.uid;
    if (!adUid) continue;
    
    const existing = campaignMap.get(adUid);
    if (!existing) {
      campaignMap.set(adUid, camp);
    } else {
      const currentStatus = (camp.compaignsStatus || '').toUpperCase();
      const existingStatus = (existing.compaignsStatus || '').toUpperCase();
      
      const priority: Record<string, number> = { 'ACTIVE': 3, 'PENDING': 2, 'INACTIVE': 1 };
      const currentPrio = priority[currentStatus] || 0;
      const existingPrio = priority[existingStatus] || 0;
      
      // Prefer Active/Pending over Inactive. If same, prefer newer.
      if (currentPrio > existingPrio || (currentPrio === existingPrio && new Date(camp.createdAt).getTime() > new Date(existing.createdAt).getTime())) {
        campaignMap.set(adUid, camp);
      }
    }
  }

  // Merge advertisements with their corresponding campaign (if one exists)
  // We use rawAds as the base so that "Draft" ads without a campaign still appear.
  let mergedAds: Advertisement[] = rawAds.map((ad: any) => {
    const adUid = ad.uid;
    const camp = campaignMap.get(adUid);

    let normalizedStatus: AdStatus = 'Draft';
    let backendStatus = '';
    
    if (camp) {
       backendStatus = (camp.compaignsStatus || '').toUpperCase();
       if (backendStatus === 'ACTIVE') normalizedStatus = 'Active';
       else if (backendStatus === 'PENDING') normalizedStatus = 'Pending';
       else if (backendStatus === 'EXPIRED' || backendStatus === 'COMPLETED') normalizedStatus = 'Expired';
       else if (backendStatus === 'INACTIVE') normalizedStatus = 'Draft';
    }

    return {
      id: adUid,
      title: ad.title || 'Untitled Campaign',
      publishers: ad.company?.name ? [ad.company.name] : [],
      status: normalizedStatus,
      startDate: camp?.dateRange?.fromDate || ad.startDate,
      endDate: camp?.dateRange?.toDate || ad.endDate,
      impressions: camp?.reachedPublishingCount ?? 0,
      clicks: camp?.clicks ?? 0,
      ctr: camp?.ctr ?? 0,
      // Payment status: check against the authoritative list from Spring Boot.
      paymentStatus: paidAdIds.has(adUid) ? 'Paid' : 'Pending',
    };
  });

  // Sort by date DESC (newest first)
  mergedAds.sort((a, b) => {
    // We don't have createdAt in the mapped object directly, 
    // but the raw objects have it. Let's find the original object for sorting.
    const getCreatedAt = (id: string) => {
      const origAd = rawAds.find((r: any) => r.uid === id);
      return origAd?.createdAt ? new Date(origAd.createdAt).getTime() : 0;
    };
    return getCreatedAt(b.id) - getCreatedAt(a.id);
  });

  // Apply Search/Status filters locally since we have the full list
  if (search) {
    const s = search.toLowerCase();
    mergedAds = mergedAds.filter(a => a.title.toLowerCase().includes(s));
  }
  if (status && status !== 'All') {
    mergedAds = mergedAds.filter(a => a.status === status);
  }

  const uniquePublishers = Array.from(new Set(mergedAds.flatMap(ad => ad.publishers))).filter(Boolean);

  // Pagination logic on the frontend
  const totalItems = mergedAds.length;
  const totalPages = Math.ceil(totalItems / limit) || 1;
  const paginatedAds = mergedAds.slice((page - 1) * limit, page * limit);

  return {
    data: paginatedAds,
    totalItems,
    totalPages,
    uniquePublishers,
  };
};

// ─────────────────────────────────────────────────────────────────────────────
// Get Ad By ID
// GET /v1/advertisements/:uid
// ─────────────────────────────────────────────────────────────────────────────

export const getAdById = async (id: string): Promise<Advertisement> => {
  const response = await adMobileApi.get(ENDPOINTS.adById(id));
  const ad = response.data.data;

  return {
    id: ad.uid,
    title: ad.title,
    publishers: ad.company?.name ? [ad.company.name] : [],
    status: ad.status ?? 'Draft',
    startDate: ad.startDate,
    endDate: ad.endDate,
    impressions: ad.impressions ?? 0,
    clicks: ad.clicks ?? 0,
    ctr: ad.ctr ?? 0,
    paymentStatus: (ad.status === 'INACTIVE' || ad.status === 'Draft' || ad.status === 'Pending') ? 'Pending' : 'Paid',
  };
};

// ─────────────────────────────────────────────────────────────────────────────
// Create Advertisement (Draft)
// POST /v1/advertisements/create
//
// Payload built using buildCreateAdPayload() from constants.ts
// ─────────────────────────────────────────────────────────────────────────────

// Sanitize helper to prevent regex-based backend crashes on special characters
const sanitize = (str: string) => {
  if (!str) return str;
  return str.replace(/[()]/g, (match) => `\\${match}`);
};

export const createAd = async (data: any): Promise<Advertisement> => {
  // Resolve frontend CTA label → backend CTA code
  const ctaCode = CTA_LABEL_TO_CODE[data.ctaType] ?? 'REDIRECT';

  const payload = buildCreateAdPayload({
    title: sanitize(data.title),
    description: sanitize(data.description),
    adType: data.type,
    imageAdUID: data.imageAdUID,     // uploaded thumbnail/Image Ad UID
    companyUID: data.companyUID,
    ctaLabel: data.ctaLabel,
    ctaCode,
    ctaActionValue: data.ctaActionValue,
    customSections: (data.customSections ?? []).map((s: any) => ({
      title: sanitize(s.title),
      description: sanitize(s.description)
    })),
    bannerUIDs: data.bannerUIDs,
    videoUID: data.videoUID,
    videoUrl: data.videoUrl,
    videoType: data.videoType,
  });

  console.log("🚀 [CREATE] Advertisement Payload:", JSON.stringify(payload, null, 2));

  const response = await adMobileApi.post(ENDPOINTS.adsCreate, payload);
  console.log('📦 Create Ad Response:', response.data);
  
  if (!response.data.success) {
    throw new Error(response.data.message || response.data.data || "Failed to create advertisement");
  }

  const result = response.data.data;

  return {
    id: result.uid || result._id,
    title: result.title,
    publishers: [],
    status: 'Draft',
    startDate: result.startDate,
    endDate: result.endDate,
    impressions: 0,
    clicks: 0,
    ctr: 0,
    paymentStatus: 'Pending',
  };
};

// ─────────────────────────────────────────────────────────────────────────────
// Update Advertisement
// PUT /v1/advertisements/update/:uid
// ─────────────────────────────────────────────────────────────────────────────

export const updateAd = async (id: string, data: any): Promise<Advertisement> => {
  const ctaCode = CTA_LABEL_TO_CODE[data.ctaType] ?? 'REDIRECT';

  const payload = buildCreateAdPayload({
    title: sanitize(data.title),
    description: sanitize(data.description),
    adType: data.type,
    imageAdUID: data.imageAdUID,
    companyUID: data.companyUID,
    ctaLabel: data.ctaLabel,
    ctaCode,
    ctaActionValue: data.ctaActionValue,
    customSections: (data.customSections ?? []).map((s: any) => ({
      title: sanitize(s.title),
      description: sanitize(s.description)
    })),
    bannerUIDs: data.bannerUIDs,
    videoUID: data.videoUID,
    videoUrl: data.videoUrl,
    videoType: data.videoType,
  });

  console.log(`🚀 [UPDATE] Advertisement (${id}) Payload:`, JSON.stringify(payload, null, 2));

  const response = await adMobileApi.put(ENDPOINTS.adsUpdate(id), payload);
  console.log(`📦 Update Ad ${id} Response:`, response.data);
  
  if (!response.data.success) {
    throw new Error(response.data.message || response.data.data || "Failed to update advertisement");
  }

  const result = response.data.data;

  return {
    id: result.uid || result._id,
    title: result.title,
    publishers: [],
    status: 'Draft',
    startDate: result.startDate,
    endDate: result.endDate,
    impressions: 0,
    clicks: 0,
    ctr: 0,
    paymentStatus: 'Pending',
  };
};

// ─────────────────────────────────────────────────────────────────────────────
// Publish Ad → Create Campaign
// POST /v1/ad-campaigns/create
//
// Payload:
// {
//   advertisementId: "<ad-uid>",
//   dateRange: { fromDate, toDate },
//   location: { lat, lng, locationName, range },  ← range in METRES
//   compaignsStatus: "ACTIVE" | "PENDING",        ← backend typo preserved
//   createdThrough: "WEB"
// }
// ─────────────────────────────────────────────────────────────────────────────

export interface PublishPayload {
  startDate: string;
  endDate: string;
  // Geo-targeting from TargetingStep
  latitude: number;
  longitude: number;
  radiusKm: number;        // converted to metres for the backend
  locationName?: string;
}

export const finalizeAdPublication = async (
  advertisementId: string,
  data: PublishPayload
): Promise<void> => {
  const payload = buildCreateCampaignPayload({
    advertisementId,
    fromDate: data.startDate,
    toDate: data.endDate,
    lat: data.latitude,
    lng: data.longitude,
    locationName: data.locationName ?? 'Selected Target Area',
    rangeMeters: Math.round(data.radiusKm * 1000), // km → metres
  });

  console.log("🚀 [PUBLISH] Campaign Payload:", JSON.stringify(payload, null, 2));

  const response = await adMobileApi.post(ENDPOINTS.campaignCreate, payload);
  console.log('📦 Create Campaign Response:', response.data);

  if (!response.data.success) {
    throw new Error(response.data.message || response.data.data || "Failed to publish advertisement");
  }
};

// ─────────────────────────────────────────────────────────────────────────────
// Delete Advertisement
// DELETE /v1/advertisements/:uid
// ─────────────────────────────────────────────────────────────────────────────

export const archiveAd = async (id: string): Promise<void> => {
  await adMobileApi.delete(ENDPOINTS.adsDelete(id));
};

// ─────────────────────────────────────────────────────────────────────────────
// Stub helpers kept for backward compatibility with other pages
// These still use mock data until the API endpoints are confirmed
// ─────────────────────────────────────────────────────────────────────────────

export const publishAd = async (id: string): Promise<Advertisement> => {
  // Kick off the campaign via API — for now returns a minimal shape
  // Real implementation should call finalizeAdPublication() from the publish wizard
  throw new Error(
    'Use finalizeAdPublication() from the publish wizard instead of publishAd().'
  );
};

export const duplicateAd = async (id: string): Promise<Advertisement> => {
  const original = await getAdById(id);
  // Duplicate by creating a new draft with the same title
  return createAd({
    title: `${original.title} (Copy)`,
    description: '',
    type: 'Image Ad',
    ctaType: 'Redirect',
    ctaLabel: 'Learn More',
    ctaActionValue: '',
    customSections: [{ title: '', description: '' }],
  });
};

export const fetchPublisherNames = async (): Promise<{ id: string; name: string }[]> => {
  try {
    // Use our Spring Boot backend instead of Mobilize API
    const response = await api.get('/api/admin/publishers');
    const publishers = response.data?.publishers || [];
    
    return publishers.map((p: any) => ({
      id: p._id || p.id,
      name: p.name
    }));
  } catch (error) {
    console.error('Error fetching publisher names:', error);
    // Fallback to old method if needed
    const companies = await fetchCompanies();
    return companies.map((c) => ({ id: c.uid, name: c.name }));
  }
};
