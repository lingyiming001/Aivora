import { useEffect, useState } from 'react';
import { Alert, Button, Card, Input, Space, Typography } from 'antd';
import { api } from '@/services/request';
type Broadcast = { enabled: boolean; rtmpUrl: string; streamId?: string };
export default function BroadcastPanel({ roomId }: { roomId: number }) {
  const [config, setConfig] = useState<Broadcast>();
  const [key, setKey] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => {
    const controller = new AbortController();
    api
      .get(`/live/rooms/${roomId}/broadcast`, { signal: controller.signal })
      .then((r) => setConfig(r.data))
      .catch(() => {
        if (!controller.signal.aborted) setError('无法读取推流配置，请刷新重试。');
      });
    return () => controller.abort();
  }, [roomId]);
  return (
    <Card title="视频直播" style={{ marginTop: 20 }}>
      {error && <Alert type="error" message={error} style={{ marginBottom: 12 }} />}
      {!config?.enabled ? (
        <Typography.Paragraph>
          视频直播需要启用媒体服务。部署入口：deploy/compose.media.yml；配置步骤见
          deploy/README.md。
        </Typography.Paragraph>
      ) : (
        <>
          <Typography.Paragraph>
            在 OBS 中选择自定义推流，填写下方服务器和推流密钥，然后开始互动并启动 OBS 推流。HLS
            播放有数秒延迟；结束互动后观看入口立即关闭，OBS 需同时停止推流。
          </Typography.Paragraph>
          <Typography.Paragraph>
            推流服务器：<Typography.Text copyable>{config.rtmpUrl}</Typography.Text>
          </Typography.Paragraph>
          <Space wrap>
            <Button
              loading={busy}
              onClick={async () => {
                setBusy(true);
                setError('');
                try {
                  const { data } = await api.post(`/live/rooms/${roomId}/broadcast`);
                  setConfig({ enabled: true, rtmpUrl: data.rtmpUrl, streamId: data.streamId });
                  setKey(data.streamKey);
                } catch {
                  setError('生成推流配置失败，请检查媒体服务是否启用。');
                } finally {
                  setBusy(false);
                }
              }}
            >
              生成新推流密钥与观看链接
            </Button>
          </Space>
          {key && (
            <div style={{ marginTop: 12 }}>
              <Typography.Paragraph>
                推流密钥仅本次显示，重新生成后请更新 OBS 配置。
              </Typography.Paragraph>
              <Input.Password readOnly value={key} aria-label="推流密钥" />
            </div>
          )}
          {config.streamId && (
            <Alert
              style={{ marginTop: 16 }}
              type="info"
              message="拥有此观看链接的人可在互动开启期间观看视频。重新生成推流配置可撤销旧观看链接。"
              description={
                <Typography.Text
                  copyable
                >{`${location.origin}/live/watch/${config.streamId}`}</Typography.Text>
              }
            />
          )}
          <Typography.Paragraph type="secondary" style={{ marginTop: 12 }}>
            将字幕页面添加到 OBS 浏览器来源，即可把字幕合成进视频。推荐 H.264 视频、AAC 音频和 2
            秒关键帧间隔。
          </Typography.Paragraph>
        </>
      )}
    </Card>
  );
}
