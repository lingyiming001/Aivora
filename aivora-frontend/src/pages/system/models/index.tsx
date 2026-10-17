import { modelTypes } from './constants';
import { explain } from './helpers';
import AdminSummary from '@/components/AdminSummary/index';
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button, Table, Tag } from 'antd';
import { Network, RefreshCw } from 'lucide-react';
import { api } from '@/services/request';
import RoutingAdmin from '@/pages/system/models/routing/index';
import { type Provider, type Model } from '@/pages/system/models/types';
import { ProviderForm } from '@/pages/system/models/components/ProviderForm';
import { ModelForm } from '@/pages/system/models/components/ModelForm';

export default function ModelAdmin() {
  const providers = useQuery({
    queryKey: ['admin', 'providers'],
    queryFn: async () => (await api.get<Provider[]>('/admin/providers')).data,
  });
  const models = useQuery({
    queryKey: ['admin', 'models'],
    queryFn: async () => (await api.get<Model[]>('/admin/models')).data,
  });
  const [providerEditing, PE] = useState<Provider | null | undefined>();
  const [editing, E] = useState<Model | null | undefined>(),
    [testing, T] = useState<number | null>(null),
    [message, M] = useState(''),
    [filter, F] = useState(''),
    [tab, S] = useState('providers');
  function refresh() {
    void providers.refetch();
    void models.refetch();
  }
  async function test(id: number) {
    T(id);
    M('');
    try {
      const { data } = await api.post('/admin/models/' + id + '/test', {}, { timeout: 25000 });
      M(data.message + (data.latencyMs ? ' · ' + data.latencyMs + ' ms' : ''));
    } catch (e) {
      M(explain(e));
    } finally {
      T(null);
    }
  }
  const route = models.data?.find((m) => m.is_default && m.enabled && m.provider_enabled);
  return (
    <main className="admin-main">
      <p className="eyebrow">PLATFORM / MODELS & ROUTING</p>
      <div className="section-heading">
        <div>
          <h1>模型与路由</h1>
          <p className="muted">配置供应商密钥，让每一场对话顺畅发生。</p>
        </div>
        <Button icon={<RefreshCw size={17} />} onClick={refresh}>
          刷新
        </Button>
      </div>
      <AdminSummary
        items={[
          {
            label: '启用供应商',
            value: providers.data?.filter((p) => p.enabled && p.kind !== 'LOCAL_DEMO').length,
            note: '统一管理连接与加密密钥',
          },
          {
            label: '可用模型',
            value: models.data?.filter((m) => m.enabled && m.provider_enabled).length,
            note: '供应商和模型均已启用',
          },
          {
            label: '路由保障',
            value: models.isPending ? '—' : route ? '已配置' : '待配置',
            note: '默认模型承接未匹配请求',
          },
        ]}
      />
      <section className="route-summary">
        <Network size={24} />
        <div>
          <strong>默认兜底模型</strong>
          <p>
            {route
              ? route.provider + ' / ' + route.model_name
              : '尚未设置，启用供应商后在模型列表中选择默认模型'}
          </p>
        </div>
      </section>
      <div className="category-tabs">
        <button className={tab === 'providers' ? 'active' : ''} onClick={() => S('providers')}>
          供应商配置
        </button>
        <button className={tab === 'models' ? 'active' : ''} onClick={() => S('models')}>
          模型列表
        </button>
        <button className={tab === 'routes' ? 'active' : ''} onClick={() => S('routes')}>
          自动路由规则
        </button>
      </div>
      {providers.isError || models.isError ? (
        <div className="panel">配置加载失败，请点击刷新重试。</div>
      ) : providers.isPending || models.isPending ? (
        <p>正在读取配置…</p>
      ) : tab === 'providers' ? (
        <>
          <div className="section-heading">
            <p className="muted">
              统一查看供应商状态，编辑时配置地址和密钥。新增供应商后，在模型列表添加对应模型。
            </p>
            <Button type="primary" onClick={() => PE(null)}>
              新增供应商
            </Button>
          </div>
          <Table
            rowKey="id"
            scroll={{ x: 850 }}
            dataSource={providers.data?.filter((p) => p.kind !== 'LOCAL_DEMO')}
            columns={[
              { title: '供应商', dataIndex: 'name' },
              { title: '接入协议', render: () => <Tag>OpenAI 兼容</Tag> },
              { title: 'Base URL', dataIndex: 'base_url', ellipsis: true },
              { title: '密钥', render: (_, p) => (p.key_configured ? '已配置' : '未配置') },
              {
                title: '状态',
                render: (_, p) => (
                  <Tag color={p.enabled ? 'green' : 'default'}>
                    {p.enabled ? '已启用' : '已停用'}
                  </Tag>
                ),
              },
              {
                title: '关联模型',
                render: (_, p) => models.data?.filter((m) => m.provider_id === p.id).length ?? 0,
              },
              { title: '操作', render: (_, p) => <Button onClick={() => PE(p)}>编辑配置</Button> },
            ]}
          />
          {providerEditing !== undefined && (
            <ProviderForm
              key={providerEditing?.id ?? 'new'}
              p={providerEditing ?? undefined}
              refresh={refresh}
              onClose={() => PE(undefined)}
            />
          )}
        </>
      ) : tab === 'routes' ? (
        <RoutingAdmin />
      ) : (
        <>
          <div className="section-heading">
            <p className="muted">
              客户端不选择模型，由等级与角色消耗规则自动路由。角色价格决定用户扣费。
            </p>
            <Button type="primary" onClick={() => E(null)}>
              添加模型
            </Button>
          </div>
          {editing !== undefined && (
            <ModelForm
              key={editing?.id ?? 'new'}
              m={editing ?? undefined}
              providers={providers.data ?? []}
              refresh={refresh}
              onClose={() => E(undefined)}
            />
          )}
          <label>
            筛选模型类型{' '}
            <select value={filter} onChange={(e) => F(e.target.value)}>
              <option value="">全部类型</option>
              {Object.entries(modelTypes).map(([v, label]) => (
                <option key={v} value={v}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <p className="muted helper">文字连接测试会发送一条短消息，可能产生少量供应商费用。</p>
          <p role="status">{message}</p>
          <Table
            rowKey="id"
            dataSource={models.data?.filter((m) => !filter || m.model_type === filter)}
            scroll={{ x: 850 }}
            columns={[
              { title: '供应商', dataIndex: 'provider' },
              { title: '模型', dataIndex: 'model_name' },
              {
                title: '类型',
                dataIndex: 'model_type',
                render: (t) => <Tag>{modelTypes[t] ?? t}</Tag>,
              },
              { title: '参考星币', dataIndex: 'star_per_request' },
              {
                title: '状态',
                render: (_, m) => (
                  <Tag color={m.enabled && m.provider_enabled ? 'green' : 'default'}>
                    {m.enabled && m.provider_enabled
                      ? '可用'
                      : m.enabled
                        ? '供应商未启用'
                        : '已停用'}
                  </Tag>
                ),
              },
              { title: '路由', render: (_, m) => (m.is_default ? '默认' : '可选') },
              {
                title: '操作',
                render: (_, m) => (
                  <div className="actions">
                    <Button onClick={() => E(m)}>编辑</Button>
                    <Button
                      disabled={testing !== null || !['CHAT', 'TEXT'].includes(m.model_type)}
                      loading={testing === m.id}
                      onClick={() => test(m.id)}
                    >
                      连接测试
                    </Button>
                  </div>
                ),
              },
            ]}
          />
        </>
      )}
    </main>
  );
}
