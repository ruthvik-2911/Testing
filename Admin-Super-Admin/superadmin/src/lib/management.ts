import { API_BASE_URL, AuthError, getAuthSession } from './auth'

export interface AdminRecord {
  id: string
  name: string
  email: string
  company: string
  registeredDate: string
  status: string
  phone: string
}

export interface PublisherMini {
  id: string
  name: string
  status: string
  adsPosted: number
}

export interface AdminDetail extends AdminRecord {
  performance: {
    totalAds: number
    revenue: number
    avgCtr: number
  }
  registration?: {
    authorizedPerson?: string
    businessAddress?: string
    addressLine2?: string
    city?: string
    state?: string
    zipCode?: string
    country?: string
    gstNumber?: string
    companyType?: string
    countryCode?: string
    mobileNumber?: string
    submittedAt?: string
  }
  documents: Array<{
    name: string
    type: string
    url: string
  }>
  publishers: PublisherMini[]
}

export interface EmailNotificationRecord {
  id: string
  trigger: string
  to: string
  content: string
  timestamp: string
}

export interface AdminActionResponse {
  admin: AdminRecord
  emailNotification?: EmailNotificationRecord | null
}

export interface PublisherRecord {
  id: string
  name: string
  adminId: string
  adminName: string
  location: string
  adsPosted: number
  impressions: number
  clicks: number
  engagement: number
  status: string
  email: string
  phone: string
  joinDate: string
}

export interface PublisherDetail extends PublisherRecord {
  ads: Array<{
    id: string
    title: string
    type: string
    status: string
    ctr: number
  }>
}

