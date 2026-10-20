export function ErrorMessage({ error }: { error: unknown }) {
  if (!error) return null;
  const message = error instanceof Error ? error.message : 'Что-то пошло не так';
  return <p className="alert" role="alert">{message}</p>;
}
