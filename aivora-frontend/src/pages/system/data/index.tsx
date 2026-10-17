import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useLocation } from 'react-router-dom';
import { Table, Button, Input, Select, Tag } from 'antd';
import { RefreshCw, Search, ShieldCheck } from 'lucide-react';
import { api } from '@/services/request';
import AdminSummary from '@/components/AdminSummary/index';
import TokenBudgetPanel from '@/pages/system/status/components/TokenBudgetPanel';
import { type Row } from '@/pages/system/data/types';

export const date = (v: unknown) =>
  v ? new Date(String(v)).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' }) : '—';

export const states: Record<string, string> = {
  PAID: '已到账',
  PENDING: '待支付',
  FAILED: '失败',
  CANCELLED: '已取消',
  RUNNING: '生成中',
  SUCCEEDED: '已完成',
  REFUNDED: '已退款',
  RECHARGE: '星币充值',
  CHAT: '角色对话',
  CHAT_REFUND: '对话退款',
};

export const state = (v: unknown) => (
  <Tag
    color={
      ['PAID', 'SUCCEEDED', 'REFUNDED'].includes(String(v))
        ? 'green'
        : ['FAILED', 'CANCELLED'].includes(String(v))
          ? 'red'
          : 'gold'
    }
  >
    {states[String(v)] ?? String(v)}
  </Tag>
);

export const definitions: Record<
  string,
  {
    title: string;
    description: string;
    columns: { title: string; dataIndex: string; render?: (v: unknown) => React.ReactNode }[];
  }
> = {
  orders: {
    title: '充值订单',
    description: '从订单创建到星币到账，清晰追踪每一笔充值。',
    columns: [
      {
        title: '订单号',
        dataIndex: 'id',
        render: (v) => <span className="admin-code">{String(v)}</span>,
      },
      { title: '用户', dataIndex: 'nickname' },
      {
        title: '金额',
        dataIndex: 'amount_cents',
        render: (v) => <strong className="admin-money">¥{(Number(v) / 100).toFixed(2)}</strong>,
      },
      { title: '星币', dataIndex: 'stars' },
      { title: '状态', dataIndex: 'status', render: state },
      { title: '时间', dataIndex: 'created_at', render: date },
    ],
  },
  ledger: {
    title: '钱包流水',
    description: '查看星币入账、对话消耗与退款，每一笔变动都有记录。',
    columns: [
      { title: '流水号', dataIndex: 'id' },
      { title: '用户', dataIndex: 'nickname' },
      {
        title: '星币变动',
        dataIndex: 'delta',
        render: (v) => (
          <strong className={Number(v) > 0 ? 'admin-positive' : 'admin-negative'}>
            {Number(v) > 0 ? '+' : ''}
            {Number(v).toLocaleString('zh-CN')}
          </strong>
        ),
      },
      { title: '变动后余额', dataIndex: 'balance_after' },
      { title: '原因', dataIndex: 'reason', render: (v) => states[String(v)] ?? String(v) },
      {
        title: '关联编号',
        dataIndex: 'reference_key',
        render: (v) => <span className="admin-code">{String(v ?? '—')}</span>,
      },
      { title: '时间', dataIndex: 'created_at', render: date },
    ],
  },
  reconciliation: {
    title: '异常对账',
    description: '排查超过五分钟未结束的请求，以及失败后缺少退款记录的请求。',
    columns: [
      {
        title: '请求编号',
        dataIndex: 'request_key',
        render: (v) => <span className="admin-code">{String(v)}</span>,
      },
      { title: '用户编号', dataIndex: 'user_id' },
      { title: '状态', dataIndex: 'status', render: state },
      { title: '消耗星币', dataIndex: 'cost_stars' },
      { title: '时间', dataIndex: 'created_at', render: date },
    ],
  },
};

