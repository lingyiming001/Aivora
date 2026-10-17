import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Bell } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Modal } from 'antd';
import { api } from '@/services/request';
import { qc } from '@/services/queryClient';
import { type Notice } from '@/pages/system/status/types';

export function BudgetBell({ enabled }: { enabled: boolean }) {
  const q = useQuery({
    queryKey: ['platform-notices'],
    enabled,
    refetchInterval: 30000,
    queryFn: async () => (await api.get<Notice[]>('/admin/status/budget/notices')).data,
  });
  const [open, O] = useState(false);
  if (!enabled) return null;
  const unread = q.data?.filter((n) => !n.read).length || 0;
  async function mark(id: number) {
    await api.post('/admin/status/budget/notices/' + id + '/read');
    await qc.invalidateQueries({ queryKey: ['platform-notices'] });
  }
  return (
    <>
      <button className="budget-bell" aria-label="平台预算通知" onClick={() => O(true)}>
        <Bell size={19} />
        {unread > 0 && <span>{unread}</span>}
      </button>
      <Modal title="平台通知" open={open} footer={null} onCancel={() => O(false)}>
        {q.isError ? (
          <p>
            通知加载失败 <button onClick={() => q.refetch()}>重试</button>
          </p>
        ) : q.isPending ? (
          <p>正在读取通知…</p>
        ) : !q.data?.length ? (
          <p>暂无预算提醒。</p>
        ) : (
          q.data.map((n) => (
            <article className="budget-notice" key={n.id}>
              <strong>
                {n.title}
                {!n.read && <i />}
              </strong>
              <p>{n.body}</p>
              <small>
                {new Date(n.created_at).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' })}
              </small>
              {!n.read && <button onClick={() => void mark(n.id)}>标为已读</button>}
            </article>
          ))
        )}
        <Link to="/admin/status" onClick={() => O(false)}>
          前往系统状态，调整预算 →
        </Link>
      </Modal>
    </>
  );
}
