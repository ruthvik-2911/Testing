import { adMobileApi } from './api';
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
  dateRange?: { start?: string; end?: string };
}

export interface FetchAdsResult {
  data: Advertisement[];
  totalItems: number;
  totalPages: number;
  uniquePublishers: string[];
}

export const fetchAds = async ({
  page,
  limit,
  search,
  status,
}: FetchAdsArgs): Promise<FetchAdsResult> => {
  const params: Record<string, any> = { page, limit };
  if (search) params.search = search;
  if (status && status !== 'All') params.status = status;

  // We fetch from /v1/ad-campaigns to get monitoring data (impressions, clicks, status)
  // as per the mobile app's PublishedAdsDashboard logic.
  const response = await adMobileApi.get(ENDPOINTS.campaignsList, { params });
  const result = response.data;

  const rawData = result.data ?? [];

  const ads: Advertisement[] = rawData.map((camp: any) => {
    const ad = camp.advertisementId || {};

    // Normalize status: backends uses "ACTIVE", "PENDING", "INACTIVE", "EXPIRED", "COMPLETED"
    // Frontend AdStatus expects: 'Draft' | 'Pending' | 'Active' | 'Expired' | 'Suspended'
    let normalizedStatus: AdStatus = 'Pending';
    const backendStatus = (camp.compaignsStatus || '').toUpperCase();

    if (backendStatus === 'ACTIVE') normalizedStatus = 'Active';
    else if (backendStatus === 'PENDING') normalizedStatus = 'Pending';
    else if (backendStatus === 'EXPIRED' || backendStatus === 'COMPLETED') normalizedStatus = 'Expired';
    else if (backendStatus === 'INACTIVE') normalizedStatus = 'Draft';

    return {
      id: ad.uid || camp.uid,
      title: ad.title || 'Untitled Campaign',
      publishers: ad.company?.name ? [ad.company.name] : [],
      status: normalizedStatus,
      startDate: camp.dateRange?.fromDate || ad.startDate,
      endDate: camp.dateRange?.toDate || ad.endDate,
      impressions: camp.reachedPublishingCount ?? 0, // using reachedPublishingCount as a proxy for impressions if not available
      clicks: camp.clicks ?? 0,
      ctr: camp.ctr ?? 0,
      paymentStatus: (normalizedStatus === 'Draft' || normalizedStatus === 'Pending') ? 'Pending' : 'Paid',
    };
  });

  const uniquePublishers = Array.from(
    new Set(rawData.map((c: any) => c.advertisementId?.company?.name).filter(Boolean))
  ) as string[];

  return {
    data: ads,
    totalItems: result.total ?? ads.length,
    totalPages: result.totalPages ?? 1,
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

export const createAd = async (data: any): Promise<Advertisement> => {
  // Resolve frontend CTA label → backend CTA code
  const ctaCode = CTA_LABEL_TO_CODE[data.ctaType] ?? 'REDIRECT';

  const payload = buildCreateAdPayload({
    title: data.title,
    description: data.description,
    adType: data.type,
    imageAdUID: data.imageAdUID,     // uploaded thumbnail/Image Ad UID
    companyUID: data.companyUID,
    ctaLabel: data.ctaLabel,
    ctaCode,
    ctaActionValue: data.ctaActionValue,
    customSections: data.customSections ?? [],
    bannerUIDs: data.bannerUIDs,
    videoUID: data.videoUID,
    videoUrl: data.videoUrl,
    videoType: data.videoType,
  });

  console.log("🚀 [CREATE] Advertisement Payload:", JSON.stringify(payload, null, 2));

  const response = await adMobileApi.post(ENDPOINTS.adsCreate, payload);
  console.log('📦 Create Ad Response:', response.data);
  const result = response.data.data;

  return {
    id: result.uid,
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
    title: data.title,
    description: data.description,
    adType: data.type,
    imageAdUID: data.imageAdUID,
    companyUID: data.companyUID,
    ctaLabel: data.ctaLabel,
    ctaCode,
    ctaActionValue: data.ctaActionValue,
    customSections: data.customSections ?? [],
    bannerUIDs: data.bannerUIDs,
    videoUID: data.videoUID,
    videoUrl: data.videoUrl,
    videoType: data.videoType,
  });

  console.log(`🚀 [UPDATE] Advertisement (${id}) Payload:`, JSON.stringify(payload, null, 2));

  const response = await adMobileApi.put(ENDPOINTS.adsUpdate(id), payload);
  console.log(`📦 Update Ad ${id} Response:`, response.data);
  const result = response.data.data;

  return {
    id: result.uid,
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
  const companies = await fetchCompanies();
  return companies.map((c) => ({ id: c.uid, name: c.name }));
};
