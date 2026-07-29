import { useBulk } from '@app/engine/bulkStore';
import { navigate, routeForQuestion } from '@app/engine/router';
import { useStore } from '@app/engine/store';
import { tl, ui, useLang } from '@app/i18n';

const PHASE_KEYS: Record<string, string> = {
  generating: 'bulkPhaseGenerating',
  waitingPace: 'bulkPhaseWaitingPace',
  waitingUsage: 'bulkPhaseWaitingUsage',
  waitingReset: 'bulkPhaseWaitingReset',
  finished: 'bulkPhaseFinished',
  stopped: 'bulkPhaseStopped',
  endReached: 'bulkPhaseEndReached',
  capReached: 'bulkPhaseCapReached',
  weeklyCapReached: 'bulkPhaseWeeklyCapReached',
  noResetInfo: 'bulkPhaseNoResetInfo',
};

function fmtTime(iso: string): string {
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

/**
 * Secondary header strip shown while a bulk-generation run exists: one segment
 * per topic (gray pending, yellow running, green done, red error), the current
 * phase, and a graceful Stop (the in-flight generation always finishes). A
 * finished run stays visible until dismissed with ✕.
 */
export function BulkGenBar() {
  const lang = useLang((s) => s.lang);
  const status = useBulk((s) => s.status);
  const stop = useBulk((s) => s.stop);
  const dismiss = useBulk((s) => s.dismiss);
  const topics = useStore((s) => s.topics);

  const run = status?.run;
  if (!run) return null;

  const active = !!status?.active;
  const doneCount = run.items.filter((i) => i.status === 'done' || i.status === 'skipped').length;
  const phaseKey = PHASE_KEYS[run.phase];

  function open(itemId: string) {
    // Theory items carry the catalog entry id directly; atoms items carry the
    // topic id, whose catalog entry is its catalogId (or the id itself in
    // domains whose tree is built from topics).
    const entryId =
      run!.kind === 'theory' ? itemId : topics.find((t) => t.id === itemId)?.catalogId || itemId;
    navigate(routeForQuestion(entryId));
  }

  return (
    <div className="bulk-bar">
      <span className="bulk-bar-label">
        {run.kind === 'theory' ? '📖' : '✨'} {phaseKey ? ui(phaseKey, lang) : run.phase}{' '}
        {doneCount}/{run.items.length}
        {run.waitUntil && ` · ${ui('bulkUntil', lang)} ${fmtTime(run.waitUntil)}`}
        {run.utilization != null && ` · ${Math.round(run.utilization)}% / ${run.maxPercent}%`}
        {run.weeklyUtilization != null
          && ` · ${ui('bulkWeekly', lang)} ${Math.round(run.weeklyUtilization)}% / ${run.maxWeeklyPercent}%`}
        {!run.endTime && ' · ∞'}
      </span>
      <div className="bulk-track">
        {run.items.map((item) => (
          <div
            key={item.id}
            className={`bulk-seg ${item.status}`}
            title={tl(item.label, lang)}
            onClick={() => open(item.id)}
          />
        ))}
      </div>
      {active ? (
        <button onClick={() => void stop()} disabled={run.stopRequested}>
          {run.stopRequested ? ui('bulkStopping', lang) : ui('bulkStop', lang)}
        </button>
      ) : (
        <button title={ui('bulkDismiss', lang)} onClick={() => void dismiss()}>
          ✕
        </button>
      )}
    </div>
  );
}
