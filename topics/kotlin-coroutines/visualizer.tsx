import type { CSSProperties, ReactNode } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid } from '@app/primitives/ArrayGrid';
import { BoxGroup } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to see what the compiler generated, which thread each coroutine borrowed, and what its scope does when one of them fails.',
    ru: 'Запустите код, чтобы увидеть, что сгенерировал компилятор, какой поток одолжила каждая корутина и что делает её область, когда одна из них падает.',
  },
  machine: { en: 'the class the compiler generated', ru: 'класс, сгенерированный компилятором' },
  notStarted: { en: 'not entered yet', ru: 'ещё не входили' },
  atLabel: { en: 'label', ru: 'label' },
  savedFields: { en: 'fields of the continuation object', ru: 'поля объекта continuation' },
  noSaved: { en: 'nothing captured yet', ru: 'пока ничего не захвачено' },
  suspendedNow: {
    en: 'returned COROUTINE_SUSPENDED — the stack is gone, the thread is free',
    ru: 'вернул COROUTINE_SUSPENDED — стека нет, поток свободен',
  },
  returnedValue: { en: 'returned', ru: 'вернул' },
  dispatchers: { en: 'dispatchers and their threads', ru: 'диспетчеры и их потоки' },
  idle: { en: 'idle', ru: 'простаивает' },
  blocked: { en: 'BLOCKED', ru: 'ЗАБЛОКИРОВАН' },
  queue: { en: 'queue', ru: 'очередь' },
  queueEmpty: { en: 'empty', ru: 'пуста' },
  coroutines: { en: 'coroutines', ru: 'корутины' },
  noCoroutines: { en: 'nothing has been launched yet', ru: 'пока ничего не запущено' },
  scopes: { en: 'the Job tree', ru: 'дерево Job' },
  noChildren: { en: 'no children', ru: 'детей нет' },
  cancelFlag: { en: 'cancel requested', ru: 'запрошена отмена' },
  awaiting: { en: 'awaits', ru: 'ждёт' },
  states: {
    NEW: { en: 'new', ru: 'новая' },
    QUEUED: { en: 'queued', ru: 'в очереди' },
    RUNNING: { en: 'running', ru: 'выполняется' },
    SUSPENDED: { en: 'suspended', ru: 'приостановлена' },
    COMPLETED: { en: 'completed', ru: 'завершена' },
    CANCELLED: { en: 'cancelled', ru: 'отменена' },
    FAILED: { en: 'failed', ru: 'упала' },
  },
  scopeKinds: {
    ROOT: { en: 'root scope', ru: 'корневая область' },
    COROUTINE_SCOPE: { en: 'coroutineScope', ru: 'coroutineScope' },
    SUPERVISOR: { en: 'supervisorScope', ru: 'supervisorScope' },
    GLOBAL: { en: 'GlobalScope — no parent', ru: 'GlobalScope — без родителя' },
  },
  scopeStates: {
    ACTIVE: { en: 'active', ru: 'активна' },
    COMPLETED: { en: 'completed', ru: 'завершена' },
    CANCELLED: { en: 'cancelled', ru: 'отменена' },
    FAILED: { en: 'failed', ru: 'упала' },
  },
  statLaunched: { en: 'launched', ru: 'запущено' },
  statAlive: { en: 'alive', ru: 'живо' },
  statPeak: { en: 'peak alive', ru: 'пик живых' },
  statThreads: { en: 'worker threads', ru: 'рабочих потоков' },
  statSuspensions: { en: 'suspensions', ru: 'приостановок' },
  statResumes: { en: 'resumes', ru: 'возобновлений' },
  statQueued: { en: 'times queued', ru: 'ожиданий очереди' },
  statSwitches: { en: 'context switches', ru: 'смен контекста' },
  statBlocking: { en: 'blocking calls', ru: 'блокирующих вызовов' },
  statCancelled: { en: 'cancelled', ru: 'отменено' },
  statFailed: { en: 'failed', ru: 'упало' },
  statLeaked: { en: 'orphaned', ru: 'осиротело' },
  scale: {
    en: 'the same concurrent tasks, three ways',
    ru: 'те же конкурентные задачи тремя способами',
  },
  scaleModels: {
    PLATFORM_THREADS: { en: 'platform threads', ru: 'платформенные потоки' },
    COROUTINES: { en: 'coroutines', ru: 'корутины' },
    VIRTUAL_THREADS: { en: 'virtual threads (Java 21)', ru: 'виртуальные потоки (Java 21)' },
  },
  colModel: { en: 'one task is', ru: 'одна задача — это' },
  colCount: { en: 'tasks', ru: 'задач' },
  colMemory: { en: 'memory (MB)', ru: 'память (МБ)' },
  colOsThreads: { en: 'OS threads', ru: 'потоков ОС' },
  colKeyword: { en: 'colours functions', ru: 'окрашивает функции' },
  colFeasible: { en: 'fits in one JVM', ru: 'влезает в одну JVM' },
  yes: { en: 'yes', ru: 'да' },
  no: { en: 'no', ru: 'нет' },
};

