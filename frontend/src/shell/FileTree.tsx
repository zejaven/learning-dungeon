/**
 * Placeholder file tree for the workspace. For now it shows a single folder with
 * Playground.java; a real multi-file tree can replace this later.
 */
export function FileTree() {
  return (
    <div className="filetree">
      <div className="filetree-folder">📂 src</div>
      <div className="filetree-file selected">📄 Playground.java</div>
    </div>
  );
}
