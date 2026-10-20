import BroadcastPanel from './components/BroadcastPanel';
import ConnectionPanel from './components/ConnectionPanel';
import SpeechPanel from './components/SpeechPanel';
import { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Form,
  Input,
  message,
  Select,
  Space,
  Tag,
  Typography,
} from 'antd';
import { api } from '@/services/request';

type Room = {
  id: number;
  name: string;
  platform: string;
  external_room: string;
  status: string;
  plugins: string;
};
type Event = { id: number; kind: string; author: string; content: string };
type Catalog = {
  platforms: { id: string; name: string; mode: string }[];
  plugins: { id: string; name: string; description: string }[];
};
export default function LiveWorkspace() {
  const [rooms, setRooms] = useState<Room[]>([]);
  const [catalog, setCatalog] = useState<Catalog>({ platforms: [], plugins: [] });
  const [selected, setSelected] = useState<number>();
  const [events, setEvents] = useState<Event[]>([]);
  const [overlay, setOverlay] = useState('');
  const [busy, setBusy] = useState(false);
  const [createForm] = Form.useForm();
  const [eventForm] = Form.useForm();
  const room = rooms.find((r) => r.id === selected);
  const fail = () => {
    void message.error('操作失败，请检查登录状态、输入内容和直播间状态');
  };
  async function load() {
    setRooms((await api.get('/live/rooms')).data);
  }
  useEffect(() => {
    Promise.all([load(), api.get('/live/catalog').then((r) => setCatalog(r.data))]).catch(fail);
  }, []);
  useEffect(() => {
    setEvents([]);
    if (!selected) return;
    let active = true;
    let after = 0;
    let timer: ReturnType<typeof setTimeout>;
    async function poll() {
      try {
        const r = await api.get<Event[]>(`/live/rooms/${selected}/events`, { params: { after } });
        if (active) {
          if (r.data.length) {
            after = r.data[r.data.length - 1].id;
            setEvents((old) => [...old, ...r.data].slice(-100));
          }
        }
      } catch {
        if (active) fail();
      }
      if (active) timer = setTimeout(poll, 2000);
    }
    void poll();
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [selected]);
  async function create(v: { name: string; platform: string; externalRoom?: string }) {
    setBusy(true);
    try {
      const r = await api.post('/live/rooms', { ...v, externalRoom: v.externalRoom || '' });
      await load();
      setSelected(r.data.id);
      link(r.data.id, r.data.overlayToken);
      createForm.resetFields();
    } catch {
      fail();
    } finally {
      setBusy(false);
    }
  }
  function link(id: number, token: string) {
    setOverlay(`${location.origin}/live/overlay#${id}/${token}`);
  }
  async function configure(status: string, plugins: string[]) {
    if (!room) return;
    setBusy(true);
    try {
      await api.put(`/live/rooms/${room.id}`, { status, plugins });
      await load();
    } catch {
      fail();
    } finally {
      setBusy(false);
    }
  }
  return (
    <main style={{ padding: 24, maxWidth: 1100, margin: 'auto' }}>
      <Typography.Title level={2}>直播与开放能力</Typography.Title>
      <Typography.Paragraph>
        管理直播互动、弹幕和字幕，将 Aivora 接入外部工具与 OBS。
      </Typography.Paragraph>
      <Alert
        type="info"
        showIcon
        message="支持 YouTube / Twitch 自动接收弹幕、麦克风字幕和 OBS 字幕输出。平台连接需要有效授权；启用媒体服务后可使用 OBS 推流和网页观看。"
      />
      <Card title="创建直播间" style={{ marginTop: 20 }}>
        <Form
          form={createForm}
          layout="vertical"
          onFinish={create}
          initialValues={{ platform: 'custom' }}
        >
          <Form.Item name="name" label="直播间名称" rules={[{ required: true, whitespace: true }]}>
            <Input maxLength={100} />
          </Form.Item>
          <Form.Item name="platform" label="平台" rules={[{ required: true }]}>
            <Select
              options={catalog.platforms.map((p) => ({
                value: p.id,
                label: `${p.name} · ${p.mode === 'overlay' ? '字幕输出' : '消息桥接'}`,
              }))}
            />
          </Form.Item>
          <Form.Item name="externalRoom" label="外部房间 ID（用于适配器识别）">
            <Input maxLength={200} />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={busy}>
            创建直播间
          </Button>
        </Form>
      </Card>
      <Card title="直播控制台" style={{ marginTop: 20 }}>
        <Select
          style={{ width: '100%' }}
          placeholder="选择直播间"
          value={selected}
          onChange={(id) => {
            setSelected(id);
            setOverlay('');
          }}
          options={rooms.map((r) => ({
            value: r.id,
            label: `${r.name} · ${r.platform} · ${r.status}`,
          }))}
        />
        {room && (
          <>
            <Space wrap style={{ margin: '20px 0' }}>
              <Tag>{room.status}</Tag>
              <Button
                disabled={busy || room.status === 'LIVE'}
                onClick={() => void configure('LIVE', room.plugins.split(',').filter(Boolean))}
              >
                开始互动
              </Button>
              <Button
                disabled={busy || room.status !== 'LIVE'}
                onClick={() => void configure('ENDED', room.plugins.split(',').filter(Boolean))}
              >
                结束互动
              </Button>
              <Button
                onClick={async () => {
                  try {
                    const r = await api.post(`/live/rooms/${room.id}/overlay-token`);
                    link(room.id, r.data.overlayToken);
                  } catch {
                    fail();
                  }
                }}
              >
                生成新字幕链接
              </Button>
            </Space>
            <div>
              <Typography.Text strong>消息插件</Typography.Text>
              <Checkbox.Group
                disabled={busy}
                value={room.plugins.split(',').filter(Boolean)}
                options={catalog.plugins.map((p) => ({
                  value: p.id,
                  label: p.name + '：' + p.description,
                }))}
                onChange={(v) => void configure(room.status, v as string[])}
              />
            </div>
            {overlay && (
              <Alert
                style={{ marginTop: 16 }}
                message="添加到 OBS 浏览器来源；此链接可读取该直播间消息，重新生成后旧链接失效。"
                description={<Typography.Text copyable>{overlay}</Typography.Text>}
              />
            )}
            <Form
              form={eventForm}
              layout="vertical"
              initialValues={{ kind: 'CHAT', author: '主播' }}
              onFinish={async (v) => {
                try {
                  await api.post(`/live/rooms/${room.id}/events`, v);
                  eventForm.setFieldValue('content', '');
                } catch {
                  fail();
                }
              }}
              style={{ marginTop: 20 }}
            >
              <Form.Item name="kind" label="消息类型">
                <Select
                  options={[
                    { value: 'CHAT', label: '弹幕互动' },
                    { value: 'SUBTITLE', label: '实时字幕' },
                  ]}
                />
              </Form.Item>
              <Form.Item name="author" label="发送者" rules={[{ required: true }]}>
                <Input maxLength={80} />
              </Form.Item>
              <Form.Item name="content" label="内容" rules={[{ required: true, whitespace: true }]}>
                <Input.TextArea maxLength={2000} showCount />
              </Form.Item>
              <Button type="primary" htmlType="submit" disabled={room.status !== 'LIVE'}>
                发送消息
              </Button>
            </Form>
            <div
              role="log"
              aria-live="polite"
              style={{ maxHeight: 320, overflow: 'auto', marginTop: 20 }}
            >
              {events.map((e) => (
                <p key={e.id}>
                  <Tag>{e.kind === 'SUBTITLE' ? '字幕' : '弹幕'}</Tag>
                  <strong>{e.author}：</strong>
                  {e.content}
                </p>
              ))}
            </div>
          </>
        )}
      </Card>
      {room && (
        <>
          <BroadcastPanel key={`broadcast-${room.id}`} roomId={room.id} />
          <ConnectionPanel
            key={`connection-${room.id}`}
            roomId={room.id}
            platform={room.platform}
            live={room.status === 'LIVE'}
          />
          <SpeechPanel key={`speech-${room.id}`} roomId={room.id} live={room.status === 'LIVE'} />
        </>
      )}
      <Card title="MCP 与平台接入" style={{ marginTop: 20 }}>
        <Typography.Paragraph>
          端点：
          <Typography.Text code copyable>
            {location.origin}/api/mcp
          </Typography.Text>
          ，使用 Authorization: Bearer 账号访问令牌；支持 MCP 2025-11-25 的 JSON
          响应模式。仅允许访问令牌所属账号的直播间。
        </Typography.Paragraph>
        <Typography.Paragraph>
          工具：live_rooms、live_catalog、live_events、live_publish。外部适配器可将平台弹幕或语音识别结果发送到
          POST /api/live/rooms/房间ID/events；externalId 用于重复消息去重。接口及示例见项目
          docs/live-platform.md。
        </Typography.Paragraph>
        <Typography.Paragraph>
          插件为当前内置的消息处理链，可逐直播间启停。可信服务端插件可自动注册；第三方插件上传执行与更多平台连接器待扩展。
        </Typography.Paragraph>
      </Card>
    </main>
  );
}
