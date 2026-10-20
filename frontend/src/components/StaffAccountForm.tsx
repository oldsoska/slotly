import { useMutation } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { staff } from '../api/endpoints';
import { ErrorMessage } from './ErrorMessage';

/** Gives a master their own STAFF login: they see bookings and schedules but cannot change the business. */
export function StaffAccountForm({ staffId, staffName }: { staffId: number; staffName: string }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const create = useMutation({
    mutationFn: () => staff.createAccount(staffId, email, password),
    onSuccess: () => setPassword(''),
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    create.mutate();
  }

  return (
    <form className="card" onSubmit={submit}>
      <h2>Доступ в кабинет</h2>
      <p className="muted">Отдельный логин для «{staffName}»: с ним видны записи и расписание, но настройки бизнеса менять нельзя.</p>
      {create.isSuccess ? (
        <p className="success">Аккаунт создан: {create.data.email}</p>
      ) : (
        <div className="grid-3">
          <label>Email
            <input type="email" autoComplete="off" required value={email} onChange={(e) => setEmail(e.target.value)} />
          </label>
          <label>Пароль
            <input type="password" autoComplete="new-password" required minLength={8} maxLength={72}
                   value={password} onChange={(e) => setPassword(e.target.value)} />
          </label>
          <button type="submit" className="primary align-end" disabled={create.isPending}>Создать доступ</button>
        </div>
      )}
      <ErrorMessage error={create.error} />
    </form>
  );
}
