import { useOffline } from '@app/engine/offlineStore';
import { ui, useLang } from '@app/i18n';

/**
 * Header chip that only exists when something is off: the backend is
 * unreachable, or answers are waiting to be sent. Silent in the normal case —
 * a permanent "online" light would just be noise.
 */
export function OfflineBadge() {
  const lang = useLang((s) => s.lang);
  const online = useOffline((s) => s.online);
  const pending = useOffline((s) => s.pending);
  const syncing = useOffline((s) => s.syncing);
  const sync = useOffline((s) => s.sync);

  if (online && pending === 0) return null;

  const title = !online
    ? ui('offlineHint', lang)
    : syncing
      ? ui('offlineSyncing', lang)
      : ui('offlinePendingHint', lang);

  return (
    <button
      className={`offline-badge${online ? ' pending' : ''}`}
      title={title}
      disabled={!online || syncing}
      onClick={() => void sync()}
    >
      {online ? '⟳' : '📴'}
      {pending > 0 && <span className="offline-count">{pending}</span>}
    </button>
  );
}
