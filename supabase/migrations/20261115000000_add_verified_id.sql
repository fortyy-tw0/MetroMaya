-- MetroMaya: remember who made each booking once they verified before booking.
-- verified_id is "egov:<eGov SSO uniqid>" or "id:<hash of the scanned government ID>".
-- Null for bookings made while verification was off.

alter table public.appointments add column if not exists verified_id text;

-- Counting a person's active bookings (at most 3 at once, enforced by AppointmentService).
create index if not exists appointments_verified_active_idx on public.appointments (verified_id)
    where status = 'CONFIRMED' and verified_id is not null;
