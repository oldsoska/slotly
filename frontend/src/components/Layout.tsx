import { useState } from 'react';
import { NavLink, Outlet } from 'react-router';
import { useMe, useTenant } from '../api/queries';
import { useAuth } from '../auth/AuthContext';
import { ErrorMessage } from './ErrorMessage';

export function Layout() {
  const { signOut } = useAuth();
  const tenant = useTenant();
  const me = useMe();

  return (
    <div className="shell">
      <header className="topbar">
        <div className="brand">
          <span className="logo" aria-hidden="true">S</span>
          <div>
            <strong>{tenant.data?.name ?? 'Slotly'}</strong>
            {me.data && <small className="muted">{me.data.email} · {me.data.role === 'OWNER' ? 'владелец' : 'мастер'}</small>}
          </div>
        </div>
        <nav className="nav" aria-label="Разделы">
          <NavLink to="/bookings">Записи</NavLink>
          <NavLink to="/staff">Мастера</NavLink>
          <NavLink to="/services">Услуги</NavLink>
        </nav>
        <button type="button" className="ghost" onClick={signOut}>Выйти</button>
      </header>

      <main className="content">
        <ErrorMessage error={tenant.error ?? me.error} />
        {tenant.data && <ClientLink slug={tenant.data.slug} />}
        <Outlet />
      </main>
    </div>
  );
}

function ClientLink({ slug }: { slug: string }) {
  const url = `${window.location.origin}/b/${slug}`;
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // clipboard unavailable (e.g. insecure context); the link is still visible to copy by hand
    }
  }

  return (
    <section className="client-link" aria-label="Ссылка для клиентов">
      <span className="muted">Страница записи для клиентов:</span>
      <a href={url} target="_blank" rel="noreferrer">{url}</a>
      <button type="button" className="ghost small" onClick={copy}>{copied ? 'Скопировано' : 'Копировать'}</button>
    </section>
  );
}
