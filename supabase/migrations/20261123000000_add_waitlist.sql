-- "Notify me if a place opens" on fully booked days. One row per email + office + day; notified_at is set once
-- the opening email has gone out. RLS on with no policies: only the server's secret key can read or write.
create table if not exists public.waitlist (
    id uuid primary key default gen_random_uuid(),
    office_id text not null,
    date date not null,
    email text not null check (char_length(email) between 3 and 254),
    created_at timestamptz not null default now(),
    notified_at timestamptz
);
create unique index if not exists waitlist_office_date_email on public.waitlist (office_id, date, lower(email));
create index if not exists waitlist_waiting on public.waitlist (office_id, date) where notified_at is null;
alter table public.waitlist enable row level security;
