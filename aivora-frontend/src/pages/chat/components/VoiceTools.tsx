import { useEffect, useRef, useState, type MutableRefObject } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Mic, Square, LoaderCircle, ImagePlus, Volume2, WandSparkles } from 'lucide-react';
import { api } from '@/services/request';
import { useAuth } from '@/hooks/useAuth';
import axios from 'axios';
import { type SpeechPlayback } from '../hooks/useSpeechPlayback';
import MessageAudio from './MessageAudio';
import GeneratedImageCard from './GeneratedImageCard';
import { X } from 'lucide-react';
import { type ChatAttachment } from '../types';
import { Switch } from 'antd';
import { Phone } from 'lucide-react';

export const explain = (e: unknown) =>
  axios.isAxiosError(e)
    ? e.response?.data?.message || '处理失败，请重试'
    : e instanceof Error
      ? e.message
      : '处理失败，请重试';

export function SpeechButton({
  messageId,
  playback,
}: {
  messageId: number;
  playback: SpeechPlayback;
}) {
  const active = playback.activeId === messageId;
  if (active && (playback.url || playback.busy || playback.notice))
    return (
      <div className="speech-control">
        <MessageAudio playback={playback} />
      </div>
    );
  return (
    <div className="speech-control">
      <button type="button" aria-label="朗读回复" onClick={() => void playback.play(messageId)}>
        <Volume2 size={13} />
        朗读
      </button>
    </div>
  );
}

