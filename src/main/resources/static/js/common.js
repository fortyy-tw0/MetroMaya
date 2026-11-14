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
  managedGet: (path, token) => request(path, {
    headers: { 'X-Management-Token': token },
    cache: 'no-store',
  }),
  managedPost: (path, data, token) => request(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-Management-Token': token },
    body: new URLSearchParams(data || {}).toString(),
    cache: 'no-store',
  }),
};

function privateManagementUrl(referenceNo, token) {
  return '/manage.html?ref=' + encodeURIComponent(referenceNo) + '#access=' + encodeURIComponent(token);
}

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
// `at` is [lat, lng] of the city (or municipal) hall, from OpenStreetMap (Nominatim), checked Nov 2026.
const NCR_CITIES = [
  { id: 'caloocan', at: [14.648854, 120.990608], name: 'Caloocan', hall: 'Caloocan City Hall', brgy: 'Grace Park East', rate: 0.95 },
  { id: 'las-pinas', at: [14.449699, 120.982604], name: 'Las Piñas', hall: 'Las Piñas City Hall', brgy: 'Pamplona Tres', rate: 1.00 },
  { id: 'makati', at: [14.570486, 121.027218], name: 'Makati', hall: 'Makati City Hall', brgy: 'Poblacion', rate: 1.30, health: 'Makati Health Department' },
  { id: 'malabon', at: [14.657705, 120.950940], name: 'Malabon', hall: 'Malabon City Hall', brgy: 'San Agustin', rate: 0.90 },
  { id: 'mandaluyong', at: [14.577704, 121.033656], name: 'Mandaluyong', hall: 'Mandaluyong City Hall', brgy: 'Plainview', rate: 1.15 },
  { id: 'manila', at: [14.589296, 120.981641], name: 'Manila', hall: 'Manila City Hall', brgy: 'Ermita', rate: 1.15, bplo: 'Bureau of Permits', health: 'Manila Health Department' },
  { id: 'marikina', at: [14.633031, 121.098945], name: 'Marikina', hall: 'Marikina City Hall', brgy: 'Santa Elena', rate: 1.00 },
  { id: 'muntinlupa', at: [14.395108, 121.044190], name: 'Muntinlupa', hall: 'Muntinlupa City Hall', brgy: 'Putatan', rate: 1.10 },
  { id: 'navotas', at: [14.658193, 120.947806], name: 'Navotas', hall: 'Navotas City Hall', brgy: 'Sipac-Almacen', rate: 0.90 },
  { id: 'paranaque', at: [14.470542, 121.022305], name: 'Parañaque', hall: 'Parañaque City Hall', brgy: 'San Antonio', rate: 1.10 },
  { id: 'pasay', at: [14.544306, 120.994292], name: 'Pasay', hall: 'Pasay City Hall', brgy: 'Barangay 70, Zone 9', rate: 1.10 },
  { id: 'pasig', at: [14.592384, 121.086173], name: 'Pasig', hall: 'Pasig Temporary City Hall', brgy: 'Rosario', rate: 1.20,
    note: 'Pasig is using a temporary city hall while its new one is built.' },
  { id: 'pateros', at: [14.542123, 121.064588], name: 'Pateros', hall: 'Pateros Municipal Hall', brgy: 'Aguho', rate: 0.85, municipality: true },
  { id: 'quezon-city', at: [14.646377, 121.050007], name: 'Quezon City', hall: 'Quezon City Hall', brgy: 'Central', rate: 1.15,
    bplo: 'Business Permits and Licensing Department', health: 'Quezon City Health Department' },
  { id: 'san-juan', at: [14.605069, 121.029676], name: 'San Juan', hall: 'San Juan City Hall', brgy: 'Pinaglabanan', rate: 1.10 },
  { id: 'taguig', at: [14.528940, 121.069927], name: 'Taguig', hall: 'Taguig City Hall', brgy: 'Tuktukan', rate: 1.25 },
  { id: 'valenzuela', at: [14.692744, 120.968146], name: 'Valenzuela', hall: 'Valenzuela City Hall', brgy: 'Karuhatan', rate: 0.95 },
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

// Compress the sticky header once the page scrolls under it, and keep --header-h current
// (the booking summary panel and jump links sit just below it).
(function () {
  const header = document.querySelector('.site-header');
  if (!header) return;
  const root = document.documentElement;
  // on phones the header scrolls away with the page, so nothing sits over the content
  const measure = () => root.style.setProperty('--header-h', (getComputedStyle(header).position === 'sticky' ? header.offsetHeight : 0) + 'px');
  const update = () => { header.classList.toggle('scrolled', window.scrollY > 40); };
  window.addEventListener('scroll', update, { passive: true });
  window.addEventListener('resize', measure);
  header.addEventListener('transitionend', measure);
  update();
  measure();
})();

// Notice strip: text size (A / A / A), high contrast and pausing animations, remembered on this device.
(function () {
  const root = document.documentElement;
  const sizeBtns = Array.prototype.slice.call(document.querySelectorAll('.a11y-btn[data-fs]'));
  const contrastBtn = document.querySelector('.a11y-btn[data-contrast]');
  const motionBtn = document.querySelector('.a11y-btn[data-motion]');
  // phones: the tools fold behind one Accessibility button
  const toggle = document.querySelector('.a11y-toggle');
  if (toggle) toggle.addEventListener('click', () => {
    const open = toggle.getAttribute('aria-expanded') !== 'true';
    toggle.setAttribute('aria-expanded', String(open));
    toggle.closest('.gov-strip').classList.toggle('a11y-open', open);
  });
  if (!sizeBtns.length && !contrastBtn) return;
  function save() {
    try {
      localStorage.setItem('metromaya.a11y', JSON.stringify({ fs: root.dataset.fs || '', hc: root.dataset.contrast === 'high', mp: root.dataset.motion === 'paused' }));
    } catch (e) { /* storage unavailable: settings last for this page only */ }
  }
  function paint() {
    const fs = root.dataset.fs || '0';
    sizeBtns.forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.fs === fs)));
    if (contrastBtn) contrastBtn.setAttribute('aria-pressed', String(root.dataset.contrast === 'high'));
    if (motionBtn) motionBtn.setAttribute('aria-pressed', String(root.dataset.motion === 'paused'));
  }
  sizeBtns.forEach((b) => b.addEventListener('click', () => {
    if (b.dataset.fs === '0') delete root.dataset.fs; else root.dataset.fs = b.dataset.fs;
    paint(); save(); window.dispatchEvent(new Event('resize'));
  }));
  if (contrastBtn) contrastBtn.addEventListener('click', () => {
    if (root.dataset.contrast === 'high') delete root.dataset.contrast; else root.dataset.contrast = 'high';
    paint(); save();
  });
  // WCAG 2.2.2: Maya's flapping runs for longer than 5 seconds, so it can be paused
  if (motionBtn) motionBtn.addEventListener('click', () => {
    if (root.dataset.motion === 'paused') delete root.dataset.motion; else root.dataset.motion = 'paused';
    paint(); save();
  });
  paint();
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
  }, { threshold: 0.01, rootMargin: '0px 0px -12% 0px' });  // a sliver is enough: panels taller than a phone screen still arrive

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

