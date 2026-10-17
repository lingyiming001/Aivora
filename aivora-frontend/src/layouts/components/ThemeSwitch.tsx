import { useState } from 'react';
import { Sun, Moon, Leaf } from 'lucide-react';
import { type Theme } from '@/layouts/components/types';

export const themes = [
  { id: 'forest', name: '森绿', Icon: Leaf },
  { id: 'sand', name: '暖砂', Icon: Sun },
  { id: 'midnight', name: '午夜', Icon: Moon },
] as const;

export function initializeTheme() {
  const stored = localStorage.getItem('aivora_theme');
  document.documentElement.dataset.theme = themes.some((t) => t.id === stored) ? stored! : 'forest';
}

export default function ThemeSwitch() {
  const [theme, T] = useState<Theme>((document.documentElement.dataset.theme as Theme) || 'forest');
  return (
    <div className="theme-switch">
      <span>主题风格</span>
      <div role="group" aria-label="选择主题">
        {themes.map(({ id, name, Icon }) => (
          <button
            type="button"
            key={id}
            aria-label={name + '主题'}
            aria-pressed={theme === id}
            onClick={() => {
              T(id);
              localStorage.setItem('aivora_theme', id);
              document.documentElement.dataset.theme = id;
            }}
          >
            <Icon size={13} />
            {name}
          </button>
        ))}
      </div>
    </div>
  );
}
