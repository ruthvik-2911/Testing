import { api } from './api'
import type { RazorpayOrder } from "../types/payment"
import type { Invoice } from "../types/invoice"

export const createOrder = async (adId: string, amount: number): Promise<RazorpayOrder> => {
  const response = await api.post('/api/admin/payments/create-order', {
    adId,
    amount
  });

  if (!response.data.success) {
    throw new Error(response.data.message || 'Failed to create order');
  }

  return {
    id: response.data.id,
    amount: response.data.amount,
    currency: response.data.currency,
    keyId: response.data.keyId
  }
}

export const verifyPayment = async (payload: {
  razorpay_payment_id: string
  razorpay_order_id: string
  razorpay_signature: string
}): Promise<boolean> => {
  const response = await api.post('/api/admin/payments/verify', payload);
  return response.data.success;
}

export interface FetchPaymentsArgs {
  page: number
  limit: number
  search?: string
  status?: string
}

export const fetchPayments = async ({ page, limit, search, status }: FetchPaymentsArgs) => {
  const response = await api.get('/api/admin/payments');
  const allData = response.data.data || [];

  // Map to frontend interface
  let filtered = allData.map((t: any) => ({
    id: t.id,
    transactionId: t.razorpayPaymentId || t.razorpayOrderId || t.id,
    adName: t.adId, // Show the full Ad ID instead of truncating
    amount: t.amount,
    status: t.status === 'SUCCESS' ? 'Success' : t.status === 'FAILED' ? 'Failed' : 'Pending',
    method: 'Razorpay',
    date: new Date(t.createdAt).toISOString().split('T')[0],
    invoiceUrl: '#'
  }));

  if (search) {
    const s = search.toLowerCase();
    filtered = filtered.filter((t: any) => t.transactionId?.toLowerCase().includes(s));
  }

  if (status && status !== 'All') {
    filtered = filtered.filter((t: any) => t.status === status);
  }

  const totalItems = filtered.length;
  const totalPages = Math.ceil(totalItems / limit) || 1;
  const paginated = filtered.slice((page - 1) * limit, page * limit);

  return {
    data: paginated,
    totalItems,
    totalPages,
    stats: {
      totalRevenue: allData.filter((t: any) => t.status === 'SUCCESS').reduce((acc: number, t: any) => acc + t.amount, 0),
      successRate: allData.length > 0 ? Math.round((allData.filter((t: any) => t.status === 'SUCCESS').length / allData.length) * 100) : 0
    }
  }
}

export const getInvoiceById = async (transactionId: string): Promise<Invoice> => {
  // Find real transaction details by scanning through API data since there's no singular endpoint yet
  const response = await api.get('/api/admin/payments');
  const allData = response.data.data || [];

  const txn = allData.find((t: any) =>
    t.id === transactionId || t.razorpayOrderId === transactionId || t.razorpayPaymentId === transactionId
  );

  const amount = txn?.amount || 365;
  const rzpTxnId = txn?.razorpayPaymentId || txn?.razorpayOrderId || transactionId;
  const statusStr = txn?.status === 'SUCCESS' ? 'Paid' : (txn?.status === 'FAILED' ? 'Failed' : 'Pending');

  // Dynamic user data from session
  const adminUserStr = localStorage.getItem('admin_user');
  const adminUser = adminUserStr ? JSON.parse(adminUserStr) : null;

  return {
    invoiceNumber: `INV-${rzpTxnId.slice(-6).toUpperCase()}`,
    date: txn ? new Date(txn.createdAt).toISOString().split("T")[0] : new Date().toISOString().split("T")[0],
    transactionId: rzpTxnId,
    paymentMethod: "Razorpay Checkout",
    status: statusStr as any,

    from: {
      company: "KELIRI Platform Services",
      address: "123 Business Park, Tech Zone, Gurgaon, Haryana - 122003",
      email: "billing@keliri.com",
      gst: "06AAAAA0000A1Z5"
    },

    to: {
      company: adminUser?.companyName || adminUser?.name || "Keliri Admin Account",
      address: adminUser?.companyAddress || adminUser?.address || "Admin Virtual Terminal",
      email: adminUser?.email || "admin@keliri.com",
      mobile: adminUser?.phoneNumber || adminUser?.mobileNumber || "+91 00000 00000"
    },

    items: [
      {
        id: "1",
        description: `Advertisement Placement System (Campaign: ${txn?.adId || "Unknown"})`,
        quantity: 1,
        rate: amount,
        amount: amount
      }
    ],

    subtotal: amount,
    tax: 0,
    total: amount
  }
}
