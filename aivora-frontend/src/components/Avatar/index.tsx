import { useState } from 'react';
import { Sparkles } from 'lucide-react';

export function Avatar({
  src,
  name,
  large = false,
}: {
  src?: string;
  name: string;
  large?: boolean;
}) {
  const [bad, B] = useState(false);
  return (
    <div className={large ? 'role-portrait' : 'role-avatar'}>
      {!bad ? (
        <img src={src || '/images/character-night.png'} alt={name} onError={() => B(true)} />
      ) : (
        <Sparkles size={large ? 52 : 24} />
      )}
    </div>
  );
}