type CoroutineState = keyof typeof LABELS.states;
type ScopeKind = keyof typeof LABELS.scopeKinds;
type ScopeState = keyof typeof LABELS.scopeStates;
type ScaleModel = keyof typeof LABELS.scaleModels;

interface WorkerThread {
  name: string;
  coroutine: string | null;
  blocked: boolean;
}
interface DispatcherPool {
  name: string;
  threads: WorkerThread[];
  queue: string[];
}
interface CoroutineRow {
  name: string;
  scope: string;
  kind: 'LAUNCH' | 'ASYNC';
  dispatcher: string;
  state: CoroutineState;
  thread: string | null;
  doing: string | null;
  cancelRequested: boolean;
  awaiting: string | null;
}
interface ScopeRow {
  name: string;
  kind: ScopeKind;
  state: ScopeState;
  children: string[];
}
interface MachineCase {
  label: number;
  code: string;
  current: boolean;
}
interface SavedField {
  name: string;
  value: string;
}
interface Machine {
  function: string;
  className: string;
  label: number;
  cases: MachineCase[];
  saved: SavedField[];
  suspended: boolean;
  returned: string | null;
}
interface Stats {
  launched: number;
  alive: number;
  peakAlive: number;
  threads: number;
  suspensions: number;
  resumes: number;
  dispatches: number;
  queued: number;
  contextSwitches: number;
  blockingCalls: number;
  completed: number;
  cancelled: number;
  failed: number;
  leaked: number;
}
interface ScaleRow {
  model: ScaleModel;
  count: number;
  memoryMb: number;
  osThreads: number;
  keyword: 'suspend' | 'none';
  feasible: boolean;
}
interface CoroutinesState {
  dispatchers: DispatcherPool[];
  coroutines: CoroutineRow[];
  scopes: ScopeRow[];
  machine: Machine | null;
  stats: Stats;
  scale?: ScaleRow[];
}

