---
name: MetroMaya
description: Business permit appointments for Metro Manila, built as an LGU e-service counter put online.
colors:
  gov-950: "#071A3A"
  gov-900: "#0A2552"
  gov-800: "#0B2E6B"
  blue: "#0B4FB3"
  blue-700: "#093F8F"
  blue-100: "#D6E3F7"
  blue-50: "#EDF3FC"
  gold: "#F4C430"
  gold-50: "#FFF6D6"
  red: "#B42318"
  red-50: "#FDECEA"
  green: "#12663E"
  green-50: "#E5F4EC"
  ink: "#14213A"
  ink-2: "#3B4A63"
  muted: "#56647A"
  line: "#DCE3EC"
  line-strong: "#B4C0D0"
  bg: "#F3F6FA"
  white: "#FFFFFF"
  on-dark: "#DCE6F7"
  on-dark-muted: "#C5D2E8"
typography:
  display:
    fontFamily: "Public Sans, system-ui, -apple-system, Segoe UI, Roboto, Helvetica, Arial, sans-serif"
    fontSize: "clamp(2.05rem, 4.2vw, 3.1rem)"
    fontWeight: 800
    lineHeight: 1.2
    letterSpacing: "-0.02em"
  headline:
    fontFamily: "Public Sans, system-ui, sans-serif"
    fontSize: "clamp(1.55rem, 2.6vw, 2.05rem)"
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: "-0.012em"
  title:
    fontFamily: "Public Sans, system-ui, sans-serif"
    fontSize: "1.25rem"
    fontWeight: 700
    lineHeight: 1.2
  body:
    fontFamily: "Public Sans, system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.6
  label:
    fontFamily: "Public Sans, system-ui, sans-serif"
    fontSize: "0.82rem"
    fontWeight: 800
    letterSpacing: "0.08em"
  figure:
    fontFamily: "Public Sans, system-ui, sans-serif"
    fontSize: "1.3rem"
    fontWeight: 800
    fontFeature: "\"tnum\" 1"
  reference:
    fontFamily: "JetBrains Mono, ui-monospace, SFMono-Regular, Menlo, monospace"
    fontSize: "clamp(1.25rem, 4.4vw, 1.95rem)"
    fontWeight: 600
    lineHeight: 1.25
    letterSpacing: "0.04em"
rounded:
  sm: "4px"
  md: "6px"
  lg: "10px"
spacing:
  gutter: "20px"
  xs: "0.35rem"
  sm: "0.75rem"
  md: "1.25rem"
  lg: "2.5rem"
  section: "clamp(3.5rem, 8vw, 5.5rem)"
components:
  button-primary:
    backgroundColor: "{colors.blue}"
    textColor: "{colors.white}"
    rounded: "{rounded.md}"
    padding: "0.85rem 1.35rem"
  button-primary-hover:
    backgroundColor: "{colors.blue-700}"
    textColor: "{colors.white}"
  button-outline:
    backgroundColor: "{colors.white}"
    textColor: "{colors.blue}"
    rounded: "{rounded.md}"
    padding: "0.85rem 1.35rem"
  button-outline-hover:
    backgroundColor: "{colors.blue-50}"
    textColor: "{colors.blue-700}"
  button-danger:
    backgroundColor: "{colors.white}"
    textColor: "{colors.red}"
    rounded: "{rounded.md}"
  input:
    backgroundColor: "{colors.white}"
    textColor: "{colors.ink}"
    rounded: "{rounded.md}"
    padding: "0.72rem 0.9rem"
  nav-bar:
    backgroundColor: "{colors.gov-900}"
    textColor: "{colors.on-dark}"
    padding: "0.78rem 1rem"
  panel-head:
    backgroundColor: "{colors.gov-800}"
    textColor: "{colors.white}"
    typography: "{typography.label}"
    padding: "0.8rem 1.25rem"
  sample-tag:
    backgroundColor: "{colors.gold}"
    textColor: "{colors.gov-950}"
    rounded: "{rounded.sm}"
    padding: "0.2rem 0.55rem"
  chip:
    backgroundColor: "{colors.blue-50}"
    textColor: "{colors.gov-800}"
    rounded: "{rounded.sm}"
    padding: "0.22rem 0.6rem"
  choice-selected:
    backgroundColor: "{colors.blue-50}"
    rounded: "{rounded.lg}"
    padding: "1rem 1.15rem"
  slot-selected:
    backgroundColor: "{colors.blue}"
    textColor: "{colors.white}"
    rounded: "{rounded.md}"
  footer:
    backgroundColor: "{colors.gov-950}"
    textColor: "{colors.on-dark-muted}"
