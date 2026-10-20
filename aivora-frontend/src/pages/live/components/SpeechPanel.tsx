import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Checkbox, Input, Select, Space, Typography } from 'antd';
import { api } from '@/services/request';

type RecognitionResult = { isFinal: boolean; 0: { transcript: string } };
type Recognition = {
  continuous: boolean;
  interimResults: boolean;
  lang: string;
  onresult: ((e: { resultIndex: number; results: ArrayLike<RecognitionResult> }) => void) | null;
  onerror: ((e: { error: string }) => void) | null;
  onend: (() => void) | null;
  start(): void;
  abort(): void;
};
type SpeechWindow = Window & {
  SpeechRecognition?: new () => Recognition;
  webkitSpeechRecognition?: new () => Recognition;
};
export default function SpeechPanel({ roomId, live }: { roomId: number; live: boolean }) {
  const [listening, setListening] = useState(false);
  const [recording, setRecording] = useState(false);
  const [busy, setBusy] = useState(false);
  const [draft, setDraft] = useState('');
  const [interim, setInterim] = useState('');
  const [error, setError] = useState('');
  const [lang, setLang] = useState('zh-CN');
  const [automatic, setAutomatic] = useState(false);
  const [asr, setAsr] = useState(false);
  const [author, setAuthor] = useState('主播');
  const recognition = useRef<Recognition | null>(null);
  const stream = useRef<MediaStream | null>(null);
  const recorder = useRef<MediaRecorder | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const mounted = useRef(true);
  const controller = useRef(new AbortController());
  const pending = useRef(Promise.resolve());
  const queued = useRef(0);
  const acquiring = useRef(false);
  const stopped = useRef(false);
  const publishId = useRef(crypto.randomUUID());
  const Speech =
    (window as SpeechWindow).SpeechRecognition ?? (window as SpeechWindow).webkitSpeechRecognition;
  function appendDraft(text: string) {
    publishId.current = crypto.randomUUID();
    setDraft((old) => (old + ' ' + text).trim());
  }
  function stop() {
    stopped.current = true;
    recognition.current?.abort();
    recognition.current = null;
    if (recorder.current?.state === 'recording') recorder.current.stop();
    stream.current?.getTracks().forEach((t) => t.stop());
    if (timer.current) clearTimeout(timer.current);
    setListening(false);
    setRecording(false);
    setInterim('');
  }
  useEffect(() => {
    mounted.current = true;
    controller.current = new AbortController();
    api
      .get(`/live/rooms/${roomId}/speech`, { signal: controller.current.signal })
      .then((r) => setAsr(r.data.asr))
      .catch(() => {});
    return () => {
      mounted.current = false;
      stopped.current = true;
      recognition.current?.abort();
      if (recorder.current?.state === 'recording') recorder.current.stop();
      stream.current?.getTracks().forEach((t) => t.stop());
      if (timer.current) clearTimeout(timer.current);
      controller.current.abort();
    };
  }, [roomId]);
  useEffect(() => {
    if (!live) stop();
  }, [live]);
  async function send(text: string, externalId: string) {
    await api.post(
      `/live/rooms/${roomId}/events`,
      { kind: 'SUBTITLE', author: author || '主播', content: text, externalId },
      { signal: controller.current.signal },
    );
  }
  function startRecognition() {
    if (!Speech || !live) return;
    setError('');
    stopped.current = false;
    const r = new Speech();
    recognition.current = r;
    r.lang = lang;
    r.continuous = true;
    r.interimResults = true;
    r.onresult = (e) => {
      if (!mounted.current || stopped.current) return;
      let temporary = '';
      for (let i = e.resultIndex; i < e.results.length; i++) {
        const result = e.results[i],
          text = result[0].transcript.trim();
        if (!result.isFinal) {
          temporary += text;
          continue;
        }
        if (!text) continue;
        if (!automatic) {
          appendDraft(text);
          continue;
        }
        if (queued.current >= 8 || text.length > 2000) {
          appendDraft(text);
          setError('字幕发送积压或内容过长，已停止识别，请检查并分段发送。');
          stop();
          return;
        }
        const externalId = `speech:${crypto.randomUUID()}`;
        queued.current++;
        pending.current = pending.current.then(async () => {
          if (!mounted.current) return;
          try {
            await send(text, externalId);
          } catch {
            if (mounted.current) {
              appendDraft(text);
              setError('字幕发送失败，已保留识别内容。请检查网络和直播间状态。');
              stop();
            }
          } finally {
            queued.current--;
          }
        });
      }
      setInterim(temporary);
    };
    r.onerror = (e) => {
      if (mounted.current && !stopped.current) {
        setError(
          e.error === 'not-allowed'
            ? '麦克风或语音服务权限被拒绝，请在浏览器中开启。'
            : '语音识别中断，请重试或使用模型录音识别。',
        );
        stop();
      }
    };
    r.onend = () => {
      if (mounted.current) {
        setListening(false);
        setInterim('');
      }
    };
    try {
      r.start();
      setListening(true);
    } catch {
      setError('无法启动浏览器语音识别，请使用模型录音识别。');
      stop();
    }
  }
  async function record() {
    if (recording) {
      recorder.current?.stop();
      return;
    }
    if (acquiring.current) return;
    acquiring.current = true;
    setBusy(true);
    setError('');
    stopped.current = false;
    try {
      if (!navigator.mediaDevices?.getUserMedia || !window.MediaRecorder) throw new Error();
      const audio = await navigator.mediaDevices.getUserMedia({ audio: true });
      if (!mounted.current || !live || stopped.current) {
        audio.getTracks().forEach((t) => t.stop());
        return;
      }
      stream.current = audio;
      const mime = ['audio/webm', 'audio/mp4', 'audio/ogg'].find((t) =>
        MediaRecorder.isTypeSupported(t),
      );
      if (!mime) throw new Error();
      const r = new MediaRecorder(audio, { mimeType: mime });
      recorder.current = r;
      const chunks: BlobPart[] = [];
      r.ondataavailable = (e) => {
        if (e.data.size) chunks.push(e.data);
      };
      r.onstop = async () => {
        audio.getTracks().forEach((t) => t.stop());
        if (timer.current) clearTimeout(timer.current);
        if (!mounted.current || stopped.current) return;
        setRecording(false);
        setBusy(true);
        try {
          const blob = new Blob(chunks, { type: mime });
          if (blob.size > 4000000 || blob.size < 44) throw new Error();
          const data = await new Promise<string>((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = () => resolve(String(reader.result));
            reader.onerror = reject;
            reader.readAsDataURL(blob);
          });
          const result = await api.post(
            `/live/rooms/${roomId}/speech`,
            { audio: data },
            { timeout: 130000, signal: controller.current.signal },
          );
          if (mounted.current) appendDraft(result.data.text);
        } catch {
          if (mounted.current) setError('录音识别失败，请检查模型配置、直播状态和录音大小。');
        } finally {
          if (mounted.current) setBusy(false);
        }
      };
      r.start();
      setRecording(true);
      timer.current = setTimeout(() => {
        if (r.state === 'recording') r.stop();
      }, 30000);
    } catch {
      stream.current?.getTracks().forEach((t) => t.stop());
      setError('无法使用麦克风，请检查浏览器权限和录音格式支持。');
    } finally {
      acquiring.current = false;
      if (mounted.current) setBusy(false);
    }
  }
  return (
    <Card title="语音字幕" style={{ marginTop: 20 }}>
      <Typography.Paragraph>
        浏览器语音识别可能使用浏览器厂商的在线服务；点击开始后才启用麦克风。模型录音识别使用平台已配置的
        ASR 模型并产生模型用量，每段最多 30 秒。
      </Typography.Paragraph>
      {error && <Alert type="error" message={error} style={{ marginBottom: 12 }} />}
      <Space wrap>
        <Select
          aria-label="字幕语言"
          value={lang}
          onChange={setLang}
          disabled={listening}
          options={[
            { value: 'zh-CN', label: '普通话' },
            { value: 'en-US', label: '英语' },
            { value: 'ja-JP', label: '日语' },
          ]}
        />
        <Input
          aria-label="字幕发送者"
          value={author}
          maxLength={80}
          onChange={(e) => setAuthor(e.target.value)}
          disabled={listening}
          style={{ width: 150 }}
        />
        <Checkbox
          checked={automatic}
          onChange={(e) => setAutomatic(e.target.checked)}
          disabled={listening}
        >
          浏览器确认识别结果后自动上屏
        </Checkbox>
        <Button
          disabled={!live || !Speech || recording || busy}
          onClick={listening ? stop : startRecognition}
        >
          {listening ? '停止实时识别' : '开始实时识别'}
        </Button>
        <Button
          loading={busy}
          disabled={!live || !asr || listening || busy}
          onClick={() => void record()}
        >
          {recording ? '结束录音并识别' : '模型录音识别'}
        </Button>
      </Space>
      {!Speech && (
        <Typography.Paragraph type="secondary">
          当前浏览器不提供实时语音识别，可使用模型录音识别。
        </Typography.Paragraph>
      )}
      <div aria-live="polite" style={{ margin: '12px 0', minHeight: 24 }}>
        {interim && <Typography.Text type="secondary">识别中：{interim}</Typography.Text>}
      </div>
      <Input.TextArea
        aria-label="字幕草稿"
        value={draft}
        onChange={(e) => {
          setDraft(e.target.value);
          publishId.current = crypto.randomUUID();
        }}
        placeholder="识别结果会保留在这里，可以编辑后发送"
        rows={3}
        showCount
      />
      <Space style={{ marginTop: 12 }}>
        <Button
          type="primary"
          disabled={!live || !draft.trim() || draft.length > 2000 || busy}
          onClick={async () => {
            setBusy(true);
            try {
              await send(draft, `speech:${publishId.current}`);
              setDraft('');
              publishId.current = crypto.randomUUID();
            } catch {
              setError('发送失败，草稿已保留，可以重试。');
            } finally {
              if (mounted.current) setBusy(false);
            }
          }}
        >
          发送字幕
        </Button>
        <Button
          disabled={!draft || busy}
          onClick={() => {
            setDraft('');
            publishId.current = crypto.randomUUID();
          }}
        >
          清空草稿
        </Button>
      </Space>
    </Card>
  );
}
