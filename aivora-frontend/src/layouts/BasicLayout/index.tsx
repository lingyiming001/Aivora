import React from 'react';
import { Dropdown } from 'antd';
import { Link, useNavigate, useLocation, NavLink, Outlet } from 'react-router-dom';
import { useAdminSession } from '@/hooks/useAdminSession';
import axios from 'axios';
import { useAuth } from '@/hooks/useAuth';
import { UserBadge } from '@/layouts/components/UserBadge';
import { BudgetBell } from '@/layouts/components/BudgetBell';
import {
  Compass,
  MessageCircle,
  UserRound,
  Wallet as WalletIcon,
  Plus,
  ShieldCheck,
  LayoutDashboard,
  Users,
  ScrollText,
  LogOut,
  Receipt,
  TriangleAlert,
  Network,
  Settings,
} from 'lucide-react';
import ThemeSwitch from '@/layouts/components/ThemeSwitch';

export const adminGroup = (path?: string) =>
  !path
    ? ''
    : ['access', 'roles', 'permissions', 'audit', 'status'].includes(path.split('/')[2])
      ? '系统管理'
      : ['orders', 'ledger', 'reconciliation'].includes(path.split('/')[2])
        ? '财务管理'
        : '平台运营';

export function Layout({ admin = false }: { admin?: boolean }) {
  const auth = useAuth();
  const nav = useNavigate();
  const location = useLocation();
  const session = useAdminSession(admin);
  const defaultLinks = admin
    ? ([
        ['/admin', '运营概览', LayoutDashboard],
        ['/admin/users', '用户管理', Users],
        ['/admin/review', '角色卡管理', UserRound],
        ['/admin/models', '模型与路由', Network],
        ['/admin/orders', '充值订单', Receipt],
        ['/admin/ledger', '钱包流水', WalletIcon],
        ['/admin/reconciliation', '异常对账', TriangleAlert],
        ['/admin/access', '管理员管理', Users],
        ['/admin/roles', '角色管理', ShieldCheck],
        ['/admin/permissions', '菜单权限', Network],
        ['/admin/audit', '操作日志', ScrollText],
        ['/admin/status', '系统状态', Settings],
      ] as const)
    : ([
        ['/', '发现角色', Compass],
        ['/chats', '我的对话', MessageCircle],
        ['/live', '直播与开放能力', Network],
        ['/favorites', '我的收藏', UserRound],
        ['/my-characters', '我的角色', UserRound],
        ['/wallet', '星币钱包', WalletIcon],
        ['/create', '创建角色', Plus],
      ] as const);
  const links =
    admin && session.data?.menuCatalog
      ? session.data.menuCatalog.map(
          (menu) =>
            [
              menu.path,
              menu.name,
              defaultLinks.find(([to]) => to === menu.path)?.[2] ?? Network,
            ] as const,
        )
      : defaultLinks;
  const groupFor = (path?: string) =>
    session.data?.menuCatalog?.find((m) => m.path === path)?.group_name ?? adminGroup(path);
  async function logout() {
    const t = admin ? auth.adminTokens : auth.tokens;
    try {
      if (t) await axios.post('/api/auth/logout', { refreshToken: t.refreshToken });
    } finally {
      auth.setTokens(null, admin ? 'admin' : 'user');
      nav(admin ? '/admin/login' : '/login');
    }
  }
  return (
    <div className={'app-shell ' + (admin ? 'admin-shell' : '')}>
      <aside className="sidebar">
        <Link className="brand" to={admin ? '/admin' : '/'}>
          <span className="brand-icon">
            <img src="/images/aivora-logo.png" alt="Aivora Logo" width={64} height={64} />
          </span>
          <span>
            Aivora<small>{admin ? '运营工作台' : 'AIVORA'}</small>
          </span>
        </Link>
        <p className="sidebar-caption">{admin ? '平台管理' : '你的灵感，在这里相遇'}</p>
        <nav>
          {links
            .filter(([to]) =>
              admin
                ? session.data?.menus?.includes(to.split('/')[2] || 'overview')
                : !!auth.tokens || to === '/' || to === '/chats',
            )
            .map(([to, label, Icon], index, items) => (
              <React.Fragment key={to}>
                {admin && groupFor(to) !== groupFor(items[index - 1]?.[0]) && (
                  <span className="nav-group">{groupFor(to)}</span>
                )}
                <NavLink end to={to}>
                  <Icon size={21} />
                  {label}
                </NavLink>
              </React.Fragment>
            ))}
        </nav>
        <div className="sidebar-bottom">
          {admin ? (
            <>
              <button onClick={logout}>
                <LogOut size={18} />
                退出登录
              </button>
            </>
          ) : auth.tokens ? (
            <UserBadge />
          ) : (
            <Link to="/login">
              <UserRound size={18} />
              用户登录
            </Link>
          )}
          {!admin && <ThemeSwitch />}
          <p>
            给想象一个回应<small>遇见新视角，开启新故事。</small>
          </p>
        </div>
      </aside>
      <div className="workspace">
        {admin && (
          <header className="admin-top">
            <div>
              <span className="admin-breadcrumb">Aivora工作台 / {groupFor(location.pathname)}</span>
              <strong>
                {links.find(([to]) => to === location.pathname)?.[1] ??
                  (location.pathname === '/admin/account' ? '我的账号' : '管理后台')}
              </strong>
            </div>
            <div className="admin-top-session">
              <BudgetBell enabled={!!session.data?.menus?.includes('status')} />
              <ShieldCheck size={16} />
              <span>{session.data?.superAdmin ? '超级管理员' : '管理员会话'}</span>
              <Dropdown
                trigger={['click']}
                menu={{
                  items: [
                    { key: 'profile', label: '个人资料', onClick: () => nav('/admin/account') },
                    {
                      key: 'password',
                      label: '修改密码',
                      onClick: () => nav('/admin/account?tab=security'),
                    },
                    {
                      key: 'phone',
                      label: '手机绑定 / 换绑',
                      onClick: () => nav('/admin/account?tab=phone'),
                    },
                    { type: 'divider' },
                    { key: 'logout', label: '退出登录', onClick: () => void logout() },
                  ],
                }}
              >
                <button type="button" className="admin-account-trigger" aria-label="管理员账号菜单">
                  {session.data?.avatar_url ? (
                    <img src={session.data.avatar_url} alt="管理员头像" />
                  ) : (
                    <span className="admin-session-avatar">管</span>
                  )}
                  <span>{session.data?.nickname ?? '我的账号'}</span>
                </button>
              </Dropdown>
            </div>
          </header>
        )}
        <Outlet />
        {admin && (
          <footer className="admin-footer">
            <span>Aivora · 运营管理工作台</span>
            <span>让每一次管理，都有迹可循。</span>
          </footer>
        )}
      </div>
    </div>
  );
}
