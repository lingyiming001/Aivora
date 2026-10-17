import AdminSummary from '@/components/AdminSummary/index';
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button, Table, Modal, Input, Tabs } from 'antd';
import { api } from '@/services/request';
import { qc } from '@/services/queryClient';
import { RolePrices } from '@/pages/system/review/components/RolePrices';
import { message } from '@/pages/system/review/helpers';
import { type Card } from '@/pages/system/review/types';

export function CardAdmin() {
  const [offlineCard, OC] = useState<{
    id: number;
    name: string;
    status: string;
    offline_reason?: string;
  } | null>(null);
  const [tab, T] = useState('list'),
    [search, SEARCH] = useState('');
  const all = useQuery({
    queryKey: ['admin', 'character-prices'],
    queryFn: async () =>
      (
        await api.get<
          Array<{
            id: number;
            name: string;
            category: string;
            status: string;
            visibility: string;
            stars_per_message: number;
            offline_reason: string;
            offlined_at: string | null;
          }>
        >('/admin/characters/all')
      ).data,
  });
  const q = useQuery({
    queryKey: ['admin', 'characters/review'],
    queryFn: async () => (await api.get<Card[]>('/admin/characters/review')).data,
  });
  const [selected, S] = useState<Card | null>(null),
    [reason, R] = useState(''),
    [error, E] = useState(''),
    [busy, B] = useState(false);
  async function decide(action: string) {
    if (!selected) return;
    B(true);
    E('');
    try {
      await api.post(
        '/admin/characters/' + selected.id + '/' + action,
        action === 'reject' ? { reason } : undefined,
      );
      S(null);
      R('');
      await q.refetch();
      await all.refetch();
      await qc.invalidateQueries({ queryKey: ['cards'] });
      await qc.invalidateQueries({ queryKey: ['my-characters'] });
    } catch (e) {
      E(message(e));
    } finally {
      B(false);
    }
  }
  async function offline() {
    if (!offlineCard || !reason.trim() || busy) return;
    B(true);
    E('');
    try {
      await api.post('/admin/characters/' + offlineCard.id + '/offline', { reason: reason.trim() });
      OC(null);
      R('');
      await Promise.all([
        all.refetch(),
        q.refetch(),
        ...['cards', 'my-characters', 'favorites', 'card', 'chat-meta'].map((key) =>
          qc.invalidateQueries({ queryKey: [key] }),
        ),
      ]);
    } catch (e) {
      E(message(e));
    } finally {
      B(false);
    }
  }
  return (
    <main className="admin-main">
      <p className="eyebrow">PLATFORM / CHARACTER CARDS</p>
      <h1>角色卡管理</h1>
      <p className="muted">审核公开投稿、管理角色价格；已发布角色可停用下架，原因会通知作者。</p>
      {q.isError && (
        <p role="alert">
          审核队列加载失败 <Button onClick={() => q.refetch()}>重试</Button>
        </p>
      )}
      <AdminSummary
        items={[
          { label: '角色卡总数', value: all.data?.length, note: '包含公开与私密角色' },
          { label: '待审核投稿', value: q.data?.length, note: '查看设定后决定是否发布' },
          {
            label: '已发布角色',
            value: all.data?.filter((c) => c.status === 'PUBLISHED').length,
            note: '已进入角色广场',
          },
        ]}
      />
      <Tabs
        activeKey={tab}
        onChange={T}
        items={[
          { key: 'list', label: '角色卡列表' },
          { key: 'review', label: '投稿审核（' + (q.data?.length ?? 0) + '）' },
          { key: 'prices', label: '价格设置' },
        ]}
      />
      {tab === 'list' ? (
        <>
          <Input.Search
            aria-label="搜索角色卡"
            placeholder="搜索角色卡名称"
            style={{ maxWidth: 320, marginBottom: 20 }}
            value={search}
            onChange={(e) => SEARCH(e.target.value)}
          />
          <Table
            rowKey="id"
            scroll={{ x: 1000 }}
            loading={all.isPending}
            dataSource={all.data?.filter((c) => c.name.includes(search))}
            columns={[
              { title: '角色卡', dataIndex: 'name' },
              { title: '分类', dataIndex: 'category' },
              {
                title: '状态',
                dataIndex: 'status',
                filters: [
                  { text: '草稿', value: 'DRAFT' },
                  { text: '待审核', value: 'REVIEW' },
                  { text: '已发布', value: 'PUBLISHED' },
                  { text: '已下架', value: 'OFFLINE' },
                ],
                onFilter: (v, c) => c.status === v,
                render: (v) =>
                  (
                    ({
                      DRAFT: '草稿',
                      REVIEW: '待审核',
                      PUBLISHED: '已发布',
                      OFFLINE: '已下架',
                    }) as Record<string, string>
                  )[v],
              },
              {
                title: '可见性',
                dataIndex: 'visibility',
                render: (v) => (v === 'PRIVATE' ? '私密' : '公开'),
              },
              { title: '星币 / 条', dataIndex: 'stars_per_message' },
              {
                title: '下架原因',
                dataIndex: 'offline_reason',
                ellipsis: true,
                render: (v) => v || '—',
              },
              {
                title: '操作',
                render: (_, c) =>
                  c.status === 'REVIEW' ? (
                    <Button
                      onClick={() => {
                        const card = q.data?.find((x) => x.id === c.id);
                        if (card) {
                          S(card);
                          R('');
                          E('');
                        }
                      }}
                    >
                      查看并审核
                    </Button>
                  ) : c.status === 'PUBLISHED' || c.status === 'OFFLINE' ? (
                    <Button
                      disabled={busy}
                      danger={c.status === 'PUBLISHED'}
                      onClick={() => {
                        OC(c);
                        R('');
                        E('');
                      }}
                    >
                      {c.status === 'PUBLISHED' ? '停用下架' : '查看下架原因'}
                    </Button>
                  ) : (
                    '—'
                  ),
              },
            ]}
          />
        </>
      ) : tab === 'review' ? (
        <Table
          rowKey="id"
          loading={q.isPending}
          dataSource={q.data}
          columns={[
            { title: '角色卡', dataIndex: 'name' },
            { title: '投稿人', dataIndex: 'owner_name' },
            { title: '分类', dataIndex: 'category' },
            { title: '简介', dataIndex: 'summary' },
            {
              title: '操作',
              render: (_, c: Card) => (
                <Button
                  onClick={() => {
                    S(c);
                    R('');
                    E('');
                  }}
                >
                  查看并审核
                </Button>
              ),
            },
          ]}
        />
      ) : (
        <RolePrices />
      )}
      <Modal
        centered
        width={720}
        title={'审核角色卡 · ' + (selected?.name || '')}
        open={!!selected}
        onCancel={() => {
          if (!busy) S(null);
        }}
        footer={
          <>
            <Button disabled={busy} onClick={() => S(null)}>
              取消
            </Button>
            <Button
              danger
              loading={busy}
              disabled={!reason.trim()}
              onClick={() => decide('reject')}
            >
              驳回投稿
            </Button>
            <Button type="primary" loading={busy} onClick={() => decide('approve')}>
              通过并发布
            </Button>
          </>
        }
      >
        {selected && (
          <>
            <p>
              投稿人：{selected.owner_name} · {selected.stars_per_message} 星币 / 条
            </p>
            {[
              ['简介', selected.summary],
              ['角色设定', selected.persona],
              ['系统提示', selected.system_prompt],
              ['开场白', selected.opening],
              ['标签', selected.tags],
            ].map(([label, value]) => (
              <section key={label}>
                <h3>{label}</h3>
                <p style={{ whiteSpace: 'pre-wrap', maxHeight: 200, overflow: 'auto' }}>
                  {value || '未填写'}
                </p>
              </section>
            ))}
            <label htmlFor="review-reason">驳回原因（驳回时必填）</label>
            <Input.TextArea
              id="review-reason"
              value={reason}
              onChange={(e) => R(e.target.value)}
              maxLength={1000}
              rows={3}
            />
            {error && <p role="alert">{error}</p>}
          </>
        )}
      </Modal>
      <Modal
        open={!!offlineCard}
        centered
        width={580}
        className="av-management-dialog"
        maskClosable={false}
        footer={null}
        keyboard={!busy}
        closable={!busy}
        onCancel={() => !busy && OC(null)}
        title={
          <div className="management-modal-title">
            <div>
              <h2>{offlineCard?.status === 'OFFLINE' ? '下架记录' : '停用下架角色卡'}</h2>
              <p>{offlineCard?.name}</p>
            </div>
          </div>
        }
      >
        {offlineCard?.status === 'OFFLINE' ? (
          <div className="management-form">
            <div className="management-form-body">
              <p className="muted">下架原因</p>
              <p style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
                {offlineCard.offline_reason || '暂无记录'}
              </p>
            </div>
            <div className="management-form-footer">
              <span className="management-footer-spacer" />
              <Button onClick={() => OC(null)}>关闭</Button>
            </div>
          </div>
        ) : (
          <form
            className="management-form"
            onSubmit={(e) => {
              e.preventDefault();
              void offline();
            }}
          >
            <div className="management-form-body">
              <p className="management-warning">
                下架后角色将从广场移除，并停止接受新对话与新消息。已有聊天记录保留。作者可修改后重新提交审核。
              </p>
              <label htmlFor="offline-reason">
                下架原因<span className="muted">（必填，作者可见）</span>
                <Input.TextArea
                  id="offline-reason"
                  required
                  autoFocus
                  disabled={busy}
                  value={reason}
                  onChange={(e) => R(e.target.value)}
                  maxLength={1000}
                  showCount
                  rows={4}
                  placeholder="请说明问题以及需要修改的内容"
                />
              </label>
              {error && (
                <p role="alert" className="auth-error">
                  {error}
                </p>
              )}
            </div>
            <div className="management-form-footer">
              <span className="management-footer-spacer" />
              <Button disabled={busy} onClick={() => OC(null)}>
                取消
              </Button>
              <Button
                danger
                type="primary"
                htmlType="submit"
                loading={busy}
                disabled={!reason.trim()}
              >
                确认下架
              </Button>
            </div>
          </form>
        )}
      </Modal>
    </main>
  );
}
