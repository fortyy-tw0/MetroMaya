// Shared helpers. Everything user-visible is built with textContent, never innerHTML,
// so names or messages from the server can't inject markup.

async function request(path, options) {
  let res;
  try {
    res = await fetch(path, options);
  } catch (e) {
    throw new Error('Could not reach the server. Check your connection and try again.');
  }
  let data = null;
  try { data = await res.json(); } catch (e) { /* non-JSON body */ }
  if (!res.ok) {
    throw new Error((data && data.error) || 'Request failed (' + res.status + ').');
  }
  return data;
}

const api = {
  get: (path) => request(path),
  post: (path, data) => request(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams(data || {}).toString(),
  }),
};

function el(tag, props, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props || {})) {
    if (value === false || value == null) continue;
    if (key === 'class') node.className = value;
    else if (key === 'text') node.textContent = value;
    else if (key.startsWith('on') && typeof value === 'function') node.addEventListener(key.slice(2), value);
    else node.setAttribute(key, value === true ? '' : value);
  }
  for (const child of children.flat()) {
    if (child == null || child === false) continue;
    node.append(child.nodeType ? child : document.createTextNode(String(child)));
  }
  return node;
}

function clear(node) { while (node.firstChild) node.removeChild(node.firstChild); return node; }

function peso(n) {
  return '₱' + Number(n).toLocaleString('en-PH', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function pesoShort(n) {
  return '₱' + Number(n).toLocaleString('en-PH', { maximumFractionDigits: 0 });
}

function minutesText(m) {
  if (m < 60) return m + ' min';
  const h = Math.floor(m / 60), r = m % 60;
  return h + ' hr' + (r ? ' ' + r + ' min' : '');
}

function formatDate(iso) {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d).toLocaleDateString('en-US',
    { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' });
}

function formatTime(hhmm) {
  const [h, m] = hhmm.split(':').map(Number);
  const suffix = h >= 12 ? 'PM' : 'AM';
  return ((h + 11) % 12 + 1) + ':' + String(m).padStart(2, '0') + ' ' + suffix;
}

function addDaysISO(iso, days) {
  const [y, m, d] = iso.split('-').map(Number);
  const date = new Date(y, m - 1, d + days);
  return date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0') + '-' + String(date.getDate()).padStart(2, '0');
}

function isWeekend(iso) {
  const [y, m, d] = iso.split('-').map(Number);
  const day = new Date(y, m - 1, d).getDay();
  return day === 0 || day === 6;
}

// Why a day can't be booked, or null if it can. `config` is /api/config: today, maxDate (3 months ahead)
// and the regular holidays in between, so the page and the server apply the same rules.
function closedReason(iso, config) {
  if (iso < config.today) return 'That date has already passed.';
  if (iso > config.maxDate) return 'Slots open up to ' + config.maxMonthsAhead + ' months ahead, until ' + formatDate(config.maxDate) + '.';
  if (isWeekend(iso)) return 'Offices are closed on weekends. Choose a weekday.';
  const h = (config.holidays || []).find((x) => x.date === iso);
  if (h) return 'Offices are closed on ' + h.name + ', a regular holiday. Choose another day.';
  return null;
}

// "Closed on Nov 30 (Bonifacio Day), Dec 25 (Christmas Day), …" for the date picker's hint.
function holidayHint(config) {
  const list = (config.holidays || []).map((h) => {
    const [y, m, d] = h.date.split('-').map(Number);
    return new Date(y, m - 1, d).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }) + ' (' + h.name + ')';
  });
  return list.length ? 'Closed for regular holidays on ' + list.join(', ') + '.' : '';
}

function showAlert(node, message, kind) {
  node.textContent = message;
  node.className = 'alert ' + (kind === 'ok' ? 'alert-ok' : 'alert-error');
  node.hidden = false;
}

function hideAlert(node) { node.hidden = true; node.textContent = ''; }

// "Sanitary & health inspection" -> "Sanitary"
function shortInspection(label) {
  return label.replace(/ inspection$/, '').split(' & ')[0];
}

// ---- quote rendering, shared by the landing page, booking wizard and manage page ----

// ---- Metro Manila cities and municipalities (shared by the landing page and the booking form) ----
// hall and brgy are from Wikipedia's list of Metro Manila city/municipal halls; rate is a SAMPLE
// multiplier for this course project, not any city's official fees.
const NCR_CITIES = [
  { id: 'caloocan', name: 'Caloocan', hall: 'Caloocan City Hall', brgy: 'Grace Park East', rate: 0.95 },
  { id: 'las-pinas', name: 'Las Piñas', hall: 'Las Piñas City Hall', brgy: 'Pamplona Tres', rate: 1.00 },
  { id: 'makati', name: 'Makati', hall: 'Makati City Hall', brgy: 'Poblacion', rate: 1.30, health: 'Makati Health Department' },
  { id: 'malabon', name: 'Malabon', hall: 'Malabon City Hall', brgy: 'San Agustin', rate: 0.90 },
  { id: 'mandaluyong', name: 'Mandaluyong', hall: 'Mandaluyong City Hall', brgy: 'Plainview', rate: 1.15 },
  { id: 'manila', name: 'Manila', hall: 'Manila City Hall', brgy: 'Ermita', rate: 1.15, bplo: 'Bureau of Permits', health: 'Manila Health Department' },
  { id: 'marikina', name: 'Marikina', hall: 'Marikina City Hall', brgy: 'Santa Elena', rate: 1.00 },
  { id: 'muntinlupa', name: 'Muntinlupa', hall: 'Muntinlupa City Hall', brgy: 'Putatan', rate: 1.10 },
  { id: 'navotas', name: 'Navotas', hall: 'Navotas City Hall', brgy: 'Sipac-Almacen', rate: 0.90 },
  { id: 'paranaque', name: 'Parañaque', hall: 'Parañaque City Hall', brgy: 'San Antonio', rate: 1.10 },
  { id: 'pasay', name: 'Pasay', hall: 'Pasay City Hall', brgy: 'Barangay 70, Zone 9', rate: 1.10 },
  { id: 'pasig', name: 'Pasig', hall: 'Pasig Temporary City Hall', brgy: 'Rosario', rate: 1.20,
    note: 'Pasig is using a temporary city hall while its new one is built.' },
  { id: 'pateros', name: 'Pateros', hall: 'Pateros Municipal Hall', brgy: 'Aguho', rate: 0.85, municipality: true },
  { id: 'quezon-city', name: 'Quezon City', hall: 'Quezon City Hall', brgy: 'Central', rate: 1.15,
    bplo: 'Business Permits and Licensing Department', health: 'Quezon City Health Department' },
  { id: 'san-juan', name: 'San Juan', hall: 'San Juan City Hall', brgy: 'Pinaglabanan', rate: 1.10 },
  { id: 'taguig', name: 'Taguig', hall: 'Taguig City Hall', brgy: 'Tuktukan', rate: 1.25 },
  { id: 'valenzuela', name: 'Valenzuela', hall: 'Valenzuela City Hall', brgy: 'Karuhatan', rate: 0.95 },
];

function cityPlace(c) { return c.municipality ? c.name : (c.name === 'Quezon City' ? c.name : c.name + ' City'); }
function cityById(id) { return NCR_CITIES.find((c) => c.id === id) || null; }

// Finds the Metro Manila city named in a free-text address, e.g. "12 Rizal St, Brgy. Kapitolyo, Pasig".
// Accents and case don't matter ("Las Pinas", "PARANAQUE"), "QC" counts as Quezon City, and
// "Metro Manila" alone doesn't count as Manila. If several cities appear, the last one wins,
// because the city comes after the street and barangay in an address.
const CITY_PATTERNS = NCR_CITIES.map((c) => {
  const base = c.name.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/\s+/g, '[\\s-]*');
  const extra = c.id === 'quezon-city' ? '|q\\.?\\s?c\\.?' : '';
  return { city: c, re: new RegExp('(?:^|[^a-z])(' + base + extra + ')(?:\\s+city)?(?![a-z])', 'g') };
});
function detectCity(text) {
  const t = String(text || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase()
    .replace(/metro[\s-]*manila/g, ' ');
  let best = null;
  CITY_PATTERNS.forEach(({ city, re }) => {
    re.lastIndex = 0;
    let m;
    while ((m = re.exec(t))) { if (!best || m.index >= best.at) best = { city, at: m.index }; }
  });
  return best ? best.city : null;
}

// The API quotes base (sample) fees; this scales every peso amount by a city's rate and
// recomputes the total from the scaled lines, so the lines always add up.
function scaleQuote(raw, rate) {
  const r = (n) => Math.round(n * rate * 100) / 100;
  const lines = raw.fee.lines.map((l) => ({ label: l.label, amount: r(l.amount) }));
  return Object.assign({}, raw, {
    fee: { lines, total: Math.round(lines.reduce((t, l) => t + l.amount, 0) * 100) / 100 },
    inspections: raw.inspections.map((i) => Object.assign({}, i, { fee: r(i.fee) })),
  });
}

function renderFee(container, fee) {
  clear(container);
  const max = Math.max(...fee.lines.map((l) => l.amount), 1);
  fee.lines.forEach((line) => {
    container.append(el('div', { class: 'fee-row' },
      el('span', { text: line.label }),
      el('span', { class: 'amt', text: peso(line.amount) }),
      el('span', { class: 'bar' }, el('i', { style: 'width:' + Math.max(4, Math.round(line.amount / max * 100)) + '%' }))));
  });
  container.append(el('div', { class: 'fee-total' },
    el('span', { text: 'Estimated total' }),
    el('span', { text: peso(fee.total) })));
}

function renderInspections(list, inspections) {
  clear(list);
  if (!inspections.length) {
    list.append(el('li', null, el('span', { text: 'No inspection needed for this service.' })));
    return;
  }
  inspections.forEach((i) => {
    list.append(el('li', null,
      el('span', { text: i.label }),
      el('span', { class: 'meta', text: minutesText(i.minutes) + ' · ' + peso(i.fee) })));
  });
}

function renderDocs(list, requirements) {
  clear(list);
  requirements.forEach((r, i) => {
    const li = el('li', { text: r }); li.style.setProperty('--i', i); list.append(li);
  });
}

// Renders the time-slot buttons for a date at an office into `container`.
// `onPick(time)` is called with "HH:MM" when a slot is chosen.
async function loadSlots(container, alertNode, officeId, date, selectedTime, onPick) {
  clear(container);
  hideAlert(alertNode);
  if (!date) return;
  // only the newest request draws: picking dates quickly must not stack two days' slots
  const ticket = (container.__slotTicket = (container.__slotTicket || 0) + 1);
  try {
    const data = await api.get('/api/slots?officeId=' + encodeURIComponent(officeId) + '&date=' + encodeURIComponent(date));
    if (ticket !== container.__slotTicket) return;
    clear(container);
    data.slots.forEach((slot) => {
      const btn = el('button', {
        type: 'button',
        class: 'slot',
        disabled: !slot.available,
        'aria-pressed': slot.time === selectedTime ? 'true' : 'false',
      },
        el('b', { text: formatTime(slot.time) }),
        el('span', { text: slot.remaining > 0 ? slot.remaining + ' left' : 'Full' }));
      btn.addEventListener('click', () => {
        container.querySelectorAll('.slot').forEach((b) => b.setAttribute('aria-pressed', 'false'));
        btn.setAttribute('aria-pressed', 'true');
        onPick(slot.time);
      });
      container.append(btn);
    });
    if (!data.slots.some((s) => s.available)) {
      showAlert(alertNode, 'No free slots on that day. Try another date.', 'error');
    }
  } catch (e) {
    if (ticket === container.__slotTicket) showAlert(alertNode, e.message, 'error');
  }
}

// Give the sticky header a hairline once the page scrolls under it.
(function () {
  const header = document.querySelector('.site-header');
  if (!header) return;
  const update = () => header.classList.toggle('scrolled', window.scrollY > 8);
  window.addEventListener('scroll', update, { passive: true });
  update();
})();

// ---- motion: sections appear as the visitor scrolls to them ----
(function () {
  // Runs for everyone; with reduced motion the CSS turns the slide into a plain fade.
  if (!('IntersectionObserver' in window)) return;

  // What fades in, and from which side. Lists of siblings are staggered.
  const RULES = [
    ['.section-head, .estimate-intro, .offices-wrap .split-head, .page-title, .stepper', ''],
    ['.feature, .sequence li, .size-card, .rows > .row', 'stagger'],
    ['.estimator, .office-card, .card, .fees-wrap .sizes-title', ''],
    ['.band', 'reveal-zoom'],
    ['.band-art', 'reveal-left'],
    ['.cta', 'reveal-zoom'],
    ['.footer-grid > *', 'stagger'],
  ];
  document.documentElement.classList.add('js-reveal');

  // Scrolling down: things fade in as they come up into view.
  // Scrolling up: the same things fade back out as they drop off the bottom of the screen.
  const io = new IntersectionObserver((entries) => {
    entries.forEach((e) => {
      if (e.isIntersecting) e.target.classList.add('in');
      else if (e.boundingClientRect.top > 0) e.target.classList.remove('in');
    });
  }, { threshold: 0.18, rootMargin: '0px 0px -10% 0px' });

  function scan(root) {
    RULES.forEach(([sel, mode]) => {
      (root || document).querySelectorAll(sel).forEach((node) => {
        if (node.classList.contains('reveal') || node.closest('.hero-band')) return;
        node.classList.add('reveal');
        if (mode && mode !== 'stagger') node.classList.add(mode);
        if (mode === 'stagger') {
          const i = Array.prototype.indexOf.call(node.parentElement.children, node);
          node.style.setProperty('--d', Math.min(i, 6) * 80 + 'ms');
        }
        io.observe(node);
      });
    });
  }
  // On the home page nothing below the slogan shows until the visitor scrolls to it.
  let armed = false;
  const queued = [];
  const watch = io.observe.bind(io);
  io.observe = (node) => { if (armed) watch(node); else queued.push(node); };
  function onFirstScroll() { if (window.scrollY > 4) arm(); }
  function arm() {
    if (armed) return;
    armed = true;
    window.removeEventListener('scroll', onFirstScroll);
    queued.splice(0).forEach(watch);
  }
  scan();
  if (window.scrollY > 4 || location.hash || !document.querySelector('.hero-band')) arm();
  else window.addEventListener('scroll', onFirstScroll, { passive: true });
  // Industry rows, office rows, size cards and tiles arrive from the API after load.
  new MutationObserver(() => scan()).observe(document.body, { childList: true, subtree: true });

})();

// ---- closing banner: Maya jumps while "Book a slot" is hovered or focused ----
(function () {
  const cta = document.querySelector('.cta'); const maya = document.querySelector('.cta-maya');
  if (!cta || !maya) return;
  const btn = cta.querySelector('.btn');
  const on = () => maya.classList.add('jump'), off = () => maya.classList.remove('jump');
  btn.addEventListener('mouseenter', on); btn.addEventListener('mouseleave', off);
  btn.addEventListener('focus', on); btn.addEventListener('blur', off);
  btn.addEventListener('touchstart', on, { passive: true });
})();

// ---- slogan: the highlighter sweeps under "get on business." whenever the hero comes into view ----
(function () {
  const mark = document.querySelector('.hero-band .mark');
  if (!mark) return;
  if (!('IntersectionObserver' in window)) { mark.classList.add('hl'); return; }
  new IntersectionObserver((entries) => entries.forEach((e) => {
    if (e.isIntersecting) mark.classList.add('hl');
    else mark.classList.remove('hl');
  }), { threshold: 0.6 }).observe(mark);
})();

// ---- nav: highlight the link for the section you're reading ----
(function () {
  const links = Array.prototype.slice.call(document.querySelectorAll('.nav-links a'));
  if (!links.length || !document.querySelector('.hero-band')) return;
  // which nav link each part of the home page belongs to
  const owner = { why: '#why', estimate: '#estimate', industries: '#industries', how: '#how', offices: '#offices' };
  const home = links.find((a) => a.getAttribute('href') === '/');
  const sections = Object.keys(owner).map((id) => document.getElementById(id)).filter(Boolean);
  function update() {
    // the middle of the screen below the header: exactly where the jump links land a section
    const header = document.querySelector('.site-header, header');
    const headH = header ? header.offsetHeight : 72;
    const line = headH + (window.innerHeight - headH) / 2;
    let current = null;
    sections.forEach((sec) => { const r = sec.getBoundingClientRect(); if (r.top <= line && r.bottom > line) current = owner[sec.id]; });
    links.forEach((a) => {
      const heroVisible = document.querySelector('.hero-band').getBoundingClientRect().bottom > line;
      const on = current ? a.getAttribute('href') === current : (a === home && heroVisible);
      a.classList.toggle('is-active', on);
      if (on) a.setAttribute('aria-current', current ? 'location' : 'page'); else a.removeAttribute('aria-current');
    });
  }
  window.addEventListener('scroll', update, { passive: true });
  window.addEventListener('resize', update);
  update();

  // Jump links land a section's content (its .wrap, so the padding around it doesn't count) in the
  // middle of the screen below the sticky header. Content taller than that space lines its top up
  // just under the header instead, so the heading is never cut off. Spots are measured from the
  // layout (offsetTop/offsetHeight), so a section still mid scroll-reveal can't throw it off.
  function layoutTop(node) { let y = 0; for (let n = node; n; n = n.offsetParent) y += n.offsetTop; return y; }
  function jumpTop(target) {
    const header = document.querySelector('.site-header, header');
    const headH = header ? header.offsetHeight : 72;
    const room = window.innerHeight - headH;
    const box = target.querySelector(':scope > .wrap') || target;
    const top = layoutTop(box), h = box.offsetHeight;
    const y = h <= room - 32 ? top + h / 2 - headH - room / 2 : top - headH - 16;
    const max = document.documentElement.scrollHeight - window.innerHeight;
    return Math.round(Math.max(0, Math.min(max, y)));
  }
  // Arriving from another page with a #section (e.g. the footer's "Offices" link): the browser's own
  // jump happens before the fees and offices finish loading, so land it again once they have.
  if (location.hash.length > 1) {
    let userScrolled = false;
    const mark = () => { userScrolled = true; };
    ['wheel', 'touchstart', 'keydown'].forEach((t) => window.addEventListener(t, mark, { once: true, passive: true }));
    document.addEventListener('landing:ready', () => {
      const target = document.getElementById(decodeURIComponent(location.hash.slice(1)));
      if (!target) return;
      const land = () => { if (!userScrolled) window.scrollTo({ top: jumpTop(target), behavior: 'auto' }); };
      requestAnimationFrame(land);
      // web fonts can still swap in and shift the page a little; land once more when they're ready
      if (document.fonts && document.fonts.ready) document.fonts.ready.then(() => requestAnimationFrame(land));
    }, { once: true });
  }
  document.addEventListener('click', (e) => {
    const a = e.target.closest && e.target.closest('a[href^="#"], a[href="/"]');
    if (!a || e.defaultPrevented || e.metaKey || e.ctrlKey || e.shiftKey || e.button) return;
    // Home and the logo: already on the home page, so glide back to the top instead of reloading
    if (a.getAttribute('href') === '/') {
      e.preventDefault();
      window.scrollTo({ top: 0, behavior: 'smooth' });
      history.replaceState(null, '', '/');
      return;
    }
    const target = document.getElementById(a.getAttribute('href').slice(1));
    if (!target) return;
    e.preventDefault();
    window.scrollTo({ top: jumpTop(target), behavior: 'smooth' });
    history.replaceState(null, '', a.getAttribute('href'));
  });
})();

// ---- show Maya only once her drawing (and beak layer) have loaded ----
(function () {
  const clip = document.querySelector('.perch-clip');
  const perch = document.getElementById('perch');
  const imgs = ['maya-frames', 'maya-beak'].map((id) => document.getElementById(id)).filter(Boolean);
  if (!clip || !imgs.length) return;
  let left = imgs.length;
  function done() {
    if (--left > 0) return;
    requestAnimationFrame(() => { clip.classList.add('ready'); if (perch) perch.classList.add('maya-ready'); });
  }
  imgs.forEach((img) => {
    if (img.complete && img.naturalWidth) done();
    else img.addEventListener('load', done, { once: true });
  });
})();

// ---- Maya's eyes follow the pointer ----
(function () {
  const pupils = Array.prototype.slice.call(document.querySelectorAll('#maya .mf-look'));
  if (!pupils.length) return;
  let px = null, py = null, raf = 0;
  function look() {
    raf = 0;
    pupils.forEach((g) => {
      if (px === null) { g.style.transform = ''; return; }
      const r = g.getBoundingClientRect();
      const dx = px - (r.left + r.width / 2), dy = py - (r.top + r.height / 2);
      const d = Math.hypot(dx, dy) || 1;
      const max = Number(g.getAttribute('data-max')) || 3;   // in drawing units
      const reach = Math.min(1, d / 160) * max;
      g.style.transform = 'translate(' + (dx / d * reach).toFixed(2) + 'px, ' + (dy / d * reach).toFixed(2) + 'px)';
    });
  }
  function aim(x, y) { px = x; py = y; if (!raf) raf = requestAnimationFrame(look); }
  window.addEventListener('pointermove', (e) => aim(e.clientX, e.clientY), { passive: true });
  window.addEventListener('pointerdown', (e) => aim(e.clientX, e.clientY), { passive: true });
  document.addEventListener('mouseleave', () => aim(null, null));
  window.addEventListener('scroll', () => { if (px !== null) aim(px, py); }, { passive: true });
})();

function debounce(fn, ms) {
  let t;
  return (...args) => { clearTimeout(t); t = setTimeout(() => fn(...args), ms); };
}
