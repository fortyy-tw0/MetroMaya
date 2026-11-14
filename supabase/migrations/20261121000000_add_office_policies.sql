-- Office-controlled calendar and service checklists. RLS allows only the server's service role.
create table if not exists public.office_policies (
    office_id text primary key,
    slot_times jsonb not null,
    capacity_per_slot integer not null check (capacity_per_slot between 1 and 50),
    closures jsonb not null default '{}'::jsonb,
    checklists jsonb not null default '{}'::jsonb,
    updated_at timestamptz not null default now()
);
alter table public.office_policies enable row level security;
