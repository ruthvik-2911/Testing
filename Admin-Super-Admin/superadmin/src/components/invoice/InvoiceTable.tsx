import type { InvoiceItem } from "../../types/invoice"

interface InvoiceTableProps {
    items: InvoiceItem[]
}

export function InvoiceTable({ items }: InvoiceTableProps) {
    return (
        <div className="w-full">
            <table className="w-full border-collapse">
                <thead>
                    <tr className="border-b-2 border-gray-900">
                        <th className="text-left py-4 text-[11px] font-black uppercase tracking-widest text-gray-400">Description</th>
                        <th className="text-center py-4 text-[11px] font-black uppercase tracking-widest text-gray-400">Qty</th>
                        <th className="text-right py-4 text-[11px] font-black uppercase tracking-widest text-gray-400">Rate</th>
                        <th className="text-right py-4 text-[11px] font-black uppercase tracking-widest text-gray-400">Amount</th>
                    </tr>
                </thead>
                <tbody>
                    {items.map((item) => (
                        <tr key={item.id} className="border-b border-gray-100 last:border-b-0">
                            <td className="py-6">
                                <p className="text-sm font-bold text-gray-900">{item.description}</p>
                            </td>
                            <td className="py-6 text-center text-sm font-medium text-gray-600">
                                {item.quantity}
                            </td>
                            <td className="py-6 text-right text-sm font-medium text-gray-600">
                                ₹{item.rate.toLocaleString()}
                            </td>
                            <td className="py-6 text-right text-sm font-black text-gray-900">
                                ₹{item.amount.toLocaleString()}
                            </td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    )
}
