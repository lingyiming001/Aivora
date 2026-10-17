import { UserTiers } from '@/pages/system/user/components/UserTiers';

export function UsersAdmin() {
  return (
    <main className="admin-main">
      <p className="eyebrow">PLATFORM / USERS</p>
      <h1>用户管理</h1>
      <UserTiers />
    </main>
  );
}