---

# Design System: MetroMaya

## Overview

**Creative North Star: "The Counter, Put Online"**

MetroMaya is a Philippine LGU business-permits counter moved onto the web. It follows GOV.PH and eGovPH public-service conventions top to bottom: a dark notice strip carrying the prototype disclaimer and the accessibility tools (text size, high contrast, pause animations), a white masthead with an emblem, a deep blue navigation bar under a gold rule, sturdy bordered forms, and an institutional dark footer. It is clearly unofficial: the emblem is Maya's own head in a ringed badge, never a seal, the GOVPH mark or an agency logo.

The ground is white panels on cool government gray. Information is laid out the way a city hall prints it: fee schedules with striped rows, a computation sheet for the estimator, an office directory, numbered stations for the process, a permit stub for the confirmation. Density is moderate and plain; every screen is meant to work on a cheap phone in plain language. Maya, the sparrow mail carrier, is the guide beside forms and help. She is never the centrepiece.

The page refuses the startup-landing default: no giant slogan hero, no floating rounded card grids, no mascot hero.

**Key Characteristics:**
- Notice strip, emblem masthead, blue nav bar with 3px gold top rule, dark footer with 4px gold top rule.
- Schedules, sheets and directories (bordered panels with a gov-blue header bar) instead of card grids.
- 2px-bordered inputs, choices and slots; 4/6/10px corners.
- Gold is a rule, a focus ring and the sample-rate tag. It is never a fill for actions.
- Guilloche security print on the hero's right edge and on the booking reference stub.
- Three user-controlled modes on `<html>`: `data-fs`, `data-contrast="high"`, `data-motion="paused"`.

## Colors

A civic palette: three depths of government blue, one action blue, gold kept to rules, and status red and green used only for state.

### Primary
- **Action Blue** (`blue`): primary buttons, links, selected tabs, selected slots, step progress, icon tints. Deepens to **Pressed Blue** (`blue-700`) on hover and press.
- **Masthead Blue** (`gov-800`): headings, panel header bars (schedule, sheet, services), the CTA banner, figures and totals.
- **Nav Blue** (`gov-900`): the navigation bar only.
- **Night Blue** (`gov-950`): notice strip, footer, the dark half of the focus ring, and text on gold.

### Secondary
- **Seal Gold** (`gold`): the nav and footer top rules, the 44 by 4px rule above section headings, the hero slogan's wavy underline, focus outlines, the "Sample rates" tag, pressed accessibility toggles, text selection and the skip link. **Gold Wash** (`gold-50`) backs disclaimers and notes.

### Tertiary
- **Error Red** (`red`, with `red-50`): required fields, errors, cancelled status, the danger button.
- **Confirmed Green** (`green`, with `green-50`): completed steps, confirmed status, success alerts.

### Neutral
- **Ink** (`ink`): body text. **Ink 2** (`ink-2`): leads and secondary copy. **Muted** (`muted`, 5.9:1 on white): hints, sublabels.
- **Rule** (`line`) and **Strong Rule** (`line-strong`): dividers, panel borders, resting input borders.
- **Counter Gray** (`bg`): the page ground between white panels, readonly fields, summary labels. **White**: panels.
- **On Dark** (`on-dark`, `on-dark-muted`): text on the notice strip, nav and footer.
- **Blue Wash** (`blue-50`, `blue-100`): selected rows and choices, icon chips, info panels, inactive progress track.

### Named Rules
**The Gold Is A Rule Rule.** Gold draws lines, rings and tags. No button, panel or banner is filled gold. (`.btn-gold` exists in the stylesheet but no page uses it; it is not part of the system.)

**The Status Colors Rule.** Red and green appear only for state: errors, required marks, confirmed, cancelled, done. They are never decorative.

## Typography

