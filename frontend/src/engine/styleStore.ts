import { create } from 'zustand';
import { deleteStyle, fetchStyles, saveStyle, type StyleDto } from './api';

/**
 * The "explanation style" chosen for theory generation: an analogy theme woven
 * into the explanation prose to aid memorisation. Built-in presets are hardcoded
 * here; user-defined styles are persisted in the DB (via /api/styles).
 *
 * `Default` means no style. The component sends {@link StyleState.instruction} as
 * the `style` field of a generate request.
 */
export interface Style {
  name: string;
  /** The instruction injected into the prompt; '' for Default. */
  instruction: string;
  builtin: boolean;
}

export const STYLE_PRESETS: Style[] = [
  { name: 'Default', instruction: '', builtin: true },
  {
    name: 'Real world',
    instruction: 'everyday real-life situations and objects (kitchens, post offices, traffic, …)',
    builtin: true,
  },
  { name: 'Sports', instruction: 'sports (teams, matches, training, tactics)', builtin: true },
  {
    name: 'Video games',
    instruction: 'video games (levels, inventory, NPCs, multiplayer, loot)',
    builtin: true,
  },
];

/** Sentinel selection for the ad-hoc (unsaved) custom instruction. */
export const CUSTOM = '__custom__';

interface StyleState {
  custom: Style[];
  /** Selected style name, or {@link CUSTOM} for the ad-hoc draft. */
  selected: string;
  /** Free-text instruction used when `selected === CUSTOM`. */
  draft: string;

  all: () => Style[];
  /** The instruction for the current selection ('' for Default). */
  instruction: () => string;

  load: () => Promise<void>;
  select: (name: string) => void;
  setDraft: (draft: string) => void;
  saveCustom: (name: string, instruction: string) => Promise<void>;
  removeCustom: (name: string) => Promise<void>;
}

const STORE_KEY = 'genStyle';

function loadSelected(): string {
  try {
    return localStorage.getItem(STORE_KEY) || 'Default';
  } catch {
    return 'Default';
  }
}

export const useStyle = create<StyleState>((set, get) => ({
  custom: [],
  selected: loadSelected(),
  draft: '',

  all: () => [...STYLE_PRESETS, ...get().custom],

  instruction: () => {
    const { selected, draft } = get();
    if (selected === CUSTOM) return draft.trim();
    return get().all().find((s) => s.name === selected)?.instruction ?? '';
  },

  async load() {
    const dtos = await fetchStyles();
    set({ custom: dtos.map((d: StyleDto) => ({ ...d, builtin: false })) });
  },

  select(name) {
    set({ selected: name });
    try {
      localStorage.setItem(STORE_KEY, name);
    } catch {
      /* ignore */
    }
  },

  setDraft(draft) {
    set({ draft });
  },

  async saveCustom(name, instruction) {
    const clean = name.trim();
    if (!clean || !instruction.trim()) return;
    await saveStyle({ name: clean, instruction: instruction.trim() });
    await get().load();
    get().select(clean);
  },

  async removeCustom(name) {
    await deleteStyle(name);
    await get().load();
    if (get().selected === name) get().select('Default');
  },
}));
