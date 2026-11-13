# MetroMaya: handoff for Claude Code

This is a business-permit appointment system for an OOP course project. It has a Java 21 backend and a static vanilla HTML/CSS/JS front end. The mascot is **Maya**, a Eurasian tree sparrow dressed as a mail carrier.

- **Repo:** `fortyy-tw0/OOP_AppointmentSystem` (GitHub). Work on `main`. This file is `CLAUDE.md` at the repo root, so Claude Code loads it automatically.
- **Live:** https://oopappointmentsys.vercel.app. Vercel auto-deploys from `main` using its Container runtime with the root `Dockerfile`.
- **Brand:** **MetroMaya**. The slogan is **"Skip the line, get on business."** with "get on business" underlined in gold.
- **Disclaimer that must stay visible:** fees are *sample rates for a course project, not official LGU fees*.

---

## 1. Run, test, deploy

```bash
./run.sh                       # http://localhost:8080 (PORT env var changes it)
./run-tests.sh                 # compiles src/main + src/test, runs appointment.SmokeTest (18 checks; all must pass)

# manual build/run (what Dockerfile does)
rm -rf out && mkdir out && javac -d out $(find src/main/java -name '*.java')
cp -r src/main/resources/. out/
PORT=8097 java -cp out appointment.Main
```

- No dependencies: it uses `com.sun.net.httpserver` and no build tool.
- **Deploy:** push to `main`, then Vercel builds `Dockerfile` (`Dockerfile.vercel` is a copy, so keep them identical). The container listens on `PORT` (default 80 in the image). The Vercel project name must not contain underscores.
- **Bookings are in memory.** They reset whenever the container scales down.
- **Commits** in this project used the trailer lines below. Use whatever attribution your environment specifies.
  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  ```
- **Checking the live site:** curl from a sandbox may be blocked. A web-fetch of `/css/style.css` that greps for a new rule worked.
- **Screenshots:** Playwright (Node) with Chromium. Clear `scroll-behavior` before using `scrollIntoView` in scripts, and re-measure element boxes after hover, because the page height changes as the API data loads.

---

## 2. Backend (Java 21)

```
src/main/java/appointment/
  Main.java                         starts WebServer on $PORT
  model/
    Industry.java (abstract)        → RetailIndustry, FoodIndustry, ManufacturingIndustry,
                                      ProfessionalServicesIndustry, HealthcareIndustry
                                      (base fee, inspections, required documents, surcharges)
    BusinessSize.java (enum)        MICRO/SMALL/MEDIUM/LARGE, constant-specific bodies
                                      (fee multiplier, inspection factor, extra inspections/docs)
    PermitService.java (abstract,   → NewPermitService, RenewalService, InspectionVisitService
      final template quote())
    Office, Appointment (quote snapshot), AppointmentStatus, Applicant, Business,
    FeeBreakdown, FeeLine, InspectionType, Quote
  repository/  AppointmentRepository (interface), InMemoryAppointmentRepository
  service/     AppointmentService (booking rules), CatalogService, SlotInfo,
               BookingException, NotFoundException
  web/         WebServer, ApiHandler (routes), StaticHandler (serves /static; has webp MIME), Json
src/test/java/appointment/SmokeTest.java
```

These three hierarchies are the course's polymorphism showcase, so keep them intact:

| Varies by | Design | Where |
|---|---|---|
| Industry | abstract class plus subclasses | `model/Industry.java` and its 5 subclasses |
| Business size | enum with constant-specific methods | `model/BusinessSize.java` |
| Service | abstract class with a final template method `quote()` | `model/PermitService.java` and its 3 subclasses |

**Booking rules:**
- Weekdays only, up to 30 days ahead.
- Hourly slots from 8:00 to 16:00, with no 12:00 slot.
- Each office has its own slot capacity.
- One active booking per email + business + service.
- References look like `BPL-20261116-ABCDE`.

**API:**
```
GET  /api/health | /api/config | /api/services | /api/industries | /api/offices
GET  /api/quote?serviceId=&industryId=&employees=
     → { quote: { size{label,range}, fee{lines[{label,amount}],total},
                  inspections[{label,fee,minutes}], requirements[], estimatedMinutes } }
