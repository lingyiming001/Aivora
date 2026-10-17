import { useState } from 'react';
import axios from 'axios';
import { api } from '@/services/request';
import { qc } from '@/services/queryClient';
import { type Budget } from '@/pages/system/status/types';

export function BudgetForm({ data, close }: { data: Budget; close: () => void }) {
  const [limit, L] = useState(data.limit || 1000000),
    [threshold, T] = useState(data.threshold),
    [baseline, B] = useState(data.baseline),
    [enabled, EN] = useState(data.enabled),
    [busy, S] = useState(false),
    [error, E] = useState('');
  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (busy) return;
    S(true);
    E('');
    try {
      await api.put('/admin/status/budget', {
        month: data.month,
        limit,
        threshold,
        baseline,
        enabled,
      });
      await Promise.all([
        qc.invalidateQueries({ queryKey: ['token-budget'] }),
        qc.invalidateQueries({ queryKey: ['platform-notices'] }),
      ]);
      close();
    } catch (e) {
      E(axios.isAxiosError(e) ? e.response?.data?.message || '设置失败' : '设置失败');
    } finally {
      S(false);
    }
  }
  return (
    <form className="budget-form" onSubmit={save}>
      <label>
        月度 Token 总预算
        <input
          className="input"
          type="number"
          min={1}
          max={1000000000000}
          step={1}
          required
          value={limit}
          onChange={(e) => L(Number(e.target.value))}
        />
      </label>
      <label>
        剩余多少百分比提醒
        <input
          className="input"
          type="number"
          min={1}
          max={99}
          step={1}
          required
          value={threshold}
          onChange={(e) => T(Number(e.target.value))}
        />
      </label>
      <label>
        {data.month} 历史用量补录
        <input
          className="input"
          type="number"
          min={0}
          max={1000000000000}
          step={1}
          required
          value={baseline}
          onChange={(e) => B(Number(e.target.value))}
        />
      </label>
      <label className="budget-enabled">
        <input type="checkbox" checked={enabled} onChange={(e) => EN(e.target.checked)} />
        开启站内预警
      </label>
      <button type="submit" disabled={busy}>
        {busy ? '保存中…' : '保存预算设置'}
      </button>
      {error && <p role="alert">{error}</p>}
    </form>
  );
}
