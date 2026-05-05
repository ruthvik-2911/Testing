import { useEffect, useState } from 'react'
import { Download, Calendar, DollarSign, Activity, Wallet, CreditCard, Loader2 } from 'lucide-react'
import KpiCard from '../components/dashboard/KpiCard'
import RevenueChart from '../components/dashboard/RevenueChart'
import RevenueDistributionChart from '../components/revenue/RevenueDistributionChart'
import TransactionsTable from '../components/revenue/TransactionsTable'
import { fetchRevenueAnalytics } from '../lib/analytics'

export default function Revenue() {
  const [loading, setLoading] = useState(true)
  const [data, setData] = useState<any>(null)

  useEffect(() => {
    async function load() {
      try {
        const result = await fetchRevenueAnalytics()
        setData(result)
      } catch (err) {
        console.error("Failed to load revenue analytics", err)
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [])

  const today = new Date().toLocaleDateString('en-IN', {
    weekday: 'long',
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  })

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[400px] gap-3">
        <Loader2 className="animate-spin text-primary-500" size={40} />
        <p className="text-sm text-gray-500 font-medium">Loading revenue data...</p>
      </div>
    )
  }

  return (
    <div className="space-y-6 pb-6 max-w-[1400px] mx-auto">
      {/* ── Page Header ── */}
      <div className="flex items-center justify-between pt-1">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 tracking-tight">Revenue Management</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            Financial analytics and payouts · {today}
          </p>
        </div>

        {/* Action Bar */}
        <div className="flex items-center gap-3">
          <button className="flex items-center gap-2 bg-white border border-gray-200 text-gray-700
                           px-3 py-1.5 rounded-xl text-xs font-semibold shadow-sm hover:bg-gray-50 transition-colors">
            <Calendar size={14} className="text-gray-400" />
            Last 30 Days
          </button>
          <button className="flex items-center gap-2 bg-primary-50 border border-primary-100 text-primary-600
                           px-3 py-1.5 rounded-xl text-xs font-semibold shadow-sm hover:bg-primary-100 transition-colors">
            <Download size={14} />
            Export CSV
          </button>
        </div>
      </div>

      {/* ── KPI Grid ── */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 sm:gap-6 scroll-animate delay-75">
        <KpiCard
          title="Total Revenue"
          value={data?.totalRevenue?.toLocaleString() || "0"}
          change={data?.revenueChange || 0}
          icon={DollarSign}
          iconBg="bg-green-100"
          iconColor="text-green-600"
          prefix="₹"
        />
        <KpiCard
          title="Avg Revenue / Ad"
          value={data?.avgRevenuePerAd?.toLocaleString() || "0"}
          change={data?.avgRevenueChange || 0}
          icon={Activity}
          iconBg="bg-primary-50"
          iconColor="text-primary-500"
          prefix="₹"
          changeLabel="vs last month"
        />
        <KpiCard
          title="Pending Payouts"
          value={data?.pendingPayouts?.toLocaleString() || "0"}
          change={data?.payoutChange || 0}
          icon={Wallet}
          iconBg="bg-yellow-100"
          iconColor="text-yellow-600"
          prefix="₹"
          changeLabel="To be cleared"
        />
        <KpiCard
          title="Total Transactions"
          value={data?.totalTransactions?.toLocaleString() || "0"}
          change={data?.transactionChange || 0}
          icon={CreditCard}
          iconBg="bg-blue-100"
          iconColor="text-blue-600"
          changeLabel="Completed this month"
        />
      </div>

      {/* ── Charts Row ── */}
      <div className="grid grid-cols-1 xl:grid-cols-3 gap-4 lg:gap-6 scroll-animate delay-150">
        <div className="xl:col-span-2">
          <RevenueChart data={data?.chartData || []} />
        </div>
        <div>
          <RevenueDistributionChart
            data={data?.breakdown?.map((b: any) => ({
              name: b.category,
              value: b.amount,
              color: b.color.replace('bg-', '#').replace('blue-500', '3B82F6').replace('green-500', '10B981').replace('purple-500', '8B5CF6').replace('orange-500', 'FF6B00')
            })) || []}
          />
        </div>
      </div>

      {/* ── Transactions Table Row ── */}
      <div className="grid grid-cols-1 scroll-animate delay-200">
        <TransactionsTable />
      </div>
    </div>
  )
}
