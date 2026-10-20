import { useMutation } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router';
import { auth } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import { ErrorMessage } from '../components/ErrorMessage';

export function LoginPage() {
  const { isAuthenticated, signIn } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/bookings';
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const login = useMutation({
    mutationFn: () => auth.login(email, password),
    onSuccess: (token) => {
      signIn(token.accessToken);
      navigate(from, { replace: true });
    },
  });

  if (isAuthenticated) return <Navigate to={from} replace />;

  function submit(event: FormEvent) {
    event.preventDefault();
    login.mutate();
  }

  return (
    <main className="auth-page">
      <form className="card auth-card" onSubmit={submit}>
        <h1>Вход в кабинет</h1>
        <label>
          Email
          <input type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <label>
          Пароль
          <input type="password" autoComplete="current-password" required value={password}
                 onChange={(e) => setPassword(e.target.value)} />
        </label>
        <ErrorMessage error={login.error} />
        <button type="submit" className="primary" disabled={login.isPending}>
          {login.isPending ? 'Входим…' : 'Войти'}
        </button>
        <p className="muted">Нет аккаунта? <Link to="/register">Зарегистрировать бизнес</Link></p>
      </form>
    </main>
  );
}
