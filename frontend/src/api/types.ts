// Mirrors the backend DTOs (dev.slotly.*Dtos). Instants are ISO strings in UTC.

export type Role = 'OWNER' | 'STAFF';
export type BookingStatus = 'CONFIRMED' | 'CANCELLED' | 'COMPLETED';
export type DayOfWeek = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface Account {
  userId: number;
  tenantId: number;
  email: string;
  role: Role;
  staffId: number | null;
}

export interface Tenant {
  id: number;
  name: string;
  slug: string;
  timezone: string;
}

export interface Staff {
  id: number;
  fullName: string;
  active: boolean;
}

export interface Service {
  id: number;
  name: string;
  durationMinutes: number;
  price: number;
  active: boolean;
}

export interface WorkingDay {
  dayOfWeek: DayOfWeek;
  start: string;
  end: string;
}

export interface TimeOff {
  id: number;
  staffId: number;
  startsAt: string;
  endsAt: string;
  reason: string | null;
}

export interface Booking {
  id: number;
  staffId: number;
  serviceId: number;
  clientName: string;
  clientPhone: string;
  startsAt: string;
  endsAt: string;
  status: BookingStatus;
}

export interface Slot {
  staffId: number;
  staffName: string;
  startsAt: string;
  endsAt: string;
}

export interface NewBooking {
  staffId: number;
  serviceId: number;
  clientName: string;
  clientPhone: string;
  startsAt: string;
}
