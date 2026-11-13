# MetroMaya

> **A modernized, citizen-first digital civic portal and Local Government Unit (LGU) service prototype.**

**MetroMaya** is a modern web application designed to reimagine how citizens interact with Local Government Unit (LGU) services in Metro Manila. Built with speed, accessibility, and clean user experience at its core, MetroMaya eliminates the visual clutter, fragmented sub-portals, and slow load times common in legacy government websites—demonstrating an end-to-end digital service workflow aligned with modern e-governance standards.

**Live Demo:** [https://metro-maya.vercel.app/](https://metro-maya.vercel.app/)

---

## How MetroMaya Differs from Traditional LGU Portals

| Feature Area | Legacy LGU Websites | MetroMaya |
| :--- | :--- | :--- |
| **Architecture & Speed** | Monolithic CMS/legacy backends prone to slow load times during peak traffic. | Edge-cached frontend deployment on **Vercel** for instant loading and responsive navigation. |
| **UI / UX Design** | Cluttered layouts, buried navigation menus, and desktop-first forms. | Clean, consumer-grade, mobile-first interface with clear visual hierarchy. |
| **Service Discovery** | Static tables and downloadable PDF checklists. | Interactive, guided service workflows and quick-access civic directories. |
| **Transaction Flow** | Fragmented redirects to external payment pages without live status sync. | Unified end-to-end dashboard tracking applications from submission to fulfillment. |

---

## Core & Planned Features

### Civic Utility & Information
- **Unified Service Directory:** Instant search and categorization for local government permits, clearances, and community programs.
- **Emergency & Public Hotline Hub:** One-tap access to local emergency response units, police precincts, fire stations, and public health facilities.
- **Announcements & Advisories:** Clean, readable feed for local ordinances, public notices, and community alerts.

### End-to-End Digital Permit Workflow *(Sandbox / Prototype)*
- **Guided Application Wizard:** Step-by-step digital intake for business permits, barangay/city clearances, and local requests.
- **Simulated Online Payment Gateway:** Interactive sandbox checkout supporting familiar local payment channels (QRPh, GCash, Maya, and online bank transfers) with instant status updates.
- **Digital Permits & QR Verification:** Auto-generated Electronic Official Receipts (e-OR) and digital permits embedded with unique verification QR codes.
- **Pickup vs. Door-to-Door Delivery:** Flexible fulfillment options allowing users to book a City Hall appointment slot or opt for courier delivery with live status tracking (*Submitted → Assessed → Paid → Out for Delivery*).

---

## Getting Started

To run a local copy of MetroMaya for development or testing, follow these steps.

### Prerequisites
- **Node.js** (v18.0 or higher recommended)
- **npm**, **yarn**, **pnpm**, or **bun**
- **Git**

### Installation

1. **Clone the repository:**
   ```bash
   git clone [https://github.com/fortyy-tw0/MetroMaya.git](https://github.com/fortyy-tw0/MetroMaya.git)
   cd MetroMaya
