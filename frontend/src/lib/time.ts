// Date helpers that work in the business's timezone rather than the browser's.
// Calendar dates are plain 'YYYY-MM-DD' strings; instants are ISO strings or Date objects.

interface ZonedParts {
  year: number;
  month: number;
  day: number;
  hour: number;
  minute: number;
  second: number;
}

const partFormatters = new Map<string, Intl.DateTimeFormat>();

function zonedParts(instant: Date, timeZone: string): ZonedParts {
  let format = partFormatters.get(timeZone);
  if (!format) {
    format = new Intl.DateTimeFormat('en-US', {
      timeZone,
      hourCycle: 'h23',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
    partFormatters.set(timeZone, format);
  }
  const parts = Object.fromEntries(format.formatToParts(instant).map((p) => [p.type, p.value]));
  return {
    year: Number(parts.year),
    month: Number(parts.month),
    day: Number(parts.day),
    hour: Number(parts.hour),
    minute: Number(parts.minute),
    second: Number(parts.second),
  };
}

/** Minutes the zone is ahead of UTC at that instant (180 for Moscow). */
function offsetMinutes(utcMillis: number, timeZone: string): number {
  const p = zonedParts(new Date(utcMillis), timeZone);
  return (Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second) - utcMillis) / 60_000;
}

/**
 * The instant at which the wall clock in `timeZone` shows `date` `time`.
 * The offset is looked up twice because the first guess can land on the other side of a DST change.
 */
export function toInstant(date: string, time: string, timeZone: string): Date {
  const [y, m, d] = date.split('-').map(Number);
  const [hh, mm] = time.split(':').map(Number);
  const wallClockAsUtc = Date.UTC(y, m - 1, d, hh, mm);
  const firstGuess = wallClockAsUtc - offsetMinutes(wallClockAsUtc, timeZone) * 60_000;
  return new Date(wallClockAsUtc - offsetMinutes(firstGuess, timeZone) * 60_000);
}

/** The calendar date an instant falls on in the zone. */
export function dateIn(instant: Date | string, timeZone: string): string {
  const p = zonedParts(new Date(instant), timeZone);
  return `${p.year}-${pad(p.month)}-${pad(p.day)}`;
}

export function todayIn(timeZone: string): string {
  return dateIn(new Date(), timeZone);
}

export function addDays(date: string, days: number): string {
  const [y, m, d] = date.split('-').map(Number);
  const shifted = new Date(Date.UTC(y, m - 1, d + days));
  return shifted.toISOString().slice(0, 10);
}

/** Monday of the week containing `date`. */
export function weekStart(date: string): string {
  const [y, m, d] = date.split('-').map(Number);
  const isoWeekday = new Date(Date.UTC(y, m - 1, d)).getUTCDay() || 7;
  return addDays(date, 1 - isoWeekday);
}

export function formatTime(instant: Date | string, timeZone: string): string {
  return new Intl.DateTimeFormat('ru-RU', { timeZone, hour: '2-digit', minute: '2-digit' }).format(new Date(instant));
}

/** "пн, 20 окт." — a calendar date needs no zone, so it is formatted as UTC noon. */
export function formatDay(date: string): string {
  return new Intl.DateTimeFormat('ru-RU', { timeZone: 'UTC', weekday: 'short', day: 'numeric', month: 'short' })
    .format(new Date(`${date}T12:00:00Z`));
}

export function formatDateTime(instant: Date | string, timeZone: string): string {
  return new Intl.DateTimeFormat('ru-RU', {
    timeZone, day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit',
  }).format(new Date(instant));
}

function pad(n: number): string {
  return String(n).padStart(2, '0');
}
