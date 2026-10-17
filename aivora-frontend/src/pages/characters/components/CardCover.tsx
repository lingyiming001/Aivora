import { useState } from 'react';
import { UserRound, BookOpen, NotebookPen, Briefcase, Telescope, Coffee } from 'lucide-react';
import { type Card } from '@/pages/characters/types';

export function CardCover({ card }: { card: Card }) {
  const [failed, F] = useState(false);
  const style = (
    {
      日常陪伴: ['#e9e4db', '#937f63'],
      创意写作: ['#e4e1ee', '#80729a'],
      学习成长: ['#e4eee5', '#6c8c6f'],
      工作效率: ['#dfe9ed', '#5f8493'],
      幻想世界: ['#e0e6ef', '#6d7d9d'],
    } as Record<string, string[]>
  )[card.category] || ['#e3eee8', '#6b9180'];
  const Icon =
    (
      {
        日常陪伴: Coffee,
        创意写作: NotebookPen,
        学习成长: BookOpen,
        工作效率: Briefcase,
        幻想世界: Telescope,
      } as Record<string, typeof Coffee>
    )[card.category] || UserRound;
  return (
    <div className="character-cover" style={{ background: style[0], color: style[1] }}>
      {card.avatar_url && !failed ? (
        <img src={card.avatar_url} alt={card.name} onError={() => F(true)} />
      ) : (
        <>
          <Icon size={52} strokeWidth={1.25} />
          <span>{card.category || 'AI 角色'}</span>
        </>
      )}
    </div>
  );
}
