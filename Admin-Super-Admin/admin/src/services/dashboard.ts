import { api, adMobileApi } from './api'
import type { Activity } from '../components/dashboard/RecentActivity'

export interface DashboardStats {
  totalAds: number
  activeAds: number
  expiredAds: number
  totalPublishers: number
  totalSpend: number
  totalClicks: number
  trends: {
    totalAds: number
    activeAds: number
    expiredAds: number
    totalPublishers: number
    totalSpend: number
    totalClicks: number
  }
}

export interface ChartDataPoint {
  name: string
  impressions?: number
  clicks: number
  spend?: number
}

export interface DashboardData {
  stats: DashboardStats
  performanceChart: ChartDataPoint[]
  engagementChart: ChartDataPoint[]
  spendChart: ChartDataPoint[]
  recentActivities: Activity[]
}

import { fetchAds } from './ads'

// ─────────────────────────────────────────────────────────────────────────────
// fetchDashboardData
//
// HYBRID APPROACH:
// 1. Fetch ADS counts from EC2 (Production) via fetchAds filter.
// 2. Fetch PUBLISHERS and charts from Spring Boot (Local).
// 3. Fetch Total Clicks from EC2 analytics endpoint.
// ─────────────────────────────────────────────────────────────────────────────
export const fetchDashboardData = async (_filter: string = '30', companyUID?: string): Promise<DashboardData> => {
  // 1. Fetch publishers/charts from Spring Boot
  const res = await api.get('/api/admin/dashboard')
  const d = res.data

  // 2. Fetch ads from EC2 (using the same logic as Ads Management)
  // This ensures the dashboard always matches the Ads list.
  let totalAds = 0
  let activeAds = 0
  let expiredAds = 0

  if (!companyUID) {
    console.warn('⚠️ No companyUID provided to fetchDashboardData. Forcing ads count to 0 for safety.');
  } else {
    try {
      console.log('DEBUG: Fetching ads for dashboard with companyUID:', companyUID);
      const adsResult = await fetchAds({ page: 1, limit: 1000, companyUID })
      totalAds = adsResult.totalItems
      activeAds = adsResult.data.filter(a => a.status === 'Active' || a.status === 'Pending').length
      expiredAds = adsResult.data.filter(a => a.status === 'Expired').length
      console.log(`DEBUG: Dashboard Ads Scoped Result -> Total: ${totalAds}, Active: ${activeAds}`);
    } catch (err) {
      console.error('Error fetching ads count for dashboard:', err)
    }
  }

  // 3. Fetch Real-time Clicks from EC2
  let totalClicks = 0;
  try {
    const days = parseInt(_filter) || 30;
    const endDateObj = new Date();
    const startDateObj = new Date();
    startDateObj.setDate(startDateObj.getDate() - days);

    const formatDt = (dt: Date) => dt.toISOString().split('T')[0];

    const clickRes = await adMobileApi.post('/v1/ad-campaigns/count/dateRange', {
      startDate: formatDt(startDateObj),
      endDate: formatDt(endDateObj),
      filters: {
        campaignType: 'ALL',
        status: 'ACTIVE',
        publisherId: companyUID // scope to the current admin's company
      }
    });

    if (clickRes.data?.success && clickRes.data?.data) {
      totalClicks = clickRes.data.data.totalClicks || 0;

      const rawDaily = clickRes.data.data.dailyStats || [];
      if (rawDaily.length > 0) {
        // Merge real EC2 daily stats into the 30-day structure from Spring Boot.
        // This ensures contiguous time-series while using real click/view numbers.
        const mergeInto = (arrKey: string) => {
          if (Array.isArray(d[arrKey])) {
            d[arrKey].forEach((dayPoint: any) => {
              const actual = rawDaily.find((r: any) => r.date === dayPoint.date);
              if (actual) {
                dayPoint.clicks = actual.clicks || 0;
                if (dayPoint.impressions !== undefined || arrKey === 'performanceTrend') {
                  dayPoint.impressions = actual.views || 0;
                }
              }
            });
          }
        };

        mergeInto('performanceTrend');
        mergeInto('engagementTrend');
        mergeInto('spendVsPerformance');
      }
    }
  } catch (err) {
    console.error('Error fetching real-time clicks:', err);
  }

  const totalPublishers = Number(d.totalPublishers ?? 0)
  const totalSpend = Number(d.totalSpend ?? 0)

  // Simple trend indicator: positive if data exists, -100 if zero (shows drop)
  const trendPct = (curr: number) => (curr > 0 ? 5 : -100)

  const mapChart = (arr: any[], key1: string, key2?: string): ChartDataPoint[] =>
    (arr || []).map((item: any) => {
      const point: any = {
        name: item.date || item.name || '',
        clicks: item.clicks || 0,
      }
      if (key1 !== 'clicks') point[key1] = item[key1] || 0
      if (key2 && key2 !== 'clicks') point[key2] = item[key2] || 0
      return point as ChartDataPoint
    })

  return {
    stats: {
      totalAds,
      activeAds,
      expiredAds,
      totalPublishers,
      totalSpend,
      totalClicks,
      trends: {
        totalAds: trendPct(totalAds),
        activeAds: trendPct(activeAds),
        expiredAds: expiredAds > 0 ? 12 : 0,
        totalPublishers: trendPct(totalPublishers),
        totalSpend: trendPct(totalSpend),
        totalClicks: trendPct(totalClicks),
      },
    },
    performanceChart: mapChart(d.performanceTrend || [], 'impressions', 'clicks'),
    engagementChart: mapChart(d.engagementTrend || [], 'clicks'),
    spendChart: mapChart(d.spendVsPerformance || [], 'spend', 'clicks'),
    recentActivities: [] as Activity[],
  }
}
