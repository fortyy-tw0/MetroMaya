(async function () {
  const STEP_LABELS = ['Service', 'Office', 'Date & time', 'Details', 'Review'];
  const PANEL_IDS = ['panel-service', 'panel-office', 'panel-when', 'panel-details', 'panel-review'];

  const state = { step: 0, services: [], offices: [], config: null,
                  serviceId: null, officeId: null, date: '', time: '', details: null };

  const $ = (id) => document.getElementById(id);
  const panels = PANEL_IDS.map($);
  const donePanel = $('panel-done');

  const serviceById = (id) => state.services.find((s) => s.id === id);
  const officeById = (id) => state.offices.find((o) => o.id === id);

  // ---------- navigation ----------

  function renderProgress() {
    clear($('progress'));
    STEP_LABELS.forEach((label, i) => {
      const cls = i < state.step ? 'done' : i === state.step ? 'current' : '';
      $('progress').append(el('li', { class: cls, 'aria-current': i === state.step ? 'step' : false, text: (i + 1) + '. ' + label }));
    });
  }

  function showStep(n) {
    state.step = n;
    panels.forEach((p, i) => { p.hidden = i !== n; });
    donePanel.hidden = true;
    $('progress').hidden = false;
    renderProgress();
    if (n === 1) renderOffices();
    if (n === 2) setupWhen();
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
    let lastCategory = null;
    state.services.forEach((s) => {
      if (s.category !== lastCategory) {
        lastCategory = s.category;
        box.append(el('div', { class: 'choice-group-title', text: s.categoryLabel }));
      }
      const input = el('input', { type: 'radio', name: 'service', value: s.id, checked: s.id === state.serviceId });
      input.addEventListener('change', () => {
        if (state.serviceId !== s.id) { state.officeId = null; state.date = ''; state.time = ''; }
        state.serviceId = s.id;
        panels[0].querySelector('[data-next]').disabled = false;
      });
      box.append(el('label', { class: 'choice' }, input,
        el('div', null,
          el('strong', { text: s.name }),
          el('small', { text: s.description }),
          el('small', { text: 'Fee ' + money(s.fee) + ' · about ' + s.durationMinutes + ' min' }))));
    });
    panels[0].querySelector('[data-next]').disabled = !state.serviceId;
  }

  // ---------- step 2: office ----------

  function renderOffices() {
    const box = clear($('office-choices'));
    const options = state.offices.filter((o) => o.serviceIds.includes(state.serviceId));
    if (!options.some((o) => o.id === state.officeId)) state.officeId = null;
    options.forEach((o) => {
      const input = el('input', { type: 'radio', name: 'office', value: o.id, checked: o.id === state.officeId });
      input.addEventListener('change', () => {
        if (state.officeId !== o.id) { state.date = ''; state.time = ''; }
        state.officeId = o.id;
        panels[1].querySelector('[data-next]').disabled = false;
      });
      box.append(el('label', { class: 'choice' }, input,
        el('div', null, el('strong', { text: o.name }), el('small', { text: o.address }))));
    });
    panels[1].querySelector('[data-next]').disabled = !state.officeId;
  }

  // ---------- step 3: date & time ----------

  function setupWhen() {
    const dateInput = $('date');
    const min = state.config.today;
    const max = addDaysISO(min, state.config.maxDaysAhead);
    dateInput.min = min;
    dateInput.max = max;
    dateInput.value = state.date;
    $('date-hint').textContent = 'Weekdays only, up to ' + state.config.maxDaysAhead + ' days ahead.';
    const next = panels[2].querySelector('[data-next]');
    next.disabled = !(state.date && state.time);
    refreshSlots();
  }

  function refreshSlots() {
    const next = panels[2].querySelector('[data-next]');
    const alertNode = $('slot-alert');
    if (!state.date) { clear($('slots')); hideAlert(alertNode); return; }
    if (state.date < state.config.today || state.date > addDaysISO(state.config.today, state.config.maxDaysAhead)) {
      clear($('slots'));
      showAlert(alertNode, 'Please choose a date within the next ' + state.config.maxDaysAhead + ' days.', 'error');
      next.disabled = true;
      return;
    }
    if (isWeekend(state.date)) {
      clear($('slots'));
      showAlert(alertNode, 'Offices are closed on weekends. Please choose a weekday.', 'error');
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
    panels[2].querySelector('[data-next]').disabled = true;
    refreshSlots();
  });

  // ---------- step 4: details ----------

  $('details-form').addEventListener('submit', (e) => {
    e.preventDefault();
    const form = e.target;
    const alertNode = $('details-alert');
    const data = Object.fromEntries(new FormData(form).entries());
    for (const key of ['fullName', 'email', 'phone', 'birthDate']) {
      if (!String(data[key] || '').trim()) {
        showAlert(alertNode, 'Please fill in all the fields.', 'error');
        return;
      }
    }
    hideAlert(alertNode);
    state.details = data;
    showStep(4);
  });

  // ---------- step 5: review & submit ----------

  function addRow(dl, label, value) {
    dl.append(el('dt', { text: label }), el('dd', { text: value }));
  }

  function renderReview() {
    const service = serviceById(state.serviceId);
    const office = officeById(state.officeId);
    const dl = clear($('review-summary'));
    addRow(dl, 'Service', service.name);
    addRow(dl, 'Fee (pay at office)', money(service.fee));
    addRow(dl, 'Office', office.name + ', ' + office.address);
    addRow(dl, 'Date', formatDate(state.date));
    addRow(dl, 'Time', formatTime(state.time));
    addRow(dl, 'Name', state.details.fullName);
    addRow(dl, 'Email', state.details.email);
    addRow(dl, 'Mobile', state.details.phone);
    clear($('review-requirements')).append(requirementsList(service.requirements));
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
        ...state.details,
      });
      showDone(appointment);
    } catch (e) {
      showAlert($('submit-alert'), e.message, 'error');
    } finally {
      btn.disabled = false;
      btn.textContent = 'Confirm appointment';
    }
  });

  function showDone(a) {
    panels.forEach((p) => { p.hidden = true; });
    $('progress').hidden = true;
    donePanel.hidden = false;
    $('done-ref').textContent = a.referenceNo;
    $('done-manage').href = '/manage.html?ref=' + encodeURIComponent(a.referenceNo);
    const dl = clear($('done-summary'));
    addRow(dl, 'Service', a.serviceName);
    addRow(dl, 'Office', a.officeName + ', ' + a.officeAddress);
    addRow(dl, 'Date', formatDate(a.date));
    addRow(dl, 'Time', formatTime(a.time));
    addRow(dl, 'Name', a.fullName);
    clear($('done-requirements')).append(requirementsList(a.requirements));
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  // ---------- start ----------

  try {
    const [config, { services }, { offices }] = await Promise.all([
      api.get('/api/config'), api.get('/api/services'), api.get('/api/offices')]);
    state.config = config;
    state.services = services;
    state.offices = offices;
    $('birthDate').max = config.today;

    const preselect = new URLSearchParams(location.search).get('service');
    if (preselect && serviceById(preselect)) state.serviceId = preselect;
    renderServices();
    showStep(state.serviceId ? 1 : 0);
  } catch (e) {
    clear($('service-choices')).append(el('p', { class: 'alert alert-error', text: e.message }));
    renderProgress();
  }
})();
