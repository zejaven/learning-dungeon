import { ui, type Lang } from '../i18n';

/**
 * Short, language-registry-driven countdowns ("3h 12m" / "3 ч 12 м").
 *
 * The unit abbreviations live in i18n.ts like every other chrome string — a
 * `lang === 'ru'` check here would be a second place that has to change when a
 * language is added, which is exactly what the registry exists to prevent.
 */

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

/**
 * Time left until `iso`, to the two largest units that matter. Anything already
 * past (or unparseable) reads as "soon", since the caller only shows this for
 * something that has not happened yet.
 */
export function formatUntil(iso: string | null | undefined, lang: Lang): string {
  if (!iso) return ui('durationSoon', lang);
  return formatSpan(Date.parse(iso) - Date.now(), lang);
}

/** {@link formatUntil} for a span that is already in hand. */
export function formatSpan(ms: number, lang: Lang): string {
  if (!Number.isFinite(ms) || ms < MINUTE) return ui('durationSoon', lang);

  const d = ui('unitDay', lang);
  const h = ui('unitHour', lang);
  const m = ui('unitMinute', lang);

  if (ms >= DAY) {
    const days = Math.floor(ms / DAY);
    const hours = Math.round((ms % DAY) / HOUR);
    // Rounding up to 24h would read as "3d 24h"; carry it instead.
    return hours === 24 ? `${days + 1}${d}` : hours > 0 ? `${days}${d} ${hours}${h}` : `${days}${d}`;
  }
  const totalMin = Math.round(ms / MINUTE);
  const hours = Math.floor(totalMin / 60);
  const mins = totalMin % 60;
  return hours > 0 ? `${hours}${h} ${mins}${m}` : `${mins}${m}`;
}
