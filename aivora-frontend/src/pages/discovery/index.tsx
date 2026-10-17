import { startCharacterChat } from '@/pages/chat/api';
import { useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import axios from 'axios';
import { api } from '@/services/request';
import { useAuth } from '@/hooks/useAuth';
import CharacterActions from '@/components/CharacterActions/index';
import { Sparkles, MessageCircle, Search, ArrowUpRight, FileText } from 'lucide-react';
import { getCharacters } from './api';
import { categories } from '@/constants/character';
import { CardCover } from '@/pages/characters/components/CardCover';

export function Square() {
  const nav = useNavigate(),
    starting = useRef(false);
  const [q, Q] = useState(''),
    [category, C] = useState(''),
    [busy, B] = useState<number | null>(null),
    [error, E] = useState('');
  async function start(id: number) {
    if (starting.current) return;
    starting.current = true;
    B(id);
    E('');
    try {
      if (!useAuth.getState().tokens) await api.post('/guest/session');
      const { data } = await startCharacterChat(id);
      nav('/chat/' + data.id);
    } catch (e) {
      E(
        axios.isAxiosError(e)
          ? e.response?.data?.message || '无法开始对话，请重试'
          : '无法开始对话，请重试',
      );
    } finally {
      starting.current = false;
      B(null);
    }
  }
  const cards = useQuery({
    queryKey: ['cards', q, category],
    queryFn: () => getCharacters(q, category),
  });
  return (
    <main className="discovery">
      <p className="eyebrow">MEET YOUR NEXT CONVERSATION</p>
      <h1>
        每一种想象，都有回响<span>。</span>
      </h1>
      <p className="subtitle">一个好奇的问题，一段未完的故事。找到与你同频的 AI 角色。</p>
      <section className="hero">
        <p>
          <Sparkles size={18} /> 本周灵感精选
        </p>
        <h2>
          世界很大，
          <br />
          从一场对话开始。
        </h2>
        <button
          className="hero-start"
          disabled={busy !== null || !cards.data?.length}
          onClick={() => cards.data?.[0] && void start(cards.data[0].id)}
        >
          写一个新故事 <ArrowUpRight size={20} />
        </button>
      </section>
      <div className="section-heading">
        <h2>
          找到你的聊天搭子 <small>{cards.data?.length ?? 0}</small>
        </h2>
        <label className="search">
          <Search size={20} />
          <input
            aria-label="搜索角色"
            placeholder="搜索名字、兴趣或灵感"
            value={q}
            onChange={(e) => Q(e.target.value)}
          />
        </label>
      </div>
      <div className="category-tabs">
        {['', ...categories].map((c) => (
          <button key={c} className={c === category ? 'active' : ''} onClick={() => C(c)}>
            {c || '全部'}
          </button>
        ))}
      </div>
      {error && (
        <p className="auth-error" role="alert">
          {error}
        </p>
      )}
      {cards.isPending ? (
        <p className="muted">正在寻找有趣的角色…</p>
      ) : cards.isError ? (
        <div className="panel">
          角色加载失败 <button onClick={() => cards.refetch()}>重新加载</button>
        </div>
      ) : !cards.data?.length ? (
        <div className="panel">暂时没有匹配的角色，换个关键词试试。</div>
      ) : (
        <div className="character-grid">
          {cards.data.map((c) => (
            <article className="character-card" key={c.id}>
              <button
                type="button"
                className="character-open"
                disabled={busy !== null}
                aria-label={'与' + c.name + '开始对话'}
                aria-busy={busy === c.id}
                onClick={() => void start(c.id)}
              >
                <CardCover card={c} />
                <div className="character-info">
                  <h3>
                    {c.name}
                    <ArrowUpRight size={18} />
                  </h3>
                  <p>{c.summary}</p>
                  <div className="discovery-cost">{c.stars_per_message} 星币 / 条</div>
                </div>
              </button>
              <div className="character-card-links">
                <Link to={'/characters/' + c.id} aria-label={'查看' + c.name + '详情'}>
                  <FileText size={15} />
                  查看详情
                </Link>
                <button
                  type="button"
                  disabled={busy !== null}
                  onClick={() => void start(c.id)}
                  aria-label={'开始与' + c.name + '聊天'}
                  aria-busy={busy === c.id}
                >
                  <MessageCircle size={15} />
                  {busy === c.id ? '正在连接…' : '开启对话'}
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
