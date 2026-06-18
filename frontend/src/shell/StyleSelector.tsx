import { useState } from 'react';
import { CUSTOM, STYLE_PRESETS, useStyle } from '@app/engine/styleStore';
import { ui, useLang } from '@app/i18n';

/**
 * Picks the explanation style for the next theory generation: Default (none),
 * built-in presets, saved custom styles, or an ad-hoc "Custom…" instruction that
 * can be saved to the dropdown (persisted in the DB).
 */
export function StyleSelector() {
  const lang = useLang((s) => s.lang);
  const selected = useStyle((s) => s.selected);
  const draft = useStyle((s) => s.draft);
  const custom = useStyle((s) => s.custom);
  const select = useStyle((s) => s.select);
  const setDraft = useStyle((s) => s.setDraft);
  const saveCustom = useStyle((s) => s.saveCustom);
  const removeCustom = useStyle((s) => s.removeCustom);
  const [saveName, setSaveName] = useState('');

  const isSavedCustom = custom.some((s) => s.name === selected);

  return (
    <div className="style-selector">
      <span className="style-label">{ui('style', lang)}</span>
      <select value={selected} onChange={(e) => select(e.target.value)}>
        {[...STYLE_PRESETS, ...custom].map((s) => (
          <option key={s.name} value={s.name}>
            {s.name}
          </option>
        ))}
        <option value={CUSTOM}>{ui('styleCustom', lang)}</option>
      </select>
      {isSavedCustom && (
        <button className="style-del" title={ui('deleteFile', lang)} onClick={() => removeCustom(selected)}>
          🗑
        </button>
      )}

      {selected === CUSTOM && (
        <div className="style-custom">
          <textarea
            rows={2}
            placeholder={ui('stylePlaceholder', lang)}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
          />
          <div className="style-save">
            <input
              placeholder={ui('styleName', lang)}
              value={saveName}
              onChange={(e) => setSaveName(e.target.value)}
            />
            <button
              disabled={!saveName.trim() || !draft.trim()}
              onClick={() => {
                saveCustom(saveName.trim(), draft);
                setSaveName('');
              }}
            >
              {ui('save', lang)}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
