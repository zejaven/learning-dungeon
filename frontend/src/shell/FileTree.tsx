import { useState } from 'react';
import { useStore } from '@app/engine/store';
import { ui, useLang } from '@app/i18n';

/**
 * Editable file tree for a structural topic's project. Lists the virtual files
 * (path → content) from the store; lets the user create, rename and delete
 * `.java` files and pick which one the editor shows.
 */
export function FileTree() {
  const files = useStore((s) => s.files);
  const activePath = useStore((s) => s.activePath);
  const selectFile = useStore((s) => s.selectFile);
  const createFile = useStore((s) => s.createFile);
  const deleteFile = useStore((s) => s.deleteFile);
  const renameFile = useStore((s) => s.renameFile);
  const lang = useLang((s) => s.lang);
  const [newName, setNewName] = useState('');

  const paths = Object.keys(files).sort();

  function add() {
    let name = newName.trim();
    if (!name) return;
    if (!name.includes('.')) name += '.java';
    createFile(name);
    setNewName('');
  }

  return (
    <div className="filetree">
      <div className="filetree-new">
        <input
          value={newName}
          placeholder={ui('newFileName', lang)}
          onChange={(e) => setNewName(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') add();
          }}
        />
        <button onClick={add} title={ui('newFile', lang)}>
          ＋
        </button>
      </div>

      {paths.length === 0 && <div className="filetree-empty">{ui('noFiles', lang)}</div>}

      {paths.map((p) => (
        <div key={p} className={`filetree-file${activePath === p ? ' selected' : ''}`}>
          <span className="filetree-name" onClick={() => selectFile(p)}>
            📄 {p}
          </span>
          <span className="filetree-actions">
            <button
              title={ui('rename', lang)}
              onClick={() => {
                const nn = window.prompt(ui('rename', lang), p);
                if (nn && nn.trim()) renameFile(p, nn.trim());
              }}
            >
              ✎
            </button>
            <button
              title={ui('deleteFile', lang)}
              onClick={() => {
                if (window.confirm(`${ui('deleteFile', lang)}: ${p}?`)) deleteFile(p);
              }}
            >
              🗑
            </button>
          </span>
        </div>
      ))}
    </div>
  );
}
