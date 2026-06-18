import Editor from '@monaco-editor/react';

export function EditorPanel({
  code,
  onChange,
  language = 'java',
}: {
  code: string;
  onChange: (code: string) => void;
  language?: string;
}) {
  return (
    <div className="editor-wrap">
      <Editor
        height="100%"
        language={language}
        theme="vs-dark"
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
