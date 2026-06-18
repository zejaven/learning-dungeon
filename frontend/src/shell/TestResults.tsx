import { useStore } from '@app/engine/store';
import { ui, useLang } from '@app/i18n';

/** Shows the result of the last "Run tests" for a challenge topic. */
export function TestResults() {
  const tests = useStore((s) => s.testResults);
  const lang = useLang((s) => s.lang);

  if (!tests) return <p className="home-hint">{ui('testsHint', lang)}</p>;
  if (tests.length === 0) return <p className="home-hint">{ui('testsHint', lang)}</p>;

  const passed = tests.filter((t) => t.passed).length;
  return (
    <div className="tests">
      <div className={`tests-summary${passed === tests.length ? ' all' : ''}`}>
        {passed} / {tests.length} ✓
      </div>
      {tests.map((t, i) => (
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
    </div>
  );
}
