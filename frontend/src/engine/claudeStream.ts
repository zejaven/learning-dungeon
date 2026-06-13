/**
 * Helpers to interpret Claude Code `stream-json` lines.
 *
 * The backend always passes --include-partial-messages, so a stream contains
 * both fine-grained `stream_event` text deltas (good for smooth Q&A) and
 * coarse `assistant` / tool_use / `result` messages (good for an activity log).
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

/** Returns the incremental text of a partial-message delta, or null. */
export function parseTextDelta(raw: string): string | null {
  const obj = parse(raw);
  if (!obj || obj.type !== 'stream_event') return null;
  const event = obj.event as AnyObj | undefined;
  if (!event || event.type !== 'content_block_delta') return null;
  const delta = event.delta as AnyObj | undefined;
  if (delta && delta.type === 'text_delta' && typeof delta.text === 'string') {
    return delta.text;
  }
  return null;
}

/** Returns a human-readable activity-log line (message granularity), or null. */
export function parseActivity(raw: string): string | null {
  const obj = parse(raw);
  if (!obj) return null;

  switch (obj.type) {
    case 'system':
      return obj.subtype === 'init' ? '· session started' : null;
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
          parts.push(`🔧 ${name} ${String(target)}`.trim());
        }
      }
      return parts.length ? parts.join('\n') : null;
    }
    case 'result': {
      const subtype = String(obj.subtype ?? 'done');
      return `✓ result: ${subtype}`;
    }
    default:
      return null;
  }
}
