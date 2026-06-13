import type { Mission } from '@app/engine/traceTypes';

export function MissionPanel({
  missions,
  completed,
}: {
  missions: Mission[];
  completed: Record<string, boolean>;
}) {
  if (missions.length === 0) return null;
  return (
    <div>
      {missions.map((m) => {
        const done = !!completed[m.id];
        return (
          <div key={m.id} className={`mission${done ? ' done' : ''}`}>
            <div className="check">{done ? '✅' : '⬜'}</div>
            <div>
              <div className="title">{m.title}</div>
              <div className="goal">{m.goal}</div>
            </div>
          </div>
        );
      })}
    </div>
  );
}