GET  /api/slots?officeId=&date=YYYY-MM-DD
POST /api/appointments                    (form-encoded)
GET  /api/appointments/{ref}
POST /api/appointments/{ref}/cancel
POST /api/appointments/{ref}/reschedule   (date, time)
```

The user asked for one data change: `RetailIndustry.requiredDocuments()` returns `List.of()`, because the "weights and measures calibration" item was removed.

---

## 3. Front end

```
src/main/resources/static/
  index.html     landing page (hero, What is MetroMaya, How It Works, Fee estimator, Industries, Offices, CTA)
  book.html      5-step booking wizard (Service, Business, Office, Date and time, Review)
  manage.html    track / cancel / reschedule by reference
  css/style.css  one stylesheet (~530 lines); tokens on :root
  js/common.js   shared helpers + all page-wide behaviours (IIFEs)
  js/landing.js  estimator, NCR city picker, industries, size cards, office card
  js/book.js, js/manage.js
  img/           Maya assets (see §5)
tools/maya/      scripts + source PNGs that build the Maya assets (see §5)
```

### Design system (taken from Maya)
- **Colour tokens:**

  | Token | Value | Comes from |
  |---|---|---|
  | `--navy` | `#2C3A72` | cap and satchel |
  | `--navy-deep` | `#1B2248` | cap band |
  | `--navy-soft` | `#E8EBF6` | light navy wash |
  | `--gold` | `#EDB24A` | piping |
  | `--gold-soft` | `#FBF0D6` | |
  | `--cream` | `#FBF7F0` | page ground |
  | `--sand` | `#F2E4C4` | CTA backdrop |
  | `--ink` | `#3E2416` | outline brown, used for body text |
  | `--ink-soft` | `#6B5446` | |
  | `--muted` | `#8C7A6E` | |

- **Fonts** (Google Fonts): Lexend for display text, Figtree for body text, JetBrains Mono for references.
- **Style:** minimalist and easy to navigate.
- **Header:** sticky, 72px tall, using the footer's brown/cream scheme. The logo is text only ("MetroMaya" with a gold dot). The old Maya logo mark (`maya-mark.png`) is no longer used.
- **Nav (five tabs, in page order; keep this capitalization):** Home, What is MetroMaya (`#why`), How It Works (`#how`), Fees (`#estimate`; `#industries` also counts as Fees in the scrollspy), Offices (`#offices`).
  - Below 1200px the tabs tighten. Below 1080px "Track a booking" leaves the header (it's still in the footer). Below 900px the tabs hide.
  - The header's **Book a slot** button uses the same tab-lift hover as the tabs.
  - Hover and active states use a **tab lift**: white background, 2px brown outline, `translateY(-2px)`. The user rejected pill shapes and glow.
  - A scrollspy (`common.js`) maps section ids to links. Home is active only while the hero is visible.
  - Jump links are handled in JS (`jumpTop` in `common.js`). They **centre** the section's content (its direct `.wrap` child, so padding doesn't count) in the screen area below the header. If the content is taller than that area (the Fees estimator, and most sections on phones), its top lands 16px under the header instead. The result is clamped to the page's scroll range. Measuring uses `offsetTop`/`offsetHeight`, so sections still mid-reveal don't throw it off. `main section[id] { scroll-margin-top: 88px }` is the no-JS fallback.
  - **Home** and the logo on the home page smooth-scroll to the top instead of reloading. On other pages they navigate to `/` as normal.
  - The scrollspy line is the centre of that same area, so the highlighted tab always matches where a jump lands.
- `body { overflow-x: clip }` guards against horizontal scroll.

