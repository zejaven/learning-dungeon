import { useState } from 'react';
import { useSystem } from '@app/engine/systemStore';
import { ui, useLang } from '@app/i18n';
import { SettingsDialog } from './SettingsDialog';

/**
 * Header gear button. Shows a small corner badge with the number of commits the
 * GitHub upstream is ahead (when > 0), and opens the settings dialog. Self-
 * contained so it can drop into any screen's header.
 */
export function SettingsButton() {
  const lang = useLang((s) => s.lang);
  const behind = useSystem((s) => s.status?.behind ?? -1);
  const [open, setOpen] = useState(false);

  return (
    <>
      <button
        className="settings-btn"
        title={ui('settings', lang)}
        aria-label={ui('settings', lang)}
        onClick={() => setOpen(true)}
      >
        ⚙️
        {behind > 0 && <span className="settings-badge-corner">{behind}</span>}
      </button>
      {open && <SettingsDialog onClose={() => setOpen(false)} />}
    </>
  );
}
