import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Bell } from 'lucide-react';
import { api } from '@/services/request';
import { type Character } from '@/pages/characters/types';

export default function MessagesPage() {
  const q = useQuery({
    queryKey: ['my-characters'],
    queryFn: async () => (await api.get<Character[]>('/characters/mine')).data,
  });
  const notices = q.data?.filter((c) => c.status === 'REVIEW' || !!c.review_note) || [];
  return (
    <main className="client-page">
      <p className="eyebrow">YOUR UPDATES</p>
      <h1>消息</h1>
      <p className="client-subtitle">查看角色投稿的审核状态与反馈。</p>
      {q.isPending ? (
        <p className="muted">正在读取消息…</p>
      ) : q.isError ? (
        <div className="panel" role="alert">
          消息加载失败 <button onClick={() => q.refetch()}>重试</button>
        </div>
      ) : !notices.length ? (
        <section className="client-empty">
          <Bell size={32} />
          <p>暂无审核消息。</p>
          <Link to="/my-characters">查看我的角色 →</Link>
        </section>
      ) : (
        <div className="account-notices">
          {notices.map((c) => (
            <article className="panel" key={c.id}>
              <h2>
                {c.name} ·{' '}
                {c.status === 'OFFLINE'
                  ? '已下架'
                  : c.status === 'REVIEW'
                    ? '审核中'
                    : c.visibility === 'PRIVATE'
                      ? '投稿未通过'
                      : '审核反馈'}
              </h2>
              <p>{c.status === 'REVIEW' ? '投稿已提交，请等待审核结果。' : c.review_note}</p>
              <Link to="/my-characters">查看角色 →</Link>
            </article>
          ))}
        </div>
      )}
    </main>
  );
}
