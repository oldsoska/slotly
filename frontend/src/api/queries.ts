import { useQuery } from '@tanstack/react-query';
import { auth, services, staff, tenant } from './endpoints';

// Shared queries; one key per resource so mutations know what to invalidate.
export const queryKeys = {
  me: ['me'] as const,
  tenant: ['tenant'] as const,
  staff: ['staff'] as const,
  services: ['services'] as const,
  bookings: ['bookings'] as const,
  workingHours: (staffId: number) => ['working-hours', staffId] as const,
  timeOff: (staffId: number) => ['time-off', staffId] as const,
  availability: ['availability'] as const,
};

export function useMe() {
  return useQuery({ queryKey: queryKeys.me, queryFn: auth.me, staleTime: Infinity });
}

export function useTenant() {
  return useQuery({ queryKey: queryKeys.tenant, queryFn: tenant.current, staleTime: Infinity });
}

export function useStaff() {
  return useQuery({ queryKey: queryKeys.staff, queryFn: staff.list });
}

export function useServices() {
  return useQuery({ queryKey: queryKeys.services, queryFn: services.list });
}

/** OWNER manages the business; STAFF only works with bookings. */
export function useIsOwner(): boolean {
  return useMe().data?.role === 'OWNER';
}