export default function CoroutinesVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as CoroutinesState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const scale = state.scale ?? [];
  if (scale.length > 0) {
    return <ScaleTable rows={scale} lang={lang} />;
  }
  if (state.machine) {
    return <MachinePanel machine={state.machine} lang={lang} />;
  }

  return (
    <div style={wrapStyle}>
      <Pane
        title={tl(LABELS.dispatchers, lang)}
        highlighted={highlight.has('dispatchers') || highlight.has('starved')}
      >
        <div style={poolsStyle}>
          {state.dispatchers.map((pool) => (
            <DispatcherCard key={pool.name} pool={pool} lang={lang} />
          ))}
        </div>
      </Pane>

      <div style={sidesStyle}>
        <Pane
          title={tl(LABELS.coroutines, lang)}
          highlighted={highlight.has('coroutines') || highlight.has('cancel')}
        >
          {state.coroutines.length === 0 ? (
            <div style={mutedStyle}>{tl(LABELS.noCoroutines, lang)}</div>
          ) : (
            <BoxGroup boxes={state.coroutines.map((c) => coroutineBox(c, lang))} />
          )}
        </Pane>

        <Pane title={tl(LABELS.scopes, lang)} highlighted={highlight.has('scopes')}>
          <div style={scopesStyle}>
            {state.scopes.map((scope) => (
              <ScopeCard key={scope.name} scope={scope} lang={lang} />
            ))}
          </div>
        </Pane>
      </div>

      <div style={statsStyle}>
        <Stat label={tl(LABELS.statLaunched, lang)} value={state.stats.launched} />
        <Stat label={tl(LABELS.statAlive, lang)} value={state.stats.alive} />
        <Stat label={tl(LABELS.statPeak, lang)} value={state.stats.peakAlive} color="var(--good)" />
        <Stat label={tl(LABELS.statThreads, lang)} value={state.stats.threads} />
        <Stat label={tl(LABELS.statSuspensions, lang)} value={state.stats.suspensions} />
        <Stat label={tl(LABELS.statResumes, lang)} value={state.stats.resumes} />
        <Stat label={tl(LABELS.statQueued, lang)} value={state.stats.queued} />
        <Stat label={tl(LABELS.statSwitches, lang)} value={state.stats.contextSwitches} />
        <Stat
          label={tl(LABELS.statBlocking, lang)}
          value={state.stats.blockingCalls}
          color={state.stats.blockingCalls > 0 ? 'var(--bad)' : undefined}
        />
        <Stat
          label={tl(LABELS.statCancelled, lang)}
          value={state.stats.cancelled}
          color={state.stats.cancelled > 0 ? 'var(--accent)' : undefined}
        />
        <Stat
          label={tl(LABELS.statFailed, lang)}
          value={state.stats.failed}
          color={state.stats.failed > 0 ? 'var(--bad)' : undefined}
        />
        <Stat
          label={tl(LABELS.statLeaked, lang)}
          value={state.stats.leaked}
          color={state.stats.leaked > 0 ? 'var(--bad)' : undefined}
        />
      </div>
    </div>
  );
}

function MachinePanel({ machine, lang }: { machine: Machine; lang: Lang }) {
  return (
    <div style={wrapStyle}>
      <div style={headerStyle}>
        <span style={badgeStyle}>{machine.className}</span>
        <span style={headNameStyle}>{`suspend fun ${machine.function}(...)`}</span>
        <span style={pillStyle}>
          {machine.label < 0
            ? tl(LABELS.notStarted, lang)
            : `${tl(LABELS.atLabel, lang)} = ${machine.label}`}
        </span>
      </div>

      <div style={sectionLabelStyle}>{tl(LABELS.machine, lang)}</div>
      <ArrayGrid
        cells={machine.cases.map((branch) => ({
          key: branch.label,
          label: `case ${branch.label}`,
          highlighted: branch.current,
          content: <span style={codeStyle}>{branch.code}</span>,
        }))}
      />

      {machine.suspended && <div style={suspendedStyle}>{tl(LABELS.suspendedNow, lang)}</div>}
      {machine.returned && (
        <div style={returnedStyle}>{`${tl(LABELS.returnedValue, lang)} ${machine.returned}`}</div>
      )}

      <div style={sectionLabelStyle}>{tl(LABELS.savedFields, lang)}</div>
      {machine.saved.length === 0 ? (
        <div style={mutedStyle}>{tl(LABELS.noSaved, lang)}</div>
      ) : (
        <BoxGroup
          boxes={machine.saved.map((field) => ({
            id: field.name,
            title: field.name,
            subtitle: field.value,
          }))}
        />
      )}
    </div>
  );
}

