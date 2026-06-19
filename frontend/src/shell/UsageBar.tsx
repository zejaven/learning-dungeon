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
  const reset = resetsAt ? formatReset(resetsAt, lang) : '';
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

/** Short "in 3h 12m" / "через 3 ч 12 м" until the window resets. */
function formatReset(iso: string, lang: Lang): string {
  const ms = new Date(iso).getTime() - Date.now();
  if (!Number.isFinite(ms) || ms <= 0) return lang === 'ru' ? 'скоро' : 'soon';
  const totalMin = Math.round(ms / 60_000);
  const h = Math.floor(totalMin / 60);
  const m = totalMin % 60;
  const hUnit = lang === 'ru' ? 'ч' : 'h';
  const mUnit = lang === 'ru' ? 'м' : 'm';
  return h > 0 ? `${h}${hUnit} ${m}${mUnit}` : `${m}${mUnit}`;
}
