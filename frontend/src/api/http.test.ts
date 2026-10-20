import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError } from './http';

function respond(status: number, body?: unknown, headers: Record<string, string> = {}) {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(
    body === undefined ? null : JSON.stringify(body),
    { status, headers: { 'Content-Type': 'application/json', ...headers } },
  )));
}

async function errorOf(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise;
  } catch (e) {
    return e as ApiError;
  }
  throw new Error('expected the request to fail');
}

afterEach(() => vi.unstubAllGlobals());

describe('api', () => {
  it('returns the parsed body on success and nothing on 204', async () => {
    respond(200, { id: 1 });
    await expect(api('/staff/1')).resolves.toEqual({ id: 1 });

    respond(204);
    await expect(api('/staff/1/time-off/2', { method: 'DELETE' })).resolves.toBeUndefined();
  });

  it('translates known Problem Details messages', async () => {
    respond(409, { status: 409, detail: 'This time slot is already booked' });

    const error = await errorOf(api('/bookings', { method: 'POST', body: {} }));

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(409);
    expect(error.message).toBe('Это время уже занято');
  });

  it('turns Retry-After into a wait in minutes', async () => {
    respond(429, { status: 429, detail: 'Too many requests, retry in 90 s' }, { 'Retry-After': '90' });

    expect((await errorOf(api('/auth/login', { method: 'POST', body: {} }))).message)
      .toBe('Слишком много попыток. Попробуйте через 2 мин.');
  });

  it('falls back to a generic message for unknown validation errors', async () => {
    respond(400, { status: 400, detail: 'Invalid request content.' });

    expect((await errorOf(api('/services', { method: 'POST', body: {} }))).message).toBe('Проверьте введённые данные');
  });
});
