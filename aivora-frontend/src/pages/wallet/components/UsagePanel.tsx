import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { MessageCircle, ChevronDown, ArrowUpRight, ArrowDownLeft } from 'lucide-react';
import { api } from '@/services/request';
import { type Group, type Entry } from '@/pages/wallet/components/types';

export const n = (v: number) => Number(v).toLocaleString('zh-CN');

export default function UsagePanel() {
  const [days, D] = useState(30),
    [selected, S] = useState<Group | null>(null),
    [page, P] = useState(0);
  const q = useQuery({
    queryKey: ['wallet-usage', days],
    queryFn: async () => (await api.get<Group[]>('/wallet/usage', { params: { days } })).data,
  });
  const details = useQuery({
    queryKey: ['wallet-usage-detail', selected?.day, selected?.character_id, page],
    enabled: !!selected,
    queryFn: async () =>
      (
        await api.get<Entry[]>('/wallet/usage/details', {
          params: { day: selected!.day, character: selected!.character_id, page },
        })
      ).data,
  });
  const totals = (q.data || []).reduce(
    (a, g) => ({
      spent: a.spent + Number(g.spent),
      refunded: a.refunded + Number(g.refunded),
      messages: a.messages + Number(g.messages),
    }),
    { spent: 0, refunded: 0, messages: 0 },
  );
  const characters = new Set(q.data?.filter((g) => g.character_id > 0).map((g) => g.character_id))
    .size;
  return (
    <div className="usage-panel">
      <div className="usage-heading">
        <div>
          <h2>对话消耗，一目了然</h2>
          <p>按日期与角色汇总，退款单独列出。</p>
        </div>
        <div className="usage-period" role="group" aria-label="统计周期">
          {[7, 30, 90].map((v) => (
            <button
              key={v}
              aria-pressed={v === days}
              onClick={() => {
                D(v);
                S(null);
                P(0);
              }}
            >
              近{v}天
            </button>
          ))}
        </div>
      </div>
      <div className="usage-stats">
        {[
          ['净消耗', totals.spent - totals.refunded, '星币'],
          ['已退回', totals.refunded, '星币'],
          ['发送消息', totals.messages, '次'],
          ['聊天角色', characters, '位'],
        ].map(([label, value, unit]) => (
          <div key={label}>
            <span>{label}</span>
            <strong>
              {q.isPending ? '…' : q.isError ? '—' : n(value as number)}
              <small>{unit}</small>
            </strong>
          </div>
        ))}
      </div>
      <p className="usage-caption">
        按北京时间统计实际扣费与退款；净消耗＝扣费－退款。失败退款保留记录，不计入净消耗。
      </p>
      {q.isPending ? (
        <div className="wallet-empty">正在统计对话消耗…</div>
      ) : q.isError ? (
        <div className="wallet-empty" role="alert">
          统计加载失败 <button onClick={() => q.refetch()}>重试</button>
        </div>
      ) : !q.data?.length ? (
        <div className="wallet-empty">
          <MessageCircle size={26} />
          <p>这段时间还没有对话消耗</p>
          <Link to="/">发现角色 →</Link>
        </div>
      ) : (
        <div className="usage-groups">
          {q.data.map((g) => {
            const open = selected?.day === g.day && selected?.character_id === g.character_id;
            return (
              <article
                className={'usage-group ' + (open ? 'expanded' : '')}
                key={g.day + '-' + g.character_id}
              >
                <button
                  className="usage-group-heading"
                  aria-expanded={open}
                  onClick={() => {
                    S(open ? null : g);
                    P(0);
                  }}
                >
                  <span className="usage-avatar">
                    <MessageCircle size={18} />
                  </span>
                  <span className="usage-group-copy">
                    <strong>{g.character_name}</strong>
                    <small>
                      {g.day} · {n(g.messages)} 次发送 · {n(g.sessions)} 段对话
                    </small>
                  </span>
                  <span className="usage-group-cost">
                    <strong>
                      {Number(g.net) >= 0 ? '-' : '+'}
                      {n(Math.abs(g.net))}
                      <small>星币</small>
                    </strong>
                    <small>
                      扣费 {n(g.spent)}
                      {Number(g.refunded) > 0 ? ' · 退回 ' + n(g.refunded) : ''}
                    </small>
                  </span>
                  <ChevronDown className={open ? 'rotated' : ''} size={15} />
                </button>
                {open && (
                  <div className="usage-detail">
                    {details.isPending ? (
                      <p>正在读取明细…</p>
                    ) : details.isError ? (
                      <p role="alert">
                        明细加载失败 <button onClick={() => details.refetch()}>重试</button>
                      </p>
                    ) : (
                      details.data?.map((r) => (
                        <div key={r.id}>
                          <span>
                            {r.delta < 0 ? <ArrowUpRight size={13} /> : <ArrowDownLeft size={13} />}
                            <time>
                              {new Date(r.created_at).toLocaleTimeString('zh-CN', {
                                timeZone: 'Asia/Shanghai',
                                hour: '2-digit',
                                minute: '2-digit',
                                second: '2-digit',
                              })}
                            </time>
                            <small>
                              {r.reason === 'CHAT_REFUND'
                                ? '失败退回'
                                : r.request_status === 'STARTED'
                                  ? '回复中'
                                  : r.request_status === 'FAILED'
                                    ? '发送失败，已退款'
                                    : '发送消息'}
                            </small>
                          </span>
                          {r.session_id && <Link to={'/chat/' + r.session_id}>查看对话</Link>}
                          <strong>
                            {r.delta > 0 ? '+' : ''}
                            {n(r.delta)}
                          </strong>
                        </div>
                      ))
                    )}
                    {details.data && Number(details.data[0]?.total || 0) > 50 && (
                      <nav className="usage-pages">
                        <button disabled={page === 0} onClick={() => P(page - 1)}>
                          上一页
                        </button>
                        <span>
                          第{page + 1}页 · 共{n(details.data[0].total)}条
                        </span>
                        <button
                          disabled={(page + 1) * 50 >= Number(details.data[0].total)}
                          onClick={() => P(page + 1)}
                        >
                          下一页
                        </button>
                      </nav>
                    )}
                    <p className="usage-detail-note">
                      逐条账目用于核对扣费与退款；清空或删除对话不会删除账目。
                    </p>
                  </div>
                )}
              </article>
            );
          })}
        </div>
      )}
    </div>
  );
}
