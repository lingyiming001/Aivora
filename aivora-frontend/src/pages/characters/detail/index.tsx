import { startCharacterChat } from '@/pages/chat/api';
import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import axios from 'axios';
import { api } from '@/services/request';
import { useAuth } from '@/hooks/useAuth';
import CharacterActions from '@/components/CharacterActions/index';
import { CardCover } from '@/pages/characters/components/CardCover';
import { Button as UiButton } from '@/components/Button/index';
import { getCharacter } from './api';

export function Detail() {
  const loggedIn = useAuth((s) => s.tokens);
  const guest = useQuery({
    queryKey: ['guest-balance'],
    enabled: !loggedIn,
    queryFn: async () =>
      (await api.get<{ active: boolean; balance: number; allowance: number }>('/guest/session'))
        .data,
  });
  const { id } = useParams(),
    nav = useNavigate();
  const card = useQuery({
    queryKey: ['card', id],
    queryFn: () => getCharacter(id),
  });
  const [busy, B] = useState(false),
    [error, E] = useState('');
  async function start() {
    B(true);
    E('');
    try {
      if (!useAuth.getState().tokens) await api.post('/guest/session');
      const { data } = await startCharacterChat(Number(id));
      nav('/chat/' + data.id);
    } catch (e) {
      E(axios.isAxiosError(e) ? e.response?.data?.message || '无法开始对话' : '无法开始对话');
    } finally {
      B(false);
    }
  }
  return (
    <main className="client-page">
      <Link className="back-link" to="/">
        ← 发现角色
      </Link>
      {card.isPending ? (
        <p>正在读取角色…</p>
      ) : card.isError ? (
        <p>角色暂不可用。</p>
      ) : (
        <div className="character-detail">
          <div className="character-detail-cover">
            {card.data && <CardCover card={card.data} />}
          </div>
          <section>
            <p className="eyebrow">MEET YOUR NEXT CONVERSATION</p>
            <h1>{card.data?.name}</h1>
            <p className="client-subtitle">{card.data?.summary}</p>
            <CharacterActions id={Number(id)} />
            <p className="detail-opening">{card.data?.opening}</p>
            <div className="role-cost">{card.data?.stars_per_message} 星币 / 条</div>
            {!loggedIn && (
              <p className="guest-detail-hint">
                无需登录即可聊天 · {guest.data?.active ? '剩余' : '赠送'}{' '}
                {guest.data?.active ? guest.data.balance : (guest.data?.allowance ?? '…')}{' '}
                体验星币。<Link to="/login">登录 →</Link>
              </p>
            )}
            {error && (
              <p className="auth-error" role="alert">
                {error}
              </p>
            )}
            <UiButton disabled={busy} onClick={start}>
              {busy ? '正在连接…' : '开始聊天'}
            </UiButton>
          </section>
        </div>
      )}
    </main>
  );
}
