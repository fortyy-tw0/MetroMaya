-- Staff operations: a no-show state, walk-in records and a per-office, per-day arrival number.
alter table public.appointments drop constraint if exists appointments_status_check;
alter table public.appointments add constraint appointments_status_check
    check (status in ('CONFIRMED', 'CANCELLED', 'NO_SHOW'));
alter table public.appointments
    add column if not exists queue_number integer,
    add column if not exists walk_in boolean not null default false,
    add column if not exists demo_record boolean not null default false,
    add column if not exists no_show_at timestamptz,
    add column if not exists counter_payment jsonb;
create unique index if not exists appointments_official_receipt_idx
    on public.appointments (office_id, lower(counter_payment->>'officialReceiptNo'))
    where counter_payment is not null;
create unique index if not exists appointments_office_queue_idx
    on public.appointments (office_id, appt_date, queue_number)
    where queue_number is not null;
drop index if exists public.appointments_one_active_idx;
create unique index appointments_one_active_idx
    on public.appointments (lower(email), lower(business_name), service_id)
    where status = 'CONFIRMED' and walk_in = false;

-- The advisory lock prevents two server instances from assigning the same next number.
create or replace function public.office_accept_booking(p_reference_no text, p_accepted_at timestamptz)
returns setof public.appointments
language plpgsql security invoker set search_path = public
as $$
declare
    booking public.appointments%rowtype;
    next_number integer;
begin
    select * into booking from public.appointments where reference_no = p_reference_no for update;
    if not found then raise exception 'Booking not found'; end if;
    if booking.status <> 'CONFIRMED' then raise exception 'Only confirmed bookings can be accepted'; end if;
    if booking.accepted_at is null then
        perform pg_advisory_xact_lock(hashtextextended(booking.office_id || ':' || booking.appt_date::text, 0));
        select coalesce(max(queue_number), 0) + 1 into next_number
          from public.appointments where office_id = booking.office_id and appt_date = booking.appt_date;
        update public.appointments set accepted_at = p_accepted_at, queue_number = next_number,
            updated_at = now() where reference_no = p_reference_no;
    end if;
    return query select * from public.appointments where reference_no = p_reference_no;
end;
$$;
revoke all on function public.office_accept_booking(text, timestamptz) from public, anon, authenticated;
grant execute on function public.office_accept_booking(text, timestamptz) to service_role;
