import { useAi } from '@app/engine/aiStore';
import { useUsage } from '@app/engine/usageStore';
import { ui, useLang } from '@app/i18n';

export function AiProviderSelector() {
  const lang = useLang((s) => s.lang);
  const providers = useAi((s) => s.providers);
  const selected = useAi((s) => s.selectedProvider);
  const setProvider = useAi((s) => s.setProvider);
  const refreshUsage = useUsage((s) => s.refresh);

  function change(provider: string) {
    setProvider(provider);
    setTimeout(() => void refreshUsage(), 0);
  }

  return (
    <label className="ai-picker" title={ui('aiProvider', lang)}>
      <span>{ui('aiProviderShort', lang)}</span>
      <select value={selected} onChange={(e) => change(e.target.value)}>
        {(providers.length ? providers : fallbackProviders()).map((p) => (
          <option key={p.id} value={p.id}>
            {p.label}{p.available ? '' : ` (${ui('missing', lang)})`}
          </option>
        ))}
      </select>
    </label>
  );
}

function fallbackProviders() {
  return [
    { id: 'claude', label: 'Claude', available: true },
    { id: 'codex', label: 'Codex', available: false },
    { id: 'kimi', label: 'Kimi', available: false },
  ];
}
