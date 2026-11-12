(async function () {
  const STEP_LABELS = ['Service', 'Business', 'Office', 'Date and time', 'Review'];
  const PANEL_IDS = ['panel-service', 'panel-business', 'panel-office', 'panel-when', 'panel-review'];

  const state = {
    step: 0, services: [], industries: [], offices: [], config: null,
    serviceId: null, form: null, quote: null, officeId: null, date: '', time: '',
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

  async function refreshLive() {
    const industryId = $('industryId').value;
    const employees = $('employees').value.trim();
    if (!state.serviceId || !industryId) return;
    const ticket = ++liveTicket;
    const qs = new URLSearchParams({ serviceId: state.serviceId, industryId, employees });
    try {
      const { quote } = await api.get('/api/quote?' + qs.toString());
      if (ticket !== liveTicket) return;
      clear(liveBody).append(
        el('p', { class: 'hint', text: quote.size.label + ' business, ' + quote.size.range }),
        el('div', { class: 'fee-total', style: 'border-top:0; padding-top:0; margin-bottom:.9rem; font-size:1.15rem;' },
          el('span', { text: 'Estimated total' }), el('span', { text: peso(quote.fee.total) })),
        el('div', { id: 'live-fee' }),
        el('p', { class: 'hint', style: 'margin:.5rem 0 0;',
          text: quote.inspections.length
            ? 'Inspections: ' + quote.inspections.map((i) => shortInspection(i.label)).join(', ') + '.'
            : 'No inspection needed for this service.' }));
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

    const problems = [];
    if (form.businessName.length < 2) problems.push('Enter your business name.');
    else if (form.businessAddress.length < 5) problems.push('Enter the business address, including the barangay.');
    else if (!/^\d+$/.test(form.employees) || Number(form.employees) < 1) problems.push('Enter the number of employees as a whole number, 1 or more.');
    else if (form.ownerName.length < 2) problems.push('Enter the owner’s full name.');
    else if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(form.email)) problems.push('Enter a valid email address.');
    else if (form.phone.replace(/\D/g, '').length < 7) problems.push('Enter a valid mobile number.');
    if (problems.length) { showAlert(alertNode, problems[0], 'error'); return; }

    try {
      const qs = new URLSearchParams({ serviceId: state.serviceId, industryId: form.industryId, employees: form.employees });
      const { quote } = await api.get('/api/quote?' + qs.toString());
      if (!state.form || state.form.industryId !== form.industryId || state.form.employees !== form.employees) {
        state.officeId = null; state.date = ''; state.time = '';
      }
      state.form = form;
      state.quote = quote;
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
    renderFee($('done-fee'), a.quote.fee);
    renderInspections($('done-insp'), a.quote.inspections);
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
