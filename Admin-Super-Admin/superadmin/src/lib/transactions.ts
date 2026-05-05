import { API_BASE_URL, AuthError, getAuthSession } from './auth'

export interface TransactionRecord {
    id: string
    date: string
    admin: string
    type: string
    amount: string
    status: 'Completed' | 'Pending' | 'Failed'
    incoming: boolean
    transactionId: string
}

export async function fetchAllTransactions(): Promise<TransactionRecord[]> {
    const session = getAuthSession()

    const response = await fetch(`${API_BASE_URL}/api/superadmin/payments`, {
        headers: session?.token ? { Authorization: `Bearer ${session.token}` } : {}
    })

    const payload = await response.json().catch(() => ({}))
    const data = payload.data || (Array.isArray(payload) ? payload : [])

    if (!response.ok) {
        throw new AuthError(payload.message || 'Unable to fetch transactions', response.status)
    }

    return data.map((t: any) => ({
        id: t.id || `TXN-${t.reference?.slice(-4) || 'XXXX'}`,
        date: t.date ? new Date(t.date).toLocaleString('en-IN', {
            day: '2-digit',
            month: 'short',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        }) : 'N/A',
        admin: t.adminName || "System",
        type: t.reference || "Platform Payment",
        amount: `₹${(t.amount || 0).toLocaleString()}`,
        status: t.status || 'Pending',
        incoming: t.incoming ?? true,
        transactionId: t.reference || t.id
    }))
}
