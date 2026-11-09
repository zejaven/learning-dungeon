import { useRef } from 'react';
import { buildCatalog, type CatalogCategory } from '@app/catalog';
import { navigate, routeForQuestion, routeForRoadmap, routeForTree } from '@app/engine/router';
import { useStore } from '@app/engine/store';
import { tl, ui, useLang } from '@app/i18n';
import {
  groupByRoadmap,
  MODULE_LAYOUT,
  moduleIdOfEntry,
  moduleProgress,
  nextEntry,
  type ModuleLayout,
} from '@app/roadmap';
import { PanZoom, type PanZoomBox } from '@app/shell/PanZoom';

const NODE_W = 230;
const NODE_H = 76;
const COL_W = 300;
const ROW_H = 120;
/** Room for the toolbar above and the legend below, so a fitted map clears them. */
const PAD_TOP = 56;
const PAD_BOTTOM = 72;
const EDGE_RADIUS = 10;

const cols = Math.max(...MODULE_LAYOUT.map((m) => m.col)) + 1;
const rows = Math.max(...MODULE_LAYOUT.map((m) => m.row)) + 1;
const WORLD_W = (cols - 1) * COL_W + NODE_W;
const WORLD_H = PAD_TOP + (rows - 1) * ROW_H + NODE_H + PAD_BOTTOM;

const left = (m: ModuleLayout) => m.col * COL_W;
const top = (m: ModuleLayout) => PAD_TOP + m.row * ROW_H;
const boxOf = (m: ModuleLayout): PanZoomBox => ({ x: left(m), y: top(m), w: NODE_W, h: NODE_H });

/** Is any module in this column strictly between the two rows? */
function blocked(col: number, fromRow: number, toRow: number): boolean {
  return MODULE_LAYOUT.some((m) => m.col === col && m.row > fromRow && m.row < toRow);
}

/**
 * The arrow from a module to one that requires it. Within a row it leaves
 * sideways. Otherwise it is orthogonal, so it never runs under a node: straight
 * down the source's column and along the gutter above the target, or, when that
 * column is occupied, along the gutter below the source and down the target's.
 */
function edgePath(from: ModuleLayout, to: ModuleLayout): string {
  if (from.row === to.row) {
    const rightward = to.col > from.col;
    const x1 = left(from) + (rightward ? NODE_W : 0);
    const x2 = left(to) + (rightward ? 0 : NODE_W);
    const y = top(from) + NODE_H / 2;
    return `M ${x1} ${y} L ${x2} ${y}`;
  }
  const x1 = left(from) + NODE_W / 2;
  const y1 = top(from) + NODE_H;
  const x2 = left(to) + NODE_W / 2;
  const y2 = top(to);
  if (x1 === x2) return `M ${x1} ${y1} L ${x2} ${y2}`;

  const gutter = (ROW_H - NODE_H) / 2;
  const y = blocked(from.col, from.row, to.row) ? y1 + gutter : y2 - gutter;
  const r = Math.sign(x2 - x1) * EDGE_RADIUS;
  return (
    `M ${x1} ${y1} L ${x1} ${y - EDGE_RADIUS} Q ${x1} ${y} ${x1 + r} ${y} ` +
    `L ${x2 - r} ${y} Q ${x2} ${y} ${x2} ${y + EDGE_RADIUS} L ${x2} ${y2}`
  );
}

/**
 * The Java domain's roadmap as a map: modules as nodes showing their progress,
 * prerequisites as arrows. It lives in the home screen's detail panel, where
 * the theory shows once a question is picked. Pressing a module opens it in
 * the question tree on the left (`#/roadmap/<module>`), so a reload or coming
 * back from a question lands on it.
 */
