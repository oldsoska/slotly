// Thin fetch wrapper: adds the bearer token, parses Problem Details errors and reports expired sessions.

const TOKEN_KEY = 'slotly.accessToken';

/**
 * The token lives in sessionStorage: it survives a page reload but not closing the tab,
 * and other tabs or sites cannot read it. It expires on the server after an hour anyway.
 */
export const tokenStore = {
  get(): string | null {
    try {
      return sessionStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },
  set(token: string): void {
    try {
      sessionStorage.setItem(TOKEN_KEY, token);
    } catch {
      // storage disabled: the session simply ends with the page
    }
  },
  clear(): void {
    try {
      sessionStorage.removeItem(TOKEN_KEY);
    } catch {
      // nothing stored
    }
  },
};

export const SESSION_EXPIRED_EVENT = 'slotly:session-expired';

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
}

export async function api<T>(path: string, { method = 'GET', body }: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const token = tokenStore.get();
  if (token) headers.Authorization = `Bearer ${token}`;

  const response = await fetch(`/api/v1${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (response.status === 401 && token) {
    tokenStore.clear();
    window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
  }
  if (!response.ok) {
    const problem: { detail?: string } | null = await response.json().catch(() => null);
    throw new ApiError(response.status, describeError(response, problem?.detail));
  }
  return (response.status === 204 ? undefined : await response.json()) as T;
}

// Server messages are English and meant for developers; the most common ones get a Russian wording.
const KNOWN_MESSAGES: [RegExp, string][] = [
  [/^This time slot is already booked$/, 'Это время уже занято'],
  [/^Booking is outside working hours$/, 'Время выходит за рабочие часы мастера'],
  [/does not work on/, 'Мастер в этот день не работает'],
  [/is unavailable at this time$/, 'У мастера в это время отгул'],
  [/^Email is already registered$/, 'Этот email уже зарегистрирован'],
  [/^Slug '.*' is already taken$/, 'Такой адрес страницы уже занят'],
  [/^Invalid email or password$/, 'Неверный email или пароль'],
  [/already has an account$/, 'У мастера уже есть аккаунт'],
  [/^Unknown timezone/, 'Неизвестный часовой пояс'],
  [/end must be after start$/, 'Конец рабочего дня должен быть позже начала'],
  [/^endsAt must be after startsAt$/, 'Конец отгула должен быть позже начала'],
];

function describeError(response: Response, detail: string | undefined): string {
  if (response.status === 429) {
    const seconds = Number(response.headers.get('Retry-After')) || 60;
    return `Слишком много попыток. Попробуйте через ${Math.ceil(seconds / 60)} мин.`;
  }
  if (response.status === 403) return 'Недостаточно прав для этого действия';
  if (detail) {
    const known = KNOWN_MESSAGES.find(([pattern]) => pattern.test(detail));
    if (known) return known[1];
  }
  if (response.status === 400) return 'Проверьте введённые данные';
  if (response.status === 401) return 'Сессия истекла, войдите снова';
  if (response.status === 404) return 'Не найдено';
  return detail ?? `Ошибка сервера (${response.status})`;
}
