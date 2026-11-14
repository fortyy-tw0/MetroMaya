# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

- **Business owners and their representatives in Metro Manila** (16 cities plus the municipality of Pateros) who need a new business permit, an annual renewal, or an inspection visit. They use a phone or laptop before going to city hall, often in Filipino-English, and want to know cost, inspections, documents and a confirmed time slot so one visit is enough.
- **Evaluators at government agencies** (DICT and LGU business permits and licensing offices) who will see MetroMaya in a pitch and judge whether it could be adopted as a public service.

## Product Purpose

MetroMaya is a business permit appointment service for Metro Manila. Applicants see their permit fees, required inspections and documents before going, verify who they are, and book a time slot at their city hall's business permits office. Success: an applicant finishes in one prepared visit; an agency sees a credible, adoptable public service.

## Positioning

Fees, inspections, documents, identity verification and a confirmed office slot in one flow, per city, before the trip — instead of discovering requirements at the counter.

## Operating Context

- Applicant pages: home (fees, fee estimator, offices), `/book.html` (Verify → Service → Business → Office → Date and time → Review), `/manage.html` (track, move or cancel with the reference number and a private access code). The office check-in QR contains a separate opaque lookup code and requires staff authentication.
- Staff workspace: role-scoped staff, district head, treasurer and IT admin accounts; daily arrivals and queue, monthly traffic charts, office calendars and checklists, verified city hall receipt records, and guarded fully booked demo data.
- Verification before booking: eGovPH SSO, or a scanned government ID (server OCR + LLM field sorting); the verified name is locked; max 3 active bookings per person.
- Office step: map with the user's location (or a typed starting point) and the fastest route to the city hall; Google Maps link for live traffic.
- Being pitched to DICT and government agencies for adoption.

## Capabilities and Constraints

- Java 21 backend with the JDK HTTP server, static HTML/CSS/vanilla JS front end, no build tool, no frameworks; Supabase storage; Vercel container hosting.
- Fees shown are **sample rates for a course project, not official LGU fees**; this disclaimer must stay visible.
- City hall addresses, barangays (PSA PSGC) and ZIP codes (PHLPost) are real.
- **Not an official government website.** It must not use the GOVPH mark, the Republic of the Philippines seal, DICT or LGU logos, or anything implying it is official or endorsed. It may follow Philippine government website conventions and must carry a visible "prototype / not an official government website" notice.

## Brand Commitments

- Name **MetroMaya**; slogan **"Skip the line, get on business."**
- **Maya**, a Eurasian tree sparrow dressed as a mail carrier, is the guide. Confirmed Nov 2026: she stays, as a guide beside forms and help, not the hero centrepiece. Her art rules (seamless animation, never changes size or jumps position, wing tips never cropped) still apply.
  - Named exception (user-requested earlier, recorded in CLAUDE.md): on the closing banner, hovering or focusing **Book a slot** lifts Maya 42px into a flying strip scaled 1.12 so her flying size matches her standing size.
- Existing user preferences that still bind: no glow effects; no pill-shaped nav highlight; sections reveal on scroll and hide again when scrolled back up (reduced-motion: fade only).

## Evidence on Hand

- Real: 17 city hall addresses and coordinates, barangay and ZIP data, the booking rules, Maya's art (`src/main/resources/static/img`, `tools/maya`).
- Absent and not to be fabricated: agency endorsements, adoption numbers, user counts, testimonials, official fees, service-level claims.

## Product Principles

1. One prepared visit: everything the applicant needs is visible before they book.
2. Trust is earned plainly: clear identity checks, honest disclaimers, no claims the product can't back.
3. Public service first: works on a cheap phone, in plain language, for every applicant.
4. Adoptable: an agency should be able to picture running it tomorrow.

## Accessibility & Inclusion

Public-service audience: aim for WCAG 2.1 AA (contrast, keyboard use, focus visibility, reduced motion, readable type on small phones).
