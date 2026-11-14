-- Apply before deploying the booking credential update. Existing rows stay locked until
-- office staff issue a private management code after checking the applicant's identity.
alter table public.appointments
    add column if not exists management_token_hash text,
    add column if not exists checkin_code text;

create unique index if not exists appointments_checkin_code_idx
    on public.appointments (checkin_code) where checkin_code is not null;
