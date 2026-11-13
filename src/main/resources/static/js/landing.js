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
    { id: 'makati', name: 'Makati', hall: 'Makati City Hall', brgy: 'Poblacion', rate: 1.30 },
    { id: 'malabon', name: 'Malabon', hall: 'Malabon City Hall', brgy: 'San Agustin', rate: 0.90 },
    { id: 'mandaluyong', name: 'Mandaluyong', hall: 'Mandaluyong City Hall', brgy: 'Plainview', rate: 1.15 },
    { id: 'manila', name: 'Manila', hall: 'Manila City Hall', brgy: 'Ermita', rate: 1.15 },
    { id: 'marikina', name: 'Marikina', hall: 'Marikina City Hall', brgy: 'Santa Elena', rate: 1.00 },
    { id: 'muntinlupa', name: 'Muntinlupa', hall: 'Muntinlupa City Hall', brgy: 'Putatan', rate: 1.10 },
    { id: 'navotas', name: 'Navotas', hall: 'Navotas City Hall', brgy: 'Sipac-Almacen', rate: 0.90 },
    { id: 'paranaque', name: 'Parañaque', hall: 'Parañaque City Hall', brgy: 'San Antonio', rate: 1.10 },
    { id: 'pasay', name: 'Pasay', hall: 'Pasay City Hall', brgy: 'Barangay 70, Zone 9', rate: 1.10 },
    { id: 'pasig', name: 'Pasig', hall: 'Pasig Temporary City Hall', brgy: 'Rosario', rate: 1.20,
      note: 'Pasig is using a temporary city hall while its new one is built.' },
    { id: 'pateros', name: 'Pateros', hall: 'Pateros Municipal Hall', brgy: 'Aguho', rate: 0.85, municipality: true },
    { id: 'quezon-city', name: 'Quezon City', hall: 'Quezon City Hall', brgy: 'Central', rate: 1.15 },
    { id: 'san-juan', name: 'San Juan', hall: 'San Juan City Hall', brgy: 'Pinaglabanan', rate: 1.10 },
    { id: 'taguig', name: 'Taguig', hall: 'Taguig City Hall', brgy: 'Tuktukan', rate: 1.25 },
    { id: 'valenzuela', name: 'Valenzuela', hall: 'Valenzuela City Hall', brgy: 'Karuhatan', rate: 0.95 },
  ];
  function cityPlace(c) { return c.municipality ? c.name : (c.name === 'Quezon City' ? c.name : c.name + ' City'); }
  let city = CITIES.find((c) => c.id === 'manila');
  try { const saved = localStorage.getItem('permitify.city'); city = CITIES.find((c) => c.id === saved) || city; } catch (e) { /* no storage */ }
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
  function renderIndustries() {
    const industryRows = clear($('industry-rows'));
    industries.forEach((i) => {
      industryRows.append(el('div', { class: 'row' },
        el('div', null, el('h4', { text: i.name }), el('p', { text: i.description })),
        el('div', { class: 'price' }, pesoShort(scale(i.baseFee)),
          el('small', { text: 'base permit fee, micro business' }),
          el('span', { class: 'city-rate', text: 'in ' + city.name })),
        el('div', { class: 'chips' },
          i.inspections.map((label) => el('span', { class: 'chip', text: label })),
          i.surcharges.map((s) => el('span', { class: 'chip chip-quiet', text: s.label + ' ' + pesoShort(scale(s.amount)) })))));
    });
  }

  // ---------- size tiers ----------
  const sizeGrid = clear($('size-grid'));
  config.sizes.forEach((s) => {
    const extras = [];
    s.extraInspections.forEach((label) => extras.push('Adds ' + label.toLowerCase()));
    s.extraDocuments.forEach((d) => extras.push('Needs ' + d.charAt(0).toLowerCase() + d.slice(1)));
    if (s.inspectionFactor !== 1) extras.push('Inspections cost ×' + s.inspectionFactor.toFixed(2));
    if (!extras.length) extras.push('No extra inspections or documents');
    sizeGrid.append(el('article', { class: 'size-card', tabindex: '0' },
      el('h4', { text: s.label }),
      el('p', { class: 'range', text: s.range }),
      el('p', { class: 'mult' }, '×' + s.feeMultiplier.toFixed(2), el('small', { text: 'on the base permit fee' })),
      el('ul', null, extras.map((x) => el('li', { text: x })))));
  });

  // ---------- office: the chosen city's hall ----------
  const serviceName = Object.fromEntries(services.map((s) => [s.id, s.name]));
  function renderOffice() {
    const place = cityPlace(city);
    const address = city.hall + ', ' + city.brgy + ', ' + place + ', Metro Manila';
    const maps = 'https://www.google.com/maps/search/?api=1&query=' + encodeURIComponent(city.hall + ', ' + place + ', Metro Manila');
    clear($('office-rows')).append(el('div', { class: 'row' },
      el('div', null,
        el('h4', { text: 'Business Permits and Licensing Office' }),
        el('p', { text: address }),
        city.note ? el('p', { class: 'hint', text: city.note }) : null,
        el('a', { class: 'directions', href: maps, target: '_blank', rel: 'noopener', text: 'Get directions to ' + city.hall + ' →' })),
      el('div', { class: 'chips' },
        services.map((s) => el('span', { class: 'chip', text: serviceName[s.id] })),
        el('span', { class: 'chip chip-quiet', text: 'All business sizes' }))));
  }

  // ---------- city picker ----------
  const citySelect = $('city-select');
  CITIES.forEach((c) => citySelect.append(el('option', { value: c.id, text: c.municipality ? c.name + ' (municipality)' : cityPlace(c) })));
  citySelect.value = city.id;
  citySelect.addEventListener('change', () => {
    city = CITIES.find((c) => c.id === citySelect.value) || city;
    try { localStorage.setItem('permitify.city', city.id); } catch (e) { /* no storage */ }
    renderIndustries(); renderOffice(); refresh();
  });
  renderIndustries();
  renderOffice();
})();
