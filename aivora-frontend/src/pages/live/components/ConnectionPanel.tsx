import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Form, Input, Space, Tag, Typography } from 'antd';
import axios from 'axios';
import { api } from '@/services/request';

type Connection = {
  status: string;
  enabled: boolean;
  has_token: boolean;
  source_id?: string;
  client_id?: string;
  actor_id?: string;
  detail?: string;
};
const names: Record<string, string> = {
  UNCONFIGURED: '未配置',
  DISCONNECTED: '已断开',
  CONNECTING: '连接中',
  CONNECTED: '已连接',
  RETRYING: '等待重试',
  ERROR: '连接失败',
};
export default function ConnectionPanel({
  roomId,
  platform,
  live,
}: {
  roomId: number;
  platform: string;
  live: boolean;
}) {
  const [form] = Form.useForm();
  const [connection, setConnection] = useState<Connection>({
    status: 'UNCONFIGURED',
    enabled: false,
    has_token: false,
  });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const initialized = useRef(false);
  const supported = platform === 'youtube' || platform === 'twitch';
  function fail(e: unknown) {
    setError(
      axios.isAxiosError(e)
        ? e.response?.data?.message || '连接操作失败，请稍后重试。'
        : '连接操作失败，请稍后重试。',
    );
  }
  async function load() {
    const { data } = await api.get<Connection>(`/live/rooms/${roomId}/connection`);
    setConnection(data);
  }
  useEffect(() => {
    if (!supported) return;
    let active = true;
    let timer: ReturnType<typeof setTimeout>;
    const controller = new AbortController();
    async function poll() {
      try {
        const { data } = await api.get<Connection>(`/live/rooms/${roomId}/connection`, {
          signal: controller.signal,
        });
        if (active) {
          setConnection(data);
          if (!initialized.current) {
            initialized.current = true;
            form.setFieldsValue({
              sourceId: data.source_id || '',
              clientId: data.client_id || '',
              actorId: data.actor_id || '',
              token: '',
            });
          }
        }
      } catch (e) {
        if (active) fail(e);
      }
      if (active) timer = setTimeout(poll, 3000);
    }
    void poll();
    return () => {
      active = false;
      clearTimeout(timer);
      controller.abort();
    };
  }, [roomId, supported, form]);
  async function action(kind: 'connect' | 'disconnect') {
    setBusy(true);
    setError('');
    try {
      await api.post(`/live/rooms/${roomId}/connection/${kind}`);
      await load();
    } catch (e) {
      fail(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Card title="平台连接器" style={{ marginTop: 20 }}>
      {!supported ? (
        <Typography.Paragraph>
          此平台当前使用通用消息桥接。哔哩哔哩、抖音需通过获授权的外部适配器接入；OBS
          使用字幕浏览器来源。
        </Typography.Paragraph>
      ) : (
        <>
          <Space style={{ marginBottom: 12 }}>
            <Tag
              color={
                connection.status === 'CONNECTED'
                  ? 'green'
                  : connection.status === 'ERROR'
                    ? 'red'
                    : 'default'
              }
            >
              {names[connection.status] || connection.status}
            </Tag>
            <span>{connection.has_token ? '访问令牌已加密保存' : '尚未保存访问令牌'}</span>
          </Space>
          {error && <Alert type="error" message={error} style={{ marginBottom: 12 }} />}
          {connection.detail && (
            <Alert type="warning" message={connection.detail} style={{ marginBottom: 12 }} />
          )}
          <Typography.Paragraph>
            {platform === 'youtube'
              ? '填写 YouTube Live Chat ID 和已授权访问令牌，连接后按平台要求的间隔自动接收聊天。'
              : '使用具有 user:read:chat 权限的 Twitch 用户访问令牌，通过官方 EventSub WebSocket 接收弹幕。'}
          </Typography.Paragraph>
          <Form
            form={form}
            layout="vertical"
            initialValues={{ sourceId: '', clientId: '', actorId: '', token: '' }}
            onFinish={async (v) => {
              setBusy(true);
              setError('');
              try {
                await api.put(`/live/rooms/${roomId}/connection`, {
                  clientId: '',
                  actorId: '',
                  ...v,
                });
                form.setFieldValue('token', '');
                await load();
              } catch (e) {
                fail(e);
              } finally {
                setBusy(false);
              }
            }}
          >
            <Form.Item
              name="sourceId"
              label={platform === 'youtube' ? 'Live Chat ID（不是视频 ID）' : '主播用户 ID'}
              rules={[{ required: true, whitespace: true }]}
            >
              <Input maxLength={200} />
            </Form.Item>
            {platform === 'twitch' && (
              <>
                <Form.Item
                  name="clientId"
                  label="应用 Client ID"
                  rules={[{ required: true, whitespace: true }]}
                >
                  <Input maxLength={100} />
                </Form.Item>
                <Form.Item
                  name="actorId"
                  label="令牌所属用户 ID"
                  rules={[{ required: true, whitespace: true }]}
                >
                  <Input maxLength={100} />
                </Form.Item>
              </>
            )}
            <Form.Item
              name="token"
              label="平台访问令牌"
              extra={
                connection.has_token
                  ? '留空保留当前令牌。保存配置会断开连接，需重新点击连接。'
                  : '令牌从平台授权流程获取，不填写平台登录密码。'
              }
              rules={connection.has_token ? [] : [{ required: true, whitespace: true }]}
            >
              <Input.Password maxLength={4096} autoComplete="new-password" />
            </Form.Item>
            <Space wrap>
              <Button htmlType="submit" loading={busy}>
                保存配置
              </Button>
              <Button
                type="primary"
                disabled={!live || !connection.has_token || busy || connection.enabled}
                onClick={() => void action('connect')}
              >
                连接平台
              </Button>
              <Button
                disabled={!connection.enabled || busy}
                onClick={() => void action('disconnect')}
              >
                断开连接
              </Button>
            </Space>
          </Form>
          {!live && (
            <Typography.Paragraph type="secondary" style={{ marginTop: 12 }}>
              开始互动后可连接平台。结束互动或账号停用后连接器会停止。
            </Typography.Paragraph>
          )}
        </>
      )}
    </Card>
  );
}
