import { useStore } from '@app/engine/store';
import { ui, useLang } from '@app/i18n';

/** Renders the result table of the last SQL query (or its error / empty state). */
export function SqlResultTable() {
  const result = useStore((s) => s.sqlResult);
  const lang = useLang((s) => s.lang);

  if (!result) return <p className="home-hint">{ui('runQueryHint', lang)}</p>;
  if (result.error) return <div className="output error">{result.error}</div>;
  if (result.columns.length === 0) return <p className="home-hint">{ui('noResult', lang)}</p>;

  return (
    <div className="sql-result">
      <table>
        <thead>
          <tr>
            {result.columns.map((c, i) => (
              <th key={i}>{c}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {result.rows.map((row, ri) => (
            <tr key={ri}>
              {row.map((cell, ci) => (
                <td key={ci}>{cell}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      <div className="sql-rowcount">
        {result.rows.length} {ui('rows', lang)}
      </div>
    </div>
  );
}
