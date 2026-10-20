import { describe, expect, it } from 'vitest';
import { addDays, dateIn, toInstant, weekStart } from './time';

describe('toInstant', () => {
  it('converts Moscow wall-clock time, which has no DST', () => {
    expect(toInstant('2026-12-01', '10:00', 'Europe/Moscow').toISOString()).toBe('2026-12-01T07:00:00.000Z');
  });

  it('applies the summer and winter offsets of a zone with DST', () => {
    expect(toInstant('2026-07-15', '10:00', 'Europe/Berlin').toISOString()).toBe('2026-07-15T08:00:00.000Z');
    expect(toInstant('2026-12-15', '10:00', 'Europe/Berlin').toISOString()).toBe('2026-12-15T09:00:00.000Z');
  });

  it('gets midnight right on the day clocks change', () => {
    // Berlin switches to winter time at 03:00 on 2026-10-25; midnight is still summer time
    expect(toInstant('2026-10-25', '00:00', 'Europe/Berlin').toISOString()).toBe('2026-10-24T22:00:00.000Z');
    expect(toInstant('2026-10-26', '00:00', 'Europe/Berlin').toISOString()).toBe('2026-10-25T23:00:00.000Z');
  });
});

describe('dateIn', () => {
  it('uses the zone, not UTC, to decide the calendar date', () => {
    // 22:30 UTC is already the next day in Moscow
    expect(dateIn('2026-12-01T22:30:00Z', 'Europe/Moscow')).toBe('2026-12-02');
    expect(dateIn('2026-12-01T22:30:00Z', 'America/New_York')).toBe('2026-12-01');
  });
});

describe('calendar arithmetic', () => {
  it('finds the Monday of the week', () => {
    expect(weekStart('2026-10-20')).toBe('2026-10-19'); // Tuesday
    expect(weekStart('2026-10-25')).toBe('2026-10-19'); // Sunday belongs to the week before
    expect(weekStart('2026-10-19')).toBe('2026-10-19');
  });

  it('adds days across month and year ends', () => {
    expect(addDays('2026-12-30', 3)).toBe('2027-01-02');
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28');
  });
});
