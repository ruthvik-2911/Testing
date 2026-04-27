import { useEffect, useState } from 'react'
import {
  ArrowDownRight, ArrowUpRight, CheckCircle2, Clock, XCircle,
  Search, FileText
} from 'lucide-react'
import { Link } from 'react-router-dom'
import toast from 'react-hot-toast'
import { Lock } from 'lucide-react'
import DataTable from '../components/shared/DataTable'
import { fetchAllTransactions, type TransactionRecord } from '../lib/transactions'

export default function Transactions() {
  const [allTransactions, setAllTransactions] = useState<TransactionRecord[]>([])
  const [loading, setLoading] = useState(true)
  const [searchTerm, setSearchTerm] = useState('')
  const [filterStatus, setFilterStatus] = useState('All')

  useEffect(() => {
    async function load() {
      setLoading(true)
      try {
        const data = await fetchAllTransactions()
        setAllTransactions(data)
      } catch (err) {
        console.error("Failed to load transactions", err)
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [])

  // Simple filtering
  const filteredTx = allTransactions.filter(tx => {
    const matchSearch = tx.transactionId.toLowerCase().includes(searchTerm.toLowerCase()) ||
      tx.admin.toLowerCase().includes(searchTerm.toLowerCase())
    const matchStatus = filterStatus === 'All' || tx.status === filterStatus

    return matchSearch && matchStatus
  })

  const columns = [
    {
      key: 'id',
      label: 'Transaction',
      render: (_: any, row: TransactionRecord) => (
        <div className="flex items-center gap-3">
          <div className={`w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0
            ${row.incoming ? 'bg-green-50 text-green-500' : 'bg-red-50 text-red-500'}`}>
            {row.incoming ? <ArrowDownRight size={14} /> : <ArrowUpRight size={14} />}
          </div>
          <div>
            <p className="font-semibold text-gray-900 leading-none">{row.id}</p>
            <p className="text-[10px] uppercase font-mono text-gray-400 mt-1">{row.transactionId}</p>
          </div>
        </div>
      )
    },
    { key: 'date', label: 'Date', className: 'text-gray-500 text-xs' },
    { key: 'admin', label: 'Admin', className: 'text-gray-700 font-medium' },
    { key: 'type', label: 'Reference', className: 'text-gray-500 text-sm' },
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
    },
    {
      key: 'actions',
      label: 'Invoice',
      className: 'text-center',
      render: (_: any, row: TransactionRecord) => {
        if (row.status === 'Completed') {
          return (
            <div className="flex justify-center">
              <Link
                to={`/invoice/${row.transactionId}`}
                className="flex items-center gap-1.5 text-primary-600 hover:text-primary-700 font-bold text-[11px] uppercase tracking-wide bg-primary-50 px-3 py-1.5 rounded-lg transition-colors group"
              >
                <FileText size={14} className="group-hover:scale-110 transition-transform" />
                View
              </Link>
            </div>
          )
        }

        return (
          <div className="flex justify-center uppercase tracking-wide">
            <button
              onClick={(e) => {
                e.stopPropagation()
                toast.error('Invoice Unavailable: Payment Not Completed', {
                  icon: <Lock className="text-red-500" size={18} />,
                  style: {
                    borderRadius: '12px',
                    background: '#fff',
                    color: '#991b1b',
                    fontWeight: 'bold',
                    fontSize: '12px',
                    border: '1px solid #fee2e2',
                    boxShadow: '0 10px 15px -3px rgba(0, 0, 0, 0.1)'
                  }
                })
              }}
              className="flex items-center gap-1.5 text-gray-400 hover:text-gray-500 font-bold text-[11px] bg-gray-50 px-3 py-1.5 rounded-lg transition-colors cursor-pointer"
            >
              <FileText size={14} />
              View
            </button>
          </div>
        )
      }
    }
  ]

  return (
    <div className="space-y-6 pb-6 max-w-[1400px] mx-auto">
      {/* ── Page Header ── */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pt-1 scroll-animate delay-75">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 tracking-tight">Financial Transactions</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            Full history of platform payments and payouts fetched from Razorpay logs
          </p>
        </div>
      </div>

      {/* ── Filter Bar ── */}
      <div className="glass-card p-4 flex flex-col md:flex-row gap-4 items-center justify-between relative z-20 scroll-animate delay-150">
        <div className="relative w-full md:max-w-xs">
          <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          <input
            type="text"
            placeholder="Search TXN or Admin..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full pl-9 pr-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:border-primary-400"
          />
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <select value={filterStatus} onChange={(e) => setFilterStatus(e.target.value)} className="bg-gray-50 border border-gray-200 rounded-xl px-3 py-2.5 text-sm outline-none">
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
        isLoading={loading}
        onRowClick={() => { }}
        exportFileName="transactions_history"
        className="scroll-animate delay-300"
      />
    </div>
  )
}

