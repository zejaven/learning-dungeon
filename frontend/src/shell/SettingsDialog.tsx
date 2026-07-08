import { useSystem } from '@app/engine/systemStore';
import { ui, useLang } from '@app/i18n';

/**
 * Settings modal: rebuild+restart the app. "Update" also pulls from GitHub first
 * (with a badge showing how many commits the upstream is ahead); "Restart"
 * rebuilds from the current local files. Actions are disabled with an
 * explanation when the deployment can't support them (see SystemStatus).
 */
export function SettingsDialog({ onClose }: { onClose: () => void }) {
  const lang = useLang((s) => s.lang);
  const status = useSystem((s) => s.status);
  const triggerUpdate = useSystem((s) => s.triggerUpdate);

  const supervised = status?.supervised ?? false;
  const canRebuild = status?.canRebuild ?? false;
  const canPull = status?.canPull ?? false;
  const behind = status?.behind ?? -1;

  const canUpdate = supervised && canRebuild && canPull;
  const canRestart = supervised && canRebuild;

  // One explanatory note about the most fundamental missing capability.
  const note = !supervised
    ? ui('settingsUnsupervised', lang)
    : !canRebuild
      ? ui('settingsNoSource', lang)
      : !canPull
        ? ui('settingsNoGit', lang)
        : null;

  async function run(pull: boolean) {
    onClose();
    await triggerUpdate(pull);
  }

  return (
    <div className="dialog-backdrop" onClick={onClose}>
      <div className="dialog settings-dialog" onClick={(e) => e.stopPropagation()}>
        <div className="dialog-head">
          <h2>{ui('settingsTitle', lang)}</h2>
          <button onClick={onClose}>✕</button>
        </div>
        <div className="dialog-body">
          <div className="settings-action">
            <button className="primary settings-action-btn" disabled={!canUpdate} onClick={() => run(true)}>
              {ui('settingsUpdate', lang)}
            </button>
            {behind > 0 && <span className="update-badge">{behind}</span>}
            <p className="settings-action-desc">{ui('settingsUpdateDesc', lang)}</p>
          </div>

          <div className="settings-action">
            <button className="settings-action-btn" disabled={!canRestart} onClick={() => run(false)}>
              {ui('settingsRestart', lang)}
            </button>
            <p className="settings-action-desc">{ui('settingsRestartDesc', lang)}</p>
          </div>

          {note && <p className="settings-note">{note}</p>}
          {status?.version && <p className="settings-version">{status.version}</p>}
        </div>
        <div className="dialog-foot">
          <button onClick={onClose}>{ui('close', lang)}</button>
        </div>
      </div>
    </div>
  );
}
