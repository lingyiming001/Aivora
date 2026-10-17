import { startCharacterChat } from '@/pages/chat/api';
import { useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { Bookmark, ArrowRight, MessageCircle, Search, Plus } from 'lucide-react';
import axios from 'axios';
import { getFavorites } from './api';
import CharacterActions from '@/components/CharacterActions/index';

export default function FavoritesPage() {
  const nav = useNavigate(),
    running = useRef(false);
  const [search, S] = useState(''),
    [busy, B] = useState<number | null>(null),
    [error, E] = useState('');
  const q = useQuery({
    queryKey: ['favorites'],
    queryFn: getFavorites,
  });
  const visible =
    q.data?.filter((c) =>
      (c.name + ' ' + c.summary).toLowerCase().includes(search.trim().toLowerCase()),
    ) || [];
  async function start(id: number) {
    if (running.current) return;
    running.current = true;
    B(id);
    E('');
    try {
      const { data } = await startCharacterChat(id);
      nav('/chat/' + data.id);
    } catch (e) {
      E(
        axios.isAxiosError(e)
          ? e.response?.data?.message || '无法开始聊天，请重试'
          : '无法开始聊天，请重试',
      );
    } finally {
      running.current = false;
      B(null);
    }
  }
  return (
    <main className="client-page favorites-page">
      <header className="inbox-heading">
        <div>
          <span className="inbox-kicker">
            <Bookmark size={12} />
            值得再次相遇
          </span>
          <h1>
            我的收藏<span>{q.data?.length ?? 0}</span>
          </h1>
          <p>喜欢的角色，下一场对话触手可及。</p>
        </div>
        <Link className="inbox-new" to="/">
          <Plus size={15} />
          发现新角色
        </Link>
      </header>
      <div className="favorites-toolbar">
        <span>{visible.length} 位角色</span>
        <label className="inbox-search">
          <Search size={15} />
          <input
            aria-label="搜索收藏角色"
            value={search}
            onChange={(e) => S(e.target.value)}
            placeholder="搜索收藏的角色…"
          />
        </label>
      </div>
      {error && (
        <p className="auth-error" role="alert">
          {error}
        </p>
      )}
      {q.isPending ? (
        <p>正在读取收藏…</p>
      ) : q.isError ? (
        <div className="panel">
          收藏加载失败 <button onClick={() => q.refetch()}>重试</button>
        </div>
      ) : !visible.length ? (
        <div className="client-empty">
          <Bookmark size={30} />
          <p>{search ? '没有匹配的收藏角色' : '还没有收藏角色。'}</p>
          <Link to="/">发现角色 →</Link>
        </div>
      ) : (
        <div className="favorite-grid">
          {visible.map((c) => (
            <article className="favorite-card favorite-compact" key={c.id}>
              <button
                type="button"
                className="favorite-intro"
                aria-label={'与' + c.name + '聊天'}
                disabled={busy !== null}
                onClick={() => void start(c.id)}
              >
                <span className="favorite-portrait">
                  {c.avatar_url ? <img src={c.avatar_url} alt="" /> : <Bookmark size={28} />}
                </span>
                <span className="favorite-copy">
                  <strong>{c.name}</strong>
                  <span className="favorite-description">{c.summary}</span>
                  <small>{c.stars_per_message} 星币 / 条</small>
                </span>
              </button>
              <div className="favorite-chat-row">
                <Link to={'/characters/' + c.id}>角色详情</Link>
                <button type="button" disabled={busy !== null} onClick={() => void start(c.id)}>
                  <MessageCircle size={14} />
                  {busy === c.id ? '正在连接…' : '开始聊天'}
                  <ArrowRight size={14} />
                </button>
              </div>
              <CharacterActions id={c.id} />
            </article>
          ))}
        </div>
      )}
    </main>
  );
}
