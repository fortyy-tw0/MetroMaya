(async function () {
  const $ = (id) => document.getElementById(id);

  // ---- hero form: remember the business name for the booking page ----
  $('hero-form').addEventListener('submit', (e) => {
    e.preventDefault();
    const name = $('hero-name').value.trim();
    try { if (name) sessionStorage.setItem('bplo.businessName', name); } catch (err) { /* storage unavailable */ }
    window.location.href = '/book.html';
  });

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
      const { quote } = await api.get('/api/quote?' + qs.toString());
      if (ticket !== latest) return; // a newer request is already on its way
      const n = quote.inspections.length;
      clear($('est-tiles')).append(
        tile('Business size', quote.size.label, quote.size.range),
        tile('Estimated total', peso(quote.fee.total), quote.fee.lines.length + ' fee items', true),
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

  // ---------- industries ----------
  const industryRows = clear($('industry-rows'));
  industries.forEach((i) => {
    industryRows.append(el('div', { class: 'row' },
      el('div', null, el('h4', { text: i.name }), el('p', { text: i.description })),
      el('div', { class: 'price' }, pesoShort(i.baseFee), el('small', { text: 'base permit fee, micro business' })),
      el('div', { class: 'chips' },
        i.inspections.map((label) => el('span', { class: 'chip', text: label })),
        i.surcharges.map((s) => el('span', { class: 'chip chip-quiet', text: s.label + ' ' + pesoShort(s.amount) })))));
  });

  // ---------- size tiers ----------
  const sizeGrid = clear($('size-grid'));
  config.sizes.forEach((s) => {
    const extras = [];
    s.extraInspections.forEach((label) => extras.push('Adds ' + label.toLowerCase()));
    s.extraDocuments.forEach((d) => extras.push('Needs ' + d.charAt(0).toLowerCase() + d.slice(1)));
    if (s.inspectionFactor !== 1) extras.push('Inspections cost ×' + s.inspectionFactor.toFixed(2));
    if (!extras.length) extras.push('No extra inspections or documents');
    sizeGrid.append(el('article', { class: 'size-card' },
      el('h4', { text: s.label }),
      el('p', { class: 'range', text: s.range }),
      el('p', { class: 'mult' }, '×' + s.feeMultiplier.toFixed(2), el('small', { text: 'on the base permit fee' })),
      el('ul', null, extras.map((x) => el('li', { text: x })))));
  });

  // ---------- offices ----------
  const serviceName = Object.fromEntries(services.map((s) => [s.id, s.name]));
  const sizeLabel = Object.fromEntries(config.sizes.map((s) => [s.id, s.label]));
  const officeRows = clear($('office-rows'));
  offices.forEach((o) => {
    const serves = o.sizesServed.length === config.sizes.length
      ? 'All business sizes'
      : sizeLabel[o.sizesServed[0]] + ' to ' + sizeLabel[o.sizesServed[o.sizesServed.length - 1]].toLowerCase() + ' businesses';
    officeRows.append(el('div', { class: 'row' },
      el('div', null, el('h4', { text: o.name }), el('p', { text: o.address })),
      el('div', { class: 'chips' },
        o.serviceIds.map((id) => el('span', { class: 'chip', text: serviceName[id] })),
        el('span', { class: 'chip chip-quiet', text: serves }))));
  });
})();
