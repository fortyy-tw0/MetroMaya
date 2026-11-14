-- MetroMaya: permanent storage for bookings.
-- Written by the Java server (SupabaseAppointmentRepository) with the project's secret / service role key.

create table if not exists public.appointments (
    reference_no      text primary key check (reference_no ~ '^BPL-[0-9]{8}-[A-Z0-9]{5}$'),
    status            text not null default 'CONFIRMED' check (status in ('CONFIRMED', 'CANCELLED')),
    service_id        text not null,
    office_id         text not null,
    appt_date         date not null,
    appt_time         time not null,
    business_name     text not null,
    business_address  text not null,
    industry_id       text not null,
    employees         integer not null check (employees between 1 and 100000),
    owner_name        text not null,
    email             text not null,
    phone             text not null check (phone ~ '^09[0-9]{9}$'),
    quote             jsonb not null,          -- the fee snapshot taken when the slot was booked
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now()
);

-- Slot look-ups: how many active bookings an office has at a date and time.
create index if not exists appointments_slot_idx on public.appointments (office_id, appt_date, appt_time)
    where status = 'CONFIRMED';

-- "One active booking per email + business + service", enforced by the database as well.
create unique index if not exists appointments_one_active_idx
    on public.appointments (lower(email), lower(business_name), service_id)
    where status = 'CONFIRMED';

-- Only the server (secret / service role key, which bypasses RLS) may touch bookings.
-- No policies are created, so the public anon key can read or write nothing.
alter table public.appointments enable row level security;
