import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { staff as staffApi } from '../api/endpoints';
import { queryKeys, useIsOwner, useStaff, useTenant } from '../api/queries';
import { ErrorMessage } from '../components/ErrorMessage';
import { StaffAccountForm } from '../components/StaffAccountForm';
import { TimeOffList } from '../components/TimeOffList';
import { WorkingHoursEditor } from '../components/WorkingHoursEditor';

export function StaffPage() {
  const queryClient = useQueryClient();
  const isOwner = useIsOwner();
  const tenant = useTenant();
  const staff = useStaff();
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [fullName, setFullName] = useState('');

  const create = useMutation({
    mutationFn: () => staffApi.create(fullName.trim()),
    onSuccess: (created) => {
      setFullName('');
      setSelectedId(created.id);
      queryClient.invalidateQueries({ queryKey: queryKeys.staff });
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    create.mutate();
  }

  const selected = staff.data?.find((s) => s.id === selectedId) ?? staff.data?.[0];

  return (
    <section>
      <div className="toolbar">
        <h1>Мастера</h1>
      </div>
      <ErrorMessage error={staff.error} />

      <div className="split">
        <aside className="card list-card">
          <ul className="select-list">
            {staff.data?.map((s) => (
              <li key={s.id}>
                <button type="button" aria-pressed={selected?.id === s.id} onClick={() => setSelectedId(s.id)}>
                  {s.fullName}
                </button>
              </li>
            ))}
          </ul>
          {staff.data?.length === 0 && <p className="muted">Пока никого нет. Добавьте первого мастера.</p>}
          {isOwner && (
            <form className="inline-form" onSubmit={submit}>
              <input required maxLength={200} placeholder="Имя мастера" value={fullName}
                     onChange={(e) => setFullName(e.target.value)} aria-label="Имя нового мастера" />
              <button type="submit" className="primary" disabled={create.isPending}>Добавить</button>
            </form>
          )}
          <ErrorMessage error={create.error} />
        </aside>

        {selected && tenant.data && (
          <div className="stack" key={selected.id}>
            <WorkingHoursEditor staffId={selected.id} readOnly={!isOwner} />
            <TimeOffList staffId={selected.id} timeZone={tenant.data.timezone} readOnly={!isOwner} />
            {isOwner && <StaffAccountForm staffId={selected.id} staffName={selected.fullName} />}
          </div>
        )}
      </div>
    </section>
  );
}