### Page sections (index.html)
1. **Hero (`.hero-band`)**
   - Full-screen white (`min-height: calc(100svh - 72px)`), with the slogan centred.
   - Maya **peeks out** above the fact card (`.perch` > `.perch-clip`, clipped at the card's top edge), showing half her body. She flies in place with a flapping strip, and her drawn SVG eyes follow the pointer.
   - There is **no greeting text** next to her; the "Hi, I'm Maya!" label was removed on request.
   - Maya stays hidden until her images load (`.perch-clip.ready`, `.perch.maya-ready`). The eyes must never appear before the body.
2. **What is MetroMaya (`#why`):** why someone should use MetroMaya.
3. **How It Works (`#how`):** a navy rounded band with a centred head and 4 step cards.
4. **Fee estimator (`#estimate`)**
   - White band containing an intro on the left plus Maya flying with a swinging wing and **winking every 5s**. The card is on the right.
   - **Card contents:**
     - service tabs (estimator-tab hover effect)
     - business type, employees, and an **NCR city select**
     - tiles: size, estimated total, inspections, time
     - fee breakdown with bars
     - inspections
     - **Documents to bring**
   - **Documents to bring:** always visible, with no `<details>` toggle and no bullets. Each item is a soft navy row that slides in (`doc-in`, staggered by `--i`). The animation replays when the list re-renders and when the section is revealed again. The same markup is used in book.html (review and done) and manage.html.
   - Estimator Maya has a **fixed** `margin-top: 3rem`, so she never moves when the card height changes. The user explicitly wanted her not to move.
   - Size cards stand out on hover (lift, outline, shadow), not by glowing.
5. **Industries (`#industries`):** fees scale with the chosen city.
6. **Offices (`#offices`):** the chosen city's hall (`"<hall>, <brgy>, <City>, Metro Manila"`, a Google Maps directions link, the Pasig note) followed by the **four desks** a permit passes through, all at the hall's Business One-Stop Shop (RA 11032):
   - BPLO/BPLD, the City (or Municipal) Treasurer's Office (CTO/MTO), the BFP desk, and the City Health Office / Sanitation Desk.
   - Each desk is a `.row.desk` with an icon, its name and one line on what you do there. There are no abbreviation tags (BPLO/CTO/…); they were removed on request. The data is in `desks(c)` in `landing.js`.
   - Cities can override the default names with `bplo`, `treasurer` and `health` on their `CITIES` entry. Manila (Bureau of Permits, Manila Health Department), Quezon City (Business Permits and Licensing Department, Quezon City Health Department) and Makati (Makati Health Department) do this now.
   - No floor or room numbers are given, because they weren't verified.
   - The "Change your city" link points to `#estimate`.
7. **CTA ("Ready to book your slot?"):** a sand card with Maya standing (`maya-ta-da.png`).
   - On hover or focus of **Book a slot** (or touch, via the `.cta-maya.jump` class), she lifts 42px, crossfades into a **flying** strip (`maya-hehe-fly.webp`, eyes closed). There is **no speech bubble**; the "Let's Go!" bubble was removed on request.
   - The fly layer is scaled by `1.12` so her flying size matches her standing size; the user complained she shrank.

### Behaviours in common.js
- Header scrolled state.
- **Scroll reveal:** an IntersectionObserver adds `.in` when an element enters the viewport and **removes it when the element drops below**, so sections disappear again when scrolling up.
  - The `RULES` list picks the elements.
  - On the home page nothing reveals until the first scroll.
  - Under reduced motion, elements fade only, with no transforms. Reveal is never fully disabled; that broke it for the user before.
- Image load-gate for the hero Maya.
- Hero eye tracking (the `.mf-look` pupils).
- CTA jump class.
- Scrollspy and jump links.
- **Helpers:** `el()`, `clear()`, `peso()`, `pesoShort()`, `renderFee`, `renderInspections`, `renderDocs(list, requirements)` (sets `--i` on each item), `loadSlots`, `debounce`, `api.get`.

### NCR cities (landing.js `CITIES`)
- The list has the 16 cities plus the municipality of Pateros, each with `hall`, `brgy` (from Wikipedia's list of Metro Manila city/municipal halls) and a **sample** `rate` multiplier.
- Pasig has a note about its temporary city hall.
- The default city is Manila. The choice is saved in `localStorage['metromaya.city']`, wrapped in try/catch.
- Fees are scaled client-side with `scale(n) = round(n * rate, 2)`, and the total is recomputed from the scaled lines.

---

## 4. Maya (mascot spec)

- **Body:** a Eurasian tree sparrow with a chestnut crown, white cheeks with a black cheek spot, a black bib, a cream chest, a brown outline, a feather tuft **centred on top of the head**, and big brown eyes.
- **Mail-carrier outfit:**
  - a navy peaked cap with a gold envelope badge, resting on her head and pointing where her face points
  - a navy satchel holding papers
  - a strap running from her **right shoulder** across her body to the bag, wrapped around the shoulder (not over her face and not cut off)
- **Hat piping rule:** the gold piping lines sit **flush against the brown outlines**, edge to edge:
  - directly under the front edge of the top rim
  - just below the band's top brown line
  - just above the band's bottom brown line

  This applies to every pose.
- **Poses** (`static/img`): `maya-hello`, `maya-ta-da` (wings up, eyes open), `maya-whoa`, `maya-hey-you` (pointing; used in the estimator), `maya-hehehe` (eyes closed, laughing; used for CTA flying), `maya-lets-go`. There is **no favicon** (removed on request); each page has `<link rel="icon" href="data:,">` so browsers don't request `/favicon.ico`.
- **Animation rules the user cares about:**
  - The animation must be seamless. Don't animate cut-out parts on top of the body. The flap is rendered by bending the whole drawing.
  - Her size must not change between states (wink, takeoff).
  - Wing tips must never be cropped.

---

## 5. Image pipeline (`tools/maya/`, committed)

- **Requirements:** Python 3 with numpy, scipy and Pillow (WebP support).
- **Paths:** every script uses repo-relative paths. Running the two shell scripts reproduces the committed assets byte for byte.

```bash
tools/maya/build_strips.sh       # rebuild all 4 flap strips from the pose PNGs (after any pose edit)
tools/maya/apply_hat_piping.sh   # redo cap piping on every pose from src/before-piping/, then build_strips.sh
```

| Asset in `static/img` | Source | Script |
|---|---|---|
| `maya-fly-frames.webp` (hero, 9760×236) | `maya-ta-da.png` | `flap_hero.py` |
| `maya-hey-fly.webp` (estimator, 8048×485) | `maya-hey-you.png` | `flap_estimator.py SRC DST` |
| `maya-hey-fly-wink.webp` (estimator wink) | `tools/maya/src/maya-hey-you-wink.png` | `flap_estimator.py SRC DST` |
| `maya-hehe-fly.webp` (CTA, 7424×476) | `maya-hehehe.png` | `flap_cta.py` (pivots (136,170)/(252,165), ±14°) |
| `maya-fly-beak.png` | beak layer drawn over the far eye in the hero | — |

- **Flap strips:** 16 frames side by side, saved as lossless WebP.
  - Each frame bends the whole drawing. The body stays still while each wing rotates about a shoulder pivot, with a smoothstep weight that grows with distance from the body.
  - Frames are padded so wing tips never touch an edge.
- **CSS playback:** an `overflow:hidden` box offset by the padding percentages holds an `img` at `width:1600%`, animated with `@keyframes flap-strip { to { transform: translateX(-100%) } }` using `steps(16)`.
- **Wink:** a second strip built through the *identical* pipeline from the closed-eye source, crossfaded for about 0.25s every 5s. Using the same pipeline is what stops her appearing to grow during the wink.
- **Hat piping (`hat_piping.py`):**
  1. Finds gold piping components in the cap zone (the top 36% of the image), skipping the badge.
  2. Merges the top-rim pieces and forces them to hug the rim's front edge from below.
  3. Clears the old piping and its halo to the nearby navy.
  4. Fits a quadratic curve to the sub-pixel brown edge.
  5. Paints an anti-aliased gold band flush against the brown, overlapping the edge by 0.9px.
  6. Extends sideways at most 40px, and only while the pixels are still on the cap.

  **It is not idempotent.** Only run it on `tools/maya/src/before-piping/`, never on its own output.
- **Rim thickening (`thicken_rim.py`):** already applied to ta-da, hehehe and hey-you. The `before-piping` sources already include it.
- **If you redraw a pose by hand:** put the new version in `src/before-piping/`, then run `apply_hat_piping.sh`.

---

## 6. User preferences (standing)

- Minimalist layout. Maya is the main visual element.
- Animations are seamless, with no visible seams or cut-outs.
- Sections appear when you scroll down to them and disappear when you scroll back up.
- No glow effects. Use the lift, outline and shadow treatment instead.
- No pill-shaped nav highlight.
- Maya never jumps position or changes size.
- The sample-fee disclaimer stays visible.
- The user communicates by sending annotated screenshots. Match their drawn alignment exactly, then verify with your own screenshot.

---

## 7. Open questions / next steps

1. **Fees jump link:** resolved. Jumps now centre each section (see §3, Nav).
2. **Persistence:** bookings live in memory and reset on scale-down. A database-backed `AppointmentRepository` is the intended way to fix this.
3. **Leftover asset:** `maya-mark.png` isn't used anywhere. Remove it or keep it for later.
4. **Booking page:** Maya next to the title is now 200px (128px on phones). Confirm with the user that she matches the "Book a slot" heading.
5. **Mobile pass:**
   - Check the docs list inside the narrow inspections box.
6. **Waiting on the user's verdict** for the latest round:
   - the piping alignment on every pose
   - the CTA flying size
   - the always-open documents list
7. **Estimator wink source:** the closed-eye pose was made by painting a lid over an eye-removed base in the session scratchpad. That intermediate is not in the repo. To change the wink, edit `tools/maya/src/before-piping/maya-hey-you-wink.png` directly.