function DispatcherCard({ pool, lang }: { pool: DispatcherPool; lang: Lang }) {
  return (
    <div style={poolStyle}>
      <div style={poolNameStyle}>{`Dispatchers.${pool.name}`}</div>
      <BoxGroup
        boxes={pool.threads.map((thread) => ({
          id: thread.name,
          title: thread.name,
          subtitle: thread.blocked
            ? `${thread.coroutine ?? ''} · ${tl(LABELS.blocked, lang)}`
            : (thread.coroutine ?? tl(LABELS.idle, lang)),
          highlighted: thread.coroutine !== null && !thread.blocked,
          dim: thread.coroutine === null,
        }))}
      />
      <div style={queueStyle}>
        {`${tl(LABELS.queue, lang)}: ${pool.queue.length === 0 ? tl(LABELS.queueEmpty, lang) : pool.queue.join(' → ')}`}
      </div>
    </div>
  );
}

function coroutineBox(c: CoroutineRow, lang: Lang) {
  const parts = [tl(LABELS.states[c.state], lang)];
  if (c.thread) {
    parts.push(c.thread);
  } else {
    parts.push(`Dispatchers.${c.dispatcher}`);
  }
  if (c.awaiting) {
    parts.push(`${tl(LABELS.awaiting, lang)} ${c.awaiting}`);
  } else if (c.doing) {
    parts.push(c.doing);
  }
  if (c.cancelRequested && c.state !== 'CANCELLED') {
    parts.push(tl(LABELS.cancelFlag, lang));
  }
  return {
    id: c.name,
    title: c.kind === 'ASYNC' ? `${c.name} : Deferred` : c.name,
    subtitle: parts.join(' · '),
    highlighted: c.state === 'RUNNING',
    dim: c.state === 'COMPLETED' || c.state === 'CANCELLED' || c.state === 'FAILED',
  };
}

function ScopeCard({ scope, lang }: { scope: ScopeRow; lang: Lang }) {
  const color =
    scope.state === 'FAILED'
      ? 'var(--bad)'
      : scope.state === 'CANCELLED'
        ? 'var(--accent)'
        : undefined;
  return (
    <div style={{ ...scopeStyle, borderColor: color ?? 'var(--border)' }}>
      <div style={scopeNameStyle}>
        <span>{scope.name}</span>
        <span style={{ ...scopeKindStyle, color }}>
          {`${tl(LABELS.scopeKinds[scope.kind], lang)} · ${tl(LABELS.scopeStates[scope.state], lang)}`}
        </span>
      </div>
      <div style={scopeChildrenStyle}>
        {scope.children.length === 0 ? tl(LABELS.noChildren, lang) : scope.children.join(', ')}
      </div>
    </div>
  );
}

