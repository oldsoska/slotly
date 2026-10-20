import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { availability, bookings } from '../api/endpoints';
import { queryKeys, useServices, useStaff } from '../api/queries';
import type { Slot } from '../api/types';
import { formatTime, todayIn } from '../lib/time';
import { ErrorMessage } from './ErrorMessage';

/** For a client who phones in: same free-slot search as the public page, booked on their behalf. */
export function NewBookingForm({ timeZone, onCreated }: { timeZone: string; onCreated: () => void }) {
  const queryClient = useQueryClient();
  const services = useServices();
  const staff = useStaff();
  const [serviceId, setServiceId] = useState<number | ''>('');
  const [staffId, setStaffId] = useState<number | ''>('');
  const [date, setDate] = useState(() => todayIn(timeZone));
  const [slot, setSlot] = useState<Slot | null>(null);
  const [clientName, setClientName] = useState('');
  const [clientPhone, setClientPhone] = useState('');

  const slots = useQuery({
    queryKey: [...queryKeys.availability, serviceId, date, staffId],
    queryFn: () => availability.freeSlots(Number(serviceId), date, staffId === '' ? undefined : staffId),
    enabled: serviceId !== '' && date !== '',
  });

  const create = useMutation({
    mutationFn: () => bookings.create({
      staffId: slot!.staffId, serviceId: Number(serviceId), clientName, clientPhone, startsAt: slot!.startsAt,
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.bookings });
      queryClient.invalidateQueries({ queryKey: queryKeys.availability });
      onCreated();
    },
    // The slot may have been taken meanwhile: refresh the list so the user picks another
    onError: () => {
      setSlot(null);
      slots.refetch();
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    if (slot) create.mutate();
  }

  const sorted = [...(slots.data ?? [])].sort((a, b) => a.startsAt.localeCompare(b.startsAt));

  return (
    <form className="card new-booking" onSubmit={submit}>
      <h2>Новая запись</h2>
      <div className="grid-3">
        <label>
          Услуга
          <select required value={serviceId} onChange={(e) => { setServiceId(Number(e.target.value) || ''); setSlot(null); }}>
            <option value="">Выберите…</option>
            {services.data?.filter((s) => s.active).map((s) => (
              <option key={s.id} value={s.id}>{s.name} · {s.durationMinutes} мин</option>
            ))}
          </select>
        </label>
        <label>
          Мастер
          <select value={staffId} onChange={(e) => { setStaffId(Number(e.target.value) || ''); setSlot(null); }}>
            <option value="">Любой</option>
            {staff.data?.filter((s) => s.active).map((s) => <option key={s.id} value={s.id}>{s.fullName}</option>)}
          </select>
        </label>
        <label>
          Дата
          <input type="date" required value={date} onChange={(e) => { setDate(e.target.value); setSlot(null); }} />
        </label>
      </div>

      {serviceId !== '' && (
        <div className="slots" role="group" aria-label="Свободное время">
          {slots.isLoading && <p className="muted">Ищем свободное время…</p>}
          {slots.data?.length === 0 && <p className="muted">Свободного времени нет</p>}
          {sorted.map((s) => (
            <button key={`${s.staffId}-${s.startsAt}`} type="button" aria-pressed={slot === s}
                    className="slot" onClick={() => setSlot(s)}>
              {formatTime(s.startsAt, timeZone)}
              {staffId === '' && <small>{s.staffName}</small>}
            </button>
          ))}
        </div>
      )}

      {slot && (
        <div className="grid-3">
          <label>
            Имя клиента
            <input required maxLength={200} value={clientName} onChange={(e) => setClientName(e.target.value)} />
          </label>
          <label>
            Телефон
            <input type="tel" required pattern="\+?[0-9]{7,15}" placeholder="+79991234567" value={clientPhone}
                   onChange={(e) => setClientPhone(e.target.value)} />
          </label>
          <button type="submit" className="primary align-end" disabled={create.isPending}>
            Записать на {formatTime(slot.startsAt, timeZone)}
          </button>
        </div>
      )}
      <ErrorMessage error={slots.error ?? create.error} />
    </form>
  );
}