**Display Font:** Public Sans (with system-ui, Segoe UI, Roboto, Helvetica, Arial)
**Body Font:** Public Sans
**Label/Mono Font:** JetBrains Mono, for booking reference numbers only

**Character:** One sturdy public-sector sans in heavy weights for headings and figures, plain weights for reading. The mono face appears only where a code must be read back character by character.

### Hierarchy
- **Display** (800, clamp 2.05 to 3.1rem, 1.2): the page H1, max about 16ch in the hero, preceded by the gold rule.
- **Headline** (700, clamp 1.55 to 2.05rem, 1.2): section H2s, gold rule above, paired with a lead paragraph in a two-column section head.
- **Title** (700, 1.25rem; 1.02 to 1.12rem inside rows, choices and services): H3s, row names, choice titles.
- **Body** (400, 1rem, 1.6): copy, max 52 to 54ch for leads and 72ch for prose.
- **Label** (800, 0.78 to 0.84rem, 0.06 to 0.1em, uppercase): panel header bars, tile labels, box headings, footer column heads, ticket and status labels. These name the panel they sit on; they are table headings, not kickers above a heading.
- **Figure** (800, tabular): prices (1.3rem), estimate tiles (1.35rem), size multipliers (1.6rem), totals.
- **Reference** (JetBrains Mono 600, clamp 1.25 to 1.95rem, 0.04em): the booking reference on the permit stub and the manage page result.

### Named Rules
**The Tabular Figures Rule.** Every number that is compared or totalled (fees, tiles, slot times, inspection meta) sets `font-variant-numeric: tabular-nums`. Body copy keeps proportional figures.

**The Mono For Codes Rule.** JetBrains Mono is used only for booking reference numbers such as `BPL-20261116-ABCDE`.

## Layout

One centered container, `min(1180px, 100% - 2.5rem)`, giving a 20px side gutter on phones; booking and tracking pages narrow to 1000px. Sections pad `clamp(3.5rem, 8vw, 5.5rem)` vertically and alternate between the gray ground and full-bleed white bands bordered top and bottom by `line`.

The masthead and nav are one sticky header; on scroll the masthead tightens, the emblem shrinks from 52 to 40px and the sub-line collapses. Section heads are a two-column grid (heading left, lead right) over a strong rule, stacking at 860px. Content uses asymmetric splits: hero 1.08/0.92, fees 7/5, estimator 4/8, offices 5/7, booking form 1.25/1 with a sticky live summary. All splits collapse to one column between 860 and 960px; the nav wraps at 760px; the accessibility label hides at 640px.

Booking is a six-station stepper (Verify, Service, Business, Office, Date and time, Review) in a bordered strip, with prev/next in a ruled nav row.

## Elevation & Depth

Mostly flat. Depth comes from borders and the white-on-gray ground; shadows are soft, blue-tinted and few. `shadow` lifts the hero services panel; `shadow-sm` lifts the estimator, office directory and booking card. The sticky header gains a soft drop only after scrolling. Maya's art carries its own drop shadows. High contrast mode removes every shadow.

### Named Rules
**The Border First Rule.** A panel is defined by its 1px `line-strong` border and its gov-blue header bar. A shadow is optional and never stands in for a border.

## Shapes

Three corner steps: 4px for chips, tags, badges and the focus ring; 6px for buttons, inputs, slots, alerts and tabs; 10px for panels, choices, the stepper, the map and the CTA banner. Circles are kept for step numbers, the emblem, document counters and checkmarks. Interactive fields carry 2px borders; containers carry 1px. Disabled and readonly states switch the border to dashed. The confirmation ticket has a perforated stub with punched half-circles.

## Components

### Buttons
Solid, square-shouldered, heavy-weight labels.
- **Shape:** gently squared (6px), 2px border.
- **Primary:** action blue fill, white text, 700 weight. `small` and `lg` sizes. On the dark CTA banner it inverts to white with masthead-blue text.
- **Outline:** white with blue border and text; hover fills blue wash. **Danger:** white with red border and text.
- **Hover / Focus / Press:** hover colors apply only under `@media (hover: hover)`. Focus is the global ring: 3px gold outline, 2px offset, 5px night-blue halo. Press scales to .96, only under `prefers-reduced-motion: no-preference`.

