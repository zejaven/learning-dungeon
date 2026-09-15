import { useState } from 'react';
import { domainById } from '@app/domains';
import { useDomain } from '@app/engine/domainStore';
import { useOffline } from '@app/engine/offlineStore';
import { cacheAvailable } from '@app/engine/offline/cache';
import { useReview } from '@app/engine/reviewStore';
import { useSystem } from '@app/engine/systemStore';
import { tl, ui, useLang } from '@app/i18n';

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

          <OfflineSection />

          <ReviewSection />

          {status?.version && <p className="settings-version">{status.version}</p>}
        </div>
        <div className="dialog-foot">
          <button onClick={onClose}>{ui('close', lang)}</button>
        </div>
      </div>
    </div>
  );
}

/**
 * Spaced-repetition section. The only destructive action left for review, and
 * deliberately buried here: under the schedule, "bring the answered questions
 * back" is no longer a useful button but a way to throw away every repetition
 * date the app has assigned. Drilling one topic without that cost is what ▶ in
 * the review tree is for.
 *
 * Two-step on purpose, and never queued offline — a wipe replayed an hour later
 * would silently discard a session the user had just finished.
 */
function ReviewSection() {
  const lang = useLang((s) => s.lang);
  const pool = useReview((s) => s.pool);
  const resetSchedule = useReview((s) => s.resetSchedule);
  const [armed, setArmed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);

  // The home screen only loads `pool`, so this must not depend on `topics`.
  if (pool.length === 0) return null;

  async function reset() {
    setBusy(true);
    try {
      await resetSchedule();
      setDone(true);
    } catch {
      /* the store surfaces the error; keep the dialog usable */
    } finally {
      setBusy(false);
      setArmed(false);
    }
  }

  return (
    <div className="settings-review">
      <div className="panel-title settings-section-title">{ui('settingsReviewTitle', lang)}</div>
      <div className="settings-action">
        {armed ? (
          <button className="settings-action-btn danger" disabled={busy} onClick={() => void reset()}>
            {ui('settingsReviewResetConfirm', lang)}
          </button>
        ) : (
          <button className="settings-action-btn" disabled={busy} onClick={() => setArmed(true)}>
            {ui('settingsReviewReset', lang)}
          </button>
        )}
        <p className="settings-action-desc">
          {done ? ui('settingsReviewResetDone', lang) : ui('settingsReviewResetDesc', lang)}
        </p>
      </div>
    </div>
  );
}

/**
 * Offline section: download the open domain for use with no backend, see what
 * is stored, and push out answers that were given while disconnected.
 *
 * Caches only exist in a secure context, so in a plain-http LAN session this
 * degrades to an explanation instead of buttons that would do nothing.
 */
function OfflineSection() {
  const lang = useLang((s) => s.lang);
  const domainId = useDomain((s) => s.domainId);
  const domain = domainById(domainId);

  const stored = useOffline((s) => s.stored);
  const bytes = useOffline((s) => s.bytes);
  const pending = useOffline((s) => s.pending);
  const syncing = useOffline((s) => s.syncing);
  const online = useOffline((s) => s.online);
  const download = useOffline((s) => s.download);
  const downloadDomain = useOffline((s) => s.downloadDomain);
  const clearDownloaded = useOffline((s) => s.clearDownloaded);
  const sync = useOffline((s) => s.sync);

  const supported = cacheAvailable() && typeof navigator !== 'undefined' && 'serviceWorker' in navigator;
  const percent = download.total > 0 ? Math.round((download.done / download.total) * 100) : 0;

  return (
    <div className="settings-offline">
      <div className="panel-title settings-section-title">{ui('offlineTitle', lang)}</div>

      {!supported && <p className="settings-note">{ui('offlineUnsupported', lang)}</p>}

      {supported && (
        <>
          <div className="settings-action">
            <button
              className="settings-action-btn"
              disabled={download.running || !online}
              onClick={() => void downloadDomain(domainId)}
            >
              {download.running ? ui('offlineDownloading', lang) : ui('offlineDownload', lang)}
            </button>
            <span className="settings-offline-domain">
              {domain.icon} {tl(domain.title, lang)}
            </span>
            <p className="settings-action-desc">{ui('offlineDownloadDesc', lang)}</p>
          </div>

          {download.running && (
            <div className="offline-progress">
              <div className="offline-progress-bar" style={{ width: `${percent}%` }} />
              <span className="offline-progress-text">
                {download.done} / {download.total}
              </span>
            </div>
          )}

          <p className="settings-note">
            {stored > 0
              ? `${stored} ${ui('offlineStored', lang)}${bytes ? ` · ${formatBytes(bytes)}` : ''}`
              : ui('offlineNothingStored', lang)}
          </p>

          {pending > 0 && (
            <div className="settings-action">
              <button
                className="settings-action-btn"
                disabled={!online || syncing}
                onClick={() => void sync()}
              >
                {syncing ? ui('offlineSyncing', lang) : ui('offlineSyncNow', lang)}
              </button>
              <span className="settings-offline-domain">
                {pending} {ui('offlinePending', lang)}
              </span>
            </div>
          )}

          {stored > 0 && (
            <div className="settings-action">
              <button
                className="settings-action-btn"
                disabled={download.running}
                onClick={() => void clearDownloaded()}
              >
                {ui('offlineClear', lang)}
              </button>
              <p className="settings-action-desc">{ui('offlineClearDesc', lang)}</p>
            </div>
          )}
        </>
      )}
    </div>
  );
}

function formatBytes(bytes: number): string {
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
