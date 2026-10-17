import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Dropdown } from 'antd';
import { UserRound, ChevronDown, Bell, LogOut } from 'lucide-react';
import { api } from '@/services/request';
import { useAuth } from '@/hooks/useAuth';
import { useProfile } from '@/pages/profile/hooks';
import { levels } from '@/constants/user';

export function UserBadge() {
  const q = useProfile(),
    nav = useNavigate(),
    [busy, B] = useState(false);
  async function logout() {
    if (busy) return;
    B(true);
    const tokens = useAuth.getState().tokens;
    try {
      if (tokens) await api.post('/auth/logout', { refreshToken: tokens.refreshToken });
    } catch {
      /* Always end the local session. */
    } finally {
      useAuth.getState().setTokens(null, 'user');
      B(false);
      nav('/login');
    }
  }
  return (
    <Dropdown
      trigger={['click']}
      placement="topLeft"
      menu={{
        items: [
          {
            key: 'profile',
            icon: <UserRound size={17} />,
            label: <Link to="/profile">个人中心</Link>,
          },
          { key: 'messages', icon: <Bell size={17} />, label: <Link to="/messages">消息</Link> },
          { type: 'divider' },
          {
            key: 'logout',
            icon: <LogOut size={17} />,
            label: busy ? '正在退出…' : '退出登录',
            disabled: busy,
            onClick: () => void logout(),
          },
        ],
      }}
    >
      <button type="button" className="user-badge" aria-label="打开头像菜单" aria-haspopup="menu">
        <img src={q.data?.avatar_url || '/images/profile-1.svg'} alt="" />
        <span>
          <strong>{q.data?.nickname || '我的账号'}</strong>
          <small>{q.data ? levels[q.data.membership_tier] : '已登录'}</small>
        </span>
        <ChevronDown size={16} />
      </button>
    </Dropdown>
  );
}