### Chips and tags
- **Chip:** blue wash, masthead-blue text, `blue-100` border, 4px corners. A quiet gold variant lists optional items; a dashed blue variant marks added items.
- **Sample tag:** gold, night-blue text, uppercase label. It rides every fee schedule and estimate header.
- **Status badge:** uppercase label with a 1.5px currentColor border: green for CONFIRMED, red for CANCELLED.

### Cards / Containers
- **Schedule, sheet, services panel, office directory:** white, 1px strong border, 10px corners, a gov-800 header bar in label type, rows divided by `line`; schedules stripe alternate rows.
- **Booking card:** white, strong border, `shadow-sm`, padding `clamp(1.25rem, 3.5vw, 2.25rem)`.
- **Box:** inner 1px `line` panel, 6px, uppercase muted heading.

### Inputs / Fields
- **Style:** white, 2px `line-strong` border, 6px corners, bold label above, muted help below. Selects draw one masthead-blue chevron.
- **Focus:** border goes night blue with a 3px gold outline at 0 offset.
- **Readonly:** gray ground, dashed border (the verified name is locked this way). **Disabled:** gray ground, muted text.
- **Choices and slots:** 2px bordered rows or buttons; selected turns blue border with blue wash (choices) or solid blue (slots); unavailable slots go dashed.

### Navigation
Nav-blue bar under a 3px gold rule. Links are 600 weight in on-dark, padded `0.78rem 1rem`. The current page or section gets a faint white wash and a full-width 3px gold underline; hover grows the underline to 35%. No pill highlight. It scrolls horizontally, then wraps under 760px.

### Notice strip
Night-blue strip above the masthead: info icon, "Prototype e-service" disclaimer, then the text-size A/A/A buttons, High contrast and Pause animations. Pressed toggles fill gold. Choices persist in `localStorage` (`metromaya.a11y`) and apply to `<html>` before paint.

### Permit stub (signature)
The booking confirmation: 2px masthead-blue border, guilloche print on blue wash, the reference in mono, a green CONFIRMED stamp, and a perforated vertical stub.

### Maya
The guide. She peeks over the hero services panel, clipped at its top edge, flies beside the estimator, sits on the CTA banner and stands by page titles. Her flap is a 16-frame sprite strip, seamless; she never changes size or jumps position, and her wing tips are never cropped. One sanctioned exception: on the CTA banner she lifts 42px into flight while "Book a slot" is hovered or focused. Under reduced motion she keeps a slow flap.

### Alerts and consent
Advisory panels: tinted ground, matching border, a masked icon at left. The verify step opens with a consent box (RA 10173): verification options stay inert and dimmed until the privacy-notice consent is ticked; once ticked the box turns blue wash.

## Do's and Don'ts

### Do:
- **Do** put the gold 44 by 4px rule above every H1 and section H2, and the 3px gold rule on the nav bar.
- **Do** lay out fees, offices and steps as schedules, sheets, directories and numbered stations with header bars and ruled rows.
- **Do** keep the "Sample rates" tag on every fee header and the sample-fee disclaimer visible in the footer and booking pages.
- **Do** keep the "Prototype e-service, not an official government website" notice in the strip on every page.
- **Do** place every `:hover` rule inside `@media (hover: hover)`.
- **Do** gate motion on `prefers-reduced-motion: no-preference` (press scale .96, hero rise, panel-in), with fade only under reduce, and honor `html[data-motion="paused"]` by pausing every animation.
- **Do** reveal sections on scroll and hide them again on scroll back up.
- **Do** check every new color and state in `html[data-contrast="high"]` and at `html[data-fs="1"]` and `"2"`.
- **Do** require consent before identity verification starts.

### Don't:
- **Don't** use the Republic of the Philippines seal, the GOVPH mark, or DICT or LGU logos, or anything implying endorsement.
- **Don't** fill a button or panel with gold, or use red and green for decoration.
- **Don't** use JetBrains Mono for anything but booking references.
- **Don't** make Maya a hero centrepiece, scale her, move her, or crop her wings (beyond the CTA lift).
- **Don't** add glow effects or a pill-shaped nav highlight.
- **Don't** add kicker or eyebrow lines above headings.