// ---- demo fee payment (no money moves) ----
// Renders into `box` for a booking: choose a channel and pay, then a QR / link to the demo checkout while it
// waits, then the demo acknowledgement receipt. `onUpdate(appointment)` gets the booking after each change.
const PAY_CHANNELS = [
  { id: 'qrph', label: 'QR Ph', note: 'Any bank or e-wallet app' },
  { id: 'ewallet', label: 'E-wallet', note: 'GCash, Maya and others' },
  { id: 'online-banking', label: 'Online banking', note: 'Pay from your bank account' },
  { id: 'landbank', label: 'LANDBANK Link.BizPortal', note: 'Government payment portal' },
];

function renderPayment(box, a, onUpdate, managementToken) {
  clear(box);
  if (box.__poll) { clearInterval(box.__poll); box.__poll = null; }
  const p = a.payment;
  const city = detectCity(a.businessAddress);
  const quote = city ? scaleQuote(a.quote, city.rate) : a.quote;
  const demoTag = el('span', { class: 'demo-tag', text: 'Demo · no money moves' });

  if (p && p.status === 'PAID') {
    const dl = el('dl', { class: 'receipt-rows' });
    const row = (k, v) => dl.append(el('dt', { text: k }), el('dd', { text: v }));
    row('Receipt no.', p.receiptNo);
    row('Transaction', p.txn);
    row('Booking reference', a.referenceNo);
    row('Paid to', a.officeName);
    row('For', a.serviceName + ' · ' + a.businessName);
    row('Channel', p.channel);
    row('Date paid', new Date(p.paidAt).toLocaleString('en-PH', { dateStyle: 'long', timeStyle: 'short' }));
    const fee = el('div', { class: 'receipt-fee' });
    renderFee(fee, quote.fee);
    box.append(el('div', { class: 'receipt' },
      el('div', { class: 'receipt-head' },
        el('div', null, el('p', { class: 'receipt-kind', text: 'Acknowledgement receipt' }), el('p', { class: 'receipt-amount', text: peso(p.amount) })),
        el('span', { class: 'receipt-paid', text: 'Paid' })),
      demoTag, dl, fee,
      el('div', { class: 'receipt-checkin' }, checkInQr(a),
        el('p', { text: 'Show this code at the office. Staff scan it to check this booking and the live payment record.' })),
      el('p', { class: 'hint receipt-note', text: 'Demo receipt from a prototype. No money moved, and this is not an official receipt. In production the city treasurer’s office would issue the official receipt.' }),
      el('div', { class: 'receipt-actions no-print' },
        el('button', { type: 'button', class: 'btn btn-outline btn-small', onclick: () => printBooking(a), text: 'Print confirmation and receipt' }))));
    return;
  }
  if (a.status !== 'CONFIRMED') return;

  if (p && p.status === 'PENDING') {
    const url = location.origin + '/pay.html?txn=' + encodeURIComponent(p.txn);
    const qrBox = el('div', { class: 'qr', role: 'img', 'aria-label': 'QR code that opens the demo payment page' });
    if (window.qrcode) {
      const q = qrcode(0, 'M'); q.addData(url); q.make();
      qrBox.innerHTML = q.createSvgTag({ cellSize: 5, margin: 2, scalable: true });
    }
    const status = el('p', { class: 'pay-wait', role: 'status', text: 'Waiting for payment…' });
    box.append(el('div', { class: 'pay-panel pay-pending' },
      el('div', { class: 'pay-panel-head' }, el('h3', { text: 'Scan to pay ' + peso(p.amount) }), demoTag),
      el('div', { class: 'pay-qr-row' }, qrBox,
        el('div', null,
          el('p', { text: 'Scan with your phone camera to open the payment page, or open it here. Channel: ' + p.channel + '.' }),
          el('a', { class: 'btn btn-primary', href: url, target: '_blank', rel: 'noopener', text: 'Open payment page' }),
          status,
          el('button', { type: 'button', class: 'link-btn', text: 'Choose another way to pay', onclick: () => renderPayment(box, Object.assign({}, a, { payment: null }), onUpdate, managementToken) })))));
    box.__poll = setInterval(async () => {
      if (!document.body.contains(box)) { clearInterval(box.__poll); return; }
      try {
        const { payment } = await api.get('/api/pay/' + encodeURIComponent(p.txn));
        if (payment.status === 'PAID') {
          clearInterval(box.__poll);
          const { appointment } = await api.managedGet('/api/appointments/' + encodeURIComponent(a.referenceNo), managementToken);
          renderPayment(box, appointment, onUpdate, managementToken);
          if (onUpdate) onUpdate(appointment);
        }
      } catch (e) { /* keep waiting */ }
    }, 2500);
    return;
  }

  // not paid yet: choose a channel
  const name = 'pay-channel-' + a.referenceNo;
  const choices = el('div', { class: 'pay-channels', role: 'radiogroup', 'aria-label': 'How to pay' });
  PAY_CHANNELS.forEach((c, i) => {
    choices.append(el('label', { class: 'pay-channel' },
      el('input', { type: 'radio', name, value: c.id, checked: i === 0 }),
      el('span', null, el('b', { text: c.label }), el('small', { text: c.note }))));
  });
  const alertNode = el('div', { class: 'alert alert-error', hidden: true, role: 'alert' });
  const btn = el('button', { type: 'button', class: 'btn btn-primary', text: 'Pay ' + peso(a.amountDue) + ' (demo)' });
  btn.addEventListener('click', async () => {
    btn.disabled = true;
    hideAlert(alertNode);
    try {
      const channel = choices.querySelector('input:checked').value;
      const { appointment } = await api.managedPost('/api/appointments/' + encodeURIComponent(a.referenceNo) + '/pay', { channel }, managementToken);
      renderPayment(box, appointment, onUpdate, managementToken);
      if (onUpdate) onUpdate(appointment);
    } catch (e) {
      showAlert(alertNode, e.message, 'error');
      btn.disabled = false;
    }
  });
  box.append(el('div', { class: 'pay-panel' },
    el('div', { class: 'pay-panel-head' }, el('h3', { text: 'Pay your fees online' }), demoTag),
    el('p', { class: 'hint', text: 'You can pay the estimated ' + peso(a.amountDue) + ' online now, or pay at city hall on the day of your visit. Your booking stays confirmed either way.' }),
    choices, alertNode, el('div', { class: 'pay-actions' },
      el('button', { type: 'button', class: 'btn btn-outline', text: 'I’ll pay at city hall', onclick: () => {
        clear(box).append(el('div', { class: 'pay-panel' },
          el('h3', { text: 'Pay at city hall' }),
          el('p', { text: 'Your booking is confirmed. Bring your confirmation and follow the office’s payment instructions when you arrive.' }),
          el('button', { type: 'button', class: 'link-btn', text: 'Pay online instead', onclick: () => renderPayment(box, a, onUpdate, managementToken) })));
      } }), btn)));
}

