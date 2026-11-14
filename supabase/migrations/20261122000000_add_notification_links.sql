-- Encrypted private management link for appointment reminders. Never expose through public API.
alter table public.appointments add column if not exists management_token_ciphertext text;
create table if not exists public.notification_events (
    event_key text primary key,
    sent_at timestamptz not null default now()
);
alter table public.notification_events enable row level security;
