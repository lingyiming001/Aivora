import { Link, useLocation } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/services/request';
import { useAdminSession } from '@/hooks/useAdminSession';
import { Table } from 'antd';
import AdminSummary from '@/components/AdminSummary/index';
import { RefreshCw } from 'lucide-react';
import { type AdminRow } from '@/pages/dashboard/types';

export function Admin() {
  const session = useAdminSession();
  const audit = useLocation().pathname.endsWith('/audit');
  const overview = useQuery({
    queryKey: ['admin', 'overview'],
    enabled: !audit,
    queryFn: async () =>
      (await api.get<{ users: number; published: number; pending: number }>('/admin/overview'))
        .data,
  });
  const reviewAllowed = !!session.data?.menus.includes('review');
  const endpoint = audit ? 'audit' : 'characters/review';
  const rows = useQuery({
    queryKey: ['admin', endpoint],
    enabled: audit || reviewAllowed,
    queryFn: async () => (await api.get<AdminRow[]>('/admin/' + endpoint)).data,
  });
  return (
    <main className="admin-main">
      <p className="eyebrow">PLATFORM / {audit ? 'AUDIT' : 'OVERVIEW'}</p>
      <div className="section-heading">
        <div>
          <h1>{audit ? '操作日志' : '运营概览'}</h1>
          <p className="muted">平台数据与运营动态</p>
        </div>
        <button
          className="icon-button"
          aria-label="刷新数据"
          onClick={() => {
            if (audit || reviewAllowed) void rows.refetch();
            if (!audit) void overview.refetch();
          }}
        >
          <RefreshCw size={21} />
        </button>
      </div>
      {overview.isError && !audit && (
        <p role="alert" className="admin-error">
          运营数据加载失败，请刷新重试。
        </p>
      )}
      {audit && (
        <AdminSummary
          items={[
            { label: '本次读取记录', value: rows.data?.length, note: '平台操作审计记录' },
            { label: '审计范围', value: '管理操作', note: '查看操作、对象及时间' },
            { label: '记录用途', value: '追溯核查', note: '定位配置与权限变更' },
          ]}
        />
      )}{' '}
      {!audit && (
        <>
          <div className="status-strip">实时读取 · 角色卡审核队列</div>
          <AdminSummary
            items={[
              { label: '累计用户', value: overview.data?.users, note: '平台累计注册账号' },
              {
                label: '已发布角色卡',
                value: overview.data?.published,
                note: '为用户提供更多对话选择',
              },
              { label: '待审核角色卡', value: overview.data?.pending, note: '及时处理公开投稿' },
            ]}
          />
          {reviewAllowed && <h2 className="queue-title">待办事项 · 角色卡投稿审核</h2>}
        </>
      )}
      {(audit || reviewAllowed) &&
        (rows.isError ? (
          <div className="panel">数据加载失败，请重试。</div>
        ) : (
          <Table
            loading={rows.isPending}
            rowKey="id"
            dataSource={rows.data}
            locale={{ emptyText: '暂无记录' }}
            scroll={{ x: 600 }}
            columns={
              audit
                ? [
                    { title: '操作', dataIndex: 'action' },
                    { title: '对象', dataIndex: 'subject' },
                    {
                      title: '时间',
                      dataIndex: 'created_at',
                      render: (v: string) =>
                        new Date(v).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' }),
                    },
                  ]
                : [
                    { title: '名称', dataIndex: 'name' },
                    { title: '简介', dataIndex: 'summary' },
                    { title: '操作', render: () => <Link to="/admin/review">查看并审核 →</Link> },
                  ]
            }
          />
        ))}
    </main>
  );
}