// The office QR carries only an opaque lookup code. Staff must sign in before they can
// view the live booking and payment status or accept the booking.
function checkInUrl(a) {
  return location.origin + '/staff.html?code=' + encodeURIComponent(a.checkInCode);
}

function checkInQr(a) {
  const box = el('div', { class: 'qr checkin-qr', role: 'img', 'aria-label': 'QR code for office staff to check this booking' });
  if (window.qrcode && a.checkInCode) {
    const code = qrcode(0, 'M');
    code.addData(checkInUrl(a));
    code.make();
    box.innerHTML = code.createSvgTag({ cellSize: 5, margin: 2, scalable: true });
  } else {
    box.append(el('span', { text: 'QR unavailable' }));
  }
  return box;
}

function renderCheckInCode(box, a) {
  clear(box);
  box.append(el('div', { class: 'checkin-code' },
    checkInQr(a),
    el('div', null,
      el('h3', { text: 'Office check-in code' }),
      el('p', { text: 'Show this QR code or your reference number at the office. Staff scan it to verify the booking and current payment status.' }),
      el('p', { class: 'checkin-ref', text: a.referenceNo }),
      el('p', { class: 'hint', text: a.acceptedAt ? 'Accepted at the office.' : 'Awaiting office check-in.' }))));
}

