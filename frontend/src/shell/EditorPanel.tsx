import Editor from '@monaco-editor/react';
import { useTheme } from '@app/engine/themeStore';

export function EditorPanel({
  code,
  onChange,
  language = 'java',
  path,
}: {
  code: string;
  onChange: (code: string) => void;
  language?: string;
  /** Model path: gives each file its own Monaco model (separate undo stack). */
  path?: string;
}) {
  const theme = useTheme((s) => s.theme);
  return (
    <div className="editor-wrap">
      <Editor
        height="100%"
        language={language}
        theme={theme === 'light' ? 'vs' : 'vs-dark'}
        path={path}
        value={code}
        onChange={(value) => onChange(value ?? '')}
        options={{
          minimap: { enabled: false },
          fontSize: 13,
          scrollBeyondLastLine: false,
          automaticLayout: true,
          tabSize: 4,
        }}
      />
    </div>
  );
}
