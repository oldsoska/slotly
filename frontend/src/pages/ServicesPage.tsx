import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { services as servicesApi } from '../api/endpoints';
import { queryKeys, useIsOwner, useServices } from '../api/queries';
import { ErrorMessage } from '../components/ErrorMessage';

const rub = new Intl.NumberFormat('ru-RU', { style: 'currency', currency: 'RUB', maximumFractionDigits: 0 });

export function ServicesPage() {
  const queryClient = useQueryClient();
  const isOwner = useIsOwner();
  const services = useServices();
  const [form, setForm] = useState({ name: '', durationMinutes: '45', price: '' });

  const create = useMutation({
    mutationFn: () => servicesApi.create({
      name: form.name.trim(), durationMinutes: Number(form.durationMinutes), price: Number(form.price),
    }),
    onSuccess: () => {
      setForm({ name: '', durationMinutes: '45', price: '' });
      queryClient.invalidateQueries({ queryKey: queryKeys.services });
    },
  });

  const set = (field: keyof typeof form) => (e: { target: { value: string } }) =>
    setForm((f) => ({ ...f, [field]: e.target.value }));

  function submit(event: FormEvent) {
    event.preventDefault();
    create.mutate();
  }

  return (
    <section>
      <div className="toolbar">
        <h1>Услуги</h1>
      </div>
      <ErrorMessage error={services.error} />

      <div className="card">
        {services.data?.length === 0 && <p className="muted">Услуг пока нет.</p>}
        {!!services.data?.length && (
          <table className="table">
            <thead>
              <tr><th scope="col">Название</th><th scope="col">Длительность</th><th scope="col">Цена</th></tr>
            </thead>
            <tbody>
              {services.data.map((s) => (
                <tr key={s.id} className={s.active ? '' : 'off'}>
                  <td>{s.name}</td>
                  <td>{s.durationMinutes} мин</td>
                  <td>{rub.format(s.price)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        {isOwner && (
          <form className="grid-4" onSubmit={submit}>
            <label>Название
              <input required maxLength={200} value={form.name} onChange={set('name')} placeholder="Мужская стрижка" />
            </label>
            <label>Минут
              <input type="number" required min={5} max={600} step={5} value={form.durationMinutes}
                     onChange={set('durationMinutes')} />
            </label>
            <label>Цена, ₽
              <input type="number" required min={0} step="0.01" value={form.price} onChange={set('price')} />
            </label>
            <button type="submit" className="primary align-end" disabled={create.isPending}>Добавить</button>
          </form>
        )}
        <ErrorMessage error={create.error} />
      </div>
    </section>
  );
}