function printRow(list, label, value) {
  list.append(el('dt', { text: label }), el('dd', { text: value == null ? '—' : String(value) }));
}

function printActionLabel(a) {
  return a.payment && a.payment.status === 'PAID' ? 'Print confirmation and receipt' : 'Print booking confirmation';
}

function printBooking(a) {
  const paid = a.payment && a.payment.status === 'PAID';
  let sheet = document.getElementById('print-sheet');
  if (!sheet) {
    sheet = el('div', { id: 'print-sheet', class: 'print-sheet' });
    document.body.append(sheet);
  }
  clear(sheet);

  const summary = el('dl', { class: 'summary print-summary' });
  [
    ['Service', a.serviceName], ['Business', a.businessName], ['Business address', a.businessAddress],
    ['Applicant', a.ownerName], ['Office', a.officeName + ', ' + a.officeAddress],
    ['Date', formatDate(a.date)], ['Time', formatTime(a.time)],
  ].forEach(([label, value]) => printRow(summary, label, value));

  sheet.append(
    el('div', { class: 'print-masthead' },
      el('div', { class: 'brand' },
        el('span', { class: 'brand-mark', 'aria-hidden': 'true' }),
        el('span', { class: 'brand-text' }, el('span', { class: 'brand-name', text: 'MetroMaya' }),
          el('span', { class: 'brand-sub', text: 'Business Permit Appointments · Metro Manila' }))),
      el('span', { class: 'print-document-type', text: paid ? 'Prototype confirmation + demo receipt · Not official' : 'Prototype booking confirmation · Not official' })),
    el('div', { class: 'ticket print-ticket' },
      el('div', { class: 'ticket-main' },
        el('div', { class: 'label', text: 'Booking reference' }),
        el('div', { class: 'ref', text: a.referenceNo }),
        el('div', { class: 'ticket-sub', text: 'Show this at the business permits office' })),
      el('div', { class: 'ticket-stub', 'aria-hidden': 'true' }, el('span', { text: 'Claim stub' })),
      el('span', { class: 'ticket-status' + (a.status === 'CANCELLED' ? ' print-ticket-cancelled' : ''), text: a.status === 'CANCELLED' ? 'Cancelled' : a.acceptedAt ? 'Accepted' : 'Confirmed' })),
    summary);

  if (paid) {
    const p = a.payment;
    const rows = el('dl', { class: 'receipt-rows' });
    [
      ['Receipt no.', p.receiptNo], ['Transaction', p.txn],
      ['Channel', p.channel],
      ['Date paid', new Date(p.paidAt).toLocaleString('en-PH', { dateStyle: 'long', timeStyle: 'short' })],
    ].forEach(([label, value]) => printRow(rows, label, value));
    const city = detectCity(a.businessAddress);
    const quote = city ? scaleQuote(a.quote, city.rate) : a.quote;
    const fee = el('div', { class: 'receipt-fee' });
    renderFee(fee, quote.fee);
    sheet.append(el('section', { class: 'receipt print-payment', 'aria-label': 'Demo payment acknowledgement' },
      el('div', { class: 'receipt-head' },
        el('div', null, el('p', { class: 'receipt-kind', text: 'Demo payment acknowledgement' }),
          el('p', { class: 'receipt-amount', text: peso(p.amount) })),
        el('span', { class: 'receipt-paid', text: 'Paid' })),
      el('span', { class: 'demo-tag', text: 'Demo · no money moves' }),
      rows, fee,
      el('p', { class: 'receipt-note', text: 'This is a prototype acknowledgement, not an official receipt. The city treasurer issues the official receipt.' })));
  }

  const docs = el('ul', { class: 'print-doc-list' });
  (a.quote.requirements || []).forEach((item) => docs.append(el('li', { text: item })));
  const inspections = el('ul', { class: 'print-inspection-list' });
  (a.quote.inspections || []).forEach((item) => inspections.append(
    el('li', { text: item.label + ' · ' + minutesText(item.minutes) })));
  sheet.append(
    el('div', { class: 'print-requirements' },
      el('section', null, el('h2', { text: 'Documents to bring' }), docs),
      el('section', null, el('h2', { text: 'Inspections' }), inspections,
        el('div', { class: 'checkin-code print-checkin' },
          checkInQr(a),
          el('div', null, el('h3', { text: 'Office check-in code' }),
            el('p', { text: paid ? 'Staff scan to verify the booking and demo payment.' : 'Staff scan to verify the booking.' }),
            el('p', { class: 'checkin-ref', text: a.referenceNo }))))));

  requestAnimationFrame(() => window.print());
}

