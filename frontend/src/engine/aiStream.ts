/**
 * Helpers to interpret provider-neutral AI stream lines.
 *
 * The backend emits provider-neutral `{ type: "text_delta" | "activity" }`
 * events, while the Claude parser below remains as a fallback for older task
 * history that may still contain raw Claude Code `stream-json` lines.
 */

interface AnyObj {
  [k: string]: unknown;
}

function parse(raw: string): AnyObj | null {
  try {
    return JSON.parse(raw) as AnyObj;
  } catch {
    return null;
  }
}

/** Returns incremental text from a provider-neutral or legacy Claude line. */
export function parseTextDelta(raw: string): string | null {
  const obj = parse(raw);
  if (!obj) return null;

  if (obj.type === 'text_delta' && typeof obj.text === 'string') {
    return obj.text;
  }

  if (obj.type !== 'stream_event') return null;
  const event = obj.event as AnyObj | undefined;
  if (!event || event.type !== 'content_block_delta') return null;
  const delta = event.delta as AnyObj | undefined;
  if (delta && delta.type === 'text_delta' && typeof delta.text === 'string') {
    return delta.text;
  }
  return null;
}

/** Returns a human-readable activity-log line, or null. */
export function parseActivity(raw: string): string | null {
  const obj = parse(raw);
  if (!obj) return null;

  if (obj.type === 'activity' && typeof obj.text === 'string') {
    return obj.text.trim() || null;
  }
  if (obj.type === 'text_delta' && typeof obj.text === 'string') {
    return obj.text.trim() || null;
  }

  switch (obj.type) {
    case 'system':
      return obj.subtype === 'init' ? '- session started' : null;
    case 'assistant': {
      const message = obj.message as AnyObj | undefined;
      const content = (message?.content as AnyObj[] | undefined) ?? [];
      const parts: string[] = [];
      for (const block of content) {
        if (block.type === 'text' && typeof block.text === 'string' && block.text.trim()) {
          parts.push(block.text.trim());
        } else if (block.type === 'tool_use') {
          const name = String(block.name ?? 'tool');
          const input = (block.input as AnyObj | undefined) ?? {};
          const target = input.file_path ?? input.path ?? input.command ?? '';
          parts.push(`[tool] ${name} ${String(target)}`.trim());
        }
      }
      return parts.length ? parts.join('\n') : null;
    }
    case 'result': {
      const subtype = String(obj.subtype ?? 'done');
      return `result: ${subtype}`;
    }
    default:
      return null;
  }
}
