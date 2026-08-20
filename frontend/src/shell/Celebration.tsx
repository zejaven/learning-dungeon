import { useStore } from '@app/engine/store';
import { ui, useLang } from '@app/i18n';
import { Fireworks } from './Fireworks';

/**
 * Fireworks + congratulations overlay shown once a topic is finished — the last
 * unit of its lesson, or its last boss-fight question when it has no lesson.
 * Driven by the global `celebrating` flag (raised by `celebrateTopic`), so it
 * works wherever the finish happens (workspace, home/theory or lesson).
 */
export function Celebration() {
  const celebrating = useStore((s) => s.celebrating);
  const setCelebrating = useStore((s) => s.setCelebrating);
  const lang = useLang((s) => s.lang);

  if (!celebrating) return null;
  return (
    <>
      <Fireworks />
      <div className="celebrate-card">
        <div className="celebrate-emoji">🏆</div>
        <h2>{ui('congratsTitle', lang)}</h2>
        <p>{ui('congratsBody', lang)}</p>
        <button className="primary" onClick={() => setCelebrating(false)}>
          {ui('celebrateClose', lang)}
        </button>
      </div>
    </>
  );
}
