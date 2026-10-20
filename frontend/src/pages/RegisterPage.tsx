import { useMutation } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link, Navigate, useNavigate } from 'react-router';
import { auth } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import { ErrorMessage } from '../components/ErrorMessage';

const browserZone = Intl.DateTimeFormat().resolvedOptions().timeZone;

export function RegisterPage() {
  const { isAuthenticated, signIn } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({
    businessName: '', slug: '', timezone: browserZone, email: '', password: '',
  });

  const register = useMutation({
    mutationFn: () => auth.register(form),
    onSuccess: (token) => {
      signIn(token.accessToken);
      navigate('/staff', { replace: true });
    },
  });

  if (isAuthenticated) return <Navigate to="/bookings" replace />;

  const set = (field: keyof typeof form) => (e: { target: { value: string } }) =>
    setForm((f) => ({ ...f, [field]: e.target.value }));

  function submit(event: FormEvent) {
    event.preventDefault();
    register.mutate();
  }

  return (
    <main className="auth-page">
      <form className="card auth-card" onSubmit={submit}>
        <h1>Регистрация бизнеса</h1>
        <label>
          Название
          <input required maxLength={200} value={form.businessName} onChange={set('businessName')}
                 placeholder="Барбершоп «Борода»" />
        </label>
        <label>
          Адрес страницы записи
          <div className="input-prefix">
            <span>/b/</span>
            <input required maxLength={100} pattern="[a-z0-9-]+" value={form.slug} onChange={set('slug')}
                   placeholder="boroda" title="Латинские буквы в нижнем регистре, цифры и дефис" />
          </div>
        </label>
        <label>
          Часовой пояс
          <input required value={form.timezone} onChange={set('timezone')} placeholder="Europe/Moscow" />
        </label>
        <label>
          Email владельца
          <input type="email" autoComplete="email" required value={form.email} onChange={set('email')} />
        </label>
        <label>
          Пароль
          <input type="password" autoComplete="new-password" required minLength={8} maxLength={72}
                 value={form.password} onChange={set('password')} />
        </label>
        <ErrorMessage error={register.error} />
        <button type="submit" className="primary" disabled={register.isPending}>
          {register.isPending ? 'Создаём…' : 'Создать кабинет'}
        </button>
        <p className="muted">Уже есть аккаунт? <Link to="/login">Войти</Link></p>
      </form>
    </main>
  );
}
