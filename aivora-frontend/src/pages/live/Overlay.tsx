import { useEffect, useState } from 'react';
import axios from 'axios';
import { API_BASE_URL } from '@/config/env';
export default function LiveOverlay() {
  const [text, setText] = useState('');
  useEffect(() => {
    const match = location.hash.match(/^#(\d+)\/([a-f0-9-]+)$/);
    if (!match) return;
    const previous = document.body.style.background;
    const rootBackground = document.documentElement.style.background;
    document.documentElement.style.background = 'transparent';
    document.body.style.background = 'transparent';
    let active = true;
    let after = 0;
    let timer: ReturnType<typeof setTimeout>;
    let clear: ReturnType<typeof setTimeout>;
    async function poll() {
      try {
        const r = await axios.get(`${API_BASE_URL}/live-overlay/${match![1]}`, {
          params: { token: match![2], after },
        });
        if (active && r.data.length) {
          after = r.data[r.data.length - 1].id;
          const subtitles = r.data.filter(
            (e: { kind: string; created_at: string }) =>
              e.kind === 'SUBTITLE' && Date.now() - new Date(e.created_at).getTime() < 10000,
          );
          if (subtitles.length) {
            setText(subtitles[subtitles.length - 1].content);
            clearTimeout(clear);
            clear = setTimeout(() => setText(''), 10000);
          }
        }
      } catch {
        if (active) setText('');
      }
      if (active) timer = setTimeout(poll, 1000);
    }
    void poll();
    return () => {
      active = false;
      clearTimeout(timer);
      clearTimeout(clear);
      document.body.style.background = previous;
      document.documentElement.style.background = rootBackground;
    };
  }, []);
  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        background: 'transparent',
        display: 'flex',
        alignItems: 'end',
        justifyContent: 'center',
        padding: 40,
        pointerEvents: 'none',
      }}
    >
      {text && (
        <div
          style={{
            color: 'white',
            background: 'rgba(0,0,0,.7)',
            borderRadius: 12,
            padding: '12px 24px',
            fontSize: 36,
            textAlign: 'center',
            maxWidth: '90%',
            overflowWrap: 'anywhere',
          }}
        >
          {text}
        </div>
      )}
    </div>
  );
}
