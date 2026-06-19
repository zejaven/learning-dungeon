import { create } from 'zustand';
import { fetchAiProviders } from './api';

export interface AiProviderStatus {
  id: string;
  label: string;
  available: boolean;
  version: string;
  error: string | null;
  downloadUrl: string;
  assistantModel: string;
  generateModel: string;
}

interface AiState {
  providers: AiProviderStatus[];
  selectedProvider: string;
  loading: boolean;
  load: () => Promise<void>;
  setProvider: (provider: string) => void;
}

function storedProvider(): string | null {
  try {
    return localStorage.getItem('aiProvider');
  } catch {
    return null;
  }
}

function persistProvider(provider: string): void {
  try {
    localStorage.setItem('aiProvider', provider);
  } catch {
    /* ignore */
  }
}

export const useAi = create<AiState>((set, get) => ({
  providers: [],
  selectedProvider: 'claude',
  loading: false,

  async load() {
    if (get().loading) return;
    set({ loading: true });
    try {
      const res = await fetchAiProviders();
      const ids = new Set(res.providers.map((p) => p.id));
      const stored = storedProvider();
      const selected = stored && ids.has(stored) ? stored : res.defaultProvider || 'claude';
      persistProvider(selected);
      set({ providers: res.providers, selectedProvider: selected, loading: false });
    } catch {
      set({ loading: false });
    }
  },

  setProvider(provider) {
    persistProvider(provider);
    set({ selectedProvider: provider });
  },
}));
