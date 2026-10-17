import { useConfirm } from '@/components/ConfirmDialog';
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { Trash2, Plus, Sparkles, Pencil, Coins, LockKeyhole } from 'lucide-react';
import { api } from '@/services/request';
import { type Character } from '@/pages/characters/types';
import { Avatar } from '@/components/Avatar/index';
import { explain } from '@/utils/actionError';

export function MyCharacters() {
  const confirm = useConfirm();
  const nav = useNavigate(),
    [error, E] = useState(''),
    [busy, B] = useState<number | null>(null);
  const rows = useQuery({
    queryKey: ['my-characters'],
    queryFn: async () => (await api.get<Character[]>('/characters/mine')).data,
  });
  async function action(c: Character, kind: string) {
    if (
      (kind === 'delete' || kind === 'submit') &&
      !(await confirm({
        title: kind === 'delete' ? '删除这个角色？' : '提交公开审核？',
        description:
          kind === 'delete'
            ? `“${c.name}”将从你的角色列表中移除。`
            : `“${c.name}”将在审核通过后出现在角色广场，供其他用户发现和聊天。`,
        note: kind === 'delete' ? '删除后无法恢复，请确认后继续。' : '提交后请等待审核结果。',
        confirmLabel: kind === 'delete' ? '确认删除' : '提交审核',
        danger: kind === 'delete',
      }))
    )
      return;
    B(c.id);
    E('');
    try {
      if (kind === 'chat') {
        const { data } = await api.post('/chats', { characterId: c.id });
        nav('/chat/' + data.id);
      } else {
        if (kind === 'delete') await api.delete('/characters/' + c.id);
        else await api.post('/characters/' + c.id + '/submit');
        await rows.refetch();
      }
    } catch (e) {
      E(explain(e));
    } finally {
      B(null);
    }
  }
  return (
    <main className="client-page owned-page">
      <p className="eyebrow">YOUR OWN LITTLE UNIVERSE</p>
      <div className="client-title-row">
        <div>
          <h1>
            我的角色 <small className="owned-count">{rows.data?.length ?? 0}</small>
          </h1>
          <p className="client-subtitle">你创造的世界，在这里生长。</p>
        </div>
        <Link className="solid-link" to="/create">
          <Plus size={18} />
          创建角色
        </Link>
      </div>
      {error && (
        <p className="auth-error" role="alert">
          {error}
        </p>
      )}
      {rows.isPending ? (
        <p>正在读取…</p>
      ) : rows.isError ? (
        <p>加载失败，请稍后重试。</p>
      ) : !rows.data?.length ? (
        <section className="client-empty">
          <Sparkles size={34} />
          <p>让你的想象，有自己的声音。</p>
          <Link to="/create">创建第一个私密角色 →</Link>
        </section>
      ) : (
        <div className="owned-characters">
          {rows.data.map((c) => (
            <section className="owned-character" key={c.id}>
              <div className="owned-character-body">
                <div className="owned-character-heading">
                  <Avatar src={c.avatar_url} name={c.name} />
                  <div>
                    <span className="visibility-label">
                      {c.status === 'OFFLINE' ? (
                        '已下架 · 待修改'
                      ) : c.visibility === 'PRIVATE' ? (
                        <>
                          <LockKeyhole size={12} />
                          仅自己可见
                        </>
                      ) : c.status === 'REVIEW' ? (
                        '公开投稿 · 审核中'
                      ) : (
                        '公开角色'
                      )}
                    </span>
                    <h2 title={c.name}>{c.name}</h2>
                  </div>
                </div>
                <p className="owned-summary" title={c.summary}>
                  {c.summary || '还没有填写角色介绍'}
                </p>
                {c.review_note && (
                  <p role="status" className="auth-error">
                    {c.status === 'OFFLINE' ? '角色已下架：' : '投稿未通过：'}
                    {c.review_note}
                  </p>
                )}
                <div className="role-cost">
                  <Coins size={16} />
                  {c.stars_per_message} 星币 / 条
                </div>
                <div className="owned-actions">
                  <button
                    disabled={busy === c.id || c.status === 'OFFLINE'}
                    className="solid-link"
                    onClick={() => action(c, 'chat')}
                  >
                    {c.status === 'OFFLINE' ? '已停用' : '开始对话'}
                  </button>
                  {c.visibility === 'PRIVATE' && (
                    <Link aria-label={'编辑' + c.name} to={'/characters/' + c.id + '/edit'}>
                      <Pencil size={17} />
                    </Link>
                  )}
                  <button
                    disabled={busy === c.id}
                    aria-label={'删除' + c.name}
                    onClick={() => action(c, 'delete')}
                  >
                    <Trash2 size={17} />
                  </button>
                </div>
                {c.visibility === 'PRIVATE' && (
                  <button
                    disabled={busy === c.id}
                    className="submission-link"
                    onClick={() => action(c, 'submit')}
                  >
                    {c.status === 'OFFLINE' ? '修改后重新提交审核 →' : '投稿至公开角色广场 →'}
                  </button>
                )}
              </div>
            </section>
          ))}
        </div>
      )}
    </main>
  );
}
