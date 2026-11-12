import { useEffect, useRef, useState } from 'react';
import type { BulkKind } from '@app/engine/api';
import { useBulk } from '@app/engine/bulkStore';
import { ui, useLang } from '@app/i18n';
import { useBackdropClose } from './backdropClose';

const BULK_TITLE: Record<BulkKind, string> = {
  theory: 'bulkDialogTitleTheory',
  atoms: 'bulkDialogTitleAtoms',
  tasks: 'bulkDialogTitleTasks',
};

/** A module (or, without a roadmap, a category) that has items to generate. */
export interface BulkGroup {
  id: string;
  /** Already localized. */
  name: string;
  count: number;
}

/** "HH:mm" a given number of hours from now (default stop time suggestion). */
function defaultEndTime(hoursAhead: number): string {
  const t = new Date(Date.now() + hoursAhead * 3_600_000);
  return `${String(t.getHours()).padStart(2, '0')}:${String(t.getMinutes()).padStart(2, '0')}`;
}

/**
 * Start-confirmation dialog for a bulk-generation run: which modules to cover
 * (all by default) and so how many items will be generated, the stop time (time-of-day; already past today = tomorrow) and the
 * usage cap that must hold once the 5-hour limit window outlives the stop time.
 */
export function BulkGenDialog({
  kind,
  groups,
  groupsAreModules,
  onConfirm,
  onClose,
}: {
  kind: BulkKind;
  groups: BulkGroup[];
  /** True where the tree is a roadmap of modules; otherwise the groups are categories. */
  groupsAreModules: boolean;
  onConfirm: (groupIds: Set<string>, endTime: string, maxPercent: number, maxWeeklyPercent: number) => void;
  onClose: () => void;
}) {
  const lang = useLang((s) => s.lang);
  const [picked, setPicked] = useState<Set<string>>(() => new Set(groups.map((g) => g.id)));
  const allRef = useRef<HTMLInputElement>(null);
  const allPicked = groups.every((g) => picked.has(g.id));
  const nonePicked = groups.every((g) => !picked.has(g.id));
  const count = groups.reduce((n, g) => n + (picked.has(g.id) ? g.count : 0), 0);
  // "All" shows a dash when only some are picked; that state exists only as a DOM property.
  useEffect(() => {
    if (allRef.current) allRef.current.indeterminate = !allPicked && !nonePicked;
  }, [allPicked, nonePicked]);

  function toggle(id: string) {
    setPicked((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  const starting = useBulk((s) => s.starting);
  const error = useBulk((s) => s.error);
  const [endTime, setEndTime] = useState(() => defaultEndTime(6));
  const [maxPercent, setMaxPercent] = useState(80);
  // Half a 5-hour window and half a week's quota are very different decisions,
  // so each limit gets its own cap.
  const [maxWeeklyPercent, setMaxWeeklyPercent] = useState(90);
  // Continuous mode: no deadline, the caps simply hold in every window.
  const [noDeadline, setNoDeadline] = useState(false);

  const canConfirm = !starting && count > 0 && (noDeadline || /^\d{2}:\d{2}$/.test(endTime));

  const backdrop = useBackdropClose(onClose);
  return (
    <div className="dialog-backdrop" {...backdrop}>
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <div className="dialog-head">
          <h2>{ui(BULK_TITLE[kind], lang)}</h2>
          <button onClick={onClose}>✕</button>
        </div>
        <div className="dialog-body">
          <div className="bulk-groups-head">
            <span>{ui(groupsAreModules ? 'bulkModules' : 'bulkCategories', lang)}</span>
            <label>
              <input
                ref={allRef}
                type="checkbox"
                checked={allPicked}
                onChange={() => setPicked(allPicked ? new Set() : new Set(groups.map((g) => g.id)))}
              />
              {ui('bulkAllGroups', lang)}
            </label>
          </div>
          <div className="bulk-groups">
            {groups.map((g) => (
              <label key={g.id}>
                <input type="checkbox" checked={picked.has(g.id)} onChange={() => toggle(g.id)} />
                <span className="bulk-group-name">{g.name}</span>
                <span className="bulk-group-count">{g.count}</span>
              </label>
            ))}
          </div>
          <p>
            {ui('bulkDialogCount', lang)} <strong>{count}</strong>
          </p>

          <label style={{ display: 'flex', gap: 8, alignItems: 'flex-start', cursor: 'pointer' }}>
            <input
              type="checkbox"
              checked={noDeadline}
              onChange={(e) => setNoDeadline(e.target.checked)}
            />
            <span>{ui('bulkNoDeadline', lang)}</span>
          </label>

          {noDeadline ? (
            <p style={{ color: 'var(--muted)', fontSize: 12, margin: '4px 0 0' }}>
              {ui('bulkNoDeadlineHint', lang)}
            </p>
          ) : (
            <>
              <label style={{ display: 'block', fontSize: 13, margin: '12px 0 4px' }}>
                {ui('bulkEndTime', lang)}
              </label>
              <input type="time" value={endTime} onChange={(e) => setEndTime(e.target.value)} />
              <p style={{ color: 'var(--muted)', fontSize: 12, marginTop: 4 }}>
                {ui('bulkEndTimeHint', lang)}
              </p>
            </>
          )}

          <label style={{ display: 'block', fontSize: 13, margin: '12px 0 4px' }}>
            {ui(noDeadline ? 'bulkMaxPercentWindow' : 'bulkMaxPercent', lang)}
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
            {ui(noDeadline ? 'bulkMaxPercentWindowHint' : 'bulkMaxPercentHint', lang)}
          </p>

          <label style={{ display: 'block', fontSize: 13, margin: '12px 0 4px' }}>
            {ui('bulkMaxWeeklyPercent', lang)}
          </label>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <input
              type="range"
              min={0}
              max={100}
              step={5}
              value={maxWeeklyPercent}
              onChange={(e) => setMaxWeeklyPercent(Number(e.target.value))}
              style={{ flex: 1 }}
            />
            <span className="bulk-slider-value">{maxWeeklyPercent}%</span>
          </div>
          <p style={{ color: 'var(--muted)', fontSize: 12, marginTop: 4 }}>
            {ui('bulkMaxWeeklyPercentHint', lang)}
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
            onClick={() => onConfirm(picked, noDeadline ? '' : endTime, maxPercent, maxWeeklyPercent)}
          >
            {ui('bulkStart', lang)}
          </button>
        </div>
      </div>
    </div>
  );
}
