import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { bookings } from '../api/endpoints';
import { queryKeys, useServices, useStaff, useTenant } from '../api/queries';
import type { Booking } from '../api/types';
import { ErrorMessage } from '../components/ErrorMessage';
import { NewBookingForm } from '../components/NewBookingForm';
import { addDays, dateIn, formatDay, formatTime, toInstant, todayIn, weekStart } from '../lib/time';

const STATUS_LABEL: Record<Booking['status'], string> = {
  CONFIRMED: 'подтверждена',
  CANCELLED: 'отменена',
  COMPLETED: 'выполнена',
};

export function BookingsPage() {
  const tenant = useTenant();
  if (!tenant.data) return null;
  return <Week timeZone={tenant.data.timezone} />;
}

function Week({ timeZone }: { timeZone: string }) {
  const queryClient = useQueryClient();
  const today = todayIn(timeZone);
  const [monday, setMonday] = useState(() => weekStart(today));
  const [staffFilter, setStaffFilter] = useState<number | ''>('');
  const [showCancelled, setShowCancelled] = useState(false);
  const [creating, setCreating] = useState(false);

  const days = useMemo(() => Array.from({ length: 7 }, (_, i) => addDays(monday, i)), [monday]);
  // Week boundaries are local midnights of the business, not of the browser
  const from = toInstant(monday, '00:00', timeZone);
  const to = toInstant(addDays(monday, 7), '00:00', timeZone);

  const week = useQuery({
    queryKey: [...queryKeys.bookings, from.toISOString(), to.toISOString()],
    queryFn: () => bookings.list(from, to),
  });
  const staff = useStaff();
  const services = useServices();
  const staffName = new Map(staff.data?.map((s) => [s.id, s.fullName]));
  const serviceName = new Map(services.data?.map((s) => [s.id, s.name]));

  const cancel = useMutation({
    mutationFn: bookings.cancel,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.bookings });
      queryClient.invalidateQueries({ queryKey: queryKeys.availability });
    },
  });

  const visible = (week.data ?? []).filter(
    (b) => (showCancelled || b.status !== 'CANCELLED') && (staffFilter === '' || b.staffId === staffFilter),
  );
  const byDay = new Map(days.map((d) => [d, visible.filter((b) => dateIn(b.startsAt, timeZone) === d)]));
  const now = Date.now();

  return (
    <section>
      <div className="toolbar">
        <h1>Записи</h1>
        <div className="week-nav">
          <button type="button" className="ghost" onClick={() => setMonday(addDays(monday, -7))} aria-label="Прошлая неделя">←</button>
          <button type="button" className="ghost" onClick={() => setMonday(weekStart(today))}>Сегодня</button>
          <button type="button" className="ghost" onClick={() => setMonday(addDays(monday, 7))} aria-label="Следующая неделя">→</button>
          <span className="muted">{formatDay(days[0])} — {formatDay(days[6])}</span>
        </div>
        <div className="filters">
          <select value={staffFilter} onChange={(e) => setStaffFilter(e.target.value ? Number(e.target.value) : '')}
                  aria-label="Мастер">
            <option value="">Все мастера</option>
            {staff.data?.map((s) => <option key={s.id} value={s.id}>{s.fullName}</option>)}
          </select>
          <label className="checkbox">
            <input type="checkbox" checked={showCancelled} onChange={(e) => setShowCancelled(e.target.checked)} />
            Отменённые
          </label>
          <button type="button" className="primary" onClick={() => setCreating((v) => !v)}>
            {creating ? 'Закрыть' : 'Новая запись'}
          </button>
        </div>
      </div>

      {creating && <NewBookingForm timeZone={timeZone} onCreated={() => setCreating(false)} />}
      <ErrorMessage error={week.error ?? cancel.error} />

      <div className="week">
        {days.map((day) => {
          const items = byDay.get(day) ?? [];
          return (
            <div key={day} className={`day${day === today ? ' today' : ''}`}>
              <h2>{formatDay(day)}</h2>
              {items.length === 0 && <p className="muted small">Нет записей</p>}
              {items.map((b) => (
                <article key={b.id} className={`booking ${b.status.toLowerCase()}`}>
                  <div className="booking-time">
                    {formatTime(b.startsAt, timeZone)}–{formatTime(b.endsAt, timeZone)}
                  </div>
                  <div className="booking-client">{b.clientName}</div>
                  <a className="small" href={`tel:${b.clientPhone}`}>{b.clientPhone}</a>
                  <div className="muted small">
                    {serviceName.get(b.serviceId) ?? 'Услуга'} · {staffName.get(b.staffId) ?? 'Мастер'}
                  </div>
                  {b.status !== 'CONFIRMED' && <div className="badge">{STATUS_LABEL[b.status]}</div>}
                  {b.status === 'CONFIRMED' && new Date(b.startsAt).getTime() > now && (
                    <button type="button" className="link-button small" disabled={cancel.isPending}
                            onClick={() => window.confirm(`Отменить запись ${b.clientName}?`) && cancel.mutate(b.id)}>
                      Отменить
                    </button>
                  )}
                </article>
              ))}
            </div>
          );
        })}
      </div>
    </section>
  );
}
