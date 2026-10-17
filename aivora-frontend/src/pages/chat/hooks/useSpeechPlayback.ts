import { useCallback, useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { api } from '@/services/request';

type Speech = { url: string; truncated: boolean };
type SpeechResponse = {
  audioUrl: string;
  truncated: boolean;
  voice: string;
  language: string;
  model: string;
};
export function useSpeechPlayback(chatId: string) {
  const audio = useRef<HTMLAudioElement>(null);
  const generation = useRef(0);
  const request = useRef<AbortController | null>(null);
  const cache = useRef(new Map<number, Speech>());
  const [speed, setSpeed] = useState(1);
  const [muted, setMuted] = useState(false);
  const settings = useRef({ speed, muted });
  settings.current = { speed, muted };
  const [activeId, setActiveId] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [playing, setPlaying] = useState(false);
  const [url, setUrl] = useState('');
  const [notice, setNotice] = useState('');
  const [readyAttempt, setReadyAttempt] = useState(0);
  const stop = useCallback(() => {
    generation.current++;
    request.current?.abort();
    request.current = null;
    audio.current?.pause();
    setBusy(false);
    setPlaying(false);
  }, []);
  useEffect(() => {
    const element = audio.current;
    if (!element || !url || readyAttempt !== generation.current) return;
    element.src = url;
    element.preservesPitch = true;
    element.defaultPlaybackRate = settings.current.speed;
    element.playbackRate = settings.current.speed;
    element.muted = settings.current.muted;
    element.currentTime = 0;
    void element.play().catch((error) => {
      if (readyAttempt !== generation.current) return;
      setNotice(
        error instanceof DOMException && error.name === 'NotAllowedError'
          ? '浏览器暂未允许播放，请点击这条回复的朗读按钮。'
          : '语音播放失败，请点击朗读重试。',
      );
      setPlaying(false);
    });
    return () => element.pause();
  }, [url, readyAttempt, activeId]);
  useEffect(() => {
    const element = audio.current;
    if (!element) return;
    element.preservesPitch = true;
    element.defaultPlaybackRate = speed;
    element.playbackRate = speed;
    element.muted = muted;
  }, [speed, muted, activeId, url]);
  useEffect(() => {
    const element = audio.current;
    stop();
    for (const speech of cache.current.values()) URL.revokeObjectURL(speech.url);
    cache.current.clear();
    setSpeed(1);
    setMuted(false);
    setActiveId(null);
    setUrl('');
    setNotice('');
    return () => {
      generation.current++;
      request.current?.abort();
      element?.pause();
      for (const speech of cache.current.values()) URL.revokeObjectURL(speech.url);
      cache.current.clear();
    };
  }, [chatId, stop]);
  const play = useCallback(
    async (messageId: number) => {
      stop();
      const attempt = generation.current;
      const controller = new AbortController();
      request.current = controller;
      setActiveId(messageId);
      setBusy(true);
      setNotice('');
      setUrl('');
      try {
        let speech = cache.current.get(messageId);
        if (!speech) {
          const { data } = await api.post<SpeechResponse>(
            '/chats/' + chatId + '/media/speech',
            { messageId },
            { timeout: 170000, signal: controller.signal },
          );
          if (attempt !== generation.current) return;
          const audioData = await api.get<Blob>(data.audioUrl, {
            responseType: 'blob',
            timeout: 30000,
            signal: controller.signal,
          });
          if (attempt !== generation.current) return;
          speech = { url: URL.createObjectURL(audioData.data), truncated: data.truncated };
          cache.current.set(messageId, speech);
          while (cache.current.size > 8) {
            const oldest = cache.current.keys().next().value;
            if (oldest === undefined) break;
            URL.revokeObjectURL(cache.current.get(oldest)!.url);
            cache.current.delete(oldest);
          }
        }
        if (attempt !== generation.current) return;
        setUrl(speech.url);
        setReadyAttempt(attempt);
        if (speech.truncated) setNotice('本次朗读前600字');
      } catch (error) {
        if (attempt !== generation.current || axios.isCancel(error)) return;
        setNotice(
          error instanceof DOMException && error.name === 'NotAllowedError'
            ? '浏览器暂未允许播放，请点击消息的朗读按钮。'
            : axios.isAxiosError(error)
              ? error.response?.data?.message || '语音生成失败，请点击朗读重试。'
              : error instanceof Error
                ? error.message
                : '朗读失败，请重试。',
        );
      } finally {
        if (attempt === generation.current) {
          setBusy(false);
          request.current = null;
        }
      }
    },
    [chatId, stop],
  );
  return {
    audio,
    activeId,
    busy,
    playing,
    url,
    notice,
    play,
    stop,
    setPlaying,
    speed,
    setSpeed,
    muted,
    setMuted,
  };
}
export type SpeechPlayback = ReturnType<typeof useSpeechPlayback>;
