import { useState } from 'react'
import {
  ArrowDownRight, ArrowUpRight, CheckCircle2, Clock, XCircle,
  Search
} from 'lucide-react'
import DataTable from '../components/shared/DataTable'

// Extended mock dataset
const allTransactions = [
  { id: 'TXN-9021', date: '14 Apr 2026, 11:30 AM', admin: 'Arjun Mehta', type: 'Banner Ad', amount: '₹14,500', status: 'Completed', incoming: true },
  { id: 'TXN-9022', date: '14 Apr 2026, 10:15 AM', admin: 'Priya Sharma', type: 'Video Ad', amount: '₹8,200', status: 'Completed', incoming: true },
  { id: 'TXN-9023', date: '13 Apr 2026, 04:45 PM', admin: 'Vishal Singh', type: 'Withdrawal', amount: '₹45,000', status: 'Pending', incoming: false },
  { id: 'TXN-9024', date: '13 Apr 2026, 02:20 PM', admin: 'Sneha Patel', type: 'Thumbnail Ad', amount: '₹3,400', status: 'Failed', incoming: true },
  { id: 'TXN-9025', date: '12 Apr 2026, 09:10 AM', admin: 'Ravi Kumar', type: 'Banner Ad', amount: '₹12,800', status: 'Completed', incoming: true },
  { id: 'TXN-9026', date: '11 Apr 2026, 01:05 PM', admin: 'Neha Gupta', type: 'Sponsored List', amount: '₹22,000', status: 'Completed', incoming: true },
  { id: 'TXN-9027', date: '10 Apr 2026, 05:30 PM', admin: 'Vikram Singh', type: 'Video Ad', amount: '₹16,500', status: 'Completed', incoming: true },
  { id: 'TXN-9028', date: '10 Apr 2026, 11:10 AM', admin: 'Vishal Singh', type: 'Withdrawal', amount: '₹30,000', status: 'Completed', incoming: false },
  { id: 'TXN-9029', date: '09 Apr 2026, 03:22 PM', admin: 'Ankit Desai', type: 'Banner Ad', amount: '₹5,600', status: 'Completed', incoming: true },
  { id: 'TXN-9030', date: '08 Apr 2026, 02:15 PM', admin: 'Arjun Mehta', type: 'Withdrawal', amount: '₹15,000', status: 'Failed', incoming: false },
]

export default function Transactions() {
  const [searchTerm, setSearchTerm] = useState('')
  const [filterStatus, setFilterStatus] = useState('All')
  const [filterType, setFilterType] = useState('All')
  const [filterDirection, setFilterDirection] = useState('All')

  // Simple filtering
  const filteredTx = allTransactions.filter(tx => {
    const matchSearch = tx.id.toLowerCase().includes(searchTerm.toLowerCase()) ||
      tx.admin.toLowerCase().includes(searchTerm.toLowerCase())
    const matchStatus = filterStatus === 'All' || tx.status === filterStatus
    const matchType = filterType === 'All' || tx.type === filterType
    let matchDirection = true
    if (filterDirection === 'Incoming') matchDirection = tx.incoming === true
    if (filterDirection === 'Outgoing') matchDirection = tx.incoming === false

    return matchSearch && matchStatus && matchType && matchDirection
  })

  const columns = [
    {
      key: 'id',
      label: 'Transaction',
      render: (_: any, row: any) => (
        <div className="flex items-center gap-3">
          <div className={`w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0
            ${row.incoming ? 'bg-green-50 text-green-500' : 'bg-red-50 text-red-500'}`}>
            {row.incoming ? <ArrowDownRight size={14} /> : <ArrowUpRight size={14} />}
          </div>
          <div>
            <p className="font-semibold text-gray-900 leading-none">{row.id}</p>
            <p className="text-xs text-gray-400 mt-1">{row.date}</p>
          </div>
        </div>
      )
    },
    { key: 'admin', label: 'Admin', className: 'text-gray-700 font-medium' },
    { key: 'type', label: 'Ad Type', className: 'text-gray-500 text-sm' },
    {
      key: 'amount',
      label: 'Amount',
      className: 'text-right',
      render: (val: string, row: any) => (
        <span className="font-bold text-gray-900">
          {row.incoming ? '+' : '-'}{val}
        </span>
      )
    },
    {
      key: 'status',
      label: 'Status',
      className: 'text-center',
      render: (val: string) => {
        if (val === 'Completed') return (
          <span className="inline-flex items-center gap-1.5 bg-green-50 text-green-600 px-2.5 py-1 rounded-full text-[11px] font-bold uppercase tracking-wide">
            <CheckCircle2 size={12} /> Completed
          </span>
        )
        if (val === 'Pending') return (
          <span className="inline-flex items-center gap-1.5 bg-yellow-50 text-yellow-600 px-2.5 py-1 rounded-full text-[11px] font-bold uppercase tracking-wide">
            <Clock size={12} /> Pending
          </span>
        )
        return (
          <span className="inline-flex items-center gap-1.5 bg-red-50 text-red-600 px-2.5 py-1 rounded-full text-[11px] font-bold uppercase tracking-wide">
            <XCircle size={12} /> Failed
          </span>
        )
      }
    }
  ]

  return (
    <div className="space-y-6 pb-6 max-w-[1400px] mx-auto">
      {/* ── Page Header ── */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pt-1 scroll-animate delay-75">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 tracking-tight">All Transactions</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            Full history of platform payments and payouts
          </p>
        </div>
      </div>

      {/* ── Filter Bar ── */}
      <div className="glass-card p-4 flex flex-col md:flex-row gap-4 items-center justify-between relative z-20 scroll-animate delay-150">
        <div className="relative w-full md:max-w-xs">
          <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          <input
            type="text"
            placeholder="Search by ID or Admin..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full pl-9 pr-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:border-primary-400"
          />
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <select value={filterType} onChange={(e) => setFilterType(e.target.value)} className="bg-gray-50 border border-gray-200 rounded-xl px-3 py-2.5 text-sm">
            <option value="All">All Types</option>
            <option value="Banner Ad">Banner Ad</option>
            <option value="Video Ad">Video Ad</option>
            <option value="Thumbnail Ad">Thumbnail Ad</option>
            <option value="Sponsored List">Sponsored List</option>
            <option value="Withdrawal">Withdrawal</option>
          </select>

          <select value={filterDirection} onChange={(e) => setFilterDirection(e.target.value)} className="bg-gray-50 border border-gray-200 rounded-xl px-3 py-2.5 text-sm">
            <option value="All">All Types & Payouts</option>
            <option value="Incoming">Incoming (Revenue)</option>
            <option value="Outgoing">Outgoing (Payouts)</option>
          </select>

          <select value={filterStatus} onChange={(e) => setFilterStatus(e.target.value)} className="bg-gray-50 border border-gray-200 rounded-xl px-3 py-2.5 text-sm">
            <option value="All">All Statuses</option>
            <option value="Completed">Completed</option>
            <option value="Pending">Pending</option>
            <option value="Failed">Failed</option>
          </select>
        </div>
      </div>

      <DataTable
        columns={columns}
        data={filteredTx}
        onRowClick={() => { }}
        exportFileName="transactions_history"
        className="scroll-animate delay-300"
      />
    </div>
  )
}