export function RoadmapView({ domainId, moduleId }: { domainId: string; moduleId: string | null }) {
  const topics = useStore((s) => s.topics);
  const manualQuestions = useStore((s) => s.manualQuestions);
  const lang = useLang((s) => s.lang);

  // The focus sticks to the last picked module instead of falling back to the
  // next question whenever the selection is cleared.
  const lastFocus = useRef<string | null>(null);

  // Until topics arrive nothing is linked and every module would read 0.
  if (topics.length === 0) return <div className="roadmap-view" />;

  const stages = groupByRoadmap(buildCatalog(topics, manualQuestions, domainId), domainId) ?? [];
  const completed = new Set(topics.filter((t) => t.completed).map((t) => t.id));
  const modules = new Map<string, CatalogCategory>(stages.flatMap((s) => s.modules).map((m) => [m.id, m]));
  const layoutById = new Map(MODULE_LAYOUT.map((m) => [m.id, m]));

  const next = nextEntry(stages, completed);
  const nextModuleId = next ? moduleIdOfEntry(next) : undefined;
  const openModule = moduleId ? modules.get(moduleId) : undefined;

  if (openModule) lastFocus.current = openModule.id;
  else if (!lastFocus.current) lastFocus.current = nextModuleId ?? null;
  const focusLayout = lastFocus.current ? layoutById.get(lastFocus.current) : undefined;

  return (
    <div className="roadmap-view">
      <div className="roadmap-stage">
        <PanZoom
          width={WORLD_W}
          height={WORLD_H}
          focus={focusLayout ? boxOf(focusLayout) : null}
          focusKey={lastFocus.current}
        >
          <svg className="roadmap-edges" width={WORLD_W} height={WORLD_H} aria-hidden="true">
            <defs>
              <marker id="roadmapArrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto">
                <path d="M 0 0 L 10 5 L 0 10 z" className="roadmap-arrow" />
              </marker>
            </defs>
            {MODULE_LAYOUT.flatMap((to) =>
              to.requires.map((reqId) => {
                const from = layoutById.get(reqId)!;
                const active = openModule && (openModule.id === to.id || openModule.id === reqId);
                return (
                  <path
                    key={`${reqId}-${to.id}`}
                    d={edgePath(from, to)}
                    className={`roadmap-edge${active ? ' active' : ''}`}
                    markerEnd="url(#roadmapArrow)"
                  />
                );
              }),
            )}
          </svg>
          {MODULE_LAYOUT.map((m) => {
            const mod = modules.get(m.id);
            if (!mod) return null;
            const { done, total } = moduleProgress(mod.entries, completed);
            const state = total > 0 && done === total ? 'done' : done > 0 ? 'progress' : 'todo';
            return (
              <button
                key={m.id}
                className={`roadmap-node${m.id === openModule?.id ? ' selected' : ''}`}
                data-state={state}
                data-stage={m.stageIndex}
                style={{ left: left(m), top: top(m), width: NODE_W, height: NODE_H }}
                onClick={() => navigate(routeForRoadmap(m.id))}
              >
                <span className="roadmap-node-name">{tl(mod.name, lang)}</span>
                <span className="roadmap-node-meta">
                  {total > 0 ? `${done}/${total}` : `◇ ${mod.entries.length}`}
                  {m.id === nextModuleId && <span className="roadmap-node-next"> ▶</span>}
                </span>
                <span className="roadmap-node-bar">
                  <span style={{ width: `${total > 0 ? (done / total) * 100 : 0}%` }} />
                </span>
              </button>
            );
          })}
        </PanZoom>
        <div className="panzoom-ui roadmap-toolbar">
          {next && (
            <button className="accent" title={tl(next.question, lang)} onClick={() => navigate(routeForQuestion(next.id))}>
              ▶ {ui('mapContinue', lang)}
            </button>
          )}
          {/* On a phone the tree is a screen of its own; on a desktop it is beside the map. */}
          <button className="mobile-only" onClick={() => navigate(routeForTree())}>
            {ui('mapTree', lang)}
          </button>
        </div>
        <div className="panzoom-ui roadmap-legend">
          {stages
            .filter((s) => !s.id.startsWith('__'))
            .map((s, i) => (
              <span key={s.id} data-stage={i}>
                <i />
                {tl(s.name, lang)}
              </span>
            ))}
          <span data-state="done">
            <i />
            {ui('mapDone', lang)}
          </span>
          <span className="roadmap-legend-next">▶ {ui('mapNext', lang)}</span>
        </div>
      </div>
    </div>
  );
}
