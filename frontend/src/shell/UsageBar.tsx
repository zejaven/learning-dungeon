import { formatUntil } from '@app/engine/duration';
import { useUsage } from '@app/engine/usageStore';
import { ui, useLang, type Lang } from '@app/i18n';

/**
 * Compact header meter/status for the selected AI provider.
 */
export function UsageBar() {
  const lang = useLang((s) => s.lang);
  const snapshot = useUsage((s) => s.snapshot);

  if (!snapshot) return null;

  if (!snapshot.available) {
    return (
      <div className="usage-status" title={snapshot.error ?? ''}>
        <span>{snapshot.providerName}: {snapshot.error}</span>
        {!snapshot.installed && snapshot.downloadUrl && (
          <a href={snapshot.downloadUrl} target="_blank" rel="noreferrer">
            {ui('installCli', lang)}
          </a>
        )}
      </div>
    );
  }

  return (
    <div className="usage-bars">
      <UsageMeter
        label={ui('usageSession', lang)}
        value={snapshot.session?.utilization ?? 0}
        resetsAt={snapshot.session?.resetsAt ?? null}
        lang={lang}
      />
      <UsageMeter
        label={ui('usageWeekly', lang)}
        value={snapshot.weekly?.utilization ?? 0}
        resetsAt={snapshot.weekly?.resetsAt ?? null}
        lang={lang}
      />
    </div>
  );
}

function UsageMeter({
  label,
  value,
  resetsAt,
  lang,
}: {
  label: string;
  value: number;
  resetsAt: string | null;
  lang: Lang;
}) {
  const pct = Math.max(0, Math.min(100, Math.round(value)));
  const level = pct >= 90 ? 'high' : pct >= 70 ? 'mid' : 'low';
  const reset = resetsAt ? formatUntil(resetsAt, lang) : '';
  const title = `${label}: ${pct}%${reset ? ` · ${ui('usageResets', lang)} ${reset}` : ''}`;

  return (
    <div className="usage-meter" title={title}>
      <span className="usage-label">{label}</span>
      <span className="usage-track">
        <span className="usage-fill" data-level={level} style={{ width: `${pct}%` }} />
      </span>
      <span className="usage-pct">{pct}%</span>
      {reset && (
        <span className="usage-reset" title={`${ui('usageResets', lang)} ${reset}`}>
          ↻ {reset}
        </span>
      )}
    </div>
  );
}

