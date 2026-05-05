

import { API_BASE_URL, getAuthSession } from './auth'

export interface DashboardSummary {
  totalAds: number
  totalCampaigns: number
  activeCampaigns: number
  totalPublishers: number
  totalUsers: number
  geoTargetedCampaigns: number
  uniqueTargetLocations: number
  averageTargetRadiusKm: number
}

export interface DashboardKpi {
  id: string
  title: string
  value: string
  change: number
  changeLabel: string
  prefix?: string
}

export interface TrendPoint {
  label: string
  value: number
}

export interface TopCreator {
  rank: number
  name: string
  email: string
  campaignCount: number
  activeCampaignCount: number
  locationCount: number
  change: number
}

export interface RecentActivity {
  id: string
  action: string
  status: string
  locationName: string
  occurredAt: string
}

export interface BreakdownItem {
  label: string
  count: number
  percentage: number
}

export interface SuperAdminDashboardPayload {
  summary: DashboardSummary
  kpis: DashboardKpi[]
  publishingTrend: TrendPoint[]
  topCreators: TopCreator[]
  recentActivities: RecentActivity[]
  adTypeBreakdown: BreakdownItem[]
  locationBreakdown: BreakdownItem[]
  notifications?: string[]
}

export const AD_MOBILE_API_URL = import.meta.env.VITE_AD_MOBILE_BACKEND_URL ?? 'http://ec2-15-206-186-192.ap-south-1.compute.amazonaws.com:3000'
export const AD_MOBILE_TOKEN = import.meta.env.VITE_AD_MOBILE_TOKEN ?? 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOiI2NGZiMTE3YS1mNTMwLTRmOTgtYTVkMy0yMWY3ZmVlYzkwNDciLCJfaWQiOiI2NGVkODZkMjMyMDBlMWQ2YzUyMDYwYmMiLCJpYXQiOjE3NzY5MTQ0MzksImV4cCI6MTgwODQ1MDQzOX0.jYRhILMRhluttAgns-OGDgGdji0DKhok6QBcUB7qdPg'

function mobileHeaders() {
  return {
    'Authorization': `Bearer ${AD_MOBILE_TOKEN}`,
    'Content-Type': 'application/json'
  }
}

export async function fetchSuperAdminDashboard(): Promise<SuperAdminDashboardPayload> {
  // Fetch from our backend which has production data from Mobilize API
  const session = getAuthSession()
  
  const responses = await Promise.all([
    fetch(`${API_BASE_URL}/api/superadmin/analytics`, {
      headers: session?.token
        ? {
          'Authorization': `Bearer ${session.token}`,
        }
        : undefined,
    }).catch(() => null),
    fetch(`${API_BASE_URL}/api/admin/publishers`, {
      headers: session?.token
        ? {
          'Authorization': `Bearer ${session.token}`,
        }
        : undefined,
    }).catch(() => null)
  ])

  const [analyticsRes, publishersRes] = responses

  // Process data with safety
  const analyticsPayload = await analyticsRes?.json().catch(() => ({}))
  const publishersPayload = await publishersRes?.json().catch(() => ({}))

  // Get analytics data from our backend
  const analytics = analyticsPayload || {}
  const publishers = publishersPayload?.publishers || []

  // Calculate dashboard metrics from production data
  const counts = {
    totalUsers: publishers.length || 0,
    totalPublishers: publishers.length || 0,
    totalAds: analytics.kpis?.find(kpi => kpi.title === "Total Advertisements")?.value || 0,
    totalCampaigns: analytics.kpis?.find(kpi => kpi.title === "Total Campaigns")?.value || 0,
    activeCampaigns: analytics.kpis?.find(kpi => kpi.title === "Active Campaigns")?.value || 0,
    totalSpend: analytics.kpis?.reduce((sum, kpi) => sum + (kpi.title === "Total Revenue" ? parseFloat(kpi.value) || 0 : 0), 0),
    totalClicks: analytics.kpis?.reduce((sum, kpi) => sum + (kpi.title === "Total Clicks" ? parseFloat(kpi.value) || 0 : 0), 0)
  }

  // Set publishers from production data
  let manualPublisherCount = publishers.length || 0

  // Return dashboard payload with production data
  return {
    summary: {
      totalAds: counts.totalAds,
      totalCampaigns: counts.totalCampaigns,
      activeCampaigns: counts.activeCampaigns,
      totalPublishers: counts.totalPublishers,
      totalUsers: counts.totalUsers,
      geoTargetedCampaigns: analytics.kpis?.find(kpi => kpi.title === "Geo Targeted")?.value || 0,
      uniqueTargetLocations: analytics.kpis?.find(kpi => kpi.title === "Unique Locations")?.value || 0,
      averageTargetRadiusKm: parseFloat(analytics.kpis?.find(kpi => kpi.title === "Avg Radius")?.value) || 0
    },
    kpis: analytics.kpis || [],
    publishingTrend: analytics.weeklyTrend || [],
    topCreators: analytics.creatorRows || [],
    recentActivities: [],
    adTypeBreakdown: analytics.adTypeBreakdown || [],
    locationBreakdown: analytics.radiusBreakdown || [],
    notifications: []
  }
}