function ScaleTable({ rows, lang }: { rows: ScaleRow[]; lang: Lang }) {
  const yesNo = (value: boolean) => tl(value ? LABELS.yes : LABELS.no, lang);
  return (
    <div style={wrapStyle}>
      <div style={sectionLabelStyle}>{tl(LABELS.scale, lang)}</div>
      <div style={tableStyle}>
        <div style={tableHeadStyle}>
          <span style={cellTextStyle}>{tl(LABELS.colModel, lang)}</span>
          <span style={cellTextStyle}>{tl(LABELS.colCount, lang)}</span>
          <span style={cellTextStyle}>{tl(LABELS.colMemory, lang)}</span>
          <span style={cellTextStyle}>{tl(LABELS.colOsThreads, lang)}</span>
          <span style={cellTextStyle}>{tl(LABELS.colKeyword, lang)}</span>
          <span style={cellTextStyle}>{tl(LABELS.colFeasible, lang)}</span>
        </div>
        {rows.map((row) => (
          <div key={row.model} style={tableRowStyle}>
            <span style={{ ...cellTextStyle, fontWeight: 700 }}>
              {tl(LABELS.scaleModels[row.model], lang)}
            </span>
            <span style={cellTextStyle}>{row.count}</span>
            <span style={cellTextStyle}>{row.memoryMb}</span>
            <span style={cellTextStyle}>{row.osThreads}</span>
            <span style={cellTextStyle}>{row.keyword === 'suspend' ? 'suspend' : '—'}</span>
            <span
              style={{ ...cellTextStyle, fontWeight: 700, color: row.feasible ? 'var(--good)' : 'var(--bad)' }}
            >
              {yesNo(row.feasible)}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

function Pane({
  title,
  highlighted,
  children,
}: {
  title: string;
  highlighted: boolean;
  children: ReactNode;
}) {
  return (
    <div style={{ ...paneStyle, ...(highlighted ? paneHighlightStyle : {}) }}>
      <div style={sectionLabelStyle}>{title}</div>
      {children}
    </div>
  );
}

function Stat({ label, value, color }: { label: string; value: number; color?: string }) {
  return (
    <div style={statStyle}>
      <div style={statLabelStyle}>{label}</div>
      <div style={{ ...statValueStyle, color: color ?? 'inherit' }}>{value}</div>
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 14 };
const headerStyle: CSSProperties = { display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' };
const badgeStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 12,
  fontWeight: 700,
  padding: '2px 6px',
  borderRadius: 4,
  background: 'var(--viz-badge)',
};
const headNameStyle: CSSProperties = { fontSize: 13, fontWeight: 600, fontFamily: 'monospace' };
const pillStyle: CSSProperties = {
  fontSize: 11,
  opacity: 0.75,
  border: '1px solid var(--border)',
  borderRadius: 10,
  padding: '1px 7px',
  fontFamily: 'monospace',
};
const sidesStyle: CSSProperties = { display: 'flex', gap: 10, alignItems: 'stretch', flexWrap: 'wrap' };
const paneStyle: CSSProperties = {
  flex: '1 1 260px',
  minWidth: 240,
  display: 'flex',
  flexDirection: 'column',
  gap: 6,
  border: '1px solid var(--border)',
  borderRadius: 8,
  padding: '8px 10px',
  background: 'var(--viz-box)',
};
const paneHighlightStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  background: 'var(--viz-highlight)',
};
const poolsStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 8 };
const poolStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 4 };
const poolNameStyle: CSSProperties = { fontSize: 12, fontWeight: 700, fontFamily: 'monospace' };
const queueStyle: CSSProperties = { fontSize: 11, opacity: 0.65, fontFamily: 'monospace' };
const scopesStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const scopeStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '5px 8px',
  background: 'var(--viz-box)',
};
const scopeNameStyle: CSSProperties = {
  display: 'flex',
  gap: 8,
  alignItems: 'baseline',
  flexWrap: 'wrap',
  fontSize: 12,
  fontWeight: 700,
  fontFamily: 'monospace',
};
const scopeKindStyle: CSSProperties = { fontSize: 10, fontWeight: 400, opacity: 0.75 };
const scopeChildrenStyle: CSSProperties = { fontSize: 11, opacity: 0.7 };
const codeStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 11,
  overflowWrap: 'anywhere',
};
const suspendedStyle: CSSProperties = { fontSize: 11, fontWeight: 700, color: 'var(--accent)' };
const returnedStyle: CSSProperties = { fontSize: 11, fontWeight: 700, color: 'var(--good)' };
const statsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const tableStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 2 };
const tableHeadStyle: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '1.7fr repeat(5, 1fr)',
  fontSize: 10,
  opacity: 0.6,
};
const tableRowStyle: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '1.7fr repeat(5, 1fr)',
  fontSize: 11,
  fontFamily: 'monospace',
  padding: '4px 0',
  borderTop: '1px solid var(--border)',
  alignItems: 'center',
};
const cellTextStyle: CSSProperties = { paddingRight: 6, overflowWrap: 'anywhere' };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 2 };
const mutedStyle: CSSProperties = { fontSize: 12, opacity: 0.5, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
