-- Recorded only after authenticated office staff accept a booking at check-in.
alter table public.appointments add column if not exists accepted_at timestamptz;
