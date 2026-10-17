import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button, Table, Tag } from 'antd';
import { api } from '@/services/request';
import { type Model, type Rule } from '@/pages/system/models/routing/types';
import { tiers } from '@/constants/membership';
import { RuleForm } from '@/pages/system/models/routing/components/RuleForm';

export default function RoutingAdmin() {
  const rules = useQuery({
    queryKey: ['admin', 'routes'],
    queryFn: async () => (await api.get<Rule[]>('/admin/routes')).data,
  });
  const models = useQuery({
    queryKey: ['admin', 'models'],
    queryFn: async () => (await api.get<Model[]>('/admin/models')).data,
  });
  const [editing, E] = useState<Rule | null | undefined>();
  return (
    <section>
      <div className="section-heading">
        <div>
          <h2>等级与角色消耗路由</h2>
          <p className="muted helper">
            每条消息根据用户等级与角色价格匹配；优先级较高的规则优先，无可用匹配时使用默认模型。
          </p>
        </div>
        <Button type="primary" onClick={() => E(null)}>
          新增路由规则
        </Button>
      </div>
      {editing !== undefined && (
        <RuleForm
          key={editing?.id ?? 'new'}
          rule={editing ?? undefined}
          models={models.data ?? []}
          close={() => E(undefined)}
          saved={() => rules.refetch()}
        />
      )}
      <Table
        rowKey="id"
        loading={rules.isPending}
        dataSource={rules.data}
        scroll={{ x: 900 }}
        columns={[
          { title: '规则', dataIndex: 'name' },
          { title: '用户等级', dataIndex: 'user_tier', render: (t) => tiers[t] },
          { title: '角色星币 / 条', render: (_, r) => r.min_stars + '–' + (r.max_stars ?? '不限') },
          { title: '主模型', render: (_, r) => r.provider + ' / ' + r.model_name },
          {
            title: '组合方案',
            render: (_, r) =>
              r.helper_model_ids?.length
                ? '辅助 ' +
                  r.helper_model_ids.length +
                  ' 个模型 · ' +
                  (r.min_characters ? '消息≥' + r.min_characters + '字' : '每条消息')
                : '单模型',
          },
          { title: '优先级', dataIndex: 'priority' },
          {
            title: '状态',
            render: (_, r) => (
              <Tag color={r.enabled && r.model_available ? 'green' : 'default'}>
                {!r.enabled ? '已停用' : r.model_available ? '生效' : '模型不可用'}
              </Tag>
            ),
          },
          { title: '操作', render: (_, r) => <Button onClick={() => E(r)}>编辑</Button> },
        ]}
      />
      {rules.isError && <p role="alert">路由加载失败，请刷新重试。</p>}
    </section>
  );
}
