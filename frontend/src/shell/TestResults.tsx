import type { TestResult } from '@app/engine/traceTypes';
import { ui, useLang } from '@app/i18n';

/**
 * The result of the last test run. A run that died still shows every case that
 * reported before it; `notRun` lists the ones it never reached, and `error`
 * says why.
 */
export function TestResults({
  tests,
  notRun = [],
  error,
  hint,
}: {
  tests: TestResult[] | null;
  notRun?: string[];
  error?: string | null;
  /** Shown before the first run. */
  hint?: string;
}) {
  const lang = useLang((s) => s.lang);

  const cases = tests ?? [];
  if (cases.length === 0 && notRun.length === 0 && !error) {
    return <p className="home-hint">{hint ?? ui('testsHint', lang)}</p>;
  }

  const passed = cases.filter((t) => t.passed).length;
  const total = cases.length + notRun.length;
  return (
    <div className="tests">
      {error && <div className="output error">{error}</div>}
      {total > 0 && (
        <div className={`tests-summary${passed === total && !error ? ' all' : ''}`}>
          {passed} / {total} ✓
        </div>
      )}
      {cases.map((t, i) => (
        <div key={i} className={`test-case${t.passed ? ' pass' : ' fail'}`}>
          <span className="test-icon">{t.passed ? '✅' : '❌'}</span>
          <span className="test-name">{t.name}</span>
          {!t.passed && t.expected !== '' && (
            <span className="test-detail">
              {ui('expected', lang)} {t.expected} · {ui('actual', lang)} {t.actual}
            </span>
          )}
        </div>
      ))}
      {notRun.map((name) => (
        <div key={name} className="test-case not-run">
          <span className="test-icon">⏸</span>
          <span className="test-name">{name}</span>
          <span className="test-detail">{ui('caseNotRun', lang)}</span>
        </div>
      ))}
    </div>
  );
}
