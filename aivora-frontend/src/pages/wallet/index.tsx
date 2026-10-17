import { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Coins,
  Sparkles,
  ArrowUpRight,
  ArrowDownLeft,
  RefreshCw,
  Check,
  ShieldCheck,
  Receipt,
  Clock3,
} from 'lucide-react';
import axios from 'axios';
import { api } from '@/services/request';
import { qc } from '@/services/queryClient';
import UsagePanel from '@/pages/wallet/components/UsagePanel';
import { type Package, type Ledger, type Order } from '@/pages/wallet/types';

export const number = (v: number) => v.toLocaleString('zh-CN'),
  money = (v: number) => (v / 100).toFixed(2),
  date = (v: string) =>
    new Date(v).toLocaleString('zh-CN', {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    });

export const reason = (r: string) =>
  r === 'RECHARGE'
    ? '星币充值'
    : r === 'CHAT'
      ? '角色对话'
      : r === 'CHAT_REFUND'
        ? '对话退款'
        : r.includes('GIFT') || r.includes('REGISTER') || r.includes('WELCOME')
          ? '赠送星币'
          : '星币调整';

export default function WalletPage() {
  const balance = useQuery({
    queryKey: ['wallet'],
    queryFn: async () => (await api.get<{ balance: number }>('/wallet')).data,
  });
  const ledger = useQuery({
    queryKey: ['ledger'],
    queryFn: async () => (await api.get<Ledger[]>('/wallet/ledger')).data,
  });
  const packages = useQuery({
    queryKey: ['packages'],
    queryFn: async () => (await api.get<Package[]>('/recharge/packages')).data,
  });
  const orders = useQuery({
    queryKey: ['recharge-orders'],
    queryFn: async () => (await api.get<Order[]>('/recharge/orders')).data,
  });
  const caps = useQuery({
    queryKey: ['payment-capabilities'],
    queryFn: async () =>
      (await api.get<{ simulatedPayment: boolean }>('/recharge/capabilities')).data,
  });
  const [selected, S] = useState<number>(),
    [tab, T] = useState('usage'),
    [filter, F] = useState('all'),
    [busy, B] = useState(false),
    [error, E] = useState(''),
    [success, OK] = useState('');
  const running = useRef(false),
    pending = useRef<{ id: string; packageId: number } | null>(null);
  const chosen = packages.data?.find((p) => p.id === selected) ?? packages.data?.[0];
  async function refresh() {
    await Promise.all([
      balance.refetch(),
      ledger.refetch(),
      orders.refetch(),
      qc.invalidateQueries({ queryKey: ['profile'] }),
      qc.invalidateQueries({ queryKey: ['wallet-usage'] }),
      qc.invalidateQueries({ queryKey: ['wallet-usage-detail'] }),
    ]);
  }
  async function buy(existing?: Order) {
    if (running.current || !caps.data?.simulatedPayment || (!chosen && !existing)) return;
    running.current = true;
    B(true);
    E('');
    OK('');
    try {
      let id = existing?.id;
      const packageId = existing?.package_id ?? chosen!.id;
      if (!id && pending.current?.packageId === packageId) id = pending.current.id;
      if (!id) {
        id = (await api.post<{ orderId: string }>('/recharge/orders', { packageId })).data.orderId;
        pending.current = { id, packageId };
      }
      await api.post('/recharge/orders/' + id + '/simulate-pay');
      pending.current = null;
      OK('充值成功，' + number(existing?.stars ?? chosen!.stars) + ' 星币已到账');
      await refresh();
    } catch (e) {
      E(
        axios.isAxiosError(e)
          ? e.response?.data?.message || '暂时无法完成充值，请重试或在充值订单中继续。'
          : '暂时无法完成充值，请重试。',
      );
      await refresh();
    } finally {
      running.current = false;
      B(false);
    }
  }
  const entries =
    ledger.data?.filter(
      (r) =>
        filter === 'all' ||
        (filter === 'in' && r.delta > 0) ||
        (filter === 'out' && r.delta < 0) ||
        (filter === 'refund' && r.reason === 'CHAT_REFUND'),
    ) ?? [];
  return (
    <main className="client-page wallet-page">
      <div className="wallet-page-title">
        <div>
          <p className="eyebrow">KEEP YOUR STORIES GOING</p>
          <h1>星币钱包</h1>
          <p className="muted">为下一场有趣的对话，留一点期待。</p>
        </div>
        <button
          className="wallet-refresh"
          aria-label="刷新钱包"
          disabled={busy || balance.isFetching}
          onClick={() => void refresh()}
        >
          <RefreshCw size={17} />
          刷新
        </button>
      </div>
      <section className="wallet-balance-card">
        <div>
          <span className="wallet-balance-label">
            <Coins size={18} />
            可用星币
          </span>
          <p className="wallet-balance-number">
            {balance.isPending ? '…' : balance.isError ? '—' : number(balance.data!.balance)}
            <small>星币</small>
          </p>
          <span className="wallet-balance-foot">每一场对话，都让故事更进一步。</span>
        </div>
        <div className="wallet-balance-art" aria-hidden="true">
          <span>
            <Sparkles size={38} strokeWidth={1.2} />
          </span>
          <i />
          <i />
        </div>
        <Link to="/">
          发现新的聊天搭子 <ArrowUpRight size={17} />
        </Link>
      </section>
      {balance.isError && (
        <p role="alert" className="wallet-feedback error">
          余额加载失败，请刷新重试。
        </p>
      )}
      <div className="wallet-recharge-grid">
        <section className="wallet-packages">
          <div className="wallet-section-title">
            <div>
              <h2>给灵感续航</h2>
              <p>选择适合你的星币套餐</p>
            </div>
            {caps.data?.simulatedPayment && (
              <span className="wallet-demo-badge">开发体验 · 模拟充值</span>
            )}
          </div>
          {packages.isPending ? (
            <div className="wallet-empty">正在读取充值套餐…</div>
          ) : packages.isError ? (
            <div className="wallet-empty">
              套餐加载失败 <button onClick={() => packages.refetch()}>重试</button>
            </div>
          ) : !packages.data?.length ? (
            <div className="wallet-empty">暂无可用套餐</div>
          ) : (
            <div
              className={
                'wallet-package-grid ' + (packages.data.length === 1 ? 'single-package' : '')
              }
              role="radiogroup"
              aria-label="选择充值套餐"
            >
              {packages.data.map((p, index) => (
                <button
                  key={p.id}
                  type="button"
                  role="radio"
                  aria-checked={chosen?.id === p.id}
                  disabled={busy}
                  className={'wallet-package ' + (chosen?.id === p.id ? 'selected' : '')}
                  onClick={() => {
                    S(p.id);
                    E('');
                    OK('');
                  }}
                >
                  <span className="wallet-plan-number">
                    PLAN {String(index + 1).padStart(2, '0')}
                  </span>
                  {index === 1 && <span className="wallet-recommended">日常推荐</span>}
                  <span className="wallet-package-top">
                    <span>{p.name}</span>
                    <span className="wallet-package-check">
                      {chosen?.id === p.id && <Check size={13} />}
                    </span>
                  </span>
                  <Coins size={27} strokeWidth={1.3} />
                  <strong>
                    {number(p.stars)}
                    <small>星币</small>
                  </strong>
                  <span className="wallet-package-price">
                    ¥<b>{money(p.amount_cents)}</b>
                  </span>
                  <span className="wallet-plan-description">
                    {['轻松开启，遇见新灵感', '日常陪伴，让故事延续', '长久相伴，尽兴聊下去'][
                      index
                    ] ?? '为下一段故事准备'}
                  </span>
                  <span className="wallet-unit">
                    约 ¥{((p.amount_cents / 100 / p.stars) * 100).toFixed(2)} / 100 星币
                  </span>
                </button>
              ))}
            </div>
          )}
          <div className="wallet-usage-note">
            <Sparkles size={18} />
            <p>星币用于角色对话，具体消耗以角色卡标价为准。生成失败会退回本次消耗。</p>
          </div>
        </section>
        <aside className="wallet-checkout">
          <div className="wallet-section-title">
            <h2>充值详情</h2>
            <Receipt size={19} />
          </div>
          <dl>
            <div>
              <dt>已选套餐</dt>
              <dd>{chosen?.name ?? '—'}</dd>
            </div>
            <div>
              <dt>到账星币</dt>
              <dd className="wallet-summary-stars">{chosen ? number(chosen.stars) : '—'} 星币</dd>
            </div>
            <div>
              <dt>充值方式</dt>
              <dd>
                {caps.isPending
                  ? '加载中…'
                  : caps.data?.simulatedPayment
                    ? '开发环境模拟支付'
                    : '暂未开放'}
              </dd>
            </div>
          </dl>
          <div className="wallet-checkout-total">
            <span>套餐金额</span>
            <strong>
              <small>¥</small>
              {chosen ? money(chosen.amount_cents) : '—'}
            </strong>
          </div>
          <button
            className="wallet-pay"
            disabled={busy || !chosen || !caps.data?.simulatedPayment}
            onClick={() => void buy()}
          >
            {busy ? '正在确认到账…' : caps.data?.simulatedPayment ? '确认模拟充值' : '充值暂未开放'}
            <ArrowUpRight size={17} />
          </button>
          <p className="wallet-checkout-hint">
            <ShieldCheck size={14} />
            {caps.data?.simulatedPayment
              ? '仅用于功能测试，不会产生真实扣款。'
              : '真实支付接入后开放充值。'}
          </p>
          {caps.isError && (
            <p className="wallet-feedback error" role="alert">
              充值服务状态读取失败，请刷新页面。
            </p>
          )}
          {error && (
            <p className="wallet-feedback error" role="alert">
              {error}
            </p>
          )}
          {success && (
            <p className="wallet-feedback success" role="status">
              <Check size={16} />
              {success}
            </p>
          )}
        </aside>
      </div>
      <section className="wallet-history">
        <div className="wallet-history-heading">
          <div className="wallet-history-tabs" role="tablist" aria-label="钱包记录">
            {[
              ['usage', '消耗统计'],
              ['ledger', '原始流水'],
              ['orders', '充值订单'],
            ].map(([key, label]) => (
              <button role="tab" aria-selected={tab === key} key={key} onClick={() => T(key)}>
                {label}
              </button>
            ))}
          </div>
          <span>{tab === 'usage' ? '日期 × 角色' : '最近 100 条记录'}</span>
        </div>
        {tab === 'usage' ? (
          <UsagePanel />
        ) : tab === 'ledger' ? (
          <>
            <div className="wallet-filters">
              {[
                ['all', '全部'],
                ['in', '入账'],
                ['out', '消耗'],
                ['refund', '退款'],
              ].map(([key, label]) => (
                <button
                  aria-pressed={filter === key}
                  className={filter === key ? 'active' : ''}
                  key={key}
                  onClick={() => F(key)}
                >
                  {label}
                </button>
              ))}
            </div>
            {ledger.isError ? (
              <div className="wallet-empty">
                明细加载失败 <button onClick={() => ledger.refetch()}>重试</button>
              </div>
            ) : ledger.isPending ? (
              <div className="wallet-empty">正在读取星币明细…</div>
            ) : !entries.length ? (
              <div className="wallet-empty">
                <Receipt size={26} />
                <p>暂无相关星币记录</p>
                <small>充值和聊天产生的星币变动会出现在这里。</small>
              </div>
            ) : (
              <div className="wallet-entry-list">
                {entries.map((r) => (
                  <div className="wallet-entry" key={r.id}>
                    <span className={'wallet-entry-icon ' + (r.delta > 0 ? 'incoming' : '')}>
                      {r.delta > 0 ? <ArrowDownLeft size={19} /> : <ArrowUpRight size={19} />}
                    </span>
                    <div className="wallet-entry-copy">
                      <strong>
                        {r.character_name ? r.character_name + ' · ' : ''}
                        {reason(r.reason)}
                      </strong>
                      <time>{date(r.created_at)}</time>
                    </div>
                    <div className="wallet-entry-amount">
                      <strong className={r.delta > 0 ? 'incoming' : ''}>
                        {r.delta > 0 ? '+' : ''}
                        {number(r.delta)}
                      </strong>
                      <small>余额 {number(r.balance_after)}</small>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </>
        ) : orders.isError ? (
          <div className="wallet-empty">
            订单加载失败 <button onClick={() => orders.refetch()}>重试</button>
          </div>
        ) : orders.isPending ? (
          <div className="wallet-empty">正在读取订单…</div>
        ) : !orders.data?.length ? (
          <div className="wallet-empty">
            <Clock3 size={26} />
            <p>还没有充值订单</p>
          </div>
        ) : (
          <div className="wallet-entry-list">
            {orders.data.map((o) => (
              <div className="wallet-entry" key={o.id}>
                <span className="wallet-entry-icon incoming">
                  <Coins size={19} />
                </span>
                <div className="wallet-entry-copy">
                  <strong>
                    {number(o.stars)} 星币 · ¥{money(o.amount_cents)}
                  </strong>
                  <time>{date(o.created_at)}</time>
                  <small>订单 {o.id.slice(0, 8)}</small>
                </div>
                <div className="wallet-order-state">
                  <span className={o.status === 'PAID' ? 'paid' : ''}>
                    {(
                      {
                        PAID: '已到账',
                        PENDING: '待完成',
                        CANCELLED: '已取消',
                        FAILED: '未完成',
                      } as Record<string, string>
                    )[o.status] ?? o.status}
                  </span>
                  {o.status === 'PENDING' && caps.data?.simulatedPayment && (
                    <button disabled={busy} onClick={() => void buy(o)}>
                      继续模拟充值
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </section>
    </main>
  );
}
