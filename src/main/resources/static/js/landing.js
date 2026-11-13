(async function () {
  const $ = (id) => document.getElementById(id);

  // ---- optional hero form: remember the business name for the booking page ----
  const heroForm = $('hero-form');
  if (heroForm) {
    heroForm.addEventListener('submit', (e) => {
      e.preventDefault();
      const name = $('hero-name').value.trim();
      try { if (name) sessionStorage.setItem('bplo.businessName', name); } catch (err) { /* storage unavailable */ }
      window.location.href = '/book.html';
    });
  }

  // Metro Manila LGUs. Halls and barangays from Wikipedia's list of city and municipal halls in Metro Manila.
  // `rate` is a SAMPLE multiplier standing in for each city's revenue code; it is not an official rate.
  const CITIES = [
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
  let city = CITIES.find((c) => c.id === 'manila');
  try { const saved = localStorage.getItem('metromaya.city'); city = CITIES.find((c) => c.id === saved) || city; } catch (e) { /* no storage */ }
  const scale = (n) => Math.round(n * city.rate * 100) / 100;

  let config, services, industries, offices;
  try {
    [config, { services }, { industries }, { offices }] = await Promise.all([
      api.get('/api/config'), api.get('/api/services'), api.get('/api/industries'), api.get('/api/offices')]);
  } catch (e) {
    clear($('est-tiles')).append(el('p', { class: 'est-error', text: e.message }));
    return;
  }

  // ---------- estimator ----------
  const state = { serviceId: services[0].id, industryId: industries[0].id, employees: 12 };

  const tabs = $('est-tabs');
  function renderTabs() {
    clear(tabs);
    services.forEach((s) => {
      tabs.append(el('button', {
        type: 'button', class: 'est-tab', role: 'tab', text: s.name,
        'aria-selected': s.id === state.serviceId ? 'true' : 'false',
        onclick: () => { state.serviceId = s.id; renderTabs(); refresh(); },
      }));
    });
  }

  const industrySelect = $('est-industry');
  industries.forEach((i) => industrySelect.append(el('option', { value: i.id, text: i.name })));
  industrySelect.value = state.industryId;
  industrySelect.addEventListener('change', () => { state.industryId = industrySelect.value; refresh(); });

  const employeesInput = $('est-employees');
  employeesInput.addEventListener('input', debounce(() => {
    state.employees = employeesInput.value.trim();
    refresh();
  }, 250));

  function tile(label, value, sub, main) {
    return el('div', { class: 'tile' + (main ? ' tile-main' : '') },
      el('p', { class: 'tile-label', text: label }),
      el('p', { class: 'tile-value', text: value }),
      el('p', { class: 'tile-sub', text: sub }));
  }

  let latest = 0;
  async function refresh() {
    const ticket = ++latest;
    $('est-book').href = '/book.html?service=' + encodeURIComponent(state.serviceId);
    const qs = new URLSearchParams({ serviceId: state.serviceId, industryId: state.industryId, employees: state.employees });
    try {
      const { quote: raw } = await api.get('/api/quote?' + qs.toString());
      if (ticket !== latest) return; // a newer request is already on its way
      const lines = raw.fee.lines.map((l) => ({ label: l.label, amount: scale(l.amount) }));
      const quote = Object.assign({}, raw, {
        fee: { lines, total: Math.round(lines.reduce((t, l) => t + l.amount, 0) * 100) / 100 },
        inspections: raw.inspections.map((i) => Object.assign({}, i, { fee: scale(i.fee) })),
      });
      const n = quote.inspections.length;
      clear($('est-tiles')).append(
        tile('Business size', quote.size.label, quote.size.range),
        tile('Estimated total', peso(quote.fee.total), 'in ' + city.name + ' · ' + quote.fee.lines.length + ' fee items', true),
        tile('Inspections', String(n), n ? quote.inspections.map((i) => shortInspection(i.label)).join(', ') : 'None for this service'),
        tile('Time needed', minutesText(quote.estimatedMinutes), state.serviceId === 'inspection' ? 'on site' : 'at the counter'));
      renderFee($('est-fee'), quote.fee);
      renderInspections($('est-insp'), quote.inspections);
      renderDocs($('est-docs'), quote.requirements);
    } catch (e) {
      if (ticket !== latest) return;
      clear($('est-tiles')).append(el('p', { class: 'est-error', text: e.message }));
      clear($('est-fee'));
      clear($('est-insp'));
      clear($('est-docs'));
    }
  }

  renderTabs();
  refresh();

  // ---------- industries (fees follow the chosen city) ----------
  // ---------- what you pay: industry rows follow the business size picked on the right ----------
  // The rows are built once and then updated in place, so picking a size or a city doesn't make
  // them disappear and replay their scroll-reveal.
  const sizes = config.sizes;
  let size = sizes[0];
  try { const saved = localStorage.getItem('metromaya.size'); size = sizes.find((s) => s.id === saved) || size; } catch (e) { /* no storage */ }
  const industryRefs = industries.map((i) => {
    const amount = el('span', { class: 'amount' });
    const basis = el('small');
    const where = el('span', { class: 'city-rate' });
    const chips = el('div', { class: 'chips' });
    $('industry-rows').append(el('div', { class: 'row' },
      el('div', null, el('h4', { text: i.name }), el('p', { text: i.description })),
      el('div', { class: 'price' }, amount, basis, where),
      chips));
    return { i, amount, basis, where, chips };
  });
  function renderIndustries() {
    industryRefs.forEach(({ i, amount, basis, where, chips }) => {
      amount.textContent = pesoShort(scale(i.baseFee * size.feeMultiplier));
      basis.textContent = 'base permit fee, ' + size.label.toLowerCase() + ' business';
      where.textContent = 'in ' + city.name;
      // the industry's own inspections, plus any this size adds (no repeats)
      const own = new Set(i.inspections);
      const added = size.extraInspections.filter((label) => !own.has(label));
      clear(chips).append(
        ...i.inspections.map((label) => el('span', { class: 'chip', text: label })),
        ...added.map((label) => el('span', { class: 'chip chip-added', title: 'Added for ' + size.label.toLowerCase() + ' businesses', text: label })),
        ...i.surcharges.map((x) => el('span', { class: 'chip chip-quiet', text: x.label + ' ' + pesoShort(scale(x.amount)) })));
    });
  }

  // ---------- size tiers: pick one to update the fees on the left ----------
  const sizeGrid = clear($('size-grid'));
  const sizeCards = sizes.map((s) => {
    const extras = [];
    s.extraInspections.forEach((label) => extras.push('Adds ' + label.toLowerCase()));
    s.extraDocuments.forEach((d) => extras.push('Needs ' + d.charAt(0).toLowerCase() + d.slice(1)));
    if (s.inspectionFactor !== 1) extras.push('Inspections cost ×' + s.inspectionFactor.toFixed(2));
    if (!extras.length) extras.push('No extra inspections or documents');
    const card = el('article', { class: 'size-card', role: 'radio', tabindex: '-1', 'aria-checked': 'false' },
      el('span', { class: 'size-pick', 'aria-hidden': 'true', text: 'Selected' }),
      el('h4', { text: s.label }),
      el('p', { class: 'range', text: s.range }),
      el('p', { class: 'mult' }, '×' + s.feeMultiplier.toFixed(2), el('small', { text: 'on the base permit fee' })),
      el('ul', null, extras.map((x) => el('li', { text: x }))));
    card.addEventListener('click', () => pickSize(s));
    sizeGrid.append(card);
    return card;
  });
  sizeGrid.setAttribute('role', 'radiogroup');
  sizeGrid.setAttribute('aria-label', 'Business size');
  // arrow keys move between sizes, like any radio group
  sizeGrid.addEventListener('keydown', (e) => {
    const k = sizes.indexOf(size);
    const step = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 }[e.key];
    if (step) { e.preventDefault(); pickSize(sizes[(k + step + sizes.length) % sizes.length], true); }
    else if (e.key === ' ' || e.key === 'Enter') { e.preventDefault(); pickSize(size, true); }
  });
  function pickSize(s, focus) {
    size = s;
    try { localStorage.setItem('metromaya.size', s.id); } catch (e) { /* no storage */ }
    sizeCards.forEach((card, k) => {
      const on = sizes[k] === s;
      card.classList.toggle('is-picked', on);
      card.setAttribute('aria-checked', String(on));
      card.tabIndex = on ? 0 : -1;
      if (on && focus) card.focus();
    });
    renderIndustries();
  }

  // ---------- offices: the chosen city's hall and the four desks a business permit passes through ----------
  // Under the Ease of Doing Business Act (RA 11032) these desks sit together in the hall's
  // Business One-Stop Shop. Cities that use their own office names override the defaults.
  const ICON = {
    permit: '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5"/><path d="M9 13h6M9 17h4"/>',
    cash: '<rect x="3" y="6" width="18" height="12" rx="2"/><circle cx="12" cy="12" r="2.5"/><path d="M7 9.5v.01M17 14.5v.01"/>',
    fire: '<path d="M12 3c1 3.5 5 5.5 5 10a5 5 0 0 1-10 0c0-2.4 1.3-3.8 2.4-5 .3 1.6 1 2.6 2.1 3 .3-2.8-.4-5.4.5-8z"/>',
    health: '<path d="M12 20s-7-4.3-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.7-7 10-7 10z"/><path d="M12 10v4M10 12h4"/>',
  };
  function desks(c) {
    const lgu = c.municipality ? 'Municipal' : 'City';
    return [
      { icon: 'permit', name: c.bplo || 'Business Permits and Licensing Office',
        what: 'Start here. Files your application, assesses your fees and releases the permit.' },
      { icon: 'cash', name: c.treasurer || lgu + ' Treasurer\u2019s Office',
        what: 'Pay the assessed fees and local business tax, and get your official receipt.' },
      { icon: 'fire', name: 'Bureau of Fire Protection Desk',
        what: 'Fire safety evaluation and your Fire Safety Inspection Certificate.' },
      { icon: 'health', name: c.health || lgu + ' Health Office / Sanitation Desk',
        what: 'Sanitary permit, plus health certificates for staff who handle food or care.' },
    ];
  }
  function deskIcon(k) {
    const span = el('span', { class: 'icon-chip', 'aria-hidden': 'true' });
    span.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' + ICON[k] + '</svg>';
    return span;
  }
  function renderOffice() {
    const place = cityPlace(city);
    const address = city.hall + ', ' + city.brgy + ', ' + place + ', Metro Manila';
    const maps = 'https://www.google.com/maps/search/?api=1&query=' + encodeURIComponent(city.hall + ', ' + place + ', Metro Manila');
    clear($('office-rows')).append(
      el('div', { class: 'row office-hall' },
        el('div', null,
          el('h4', { text: city.hall }),
          el('p', { text: address }),
          city.note ? el('p', { class: 'hint', text: city.note }) : null,
          el('a', { class: 'directions', href: maps, target: '_blank', rel: 'noopener', text: 'Get directions to ' + city.hall + ' \u2192' })),
        el('p', { class: 'office-boss', text: 'All four desks below are at the hall\u2019s Business One-Stop Shop.' })),
      ...desks(city).map((d) => el('div', { class: 'row desk' },
        deskIcon(d.icon),
        el('div', null,
          el('h4', { text: d.name }),
          el('p', { text: d.what })))));
  }

  // ---------- city pickers: one in Fees, one in the estimator; they always agree ----------
  const citySelects = [$('fees-city-select'), $('city-select')].filter(Boolean);
  citySelects.forEach((sel) => {
    CITIES.forEach((c) => sel.append(el('option', { value: c.id, text: c.municipality ? c.name + ' (municipality)' : cityPlace(c) })));
    sel.value = city.id;
    sel.addEventListener('change', () => {
      city = CITIES.find((c) => c.id === sel.value) || city;
      citySelects.forEach((other) => { other.value = city.id; });
      try { localStorage.setItem('metromaya.city', city.id); } catch (e) { /* no storage */ }
      renderIndustries(); renderOffice(); refresh();
    });
  });
  pickSize(size);
  renderOffice();
})();
