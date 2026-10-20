import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState, type FormEvent } from 'react';
import { staff } from '../api/endpoints';
import { queryKeys } from '../api/queries';
import type { DayOfWeek, WorkingDay } from '../api/types';
import { ErrorMessage } from './ErrorMessage';

const DAYS: [DayOfWeek, string][] = [
  ['MONDAY', 'Понедельник'], ['TUESDAY', 'Вторник'], ['WEDNESDAY', 'Среда'], ['THURSDAY', 'Четверг'],
  ['FRIDAY', 'Пятница'], ['SATURDAY', 'Суббота'], ['SUNDAY', 'Воскресенье'],
];

interface DayRow {
  works: boolean;
  start: string;
  end: string;
}

type Week = Record<DayOfWeek, DayRow>;

function toWeek(days: WorkingDay[]): Week {
  const byDay = new Map(days.map((d) => [d.dayOfWeek, d]));
  return Object.fromEntries(DAYS.map(([day]) => {
    const saved = byDay.get(day);
    // The API returns "10:00:00"; <input type="time"> shows minutes only
    return [day, saved
      ? { works: true, start: saved.start.slice(0, 5), end: saved.end.slice(0, 5) }
      : { works: false, start: '10:00', end: '19:00' }];
  })) as Week;
}

export function WorkingHoursEditor({ staffId, readOnly }: { staffId: number; readOnly: boolean }) {
  const queryClient = useQueryClient();
  const saved = useQuery({ queryKey: queryKeys.workingHours(staffId), queryFn: () => staff.workingHours(staffId) });
  const [week, setWeek] = useState<Week | null>(null);
  const [savedAt, setSavedAt] = useState<number | null>(null);

  useEffect(() => {
    if (saved.data) setWeek(toWeek(saved.data));
  }, [saved.data]);

  const save = useMutation({
    mutationFn: (current: Week) => staff.replaceWorkingHours(staffId, DAYS
      .filter(([day]) => current[day].works)
      .map(([day]) => ({ dayOfWeek: day, start: current[day].start, end: current[day].end }))),
    onSuccess: (days) => {
      queryClient.setQueryData(queryKeys.workingHours(staffId), days);
      queryClient.invalidateQueries({ queryKey: queryKeys.availability });
      setSavedAt(Date.now());
    },
  });

  function update(day: DayOfWeek, patch: Partial<DayRow>) {
    setWeek((w) => (w ? { ...w, [day]: { ...w[day], ...patch } } : w));
    setSavedAt(null);
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (week) save.mutate(week);
  }

  return (
    <form className="card" onSubmit={submit}>
      <h2>Рабочие часы</h2>
      <ErrorMessage error={saved.error} />
      {week && (
        <table className="hours">
          <tbody>
            {DAYS.map(([day, label]) => {
              const row = week[day];
              return (
                <tr key={day} className={row.works ? '' : 'off'}>
                  <th scope="row">
                    <label className="checkbox">
                      <input type="checkbox" checked={row.works} disabled={readOnly}
                             onChange={(e) => update(day, { works: e.target.checked })} />
                      {label}
                    </label>
                  </th>
                  <td>
                    {row.works ? (
                      <span className="time-range">
                        <input type="time" required value={row.start} disabled={readOnly} aria-label={`${label}, начало`}
                               onChange={(e) => update(day, { start: e.target.value })} />
                        –
                        <input type="time" required value={row.end} disabled={readOnly} aria-label={`${label}, конец`}
                               onChange={(e) => update(day, { end: e.target.value })} />
                      </span>
                    ) : <span className="muted">выходной</span>}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
      <ErrorMessage error={save.error} />
      {!readOnly && (
        <div className="actions">
          <button type="submit" className="primary" disabled={!week || save.isPending}>Сохранить</button>
          {savedAt && <span className="muted">Сохранено</span>}
        </div>
      )}
    </form>
  );
}
