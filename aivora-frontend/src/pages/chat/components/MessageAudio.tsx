import { useEffect, useState } from 'react';
import { Play, Pause, Volume2, VolumeX, LoaderCircle } from 'lucide-react';
import { type SpeechPlayback } from '../hooks/useSpeechPlayback';
const clock = (seconds: number) =>
  `${Math.floor(seconds / 60)}:${Math.floor(seconds % 60)
    .toString()
    .padStart(2, '0')}`;
export default function MessageAudio({ playback }: { playback: SpeechPlayback }) {
  const [time, T] = useState(0),
    [duration, D] = useState(0),
    [error, E] = useState('');
  const { speed, setSpeed: S, muted, setMuted: M } = playback;
  useEffect(() => {
    T(0);
    D(0);
    E('');
  }, [playback.url]);
  async function toggle() {
    const audio = playback.audio.current;
    if (!audio || !playback.url) return;
    E('');
    if (!audio.paused) {
      audio.pause();
      return;
    }
    if (audio.ended) audio.currentTime = 0;
    try {
      await audio.play();
    } catch {
      E('暂时无法播放，请点击朗读重试。');
    }
  }
  const fraction = duration ? Math.min(100, (time / duration) * 100) : 0;
  return (
    <div className="message-audio-block">
      <audio
        ref={playback.audio}
        hidden
        preload="metadata"
        onPlay={() => playback.setPlaying(true)}
        onPause={() => playback.setPlaying(false)}
        onEnded={() => playback.setPlaying(false)}
        onLoadedMetadata={(e) =>
          D(Number.isFinite(e.currentTarget.duration) ? e.currentTarget.duration : 0)
        }
        onDurationChange={(e) =>
          D(Number.isFinite(e.currentTarget.duration) ? e.currentTarget.duration : 0)
        }
        onTimeUpdate={(e) => T(e.currentTarget.currentTime)}
        onVolumeChange={(e) => M(e.currentTarget.muted)}
        onError={() => {
          playback.setPlaying(false);
          E('语音加载失败，请重新朗读。');
        }}
      />
      <div className="message-audio-player" role="group" aria-label="回复语音播放器">
        <button
          className="message-audio-play"
          type="button"
          disabled={playback.busy || !playback.url}
          aria-label={playback.playing ? '暂停语音' : '播放语音'}
          onClick={() => void toggle()}
        >
          {playback.busy ? (
            <LoaderCircle size={17} className="spin" />
          ) : playback.playing ? (
            <Pause size={16} />
          ) : (
            <Play size={16} />
          )}
        </button>
        <div className="message-audio-track">
          <div className="message-audio-caption">
            <span>{playback.busy ? '正在生成语音' : '语音回复'}</span>
            <small>
              {clock(time)} / {clock(duration)}
            </small>
          </div>
          <input
            type="range"
            min={0}
            max={duration || 1}
            step={0.1}
            value={Math.min(time, duration || 1)}
            disabled={!duration || playback.busy}
            aria-label="语音播放进度"
            style={{
              background: `linear-gradient(to right,var(--av-accent) ${fraction}%,var(--av-line) ${fraction}%)`,
            }}
            onChange={(e) => {
              const value = Number(e.target.value);
              if (playback.audio.current) playback.audio.current.currentTime = value;
              T(value);
            }}
          />
        </div>
        <button
          type="button"
          className="message-audio-speed"
          disabled={!playback.url}
          aria-label={'播放速度 ' + speed + '倍'}
          onClick={() => {
            const options = [1, 1.25, 1.5, 2];
            const value = options[(options.indexOf(speed) + 1) % options.length];
            if (playback.audio.current) playback.audio.current.playbackRate = value;
            S(value);
          }}
        >
          {speed}×
        </button>
        <button
          type="button"
          className="message-audio-volume"
          disabled={!playback.url}
          aria-label={muted ? '取消静音' : '静音'}
          onClick={() => {
            if (playback.audio.current) playback.audio.current.muted = !muted;
          }}
        >
          {muted ? <VolumeX size={16} /> : <Volume2 size={16} />}
        </button>
      </div>
      {(error || playback.notice) && (
        <small className="message-audio-notice" role="status">
          {error || playback.notice}
          {error && (
            <button type="button" onClick={() => void playback.play(playback.activeId!)}>
              重新朗读
            </button>
          )}
        </small>
      )}
    </div>
  );
}
