export interface Invoice {
    invoiceNumber: string;
    date: string;
    transactionId: string;
    paymentMethod: string;
    status: 'Paid' | 'Pending' | 'Failed';

    from: {
        company: string;
        address: string;
        email: string;
        gst: string;
    };

    to: {
        company: string;
        address: string;
        email: string;
        mobile: string;
    };

    items: InvoiceItem[];

    subtotal: number;
    tax: number;
    total: number;
}

export interface InvoiceItem {
    id: string;
    description: string;
    quantity: number;
    rate: number;
    amount: number;
}