export default function VoiceTools({
  chatId,
  onText,
  disabled,
  voiceChat,
  onVoiceChatChange,
  onRecordStart,
  onAttachment,
  onProcessingChange,
  attachmentTools,
}: {
  chatId: string;
  onText: (text: string) => void;
  disabled: boolean;
  voiceChat: boolean;
  onVoiceChatChange: (enabled: boolean) => void;
  onRecordStart: () => void;
  onAttachment: (attachment: ChatAttachment) => void;
  onProcessingChange: (processing: boolean) => void;
  attachmentTools: MutableRefObject<{
    image: (file: File) => Promise<void>;
    draw: () => void;
  } | null>;
}) {
  const token = useAuth((s) => s.tokens);
  const cap = useQuery({
    queryKey: ['media-capabilities', chatId, !!token],
    enabled: !!token,
    queryFn: async () =>
      (
        await api.get<{ asr: boolean; tts: boolean; vision: boolean; image: boolean }>(
          '/chats/' + chatId + '/media/capabilities',
        )
      ).data,
    retry: false,
  });
  const [recording, R] = useState(false),
    [busy, B] = useState(false),
    [error, E] = useState(''),
    [drawOpen, DO] = useState(false),
    [prompt, PR] = useState(''),
    [picture, PI] = useState<ChatAttachment | null>(null);
  const recorder = useRef<MediaRecorder | null>(null),
    stream = useRef<MediaStream | null>(null),
    timer = useRef<ReturnType<typeof setTimeout> | null>(null),
    mounted = useRef(true),
    file = useRef<HTMLInputElement>(null);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      if (timer.current) clearTimeout(timer.current);
      if (recorder.current?.state === 'recording') recorder.current.stop();
      stream.current?.getTracks().forEach((t) => t.stop());
    };
  }, [chatId]);
  const processingCallback = useRef(onProcessingChange);
  processingCallback.current = onProcessingChange;
  useEffect(() => {
    processingCallback.current(busy || recording);
  }, [busy, recording]);
  useEffect(() => () => processingCallback.current(false), []);
  async function record() {
    if (recording) {
      recorder.current?.stop();
      return;
    }
    if (disabled || busy) return;
    onRecordStart();
    E('');
    try {
      if (!navigator.mediaDevices?.getUserMedia || !window.MediaRecorder)
        throw new Error('浏览器不支持录音，请使用 Chrome 或 Safari');
      stream.current = await navigator.mediaDevices.getUserMedia({ audio: true });
      if (!mounted.current) {
        stream.current.getTracks().forEach((t) => t.stop());
        return;
      }
      const type = ['audio/webm', 'audio/mp4', 'audio/ogg'].find((t) =>
        MediaRecorder.isTypeSupported(t),
      );
      if (!type) throw new Error('浏览器录音格式暂不支持');
      const r = new MediaRecorder(stream.current, { mimeType: type });
      recorder.current = r;
      const chunks: BlobPart[] = [];
      r.ondataavailable = (e) => {
        if (e.data.size) chunks.push(e.data);
      };
      r.onstop = async () => {
        if (timer.current) clearTimeout(timer.current);
        stream.current?.getTracks().forEach((t) => t.stop());
        if (!mounted.current) return;
        R(false);
        B(true);
        try {
          const blob = new Blob(chunks, { type });
          if (blob.size > 4000000) throw new Error('录音过大，请缩短后重试');
          const reader = new FileReader();
          const data = await new Promise<string>((resolve, reject) => {
            reader.onload = () => resolve(String(reader.result));
            reader.onerror = reject;
            reader.readAsDataURL(blob);
          });
          const { data: result } = await api.post<{ text: string }>(
            '/chats/' + chatId + '/media/transcribe',
            { audio: data },
            { timeout: 130000 },
          );
          if (mounted.current) onText(result.text);
        } catch (e) {
          if (mounted.current) E(explain(e));
        } finally {
          if (mounted.current) B(false);
        }
      };
      r.start();
      R(true);
      timer.current = setTimeout(() => r.state === 'recording' && r.stop(), 60000);
    } catch (e) {
      stream.current?.getTracks().forEach((t) => t.stop());
      E(explain(e));
    }
  }
  async function image(f?: File) {
    if (!f || disabled || busy) return;
    B(true);
    E('');
    let url = '';
    try {
      if (!['image/png', 'image/jpeg', 'image/webp'].includes(f.type) || f.size > 5 * 1024 * 1024)
        throw new Error('请选择5MB以内的PNG/JPEG/WebP图片');
      url = URL.createObjectURL(f);
      const img = new Image();
      img.src = url;
      await img.decode();
      const canvas = document.createElement('canvas');
      const scale = Math.min(512 / img.width, 512 / img.height, 1);
      canvas.width = Math.max(1, Math.round(img.width * scale));
      canvas.height = Math.max(1, Math.round(img.height * scale));
      canvas.getContext('2d')!.drawImage(img, 0, 0, canvas.width, canvas.height);
      let data = canvas.toDataURL('image/jpeg', 0.7);
      for (const quality of [0.6, 0.5, 0.4]) {
        if (data.length <= 290000) break;
        data = canvas.toDataURL('image/jpeg', quality);
      }
      if (data.length > 290000) throw new Error('图片过大，请换一张图片');
      const { data: result } = await api.post<ChatAttachment>(
        '/chats/' + chatId + '/media/describe',
        { image: data },
        { timeout: 130000 },
      );
      if (mounted.current) onAttachment(result);
    } catch (e) {
      if (mounted.current) E(explain(e));
    } finally {
      if (url) URL.revokeObjectURL(url);
      if (file.current) file.current.value = '';
      if (mounted.current) B(false);
    }
  }
  async function draw() {
    if (disabled || busy || !prompt.trim()) return;
    B(true);
    E('');
    try {
      const { data } = await api.post<ChatAttachment>(
        '/chats/' + chatId + '/media/draw',
        { prompt: prompt.trim() },
        { timeout: 280000 },
      );
      if (mounted.current) PI(data);
    } catch (e) {
      if (mounted.current) E(explain(e));
    } finally {
      if (mounted.current) B(false);
    }
  }
  attachmentTools.current = { image, draw: () => DO(true) };
  return (
    <div className="voice-tools">
      <div>
        <label className="voice-chat-switch">
          <Phone size={15} />
          <span>语音聊天</span>
          <Switch
            size="small"
            aria-label="语音聊天总开关"
            checked={voiceChat}
            disabled={!voiceChat && (disabled || !token || !cap.data?.tts)}
            onChange={onVoiceChatChange}
          />
          <small>{voiceChat ? '回复自动朗读' : '手动朗读'}</small>
        </label>
        <button
          type="button"
          disabled={disabled || busy || !cap.data?.asr}
          onClick={() => void record()}
        >
          {recording ? (
            <Square size={14} />
          ) : busy ? (
            <LoaderCircle className="spin" size={14} />
          ) : (
            <Mic size={14} />
          )}{' '}
          {recording ? '结束录音' : '语音输入'}
        </button>
        <button
          type="button"
          disabled={disabled || busy || recording || !cap.data?.vision}
          className="legacy-attachment-tool"
          onClick={() => file.current?.click()}
        >
          <ImagePlus size={14} />
          图片理解
        </button>
        <button
          type="button"
          disabled={disabled || busy || recording || !cap.data?.image}
          className="legacy-attachment-tool"
          onClick={() => DO(!drawOpen)}
        >
          <WandSparkles size={14} />
          绘图
        </button>
        <input
          ref={file}
          type="file"
          className="sr-only"
          accept="image/png,image/jpeg,image/webp"
          aria-label="选择聊天图片"
          onChange={(e) => void image(e.target.files?.[0])}
        />
        <span>
          {recording
            ? '正在录音，最长60秒'
            : busy
              ? '正在识别…'
              : !token
                ? '登录开启语音与图片'
                : '识别后可编辑，发送按角色标价扣费'}
        </span>
      </div>
      {drawOpen && (
        <section className="chat-draw image-studio">
          <div className="image-studio-heading">
            <div>
              <WandSparkles size={18} />
              <div>
                <strong>图像创作</strong>
                <p>把想象变成画面</p>
              </div>
            </div>
            <button
              type="button"
              aria-label="收起图像创作"
              disabled={busy}
              onClick={() => DO(false)}
            >
              <X size={17} />
            </button>
          </div>
          <label htmlFor="draw-prompt">描述你的画面</label>
          <textarea
            id="draw-prompt"
            maxLength={800}
            rows={2}
            placeholder="例如：海边的独立书店，暖色灯光与窗外的蓝色暮光"
            value={prompt}
            disabled={busy}
            onChange={(e) => PR(e.target.value)}
          />
          <div className="image-studio-submit">
            <span>生成后可继续编辑或加入聊天</span>
            <button
              type="button"
              disabled={disabled || busy || !prompt.trim()}
              onClick={() => void draw()}
            >
              {busy ? <LoaderCircle size={14} className="spin" /> : <WandSparkles size={14} />}{' '}
              {busy ? '正在创作…' : '生成图片'}
            </button>
          </div>
          {picture && (
            <GeneratedImageCard
              picture={picture}
              chatId={chatId}
              disabled={disabled || busy}
              canChat={!!token}
              onBusy={B}
              onEdited={PI}
              onAdd={(image) => {
                onAttachment(image);
                DO(false);
              }}
            />
          )}
        </section>
      )}
      {(error || cap.isError) && <small role="alert">{error || '语音配置读取失败'}</small>}
    </div>
  );
}
