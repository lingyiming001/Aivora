import React from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import axios from 'axios';
import { useAdminSession } from '@/hooks/useAdminSession';
import { useAuth } from '@/hooks/useAuth';

export function Protected({
  children,
  admin = false,
}: {
  children: React.ReactNode;
  admin?: boolean;
}) {
  const t = useAuth((s) => (admin ? s.adminTokens : s.tokens));
  const path = useLocation().pathname;
  const session = useAdminSession(admin);
  const denied =
    axios.isAxiosError(session.error) && [401, 403].includes(session.error.response?.status ?? 0);

  if (!t) return <Navigate replace to={admin ? '/admin/login' : '/login'} />;
  if (admin && !session.data && session.isPending)
    return <div className="admin-session-loading">正在验证管理员会话…</div>;
  if (admin && (denied || (session.data && session.data.role !== 'ADMIN')))
    return <Navigate replace to="/admin/login" />;
  if (admin && !session.data)
    return (
      <main className="admin-main" role="alert">
        <h1>暂时无法读取管理员会话</h1>
        <p>请重试，当前登录状态会保留。</p>
        <button disabled={session.isFetching} onClick={() => void session.refetch()}>
          重新加载
        </button>
      </main>
    );
  if (
    admin &&
    path !== '/admin/account' &&
    !session.data?.menus?.includes(path.split('/')[2] || 'overview')
  )
    return (
      <main className="admin-main">
        <h1>暂无此菜单权限</h1>
        <p>请联系超级管理员分配权限。</p>
        {session.data?.menus
          ?.filter((k) => k !== 'access')
          .map((k) => (
            <p key={k}>
              <Link to={k === 'overview' ? '/admin' : '/admin/' + k}>前往授权菜单：{k}</Link>
            </p>
          ))}
        <Link to="/admin/account">我的账号</Link> · <Link to="/admin/login">返回管理员登录</Link> ·{' '}
        <Link to="/">返回用户端</Link>
      </main>
    );
  return children;
}
