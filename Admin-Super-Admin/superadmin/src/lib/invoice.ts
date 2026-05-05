import type { Invoice } from "../types/invoice";
import { fetchAllTransactions } from "./transactions";

export async function getInvoiceByTransactionId(transactionId: string): Promise<Invoice> {
    const allTransactions = await fetchAllTransactions();

    const txn = allTransactions.find(t =>
        t.id === transactionId || t.transactionId === transactionId
    );

    const amountStr = txn?.amount.replace(/[₹,]/g, '') || "0";
    const amount = parseFloat(amountStr);
    const rzpTxnId = txn?.transactionId || transactionId;
    const statusStr = txn?.status === 'Completed' ? 'Paid' : (txn?.status === 'Failed' ? 'Failed' : 'Pending');

    return {
        invoiceNumber: `INV-${rzpTxnId.slice(-6).toUpperCase()}`,
        date: txn?.date || new Date().toISOString().split("T")[0],
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
            company: txn?.admin || "Keliri Admin Account",
            address: "Admin Virtual Terminal",
            email: "admin@keliri.com",
            mobile: "+91 00000 00000"
        },

        items: [
            {
                id: "1",
                description: `Advertisement Placement System (Reference: ${txn?.type || "Unknown"})`,
                quantity: 1,
                rate: amount,
                amount: amount
            }
        ],

        subtotal: amount,
        tax: 0,
        total: amount
    };
}
