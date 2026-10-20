import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Typography } from 'antd';
import { useParams } from 'react-router-dom';
export default function LiveWatch() {
  const { stream } = useParams();
  const video = useRef<HTMLVideoElement>(null);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  const [state, setState] = useState('加载直播中');
  useEffect(() => {
    setError('');
    setState('加载直播中');
    if (!stream || !/^[a-f0-9-]{36}$/i.test(stream) || !video.current) {
      setError('观看链接无效。');
      return;
    }
    let disposed = false;
    let destroy: (() => void) | undefined;
    const element = video.current;
    const source = `/api/live-view/${stream}/index.m3u8`;
    async function load() {
      if (element.canPlayType('application/vnd.apple.mpegurl')) {
        element.src = source;
        return;
      }
      const { default: Hls } = await import('hls.js');
      if (disposed) return;
      if (!Hls.isSupported()) {
        setError('当前浏览器不支持直播播放，请使用支持 HLS 的浏览器。');
        return;
      }
      const player = new Hls();
      destroy = () => player.destroy();
      player.on(Hls.Events.MANIFEST_PARSED, () => setState('直播已就绪，点击播放'));
      player.on(Hls.Events.ERROR, (_event, data) => {
        if (data.fatal && !disposed) {
          setError('直播尚未开始、媒体服务不可用或链接已失效。请稍后重试。');
          player.destroy();
        }
      });
      player.loadSource(source);
      player.attachMedia(element);
    }
    void load().catch(() => {
      if (!disposed) setError('无法加载播放器，请重试。');
    });
    return () => {
      disposed = true;
      destroy?.();
      element.pause();
      element.removeAttribute('src');
      element.load();
    };
  }, [stream, retry]);
  return (
    <main style={{ maxWidth: 1100, padding: 24, margin: 'auto' }}>
      <Typography.Title level={2}>Aivora 直播</Typography.Title>
      <Typography.Paragraph>{state}</Typography.Paragraph>
      {error && (
        <Alert
          type="warning"
          message={error}
          action={<Button onClick={() => setRetry((v) => v + 1)}>重试</Button>}
          style={{ marginBottom: 16 }}
        />
      )}
      <video
        ref={video}
        controls
        playsInline
        onLoadedMetadata={() => setState('直播已就绪，点击播放')}
        onPlaying={() => setState('正在直播')}
        onError={() => setError('尚未收到视频，请确认主播正在推流后重试。')}
        style={{ width: '100%', background: '#101512', aspectRatio: '16 / 9' }}
      />
    </main>
  );
}
