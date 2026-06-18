import { useStore } from '@app/engine/store';

interface Table {
  name: string;
  columns: string[];
}

/**
 * Lightweight schema view for a SQL topic: lists tables and columns parsed from
 * the `CREATE TABLE` statements in the topic's starter/schema.sql. Falls back to
 * the raw DDL if parsing finds nothing.
 */
function parseSchema(sql: string): Table[] {
  const tables: Table[] = [];
  const re = /create\s+table\s+(\w+)\s*\(([\s\S]*?)\)\s*;/gi;
  let m: RegExpExecArray | null;
  while ((m = re.exec(sql))) {
    const columns = m[2]
      .split(',')
      .map((line) => line.trim().split(/\s+/)[0])
      .filter((c) => c && !/^(primary|foreign|unique|constraint|check|key)$/i.test(c));
    tables.push({ name: m[1], columns });
  }
  return tables;
}

export function SchemaPanel() {
  const topic = useStore((s) => s.topic);
  const schema = (topic?.starterFiles ?? []).find((f) => f.path.endsWith('schema.sql'))?.content ?? '';
  const tables = parseSchema(schema);

  if (tables.length === 0) {
    return <pre className="schema-raw">{schema}</pre>;
  }
  return (
    <div className="schema">
      {tables.map((t) => (
        <div key={t.name} className="schema-table">
          <div className="schema-table-name">▦ {t.name}</div>
          <ul className="schema-cols">
            {t.columns.map((c) => (
              <li key={c}>{c}</li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  );
}
