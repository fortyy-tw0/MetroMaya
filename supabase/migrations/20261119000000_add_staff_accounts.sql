-- Private staff identities. Only the server's service role may access this table.
create table if not exists public.staff_accounts (
    username text primary key check (username ~ '^[a-z0-9][a-z0-9._-]{2,39}$'),
    password_hash text not null,
    role text not null check (role in ('STAFF', 'DISTRICT_HEAD', 'ADMIN', 'TREASURER')),
    office_id text,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    check ((role = 'ADMIN' and office_id is null) or (role <> 'ADMIN' and office_id is not null))
);
create index if not exists staff_accounts_office_idx on public.staff_accounts (office_id);
alter table public.staff_accounts enable row level security;
