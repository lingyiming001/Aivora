import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Settings, AlertTriangle } from 'lucide-react';
import { api } from '@/services/request';
import { type Budget } from '@/pages/system/status/types';
import { BudgetForm } from '@/pages/system/status/components/BudgetForm';

export default function TokenBudgetPanel() {
  const q = useQuery({
    queryKey: ['token-budget'],
    refetchInterval: 30000,
    queryFn: async () => (await api.get<Budget>('/admin/status/budget')).data,
  });
  const [editing, E] = useState(false);
  return (
    <section className="token-budget-panel" id="token-budget">
      <header>
        <div>
          <h2>月度 Token 预算</h2>
          <p>平台实际用量监控 · 北京时间按月重置</p>
        </div>
        <button disabled={!q.data} onClick={() => E(!editing)}>
          <Settings size={15} />
          {editing ? '收起设置' : '预算设置'}
        </button>
      </header>
      {q.isPending ? (
        <p>正在统计用量…</p>
      ) : q.isError ? (
        <p role="alert">
          预算加载失败 <button onClick={() => q.refetch()}>重试</button>
        </p>
      ) : (
        q.data && (
          <>
            <div className="budget-metrics">
              <div>
                <span>本月已用</span>
                <strong>{q.data.used.toLocaleString()}</strong>
                <small>
                  已计量 {q.data.measured.toLocaleString()} + 补录{' '}
                  {q.data.baseline.toLocaleString()}
                </small>
              </div>
              <div>
                <span>月度预算</span>
                <strong>{q.data.limit ? q.data.limit.toLocaleString() : '待设置'}</strong>
                <small>{q.data.month} · Token</small>
              </div>
              <div>
                <span>预算剩余</span>
                <strong className={q.data.low ? 'budget-warning' : ''}>
                  {q.data.limit ? q.data.remainingPercent.toFixed(1) + '%' : '—'}
                </strong>
                <small>
                  阈值 {q.data.threshold}% · {q.data.enabled ? '监控开启' : '监控关闭'}
                </small>
              </div>
            </div>
            {q.data.limit > 0 && (
              <div className="budget-bar">
                <span style={{ width: Math.min(100, (q.data.used / q.data.limit) * 100) + '%' }} />
              </div>
            )}
            {q.data.low && (
              <p className="budget-warning">
                <AlertTriangle size={15} />
                预算低于阈值，已生成平台通知；请充值或调整预算。
              </p>
            )}
            {!q.data.limit && (
              <p className="budget-help">设置月度 Token 总预算后启用预警，默认剩余20%提醒。</p>
            )}
            <p className="budget-help">
              仅统计供应商返回的实际 Token；未返回用量的 {q.data.unknownCalls}{' '}
              次调用不计入。功能上线前的历史用量可手动补录。预算增加后恢复余量，再次低于阈值会重新提醒。
            </p>
            {editing && (
              <BudgetForm
                key={
                  q.data.month + ':' + q.data.limit + ':' + q.data.threshold + ':' + q.data.baseline
                }
                data={q.data}
                close={() => E(false)}
              />
            )}
          </>
        )
      )}
    </section>
  );
}
