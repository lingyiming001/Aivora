import { useConfirm, ConfirmDialog } from '@/components/ConfirmDialog';
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import {
  Eraser,
  Trash2,
  Plus,
  MessageCircle,
  Search,
  MoreHorizontal,
  ArrowRight,
  ListChecks,
} from 'lucide-react';
import { Dropdown } from 'antd';
import { api } from '@/services/request';
import { useAuth } from '@/hooks/useAuth';
import { Avatar } from '@/components/Avatar/index';
import { explain } from '@/utils/actionError';

export function Conversations() {
  const confirm = useConfirm();
  const [search, SEARCH] = useState('');
  const token = useAuth((s) => s.tokens);
  const guest = useQuery({
    queryKey: ['guest-balance'],
    enabled: !token,
    queryFn: async () => (await api.get<{ active: boolean }>('/guest/session')).data,
  });
  const [error, E] = useState(''),
    [busy, B] = useState<number | null>(null),
    [managing, MAN] = useState(false),
    [selected, S] = useState<number[]>([]),
    [deleting, D] = useState(false),
    [notice, N] = useState(''),
    [confirming, CONFIRM] = useState(false);
  const rows = useQuery({
    queryKey: ['my-chats', token?.accessToken, guest.data?.active],
    enabled: !!token || guest.isSuccess,
    queryFn: async () =>
      !token && !guest.data?.active
        ? []
        : (
            await api.get<
              Array<{
                id: number;
                name: string;
                avatar_url?: string;
                last_message?: string;
                updated_at: string;
              }>
            >('/chats')
          ).data,
  });
  const ids = selected.filter((id) => rows.data?.some((r) => r.id === id));
  const blocked = busy !== null || deleting;
  const visible =
    rows.data?.filter((r) =>
      (r.name + ' ' + (r.last_message || '')).toLowerCase().includes(search.trim().toLowerCase()),
    ) || [];
  const all = !!visible.length && visible.slice(0, 100).every((r) => ids.includes(r.id));
  function toggle(id: number) {
    S((old) =>
      old.includes(id) ? old.filter((x) => x !== id) : old.length < 100 ? [...old, id] : old,
    );
  }
  async function batch() {
    if (!ids.length || blocked) return;
    D(true);
    E('');
    N('');
    try {
      const { data } = await api.post<{ deleted: number }>('/chats/batch-delete', {
        ids,
        confirmed: true,
      });
      S([]);
      MAN(false);
      CONFIRM(false);
      await rows.refetch();
      N('已删除 ' + data.deleted + ' 段对话');
    } catch (e) {
      E(explain(e));
    } finally {
      D(false);
    }
  }
  async function act(id: number, remove: boolean) {
    if (
      blocked ||
      !(await confirm({
        title: remove ? '删除这段对话？' : '清空这段对话？',
        description: remove
          ? '这段对话及消息记录将从你的对话列表中移除。'
          : '消息记录将被清空，下一次回复会重新开始。',
        note: '已消耗的星币不会退还，请确认后继续。',
        confirmLabel: remove ? '确认删除' : '清空对话',
        danger: true,
      }))
    )
      return;
    B(id);
    E('');
    N('');
    try {
      if (remove) await api.delete('/chats/' + id);
      else await api.post('/chats/' + id + '/clear');
      S((old) => old.filter((x) => x !== id));
      await rows.refetch();
    } catch (e) {
      E(explain(e));
    } finally {
      B(null);
    }
  }
  return (
    <main className="client-page inbox-page">
      <ConfirmDialog
        title="批量删除对话？"
        description={`所选的 ${ids.length} 段对话及消息记录将从列表中移除。`}
        note="已消耗的星币不会退还，删除后无法恢复。"
        open={confirming}
        onCancel={() => !deleting && CONFIRM(false)}
        onConfirm={() => void batch()}
        loading={deleting}
        confirmLabel="确认删除"
        disabled={!ids.length}
        danger
      >
        {error && <p role="alert">{error}</p>}
      </ConfirmDialog>
      <header className="inbox-heading">
        <div>
          <span className="inbox-kicker">
            <span />
            你的故事收藏夹
          </span>
          <h1>
            我的对话<span>{rows.data?.length ?? 0}</span>
          </h1>
          <p>回到熟悉的声音，让故事继续。</p>
        </div>
        <Link className="inbox-new" to="/">
          <Plus size={16} />
          开启新对话
        </Link>
      </header>
      <section className="inbox-surface">
        <div className="inbox-toolbar">
          <div className="inbox-tab">
            <MessageCircle size={16} />
            <span>全部对话</span>
            <small>{rows.data?.length ?? 0}</small>
          </div>
          <div className="inbox-tools">
            <label className="inbox-search">
              <Search size={15} />
              <input
                aria-label="搜索对话"
                value={search}
                onChange={(e) => {
                  SEARCH(e.target.value);
                  S([]);
                }}
                placeholder="搜索角色或消息…"
              />
              {search && (
                <button
                  type="button"
                  onClick={() => {
                    SEARCH('');
                    S([]);
                  }}
                  aria-label="清除搜索"
                >
                  ×
                </button>
              )}
            </label>
            {!!rows.data?.length && (
              <button
                className="batch-manage"
                disabled={blocked}
                onClick={() => {
                  MAN(!managing);
                  S([]);
                  E('');
                  N('');
                }}
              >
                <ListChecks size={15} />
                {managing ? '完成' : '管理'}
              </button>
            )}
          </div>
        </div>
        {managing && (
          <div className="conversation-batch-toolbar">
            <label>
              <input
                type="checkbox"
                aria-label="全选当前结果（最多100段）"
                checked={all}
                ref={(el) => {
                  if (el) el.indeterminate = ids.length > 0 && !all;
                }}
                disabled={blocked}
                onChange={() => S(all ? [] : visible.slice(0, 100).map((r) => r.id))}
              />
              全选
            </label>
            <span>
              已选 {ids.length} 段 <small>每次最多 100 段</small>
            </span>
            <button
              className="batch-delete"
              disabled={!ids.length || blocked}
              onClick={() => CONFIRM(true)}
            >
              <Trash2 size={16} />
              {deleting ? '正在删除…' : '删除所选'}
            </button>
          </div>
        )}
        {error && (
          <p className="auth-error" role="alert">
            {error}
          </p>
        )}
        {notice && (
          <p className="batch-success" role="status">
            {notice}
          </p>
        )}
        {!token && guest.isError ? (
          <p>
            暂时无法读取游客会话。<button onClick={() => guest.refetch()}>重试</button>
          </p>
        ) : rows.isError ? (
          <p>
            暂时无法读取对话。<button onClick={() => rows.refetch()}>重试</button>
          </p>
        ) : rows.isPending ? (
          <p className="muted">正在读取你的故事…</p>
        ) : !rows.data?.length ? (
          <section className="client-empty">
            <span className="inbox-empty-icon">
              <MessageCircle size={27} />
            </span>
            <h2>第一段故事，从这里开始</h2>
            <p>遇见喜欢的角色，留下值得继续的对话。</p>
            <Link to="/">发现角色 →</Link>
          </section>
        ) : !visible.length ? (
          <section className="client-empty">
            <Search size={26} />
            <h2>没有找到这段故事</h2>
            <p>试试角色名字，或者换一个关键词。</p>
            <button onClick={() => SEARCH('')}>查看全部对话</button>
          </section>
        ) : (
          <div className={'conversation-list ' + (managing ? 'selecting' : '')}>
            {visible.map((r) => (
              <article
                className={'conversation-row ' + (ids.includes(r.id) ? 'is-selected' : '')}
                key={r.id}
              >
                {managing && (
                  <label className="conversation-select">
                    <input
                      type="checkbox"
                      aria-label={'选择与' + r.name + '的对话 ' + r.id}
                      checked={ids.includes(r.id)}
                      disabled={blocked || (!ids.includes(r.id) && ids.length >= 100)}
                      onChange={() => toggle(r.id)}
                    />
                  </label>
                )}
                <div
                  className="inbox-avatar"
                  style={{ background: ['#e9efe5', '#eee8de', '#e8ebf2', '#eee6eb'][r.id % 4] }}
                >
                  {r.avatar_url ? (
                    <Avatar src={r.avatar_url} name={r.name} />
                  ) : (
                    <span>{r.name.slice(0, 1)}</span>
                  )}
                </div>
                <Link className="conversation-copy" to={'/chat/' + r.id}>
                  <h2>{r.name}</h2>
                  <p>{r.last_message || '还没有开始的故事，等你来写。'}</p>
                </Link>
                <time dateTime={r.updated_at}>
                  {new Date(r.updated_at).toLocaleDateString('zh-CN', {
                    timeZone: 'Asia/Shanghai',
                    month: 'long',
                    day: 'numeric',
                  })}
                </time>
                {!managing && (
                  <div className="conversation-actions">
                    <Link
                      className="inbox-continue"
                      aria-label={'继续与' + r.name + '对话'}
                      to={'/chat/' + r.id}
                    >
                      <span>继续对话</span>
                      <ArrowRight size={15} />
                    </Link>
                    <Dropdown
                      trigger={['click']}
                      menu={{
                        items: [
                          {
                            key: 'clear',
                            icon: <Eraser size={15} />,
                            label: '清空对话',
                            disabled: blocked,
                            onClick: () => void act(r.id, false),
                          },
                          {
                            key: 'delete',
                            icon: <Trash2 size={15} />,
                            label: '删除对话',
                            danger: true,
                            disabled: blocked,
                            onClick: () => void act(r.id, true),
                          },
                        ],
                      }}
                    >
                      <button
                        className="inbox-more"
                        disabled={blocked}
                        aria-label={'更多操作：' + r.name}
                      >
                        <MoreHorizontal size={19} />
                      </button>
                    </Dropdown>
                  </div>
                )}
              </article>
            ))}
          </div>
        )}
      </section>
      <p className="inbox-footnote">每一次相遇，都值得下一句。</p>
    </main>
  );
}
