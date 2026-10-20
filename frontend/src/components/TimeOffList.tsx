import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { staff } from '../api/endpoints';
import { queryKeys } from '../api/queries';
import { formatDateTime, toInstant, todayIn } from '../lib/time';
import { ErrorMessage } from './ErrorMessage';

interface Props {
  staffId: number;
  timeZone: string;
  readOnly: boolean;
}

export function TimeOffList({ staffId, timeZone, readOnly }: Props) {
  const queryClient = useQueryClient();
  const list = useQuery({ queryKey: queryKeys.timeOff(staffId), queryFn: () => staff.timeOff(staffId) });
  const [form, setForm] = useState(() => ({
    fromDate: todayIn(timeZone), fromTime: '13:00', toDate: todayIn(timeZone), toTime: '14:00', reason: '',
  }));

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: queryKeys.timeOff(staffId) });
    queryClient.invalidateQueries({ queryKey: queryKeys.availability });
  };

  const add = useMutation({
    // Entered in the business's local time, sent as instants
    mutationFn: () => staff.addTimeOff(staffId, {
      startsAt: toInstant(form.fromDate, form.fromTime, timeZone).toISOString(),
      endsAt: toInstant(form.toDate, form.toTime, timeZone).toISOString(),
      reason: form.reason.trim() || undefined,
    }),
    onSuccess: () => {
      setForm((f) => ({ ...f, reason: '' }));
      refresh();
    },
  });

  const remove = useMutation({
    mutationFn: (timeOffId: number) => staff.deleteTimeOff(staffId, timeOffId),
    onSuccess: refresh,
  });

  const set = (field: keyof typeof form) => (e: { target: { value: string } }) =>
    setForm((f) => ({ ...f, [field]: e.target.value }));

  function submit(event: FormEvent) {
    event.preventDefault();
    add.mutate();
  }

  const upcoming = (list.data ?? []).filter((t) => new Date(t.endsAt).getTime() > Date.now());

  return (
    <section className="card">
      <h2>Отгулы</h2>
      <ErrorMessage error={list.error ?? remove.error} />
      {upcoming.length === 0 && <p className="muted">Предстоящих отгулов нет</p>}
      <ul className="plain-list">
        {upcoming.map((t) => (
          <li key={t.id}>
            <span>{formatDateTime(t.startsAt, timeZone)} — {formatDateTime(t.endsAt, timeZone)}</span>
            {t.reason && <span className="muted"> · {t.reason}</span>}
            {!readOnly && (
              <button type="button" className="link-button small" disabled={remove.isPending}
                      onClick={() => remove.mutate(t.id)}>Удалить</button>
            )}
          </li>
        ))}
      </ul>

      {!readOnly && (
        <form className="time-off-form" onSubmit={submit}>
          <label>С<span className="time-range">
            <input type="date" required value={form.fromDate} onChange={set('fromDate')} aria-label="Дата начала" />
            <input type="time" required value={form.fromTime} onChange={set('fromTime')} aria-label="Время начала" />
          </span></label>
          <label>По<span className="time-range">
            <input type="date" required value={form.toDate} onChange={set('toDate')} aria-label="Дата конца" />
            <input type="time" required value={form.toTime} onChange={set('toTime')} aria-label="Время конца" />
          </span></label>
          <label>Причина
            <input maxLength={200} value={form.reason} onChange={set('reason')} placeholder="Необязательно" />
          </label>
          <button type="submit" className="primary align-end" disabled={add.isPending}>Добавить отгул</button>
        </form>
      )}
      <ErrorMessage error={add.error} />
    </section>
  );
}
