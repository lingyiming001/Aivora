import { type ReactNode } from 'react';
import { Activity, ArrowUpRight } from 'lucide-react';
import { Link } from 'react-router-dom';

export default function AdminSummary({
  items,
}: {
  items: { label: string; value: ReactNode; note: string; to?: string }[];
}) {
  return (
    <section className="admin-summary" aria-label="页面数据摘要">
      {items.map((item, index) => (
        <article key={item.label}>
          <div className="admin-summary-label">
            <span>{item.label}</span>
            <Activity size={16} />
          </div>
          <strong>{item.value ?? '—'}</strong>
          <div className="admin-summary-note">
            {item.note}
            {item.to && (
              <Link to={item.to} aria-label={'前往' + item.label}>
                <ArrowUpRight size={17} />
              </Link>
            )}
          </div>
          <span className="admin-summary-index">0{index + 1}</span>
        </article>
      ))}
    </section>
  );
}