export function AdminData() {
  const section = useLocation().pathname.split('/')[2];
  const settings = definitions[section];
  const [query, Q] = useState(''),
    [filter, F] = useState('all');
  const errors = useQuery({
    queryKey: ['admin', 'chat-errors'],
    enabled: section === 'status',
    queryFn: async () => (await api.get<Row[]>('/admin/status/errors')).data,
  });
  const rows = useQuery({
    queryKey: ['admin', section],
    queryFn: async () => (await api.get('/admin/' + section)).data,
  });
  const records: Row[] = Array.isArray(rows.data) ? rows.data : [];
  const visible = records.filter(
    (r) =>
      Object.values(r).some((v) =>
        String(v ?? '')
          .toLowerCase()
          .includes(query.trim().toLowerCase()),
      ) &&
      (filter === 'all' ||
        (section === 'ledger'
          ? filter === 'in'
            ? r.delta > 0
            : filter === 'out'
              ? r.delta < 0
              : r.reason === 'CHAT_REFUND'
          : r.status === filter)),
  );
  const options =
    section === 'ledger'
      ? [
          { value: 'in', label: '入账' },
          { value: 'out', label: '消耗' },
          { value: 'refund', label: '退款' },
        ]
      : [...new Set(records.map((r) => r.status))]
          .filter(Boolean)
          .map((value) => ({ value, label: states[value] ?? value }));
  const loaded = rows.isSuccess && !rows.isError;
  const summary =
    section === 'orders'
      ? [
          {
            label: '本次读取订单',
            value: loaded ? records.length : undefined,
            note: '统计范围为当前返回记录',
          },
          {
            label: '已到账金额',
            value: loaded
              ? '¥' +
                (
                  records
                    .filter((r) => r.status === 'PAID')
                    .reduce((n, r) => n + r.amount_cents, 0) / 100
                ).toFixed(2)
              : undefined,
            note: '仅累计已支付订单金额',
          },
          {
            label: '待支付订单',
            value: loaded ? records.filter((r) => r.status === 'PENDING').length : undefined,
            note: '创建后尚未完成支付',
          },
        ]
      : section === 'ledger'
        ? [
            {
              label: '本次读取流水',
              value: loaded ? records.length : undefined,
              note: '统计范围为当前返回记录',
            },
            {
              label: '入账星币',
              value: loaded
                ? records.reduce((n, r) => n + Math.max(0, r.delta), 0).toLocaleString('zh-CN')
                : undefined,
              note: '含充值、赠送及退款',
            },
            {
              label: '消耗星币',
              value: loaded
                ? records.reduce((n, r) => n + Math.max(0, -r.delta), 0).toLocaleString('zh-CN')
                : undefined,
              note: '当前记录中全部星币支出',
            },
          ]
        : [
            {
              label: '待排查请求',
              value: loaded ? records.length : undefined,
              note: '以服务端异常检测结果为准',
            },
            {
              label: '涉及星币',
              value: loaded ? records.reduce((n, r) => n + (r.cost_stars ?? 0), 0) : undefined,
              note: '异常请求中的预扣额度',
            },
            {
              label: '核查状态',
              value: loaded ? (records.length ? '待处理' : '无异常') : undefined,
              note: '刷新获取最新检测结果',
            },
          ];
  return (
    <main className="admin-main" key={section}>
      <p className="eyebrow">PLATFORM / {section.toUpperCase()}</p>
      <div className="section-heading">
        <div>
          <h1>{settings?.title ?? '系统状态'}</h1>
          <p className="muted">
            {settings?.description ?? '查看基础设施、模型可用性和最近生成失败。'}
          </p>
        </div>
        <Button
          loading={rows.isFetching || (section === 'status' && errors.isFetching)}
          icon={<RefreshCw size={17} />}
          onClick={() => {
            void rows.refetch();
            if (section === 'status') void errors.refetch();
          }}
        >
          刷新数据
        </Button>
      </div>
      {section === 'status' && <TokenBudgetPanel />}
      {rows.isError ? (
        <div className="panel admin-error" role="alert">
          数据加载失败，请刷新重试。
        </div>
      ) : section === 'status' ? (
        rows.isPending ? (
          <div className="panel">正在检查系统状态…</div>
        ) : (
          <>
            <div className="status-strip">
              <ShieldCheck size={17} />
              数据库查询成功 · 检查时间 {date(rows.data.checked_at)}
            </div>
            <AdminSummary
              items={[
                {
                  label: '已启用供应商',
                  value: rows.data.enabled_providers,
                  note: '当前已启用的模型服务供应商',
                },
                {
                  label: '可用模型',
                  value: rows.data.enabled_models,
                  note: '系统中已启用的模型配置',
                },
                {
                  label: '进行中的请求',
                  value: rows.data.running_requests,
                  note: '当前尚未结束的生成请求',
                },
              ]}
            />
            <div className="admin-table-heading">
              <div>
                <h2>最近生成失败</h2>
                <p>检查供应商响应与退款状态，定位服务问题。</p>
              </div>
              <Tag>故障排查</Tag>
            </div>
            {errors.isError ? (
              <p role="alert" className="admin-error">
                失败记录加载失败，请刷新重试。
              </p>
            ) : (
              <Table
                loading={errors.isPending}
                rowKey="id"
                dataSource={errors.data}
                locale={{ emptyText: '暂无生成失败记录' }}
                scroll={{ x: 850 }}
                columns={[
                  { title: '请求', dataIndex: 'request_key' },
                  { title: '供应商', dataIndex: 'provider' },
                  { title: '模型', dataIndex: 'model_name' },
                  { title: '原因', dataIndex: 'error_message' },
                  {
                    title: '退款',
                    dataIndex: 'refunded',
                    render: (v) => (
                      <Tag color={v ? 'green' : 'gold'}>{v ? '已退款' : '待核查'}</Tag>
                    ),
                  },
                ]}
              />
            )}
            <p className="helper muted">供应商连通性请前往“模型与路由”执行连接测试。</p>
          </>
        )
      ) : (
        <>
          <AdminSummary items={summary} />
          <div className="admin-table-toolbar">
            <Input
              aria-label="搜索记录"
              prefix={<Search size={16} />}
              placeholder="搜索用户、编号或记录内容"
              allowClear
              value={query}
              onChange={(e) => Q(e.target.value)}
            />
            <Select
              aria-label="筛选记录"
              value={filter}
              onChange={F}
              options={[
                { value: 'all', label: section === 'ledger' ? '全部变动' : '全部状态' },
                ...options,
              ]}
            />
            <span>{visible.length} 条匹配记录</span>
          </div>
          <Table
            loading={rows.isPending}
            rowKey={section === 'reconciliation' ? 'request_key' : 'id'}
            dataSource={visible}
            scroll={{ x: 1000 }}
            pagination={{ showSizeChanger: true, showTotal: (total) => '共 ' + total + ' 条记录' }}
            locale={{
              emptyText:
                query || filter !== 'all'
                  ? '没有匹配的记录'
                  : section === 'reconciliation'
                    ? '当前没有待排查的异常请求'
                    : '暂无记录',
            }}
            columns={settings.columns}
          />
        </>
      )}
    </main>
  );
}
