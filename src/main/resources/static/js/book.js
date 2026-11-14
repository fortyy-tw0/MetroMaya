(async function () {
  const STEP_LABELS = ['Service', 'Business', 'Office', 'Date and time', 'Review'];
  const PANEL_IDS = ['panel-service', 'panel-business', 'panel-office', 'panel-when', 'panel-review'];

  const state = {
    step: 0, services: [], industries: [], offices: [], config: null,
    serviceId: null, form: null, quote: null, city: null, officeId: null, date: '', time: '',
  };

  const $ = (id) => document.getElementById(id);
  const panels = PANEL_IDS.map($);
  const donePanel = $('panel-done');

  const serviceById = (id) => state.services.find((s) => s.id === id);
  const officeById = (id) => state.offices.find((o) => o.id === id);
  const industryById = (id) => state.industries.find((i) => i.id === id);

  // ---------- navigation ----------

  function renderStepper() {
    clear($('stepper'));
    STEP_LABELS.forEach((label, i) => {
      const cls = i < state.step ? 'done' : i === state.step ? 'current' : '';
      $('stepper').append(el('li', { class: cls, 'aria-current': i === state.step ? 'step' : false, text: label }));
    });
  }

  function showStep(n) {
    state.step = n;
    panels.forEach((p, i) => { p.hidden = i !== n; });
    donePanel.hidden = true;
    $('stepper').hidden = false;
    renderStepper();
    if (n === 1) refreshLive();
    if (n === 2) renderOffices();
    if (n === 3) setupWhen();
    if (n === 4) renderReview();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  panels.forEach((panel, i) => {
    const next = panel.querySelector('[data-next]');
    const back = panel.querySelector('[data-back]');
    if (next) next.addEventListener('click', () => showStep(i + 1));
    if (back) back.addEventListener('click', () => showStep(i - 1));
  });

  // ---------- step 1: service ----------

  function renderServices() {
    const box = clear($('service-choices'));
    state.services.forEach((s) => {
      const input = el('input', { type: 'radio', name: 'service', value: s.id, checked: s.id === state.serviceId });
      input.addEventListener('change', () => {
        if (state.serviceId !== s.id) { state.quote = null; state.officeId = null; state.date = ''; state.time = ''; }
        state.serviceId = s.id;
        panels[0].querySelector('[data-next]').disabled = false;
      });
      box.append(el('label', { class: 'choice' }, input,
        el('div', null, el('strong', { text: s.name }), el('small', { text: s.description }))));
    });
    panels[0].querySelector('[data-next]').disabled = !state.serviceId;
  }

  // ---------- step 2: business ----------

  const liveBody = $('live-body');
  let liveTicket = 0;

  // Address: Line 1 is typed; City → Barangay → ZIP code are picked from lists that depend on each other.
  // Barangays come from the PSA's PSGC list and ZIP codes from PHLPost's directory (see tools/places).
  const citySelect = $('cityId');
  const brgySelect = $('barangay');
  const zipInput = $('zip');
  const cityNote = $('city-note');
  const zipNote = $('zip-note');
  NCR_CITIES.forEach((c) => citySelect.append(el('option', { value: c.id, text: c.municipality ? c.name + ' (municipality)' : cityPlace(c) })));

  const fold = (t) => String(t).normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()
    .replace(/\bsta\.?\s/g, 'santa ').replace(/\bsto\.?\s/g, 'santo ').replace(/\(pob\.\)/g, '').replace(/[^a-z0-9]+/g, ' ').trim();

  function fillSelect(sel, placeholder, options) {
    clear(sel).append(el('option', { value: '', selected: true, disabled: true, text: placeholder }));
    options.forEach((o) => sel.append(o));
    sel.disabled = !options.length;
  }

  function renderBarangays() {
    const places = NCR_PLACES[citySelect.value];
    zipNote.hidden = true;
    if (!places) {
      fillSelect(brgySelect, 'Choose a city first', []);
      suggestZip();
      return;
    }
    if (citySelect.value === 'manila') {
      // Manila's 897 barangays are numbered, so they're grouped by district
      const groups = {};
      places.barangays.forEach((b) => { (groups[b.district] = groups[b.district] || []).push(b); });
      fillSelect(brgySelect, 'Choose your barangay', Object.keys(groups).map((d) => el('optgroup', { label: d },
        groups[d].map((b) => el('option', { value: b.name, 'data-district': d, text: b.name })))));
    } else {
      fillSelect(brgySelect, 'Choose your barangay', places.barangays.map((b) => el('option', { value: b.name, text: b.name })));
    }
    suggestZip();
  }

  // The ZIP code is typed by the user. When the barangay (or, in Manila, its district) clearly matches a
  // PHLPost area, its code is offered as the placeholder and a one-click hint, but nothing is filled in.
  function suggestZip() {
    const places = NCR_PLACES[citySelect.value];
    zipNote.hidden = true;
    clear(zipNote);
    zipInput.placeholder = '4 digits';
    if (!places) return;
    const brgy = places.barangays.find((b) => b.name === brgySelect.value);
    let match = null;
    if (brgy && brgy.zips) {
      const zips = places.zips.filter((z) => brgy.zips.includes(z.code));
      if (zips.length === 1) match = zips[0];
    } else if (brgy) {
      const name = fold(brgy.name);
      match = places.zips.find((z) => fold(z.area).split(/\s*(?:,|&|\/|\+|-)\s*|\s+and\s+/).includes(name))
        || places.zips.find((z) => (' ' + fold(z.area) + ' ').includes(' ' + name + ' '))
        // barangays named after their area, e.g. "Almanza Uno" in PHLPost's "Almanza"
        || places.zips.find((z) => fold(z.area).split(/\s*(?:,|&|\/|\+|-)\s*|\s+and\s+/).some((part) => part.length >= 5 && name.startsWith(part + ' ')))
        || null;
    }
    if (match) {
      zipInput.placeholder = 'e.g. ' + match.code;
      if (zipInput.value.trim() !== match.code) {
        const use = el('button', { type: 'button', class: 'link-btn', text: 'Use ' + match.code });
        use.addEventListener('click', () => { zipInput.value = match.code; zipNote.hidden = true; });
        zipNote.append('PHLPost lists ' + match.code + ' for ' + match.area + '. ', use);
        zipNote.hidden = false;
      }
    }
  }

  // Typing a city into Line 1 still picks it for you.
  let lastDetected = null;
  function detectFromAddress() {
    const found = detectCity($('addressLine1').value);
    if (found && found.id !== lastDetected) {
      lastDetected = found.id;
      if (citySelect.value !== found.id) {
        citySelect.value = found.id;
        renderBarangays();
        cityNote.textContent = 'Found ' + cityPlace(found) + ' in your address.';
        cityNote.hidden = false;
        refreshLive();
      }
    } else if (!found) {
      lastDetected = null;
    }
  }
  $('addressLine1').addEventListener('input', debounce(detectFromAddress, 200));
  citySelect.addEventListener('change', () => { cityNote.hidden = true; renderBarangays(); refreshLive(); });
  brgySelect.addEventListener('change', suggestZip);
  zipInput.addEventListener('input', () => {
    zipInput.value = zipInput.value.replace(/\D/g, '').slice(0, 4);
    if (zipInput.value.length === 4) zipNote.hidden = true;
  });

  const employeesOk = (v) => /^\d+$/.test(v) && Number(v) >= 1;

  // The estimate only appears once the three things it depends on are filled in.
  function missingForEstimate() {
    const missing = [];
    if (!$('industryId').value) missing.push('type of business');
    if (!employeesOk($('employees').value.trim())) missing.push('number of employees');
    if (!citySelect.value) missing.push('city');
    return missing;
  }

  function showWaiting(missing) {
    const list = missing.length > 1 ? missing.slice(0, -1).join(', ') + ' and ' + missing[missing.length - 1] : missing[0];
    clear(liveBody).append(
      el('p', { class: 'live-empty', text: 'Add your ' + list + ' to see your estimate.' }),
      el('ul', { class: 'live-todo' }, ['type of business', 'number of employees', 'city'].map((item) =>
        el('li', { class: missing.includes(item) ? '' : 'done', text: item.charAt(0).toUpperCase() + item.slice(1) }))));
  }

  async function refreshLive() {
    const missing = missingForEstimate();
    if (!state.serviceId) return;
    if (missing.length) { ++liveTicket; showWaiting(missing); return; }
    const industryId = $('industryId').value;
    const employees = $('employees').value.trim();
    const city = cityById(citySelect.value);
    const ticket = ++liveTicket;
    const qs = new URLSearchParams({ serviceId: state.serviceId, industryId, employees });
    try {
      const { quote: raw } = await api.get('/api/quote?' + qs.toString());
      if (ticket !== liveTicket) return;
      const quote = scaleQuote(raw, city.rate);
      clear(liveBody).append(
        el('p', { class: 'hint', text: quote.size.label + ' business, ' + quote.size.range + ', in ' + cityPlace(city) }),
        el('div', { class: 'fee-total', style: 'border-top:0; padding-top:0; margin-bottom:.9rem; font-size:1.15rem;' },
          el('span', { text: 'Estimated total' }), el('span', { text: peso(quote.fee.total) })),
        el('div', { id: 'live-fee' }),
        el('p', { class: 'hint', style: 'margin:.5rem 0 0;',
          text: quote.inspections.length
            ? 'Inspections: ' + quote.inspections.map((i) => shortInspection(i.label)).join(', ') + '.'
            : 'No inspection needed for this service.' }),
        el('p', { class: 'hint live-note', text: 'Sample rates for a course project, not official LGU fees.' }));
      const feeBox = $('live-fee');
      quote.fee.lines.forEach((line) => {
        feeBox.append(el('div', { class: 'fee-row' },
          el('span', { text: line.label }), el('span', { class: 'amt', text: peso(line.amount) })));
      });
    } catch (e) {
      if (ticket !== liveTicket) return;
      clear(liveBody).append(el('p', { class: 'live-empty', text: e.message }));
    }
  }

  $('industryId').addEventListener('change', refreshLive);
  $('employees').addEventListener('input', debounce(refreshLive, 250));

  $('business-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const alertNode = $('business-alert');
    const form = Object.fromEntries(new FormData(e.target).entries());
    Object.keys(form).forEach((k) => { form[k] = String(form[k]).trim(); });
    const city = cityById(form.cityId || '');

    const problems = [];
    if (form.businessName.length < 2) problems.push('Enter your business name.');
    else if ((form.addressLine1 || '').length < 3) problems.push('Enter Address Line 1: unit or block number, street and subdivision.');
    else if (!city) problems.push('Choose the city your business is in.');
    else if (!form.barangay) problems.push('Choose your barangay.');
    else if (!/^\d{4}$/.test(form.zip || '')) problems.push('Enter your 4-digit ZIP code.');
    else if (!form.industryId) problems.push('Choose your type of business.');
    else if (!employeesOk(form.employees)) problems.push('Enter the number of employees as a whole number, 1 or more.');
    else if (form.ownerName.length < 2) problems.push('Enter the owner’s full name.');
    else if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(form.email)) problems.push('Enter a valid email address.');
    else if (form.phone.replace(/\D/g, '').length < 7) problems.push('Enter a valid mobile number.');
    if (problems.length) { showAlert(alertNode, problems[0], 'error'); return; }

    // one address line for the booking, ending in the city and ZIP, e.g.
    // "Unit 3 Ruby Bldg, 12 Rizal St, Kapitolyo, Pasig City 1603". Track a booking reads the city from it.
    const brgyLabel = /^barangay\s/i.test(form.barangay) ? form.barangay : 'Brgy. ' + form.barangay;
    form.businessAddress = [form.addressLine1, brgyLabel, cityPlace(city) + ' ' + form.zip].join(', ');
    ['addressLine1', 'cityId', 'barangay', 'zip'].forEach((k) => delete form[k]);

    try {
      const qs = new URLSearchParams({ serviceId: state.serviceId, industryId: form.industryId, employees: form.employees });
      const { quote } = await api.get('/api/quote?' + qs.toString());
      if (!state.form || state.form.industryId !== form.industryId || state.form.employees !== form.employees) {
        state.officeId = null; state.date = ''; state.time = '';
      }
      state.form = form;
      state.city = city;
      state.quote = scaleQuote(quote, city.rate);
      hideAlert(alertNode);
      showStep(2);
    } catch (err) {
      showAlert(alertNode, err.message, 'error');
    }
  });

  // ---------- step 3: office ----------

  function officeProblem(office) {
    if (!office.serviceIds.includes(state.serviceId)) {
      return 'Does not handle ' + serviceById(state.serviceId).name.toLowerCase() + '.';
    }
    if (!office.sizesServed.includes(state.quote.size.id)) {
      return 'Does not serve ' + state.quote.size.label.toLowerCase() + ' businesses.';
    }
    return null;
  }

  function renderOffices() {
    const box = clear($('office-choices'));
    const next = panels[2].querySelector('[data-next]');
    $('office-hint').textContent = 'Showing which desks can serve a ' + state.quote.size.label.toLowerCase()
      + ' business for ' + serviceById(state.serviceId).name.toLowerCase() + '.';
    const current = officeById(state.officeId);
    if (!current || officeProblem(current)) state.officeId = null;

    state.offices.forEach((o) => {
      const problem = officeProblem(o);
      const input = el('input', { type: 'radio', name: 'office', value: o.id, checked: o.id === state.officeId, disabled: !!problem });
      input.addEventListener('change', () => {
        if (state.officeId !== o.id) { state.date = ''; state.time = ''; }
        state.officeId = o.id;
        next.disabled = false;
      });
      box.append(el('label', { class: 'choice' + (problem ? ' disabled' : '') }, input,
        el('div', null,
          el('strong', { text: o.name }),
          el('small', { text: o.address }),
          problem && el('small', { text: problem }))));
    });
    next.disabled = !state.officeId;
  }

  // ---------- step 4: date & time ----------

  function setupWhen() {
    const dateInput = $('date');
    const min = state.config.today;
    dateInput.min = min;
    dateInput.max = addDaysISO(min, state.config.maxDaysAhead);
    dateInput.value = state.date;
    $('date-hint').textContent = 'Weekdays only, up to ' + state.config.maxDaysAhead + ' days ahead. '
      + 'Expect about ' + minutesText(state.quote.estimatedMinutes)
      + (state.serviceId === 'inspection' ? ' on site.' : ' at the counter.');
    panels[3].querySelector('[data-next]').disabled = !(state.date && state.time);
    refreshSlots();
  }

  function refreshSlots() {
    const next = panels[3].querySelector('[data-next]');
    const alertNode = $('slot-alert');
    if (!state.date) { clear($('slots')); hideAlert(alertNode); return; }
    if (state.date < state.config.today || state.date > addDaysISO(state.config.today, state.config.maxDaysAhead)) {
      clear($('slots'));
      showAlert(alertNode, 'Choose a date within the next ' + state.config.maxDaysAhead + ' days.', 'error');
      next.disabled = true;
      return;
    }
    if (isWeekend(state.date)) {
      clear($('slots'));
      showAlert(alertNode, 'Offices are closed on weekends. Choose a weekday.', 'error');
      next.disabled = true;
      return;
    }
    loadSlots($('slots'), alertNode, state.officeId, state.date, state.time, (time) => {
      state.time = time;
      next.disabled = false;
    });
  }

  $('date').addEventListener('change', (e) => {
    state.date = e.target.value;
    state.time = '';
    panels[3].querySelector('[data-next]').disabled = true;
    refreshSlots();
  });

  // ---------- step 5: review & submit ----------

  function addRow(dl, label, value) {
    dl.append(el('dt', { text: label }), el('dd', { text: value }));
  }

  function renderReview() {
    const service = serviceById(state.serviceId);
    const office = officeById(state.officeId);
    const f = state.form;
    const dl = clear($('review-summary'));
    addRow(dl, 'Service', service.name);
    addRow(dl, 'Business', f.businessName + ' (' + industryById(f.industryId).name.toLowerCase() + ', '
      + state.quote.size.label.toLowerCase() + ')');
    addRow(dl, 'Address', f.businessAddress);
    addRow(dl, 'Office', office.name + ', ' + office.address);
    addRow(dl, 'Date', formatDate(state.date));
    addRow(dl, 'Time', formatTime(state.time));
    addRow(dl, 'Contact', f.ownerName + ', ' + f.email + ', ' + f.phone);
    renderFee($('review-fee'), state.quote.fee);
    renderInspections($('review-insp'), state.quote.inspections);
    renderDocs($('review-docs'), state.quote.requirements);
    hideAlert($('submit-alert'));
  }

  $('confirm-btn').addEventListener('click', async () => {
    const btn = $('confirm-btn');
    btn.disabled = true;
    btn.textContent = 'Booking…';
    hideAlert($('submit-alert'));
    try {
      const { appointment } = await api.post('/api/appointments', {
        serviceId: state.serviceId,
        officeId: state.officeId,
        date: state.date,
        time: state.time,
        ...state.form,
      });
      showDone(appointment);
    } catch (e) {
      showAlert($('submit-alert'), e.message, 'error');
    } finally {
      btn.disabled = false;
      btn.textContent = 'Confirm booking';
    }
  });

  function showDone(a) {
    panels.forEach((p) => { p.hidden = true; });
    $('stepper').hidden = true;
    donePanel.hidden = false;
    $('done-ref').textContent = a.referenceNo;
    $('done-manage').href = '/manage.html?ref=' + encodeURIComponent(a.referenceNo);
    const dl = clear($('done-summary'));
    addRow(dl, 'Service', a.serviceName);
    addRow(dl, 'Business', a.businessName);
    addRow(dl, 'Office', a.officeName + ', ' + a.officeAddress);
    addRow(dl, 'Date', formatDate(a.date));
    addRow(dl, 'Time', formatTime(a.time));
    const doneQuote = state.city ? scaleQuote(a.quote, state.city.rate) : a.quote;
    renderFee($('done-fee'), doneQuote.fee);
    renderInspections($('done-insp'), doneQuote.inspections);
    renderDocs($('done-docs'), a.quote.requirements);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  // ---------- start ----------

  try {
    const [config, { services }, { industries }, { offices }] = await Promise.all([
      api.get('/api/config'), api.get('/api/services'), api.get('/api/industries'), api.get('/api/offices')]);
    state.config = config;
    state.services = services;
    state.industries = industries;
    state.offices = offices;

    industries.forEach((i) => $('industryId').append(el('option', { value: i.id, text: i.name })));

    try {
      const saved = sessionStorage.getItem('bplo.businessName');
      if (saved) $('businessName').value = saved;
    } catch (err) { /* storage unavailable */ }

    const preselect = new URLSearchParams(location.search).get('service');
    if (preselect && serviceById(preselect)) state.serviceId = preselect;
    renderServices();
    showStep(state.serviceId ? 1 : 0);
    refreshLive();
  } catch (e) {
    clear($('service-choices')).append(el('p', { class: 'alert alert-error', text: e.message }));
    renderStepper();
  }
})();
