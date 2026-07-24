import { useState } from 'react';
import type { BulkKind } from '@app/engine/api';
import { useBulk } from '@app/engine/bulkStore';
import { ui, useLang } from '@app/i18n';

/** "HH:mm" a given number of hours from now (default stop time suggestion). */
function defaultEndTime(hoursAhead: number): string {
  const t = new Date(Date.now() + hoursAhead * 3_600_000);
  return `${String(t.getHours()).padStart(2, '0')}:${String(t.getMinutes()).padStart(2, '0')}`;
}

/**
 * Start-confirmation dialog for a bulk-generation run: how many items will be
 * generated, the stop time (time-of-day; already past today = tomorrow) and the
 * usage cap that must hold once the 5-hour limit window outlives the stop time.
 */
export function BulkGenDialog({
  kind,
  count,
  onConfirm,
  onClose,
}: {
  kind: BulkKind;
  count: number;
  onConfirm: (endTime: string, maxPercent: number) => void;
  onClose: () => void;
}) {
  const lang = useLang((s) => s.lang);
  const starting = useBulk((s) => s.starting);
  const error = useBulk((s) => s.error);
  const [endTime, setEndTime] = useState(() => defaultEndTime(6));
  const [maxPercent, setMaxPercent] = useState(80);

  const canConfirm = !starting && /^\d{2}:\d{2}$/.test(endTime);

  return (
    <div className="dialog-backdrop" onClick={onClose}>
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <div className="dialog-head">
          <h2>{ui(kind === 'theory' ? 'bulkDialogTitleTheory' : 'bulkDialogTitleAtoms', lang)}</h2>
          <button onClick={onClose}>✕</button>
        </div>
        <div className="dialog-body">
          <p>
            {ui('bulkDialogCount', lang)} <strong>{count}</strong>
          </p>

          <label style={{ display: 'block', fontSize: 13, marginBottom: 4 }}>
            {ui('bulkEndTime', lang)}
          </label>
          <input type="time" value={endTime} onChange={(e) => setEndTime(e.target.value)} />
          <p style={{ color: 'var(--muted)', fontSize: 12, marginTop: 4 }}>
            {ui('bulkEndTimeHint', lang)}
          </p>

          <label style={{ display: 'block', fontSize: 13, margin: '12px 0 4px' }}>
            {ui('bulkMaxPercent', lang)}
          </label>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <input
              type="range"
              min={0}
              max={100}
              step={5}
              value={maxPercent}
              onChange={(e) => setMaxPercent(Number(e.target.value))}
              style={{ flex: 1 }}
            />
            <span className="bulk-slider-value">{maxPercent}%</span>
          </div>
          <p style={{ color: 'var(--muted)', fontSize: 12, marginTop: 4 }}>
            {ui('bulkMaxPercentHint', lang)}
          </p>

          {error && (
            <p style={{ color: 'var(--bad)', fontSize: 13, marginTop: 8 }}>{error}</p>
          )}
        </div>
        <div className="dialog-foot">
          <button onClick={onClose}>{ui('close', lang)}</button>
          <button
            className="primary"
            disabled={!canConfirm}
            onClick={() => onConfirm(endTime, maxPercent)}
          >
            {ui('bulkStart', lang)}
          </button>
        </div>
      </div>
    </div>
  );
}
