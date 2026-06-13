import type { Mission } from '@app/engine/traceTypes';
import { tl, useLang } from '@app/i18n';

export function MissionPanel({
  missions,
  completed,
}: {
  missions: Mission[];
  completed: Record<string, boolean>;
}) {
  const lang = useLang((s) => s.lang);
  if (missions.length === 0) return null;
  return (
    <div>
      {missions.map((m) => {
        const done = !!completed[m.id];
        return (
          <div key={m.id} className={`mission${done ? ' done' : ''}`}>
            <div className="check">{done ? '✅' : '⬜'}</div>
            <div>
              <div className="title">{tl(m.title, lang)}</div>
              <div className="goal">{tl(m.goal, lang)}</div>
            </div>
          </div>
        );
      })}
    </div>
  );
}