export interface AuditLogRecord {
  id: string
  timestamp: string
  actorName: string
  actorRole: string
  actionType: string
  entityType: string
  entityId: string
  action: string
  ip: string
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

export async function fetchAdmins(params?: { search?: string; status?: string }): Promise<AdminRecord[]> {
  const query = new URLSearchParams()
  if (params?.search) query.set('search', params.search)
  if (params?.status) query.set('status', params.status)

  const response = await fetch(`${API_BASE_URL}/api/superadmin/admins?${query.toString()}`, {
    headers: authHeaders(),
  })

  return handleJsonResponse<AdminRecord[]>(response, 'Unable to fetch admins')
}

export async function fetchAdminDetail(adminId: string): Promise<AdminDetail> {
  const response = await fetch(`${API_BASE_URL}/api/superadmin/admins/${adminId}`, {
    headers: authHeaders(),
  })

  return handleJsonResponse<AdminDetail>(response, 'Unable to fetch admin details')
}

export async function runAdminAction(adminId: string, action: 'approve' | 'reject' | 'suspend' | 'reinstate', reason?: string): Promise<AdminActionResponse> {
  const response = await fetch(`${API_BASE_URL}/api/superadmin/admins/${adminId}/${action}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(authHeaders() ?? {}),
    },
    body: action === 'reject' ? JSON.stringify({ reason }) : undefined,
  })

  return handleJsonResponse<AdminActionResponse>(response, 'Unable to perform admin action')
}

export async function deleteAdmin(adminId: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/superadmin/admins/${adminId}`, {
    method: 'DELETE',
    headers: authHeaders(),
  })

  if (!response.ok) {
    const payload = await response.json().catch(() => null) as { message?: string; error?: string } | null
    throw new AuthError(payload?.message || payload?.error || 'Unable to delete admin', response.status)
  }
}

export async function fetchAdminNotifications(): Promise<EmailNotificationRecord[]> {
  const response = await fetch(`${API_BASE_URL}/api/superadmin/admin-notifications`, {
    headers: authHeaders(),
  })

  return handleJsonResponse<EmailNotificationRecord[]>(response, 'Unable to fetch notifications')
}

export const AD_MOBILE_API_URL = import.meta.env.VITE_AD_MOBILE_BACKEND_URL ?? 'http://ec2-15-206-186-192.ap-south-1.compute.amazonaws.com:3000'
export const AD_MOBILE_TOKEN = import.meta.env.VITE_AD_MOBILE_TOKEN ?? 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOiI2NGZiMTE3YS1mNTMwLTRmOTgtYTVkMy0yMWY3ZmVlYzkwNDciLCJfaWQiOiI2NGVkODZkMjMyMDBlMWQ2YzUyMDYwYmMiLCJpYXQiOjE3NzY5MTQ0MzksImV4cCI6MTgwODQ1MDQzOX0.jYRhILMRhluttAgns-OGDgGdji0DKhok6QBcUB7qdPg'

function mobileHeaders() {
  return {
    'Authorization': `Bearer ${AD_MOBILE_TOKEN}`,
    'Content-Type': 'application/json'
  }
}

export async function fetchPublishers(params?: { adminId?: string; status?: string; location?: string; search?: string }): Promise<PublisherRecord[]> {
  // Fetch from our backend which has production data from Mobilize API
  const session = getAuthSession()

  const response = await fetch(`${API_BASE_URL}/api/admin/publishers`, {
    headers: session?.token
      ? {
        'Authorization': `Bearer ${session.token}`,
      }
      : undefined,
  })

  const payload = await response.json().catch(() => null) as
    | { success: boolean; publishers: PublisherRecord[] }
    | { error?: string; message?: string }
    | null

  if (!response.ok || !payload || !('success' in payload) || !payload.success) {
    console.error("Could not fetch Publishers from backend API.")
    return []
  }

  const publishers = payload.publishers || []

  return publishers.map((p: any) => ({
    id: p.id || "Unknown ID",
    name: p.name || "Unknown Publisher",
    adminId: p.adminId || "N/A",
    adminName: "System Admin",
    location: p.location || "Unknown",
    adsPosted: p.ads || 0,
    impressions: Math.floor(Math.random() * 1000),
    clicks: Math.floor(Math.random() * 100),
    engagement: p.engagement || Math.floor(Math.random() * 100),
    status: p.ads === "Active" ? "Active" : "Inactive",
    email: p.email || "N/A",
    phone: "N/A",
    joinDate: new Date().toLocaleDateString()
  }))
}

export async function fetchPublisherDetail(publisherId: string): Promise<PublisherDetail> {
  // Use the local backend instead of EC2 directly (EC2 token may be rejected)
  const session = getAuthSession()
  try {
    const response = await fetch(`${API_BASE_URL}/api/admin/publishers/${publisherId}`, {
      headers: session?.token
        ? { 'Authorization': `Bearer ${session.token}` }
        : undefined,
    })
    if (response.ok) {
      const payload = await response.json().catch(() => null)
      const p = payload?.publisher || payload
      if (p) {
        return {
          id: p.id || publisherId,
          name: p.name || 'Unknown',
          adminId: p.adminId || 'N/A',
          adminName: p.adminName || 'System Admin',
          location: p.location || 'Unknown',
          adsPosted: p.ads || 0,
          impressions: p.impressions || 0,
          clicks: p.clicks || 0,
          engagement: p.engagement || 0,
          status: p.status || 'Active',
          email: p.email || 'N/A',
          phone: p.phone || 'N/A',
          joinDate: p.joinDate || new Date().toLocaleDateString(),
          ads: p.ads_list || []
        }
      }
    }
  } catch {
    // fall through to EC2 fallback
  }

  // Fallback: try EC2 directly with token
  const response = await fetch(`${AD_MOBILE_API_URL}/v1/company/all/list`, {
    headers: mobileHeaders()
  })
  const payload = await response.json().catch(() => null)
  const rawCompanies = payload?.data || (Array.isArray(payload) ? payload : [])
  const c = rawCompanies.find((comp: any) => (comp.uid || comp.id || comp._id) === publisherId)

  if (!c) {
    throw new AuthError('Publisher not found', 404)
  }

  return {
    id: c.uid || c.id || c._id,
    name: c.name || 'Unknown',
    adminId: 'N/A',
    adminName: 'Automated',
    location: c.billingAddress?.addressLine1 || c.location || 'Unknown',
    adsPosted: 0,
    impressions: 0,
    clicks: 0,
    engagement: 0,
    status: c.status === 'INACTIVE' ? 'Suspended' : 'Active',
    email: c.email || 'N/A',
    phone: c.phoneNumber?.dialNumber || 'N/A',
    joinDate: new Date(c.createdAt || Date.now()).toLocaleDateString(),
    ads: []
  }
}

export async function fetchAuditLogs(params?: {
  search?: string
  actionType?: string
  actorRole?: string
  entityType?: string
  fromDate?: string
  toDate?: string
}): Promise<AuditLogRecord[]> {
  const query = new URLSearchParams()
  if (params?.search) query.set('search', params.search)
  if (params?.actionType) query.set('actionType', params.actionType)
  if (params?.actorRole) query.set('actorRole', params.actorRole)
  if (params?.entityType) query.set('entityType', params.entityType)
  if (params?.fromDate) query.set('fromDate', params.fromDate)
  if (params?.toDate) query.set('toDate', params.toDate)

  const response = await fetch(`${API_BASE_URL}/api/superadmin/audit-logs?${query.toString()}`, {
    headers: authHeaders(),
  })

  return handleJsonResponse<AuditLogRecord[]>(response, 'Unable to fetch audit logs')
}
