-- MetroMaya: the DEMO fee payment of a booking (no money moves), stored as one JSON object:
-- { txn, channel, amount, status: PENDING|PAID, createdAt, receiptNo?, paidAt? }
-- Null until the applicant starts a payment.

alter table public.appointments add column if not exists payment jsonb;
