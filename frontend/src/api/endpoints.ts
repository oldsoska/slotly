import { api } from './http';
import type {
  Account, Booking, NewBooking, Service, Slot, Staff, Tenant, TimeOff, TokenResponse, WorkingDay,
} from './types';

export const auth = {
  login: (email: string, password: string) =>
    api<TokenResponse>('/auth/login', { method: 'POST', body: { email, password } }),
  register: (body: { businessName: string; slug: string; timezone: string; email: string; password: string }) =>
    api<TokenResponse>('/auth/register', { method: 'POST', body }),
  me: () => api<Account>('/me'),
};

export const tenant = {
  current: () => api<Tenant>('/tenant'),
};

export const staff = {
  list: () => api<Staff[]>('/staff'),
  create: (fullName: string) => api<Staff>('/staff', { method: 'POST', body: { fullName } }),
  createAccount: (staffId: number, email: string, password: string) =>
    api<Account>(`/staff/${staffId}/account`, { method: 'POST', body: { email, password } }),
  workingHours: (staffId: number) => api<WorkingDay[]>(`/staff/${staffId}/working-hours`),
  replaceWorkingHours: (staffId: number, days: WorkingDay[]) =>
    api<WorkingDay[]>(`/staff/${staffId}/working-hours`, { method: 'PUT', body: days }),
  timeOff: (staffId: number) => api<TimeOff[]>(`/staff/${staffId}/time-off`),
  addTimeOff: (staffId: number, body: { startsAt: string; endsAt: string; reason?: string }) =>
    api<TimeOff>(`/staff/${staffId}/time-off`, { method: 'POST', body }),
  deleteTimeOff: (staffId: number, timeOffId: number) =>
    api<void>(`/staff/${staffId}/time-off/${timeOffId}`, { method: 'DELETE' }),
};

export const services = {
  list: () => api<Service[]>('/services'),
  create: (body: { name: string; durationMinutes: number; price: number }) =>
    api<Service>('/services', { method: 'POST', body }),
};

export const bookings = {
  list: (from: Date, to: Date) =>
    api<Booking[]>(`/bookings?${new URLSearchParams({ from: from.toISOString(), to: to.toISOString() })}`),
  create: (body: NewBooking) => api<Booking>('/bookings', { method: 'POST', body }),
  cancel: (bookingId: number) => api<Booking>(`/bookings/${bookingId}/cancel`, { method: 'POST' }),
};

export const availability = {
  freeSlots: (serviceId: number, date: string, staffId?: number) => {
    const params = new URLSearchParams({ serviceId: String(serviceId), date });
    if (staffId) params.set('staffId', String(staffId));
    return api<Slot[]>(`/availability?${params}`);
  },
};
