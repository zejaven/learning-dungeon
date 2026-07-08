import { useSystem } from '@app/engine/systemStore';
import { ui, useLang } from '@app/i18n';

/**
 * Full-screen overlay shown while the app rebuilds and restarts. The backend is
 * down during the rebuild, so this just spins and waits; systemStore reloads the
 * page automatically once the server returns with a new bootId. Rendered once at
 * the app root so it persists across dialog close and screen changes.
 */
export function UpdatingOverlay() {
  const lang = useLang((s) => s.lang);
  const updating = useSystem((s) => s.updating);
  const updateError = useSystem((s) => s.updateError);

  if (!updating) return null;

  return (
    <div className="updating-overlay">
      <div className="updating-box">
        {!updateError && <div className="updating-spinner" />}
        <p>{ui(updateError ? 'settingsRestartTimeout' : 'settingsRestarting', lang)}</p>
        {updateError && (
          <button className="primary" onClick={() => window.location.reload()}>
            {ui('settingsReloadNow', lang)}
          </button>
        )}
      </div>
    </div>
  );
}
