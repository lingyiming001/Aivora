import UserMemories from './components/UserMemories';
import { useProfile } from '@/pages/profile/hooks';
import { ProfileForm } from '@/pages/profile/components/ProfileForm';

export default function ProfilePage() {
  const q = useProfile();
  return (
    <main className="client-page profile-page">
      <p className="eyebrow">A SPACE OF YOUR OWN</p>
      <h1>个人中心</h1>
      <p className="client-subtitle">你的资料，你的角色，你的故事。</p>
      {q.isPending ? (
        <p>正在读取个人资料…</p>
      ) : q.isError ? (
        <div className="panel">
          资料加载失败 <button onClick={() => q.refetch()}>重试</button>
        </div>
      ) : (
        q.data && <ProfileForm key={q.data.id} profile={q.data} />
      )}
      {q.data && <UserMemories />}
    </main>
  );
}