// ---- month calendar (pick a day, see its time slots) ----
// A whole month at a glance for one office: closed days (weekends, regular holidays, outside the booking window)
// are locked, open days show how many places are left across their slots, and picking a day calls
// onSelect(iso, day). Availability comes from /api/availability, one request per month.
function mountCalendar(container, opts) {
  const { officeId, config, onSelect } = opts;
  let selected = opts.selected || '';
  const ym = (iso) => iso.slice(0, 7);
  let month = ym(selected || config.today);
  const cache = {};
  const CHEV = (d) => '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="' + d + '"/></svg>';

  const title = el('strong', { 'aria-live': 'polite' });
  const prev = el('button', { type: 'button', class: 'cal-nav', 'aria-label': 'Previous month' });
  const next = el('button', { type: 'button', class: 'cal-nav', 'aria-label': 'Next month' });
  prev.innerHTML = CHEV('M15 6l-6 6 6 6'); next.innerHTML = CHEV('M9 6l6 6-6 6');
  const grid = el('div', { class: 'cal-days' });
  const weekdays = el('div', { class: 'cal-weekdays', 'aria-hidden': 'true' },
    ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].map((d) => el('span', { text: d })));
  const legend = el('div', { class: 'cal-legend' },
    el('span', null, el('i', { class: 'cal-dot lvl-many' }), 'Many places'),
    el('span', null, el('i', { class: 'cal-dot lvl-few' }), 'Few left'),
    el('span', null, el('i', { class: 'cal-dot lvl-full' }), 'Fully booked'),
    el('span', null, el('i', { class: 'cal-lock-key', 'aria-hidden': 'true' }), 'Closed'));
  clear(container).append(el('div', { class: 'cal' },
    el('div', { class: 'cal-head' }, prev, title, next), weekdays, grid, legend));

  const shift = (m, by) => { const [y, mo] = m.split('-').map(Number); const d = new Date(y, mo - 1 + by, 1); return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0'); };
  prev.addEventListener('click', () => { month = shift(month, -1); draw(); });
  next.addEventListener('click', () => { month = shift(month, 1); draw(); });

  function level(d) {
    if (!d.capacity || d.remaining === 0) return 'full';
    return d.remaining / d.capacity <= 0.25 ? 'few' : 'many';
  }

  async function draw() {
    const [y, m] = month.split('-').map(Number);
    title.textContent = new Date(y, m - 1, 1).toLocaleDateString('en-PH', { month: 'long', year: 'numeric' });
    prev.disabled = month <= ym(config.today);
    next.disabled = month >= ym(config.maxDate);
    grid.setAttribute('aria-busy', 'true');
    const want = month;   // a slow month must not draw over the one the user moved on to
    let days;
    try {
      days = cache[month] || (cache[month] = (await api.get('/api/availability?officeId=' + encodeURIComponent(officeId) + '&month=' + month)).days);
    } catch (e) {
      clear(grid).append(el('p', { class: 'alert alert-error cal-error', text: e.message }));
      return;
    }
    if (want !== month) return;
    clear(grid).removeAttribute('aria-busy');
    const lead = new Date(y, m - 1, 1).getDay();
    for (let i = 0; i < lead; i++) grid.append(el('span', { class: 'cal-blank', 'aria-hidden': 'true' }));
    days.forEach((d) => {
      const n = Number(d.date.slice(8));
      const long = new Date(y, m - 1, n).toLocaleDateString('en-PH', { weekday: 'long', month: 'long', day: 'numeric' });
      const lvl = d.open ? level(d) : null;
      const status = !d.open ? (d.reason === 'Past' ? 'past' : 'closed, ' + d.reason)
        : lvl === 'full' ? 'fully booked' : d.remaining + ' of ' + d.capacity + ' places left';
      const btn = el('button', {
        type: 'button',
        class: 'cal-day' + (d.open ? ' lvl-' + lvl : ' is-closed') + (d.date === config.today ? ' is-today' : '') + (d.date === selected ? ' is-selected' : '') + (d.reason === 'Past' ? ' is-past' : ''),
        'aria-pressed': d.date === selected ? 'true' : 'false',
        'aria-label': long + ', ' + status,
        disabled: !d.open || lvl === 'full',
        title: d.open ? (lvl === 'full' ? 'Fully booked' : d.remaining + ' of ' + d.capacity + ' places left') : (d.reason === 'Past' ? '' : 'Closed: ' + d.reason),
      }, el('span', { class: 'cal-num', text: String(n) }));
      if (d.open && lvl !== 'full') btn.append(el('span', { class: 'cal-left', text: d.remaining + ' left' }), el('i', { class: 'cal-dot lvl-' + lvl, 'aria-hidden': 'true' }));
      else if (d.open) btn.append(el('span', { class: 'cal-left', text: 'Full' }));
      else if (d.reason !== 'Past' && d.reason !== 'Weekend') btn.append(el('span', { class: 'cal-left cal-holiday', text: d.reason.length > 14 ? 'Holiday' : d.reason }));
      btn.addEventListener('click', () => {
        selected = d.date;
        grid.querySelectorAll('.cal-day').forEach((b) => { b.classList.remove('is-selected'); b.setAttribute('aria-pressed', 'false'); });
        btn.classList.add('is-selected');
        btn.setAttribute('aria-pressed', 'true');
        onSelect(d.date, d);
      });
      grid.append(btn);
    });
  }
  draw();
  return { refresh() { delete cache[month]; draw(); } };
}

// "Thursday, November 20 · 18 of 32 places left" under the calendar
function dayStatusText(iso, d) {
  const [y, m, n] = iso.split('-').map(Number);
  const day = new Date(y, m - 1, n).toLocaleDateString('en-PH', { weekday: 'long', month: 'long', day: 'numeric' });
  return day + ' · ' + d.remaining + ' of ' + d.capacity + ' places left. Pick a time:';
}
