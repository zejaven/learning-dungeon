import Editor from '@monaco-editor/react';
import { useTheme } from '@app/engine/themeStore';

export function EditorPanel({
  code,
  onChange,
  language = 'java',
}: {
  code: string;
  onChange: (code: string) => void;
  language?: string;
}) {
  const theme = useTheme((s) => s.theme);
  return (
    <div className="editor-wrap">
      <Editor
        height="100%"
        language={language}
        theme={theme === 'light' ? 'vs' : 'vs-dark'}
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
