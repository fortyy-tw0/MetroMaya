(function () {
  const $ = (id) => document.getElementById(id);
  const ROLE_NAMES = { STAFF: 'Office staff', DISTRICT_HEAD: 'District head', ADMIN: 'IT administrator', TREASURER: 'Treasurer' };
  let token = sessionStorage.getItem('metromaya.staffToken');
  let user = null;
  let offices = [];
  let current = null;
  let currentPolicy = null;
  let cameraStream = null;
  let scanTimer = null;

  async function staffRequest(path, data) {
    const options = { cache: 'no-store', headers: { Authorization: 'Bearer ' + token } };
    if (data) {
      options.method = 'POST';
      options.headers['Content-Type'] = 'application/x-www-form-urlencoded';
      options.body = new URLSearchParams(data).toString();
    }
    const response = await fetch(path, options);
    const body = await response.json();
    if (!response.ok) {
      if (response.status === 401 && !path.endsWith('/login')) signOut();
      throw new Error(body.error || 'Request failed. Please try again.');
    }
    return body;
  }

  function signOut() {
    stopCamera();
    token = null;
    user = null;
    current = null;
    sessionStorage.removeItem('metromaya.staffToken');
    $('workspace').hidden = true;
    $('session-info').hidden = true;
    $('login-view').hidden = false;
    $('password').value = '';
  }

  function officeName(id) { return (offices.find((office) => office.id === id) || {}).name || id; }
  function officeId() { return user.role === 'ADMIN' ? $('office-select').value : user.officeId; }
  function officeParam() { return '?officeId=' + encodeURIComponent(officeId()); }

  function metric(label, value, note) {
    return el('div', { class: 'staff-metric' }, el('span', { class: 'staff-metric-label', text: label }),
      el('strong', { text: value }), el('small', { text: note || '' }));
  }

  function showTab(name) {
    if (name === 'analytics' && !['DISTRICT_HEAD', 'ADMIN'].includes(user.role)) name = 'today';
    if (name === 'accounts' && user.role !== 'ADMIN') name = 'today';
    if (name === 'settings' && user.role !== 'DISTRICT_HEAD') name = 'today';
    if (name === 'payments' && !['DISTRICT_HEAD', 'TREASURER'].includes(user.role)) name = 'today';
    if (name === 'checkin' && !['STAFF', 'DISTRICT_HEAD'].includes(user.role)) name = 'today';
    document.querySelectorAll('.staff-tab').forEach((button) => {
      if (button.dataset.tab === name) button.setAttribute('aria-current', 'page');
      else button.removeAttribute('aria-current');
    });
    ['today', 'checkin', 'analytics', 'payments', 'settings', 'accounts'].forEach((section) => {
      $('panel-' + section).hidden = section !== name;
    });
    if (name !== 'checkin') stopCamera();
    if (name === 'today') loadDay();
    if (name === 'analytics') loadMonth();
    if (name === 'settings') loadPolicy();
    if (name === 'payments') loadReconciliation();
    if (name === 'accounts') { loadAccounts(); loadSystem(); }
  }

  async function openWorkspace(account) {
    user = account;
    offices = (await api.get('/api/offices')).offices;
    $('login-view').hidden = true;
    $('workspace').hidden = false;
    $('session-info').hidden = false;
    $('session-label').textContent = account.username;
    $('role-label').textContent = ROLE_NAMES[account.role];
    $('analytics-tab').hidden = !['DISTRICT_HEAD', 'ADMIN'].includes(account.role);
    $('checkin-tab').hidden = !['STAFF', 'DISTRICT_HEAD'].includes(account.role);
    $('payments-tab').hidden = !['DISTRICT_HEAD', 'TREASURER'].includes(account.role);
    $('treasurer-tools').hidden = account.role !== 'TREASURER';
    $('walkin-section').hidden = !['STAFF', 'DISTRICT_HEAD'].includes(account.role);
    $('accounts-tab').hidden = account.role !== 'ADMIN';
    $('settings-tab').hidden = account.role !== 'DISTRICT_HEAD';
    if (account.role === 'ADMIN') {
      $('workspace-title').textContent = 'Metro Manila operations';
      $('office-picker').hidden = false;
      clear($('office-select'));
      clear($('new-office'));
      offices.forEach((office) => {
        $('office-select').append(el('option', { value: office.id, text: office.name }));
        $('new-office').append(el('option', { value: office.id, text: office.name }));
      });
    } else {
      $('workspace-title').textContent = officeName(account.officeId);
      $('office-picker').hidden = true;
    }
    const [services, industries] = await Promise.all([api.get('/api/services'), api.get('/api/industries')]);
    clear($('walkin-service'));
    clear($('walkin-industry'));
    clear($('checklist-service'));
    services.services.forEach((service) => $('walkin-service').append(el('option', { value: service.id, text: service.name })));
    services.services.forEach((service) => $('checklist-service').append(el('option', { value: service.id, text: service.name })));
    industries.industries.forEach((industry) => $('walkin-industry').append(el('option', { value: industry.id, text: industry.name })));
    $('office-subtitle').textContent = account.role === 'ADMIN' ? 'Choose an office to see its live appointments.' : 'Bookings and arrivals for your office.';
    const config = await api.get('/api/config');
    $('day-select').value = config.today;
    $('month-select').value = config.today.slice(0, 7);
    $('reconcile-date').value = config.today;
    $('simulation-date').value = config.today;
    const scanCode = new URLSearchParams(location.search).get('code');
    if (scanCode) {
      $('checkin-code').value = scanCode;
      history.replaceState(null, '', location.pathname);
      if (['STAFF', 'DISTRICT_HEAD'].includes(account.role)) { showTab('checkin'); lookup(); }
      else showTab('today');
    } else showTab('today');
  }

  async function loadDay() {
    if (!user || !officeId()) return;
    hideAlert($('daily-error'));
    try {
      const day = $('day-select').value;
      const result = await staffRequest('/api/staff/bookings' + officeParam() + '&from=' + day + '&to=' + day);
      const bookings = result.bookings;
      clear($('daily-metrics')).append(
        metric('Scheduled', bookings.filter((a) => !a.walkIn).length, 'For ' + formatDate(day)),
        metric('Expected', bookings.filter((a) => a.status === 'CONFIRMED' && !a.acceptedAt && !a.walkIn).length, 'Awaiting arrival'),
        metric('Checked in', bookings.filter((a) => !!a.acceptedAt).length, 'Accepted at office'),
        metric('No-shows', bookings.filter((a) => a.status === 'NO_SHOW').length, 'Marked by staff'),
        metric('Walk-ins', bookings.filter((a) => a.walkIn).length, 'Added at office'),
        metric('Online paid', bookings.filter((a) => a.payment && a.payment.status === 'PAID').length, 'Demo records'));
      const rows = clear($('daily-rows'));
      bookings.forEach((a) => {
        const status = a.status === 'CANCELLED' ? 'Cancelled' : a.status === 'NO_SHOW' ? 'No-show' : a.demoRecord ? 'Demo booking' : a.walkIn ? 'Walk-in' : a.acceptedAt ? 'Checked in' : 'Expected';
        const payment = a.counterPayment ? 'City hall verified' : a.payment && a.payment.status === 'PAID'
          ? 'Online demo paid' : 'No verified payment';
        const button = el('button', { type: 'button', class: 'staff-ref-button', text: a.referenceNo,
          onclick: () => { $('checkin-code').value = a.referenceNo; showTab('checkin'); lookup(); } });
        rows.append(el('tr', {},
          el('td', {}, el('strong', { text: a.walkIn ? 'Walk-in ' + formatTime(a.time) : formatTime(a.time) }), button),
          el('td', {}, el('strong', { text: a.ownerName }), el('small', { text: a.businessName })),
          el('td', { text: a.serviceName }),
          el('td', {}, el('span', { class: 'staff-status ' + (a.acceptedAt ? 'is-arrived' : ''), text: status }),
            a.queueNumber ? el('small', { text: 'Queue #' + a.queueNumber }) : null),
          el('td', { text: payment }),
          el('td', {}, ['STAFF', 'DISTRICT_HEAD'].includes(user.role) && a.status === 'CONFIRMED' && !a.acceptedAt && !a.walkIn
            ? el('button', { type: 'button', class: 'staff-ref-button', text: 'Mark no-show', onclick: () => markNoShow(a) }) : '—')));
      });
      $('daily-empty').hidden = bookings.length > 0;
    } catch (error) { showAlert($('daily-error'), error.message, 'error'); }
  }

  async function markNoShow(a) {
    if (!window.confirm('Mark ' + a.ownerName + ' as a no-show? The booking will be closed.')) return;
    try {
      await staffRequest('/api/staff/bookings/' + encodeURIComponent(a.referenceNo) + '/no-show', {});
      loadDay();
    } catch (error) { showAlert($('daily-error'), error.message, 'error'); }
  }

  function parseCode(value) {
    const text = value.trim();
    try {
      const url = new URL(text);
      return url.searchParams.get('code') || text;
    } catch (error) { return text; }
  }

  async function lookup() {
    hideAlert($('checkin-error'));
    hideAlert($('checkin-success'));
    $('checkin-result').hidden = true;
    const value = parseCode($('checkin-code').value);
    if (!value) return;
    const path = value.startsWith('BPL-') ? '/api/staff/bookings/' : '/api/staff/checkin/';
    try {
      const result = await staffRequest(path + encodeURIComponent(value));
      renderCheckin(result.appointment);
    } catch (error) { showAlert($('checkin-error'), error.message, 'error'); }
  }

  function renderCheckin(a) {
    current = a;
    $('checkin-result').hidden = false;
    $('checkin-ref').textContent = a.referenceNo;
    $('checkin-status').textContent = a.status === 'CANCELLED' ? 'Cancelled' : a.status === 'NO_SHOW' ? 'No-show' : a.acceptedAt ? 'Already checked in' : 'Confirmed';
    $('checkin-status').className = 'badge badge-' + a.status;
    const details = clear($('checkin-details'));
    [['Applicant', a.ownerName], ['Business', a.businessName], ['Service', a.serviceName],
      ['Appointment', formatDate(a.date) + ' at ' + formatTime(a.time)], ['Office', a.officeName]].forEach(([label, value]) => {
      details.append(el('dt', { text: label }), el('dd', { text: value }));
    });
    if (a.queueNumber) details.append(el('dt', { text: 'Office queue' }), el('dd', { text: '#' + a.queueNumber }));
    const p = a.payment;
    $('checkin-payment').textContent = p && p.status === 'PAID'
      ? 'Demo online payment recorded · Receipt ' + p.receiptNo + ' · ' + peso(p.amount)
      : 'No verified payment is recorded. Check with the treasurer before issuing an official receipt.';
    $('accept-booking').disabled = a.status !== 'CONFIRMED' || !!a.acceptedAt;
    $('accept-booking').textContent = a.acceptedAt ? 'Already accepted' : 'Accept at office';
    $('head-move').hidden = user.role !== 'DISTRICT_HEAD' || a.status !== 'CONFIRMED' || !!a.acceptedAt || a.walkIn;
    if (!$('head-move').hidden) {
      $('head-move-date').value = a.date;
      loadMoveSlots();
    }
  }

  async function loadMoveSlots() {
    if (!current || $('head-move').hidden) return;
    hideAlert($('head-move-message'));
    const select = clear($('head-move-time'));
    try {
      const result = await api.get('/api/slots?officeId=' + encodeURIComponent(current.officeId)
        + '&date=' + encodeURIComponent($('head-move-date').value));
      result.slots.filter((slot) => slot.available).forEach((slot) =>
        select.append(el('option', { value: slot.time, text: formatTime(slot.time) + ' · ' + slot.remaining + ' left' })));
      if (!select.options.length) showAlert($('head-move-message'), 'No open times on this date. Choose another day.', 'error');
    } catch (error) { showAlert($('head-move-message'), error.message, 'error'); }
  }

  function renderChart(days) {
    const max = Math.max(1, ...days.map((day) => day.booked));
    const chart = clear($('traffic-chart'));
    days.forEach((day) => {
      const booked = day.booked ? Math.max(2, Math.round(day.booked / max * 150)) : 0;
      const arrived = day.checkedIn ? Math.max(2, Math.round(day.checkedIn / max * 150)) : 0;
      chart.append(el('div', { class: 'staff-chart-day', title: day.date + ': ' + day.booked + ' bookings, ' + day.checkedIn + ' checked in' },
        el('div', { class: 'staff-chart-bars' },
          el('span', { class: 'staff-bar staff-bar-booked', style: 'height:' + booked + 'px' }),
          el('span', { class: 'staff-bar staff-bar-arrived', style: 'height:' + arrived + 'px' })),
        el('span', { class: 'staff-chart-label', text: String(Number(day.date.slice(-2))) })));
    });
    chart.setAttribute('aria-label', days.map((day) => day.date + ': ' + day.booked + ' bookings and ' + day.checkedIn + ' check-ins').join('; '));
  }

  async function loadMonth() {
    if (!user || user.role === 'STAFF') return;
    hideAlert($('analytics-error'));
    try {
      const month = $('month-select').value;
      const result = await staffRequest('/api/staff/analytics' + officeParam() + '&month=' + month);
      const days = result.days;
      clear($('monthly-metrics')).append(
        metric('Booked', result.totalBookings, 'Across ' + month),
        metric('Checked in', result.totalCheckIns, 'Office arrivals'),
        metric('No-shows', days.reduce((sum, day) => sum + day.noShows, 0), 'Marked by staff'),
        metric('Walk-ins', days.reduce((sum, day) => sum + day.walkIns, 0), 'Added at office'),
        metric('Online paid', days.reduce((sum, day) => sum + day.paidOnline, 0), 'Demo payments'),
        metric('Busiest day', result.totalBookings
          ? days.reduce((best, day) => day.booked > best.booked ? day : best, days[0]).date.slice(-2)
          : '—', 'Day of month'));
      renderChart(days);
      const rows = clear($('traffic-rows'));
      days.forEach((day) => rows.append(el('tr', {}, el('th', { scope: 'row', text: formatDate(day.date) }),
        el('td', { text: day.booked }), el('td', { text: day.active }),
        el('td', { text: day.checkedIn }), el('td', { text: day.noShows }),
        el('td', { text: day.walkIns }), el('td', { text: day.paidOnline }))));
    } catch (error) { showAlert($('analytics-error'), error.message, 'error'); }
  }

  async function loadAccounts() {
    if (!user || user.role !== 'ADMIN') return;
    try {
      const result = await staffRequest('/api/staff/users');
      const rows = clear($('account-rows'));
      const selector = clear($('manage-username'));
      result.users.forEach((account) => rows.append(el('tr', {},
        el('th', { scope: 'row', text: account.username }), el('td', { text: ROLE_NAMES[account.role] }),
        el('td', { text: account.officeId ? officeName(account.officeId) : 'All offices' }),
        el('td', { text: account.active ? 'Active' : 'Disabled' }))));
      result.users.forEach((account) => selector.append(el('option', { value: account.username, text: account.username })));
    } catch (error) { showAlert($('account-message'), error.message, 'error'); }
  }

  async function loadSystem() {
    try {
      const result = await staffRequest('/api/staff/system');
      const details = clear($('system-status'));
      [['Staff login', result.staffLogin], ['Persistent storage', result.persistentStorage],
        ['Email notifications', result.emailNotifications], ['eGov verification', result.egovVerification],
        ['ID scanning', result.idScan], ['Demo seed', result.demoSeedEnabled]].forEach(([label, active]) =>
        details.append(el('dt', { text: label }), el('dd', { text: active ? 'Configured' : 'Off' })));
    } catch (error) { showAlert($('account-message'), error.message, 'error'); }
  }

  function showAffected(bookings) {
    const box = clear($('closure-affected'));
    box.hidden = bookings.length === 0;
    if (!bookings.length) return;
    box.append(el('strong', { text: bookings.length + ' existing booking(s) need contact and a new slot:' }));
    const list = el('ul');
    bookings.forEach((a) => list.append(el('li', {},
      el('span', { text: a.referenceNo + ' · ' + a.time + ' · ' + a.email + ' ' }),
      el('button', { type: 'button', class: 'staff-ref-button', text: 'Open to move', onclick: () => {
        $('checkin-code').value = a.referenceNo;
        showTab('checkin');
        lookup();
      } }))));
    box.append(list);
  }

  function showChecklist() {
    if (!currentPolicy) return;
    const selected = currentPolicy.checklists[$('checklist-service').value];
    $('checklist-documents').value = selected ? selected.documents.join('\n') : '';
    $('checklist-instructions').value = selected ? selected.instructions : '';
    $('checklist-updated').textContent = selected ? 'Last updated ' + formatDate(selected.updatedAt) : 'No office additions saved yet.';
  }

  function renderPolicy(policy) {
    currentPolicy = policy;
    $('schedule-times').value = policy.times.join(', ');
    $('schedule-capacity').value = policy.capacity;
    const list = clear($('closure-list'));
    if (!policy.closures.length) list.append(el('li', { text: 'No local closures set.' }));
    policy.closures.forEach((closure) => list.append(el('li', {},
      el('span', { text: formatDate(closure.date) + ' · ' + closure.reason + ' (' + closure.kind.replace('_', ' ').toLowerCase() + ')' }),
      el('button', { type: 'button', class: 'staff-ref-button', text: 'Reopen day', onclick: async () => {
        if (!window.confirm('Reopen ' + formatDate(closure.date) + ' for bookings?')) return;
        try {
          const result = await staffRequest('/api/staff/policy/closure', { officeId: officeId(),
            date: closure.date, action: 'remove' });
          renderPolicy(result.policy);
          showAlert($('closure-message'), 'Day reopened for new bookings.', 'ok');
        } catch (error) { showAlert($('closure-message'), error.message, 'error'); }
      } }))));
    showChecklist();
  }

  async function loadPolicy() {
    if (!user || user.role !== 'DISTRICT_HEAD') return;
    hideAlert($('settings-error'));
    try {
      const result = await staffRequest('/api/staff/policy' + officeParam());
      renderPolicy(result.policy);
    } catch (error) { showAlert($('settings-error'), error.message, 'error'); }
  }

  async function loadReconciliation() {
    if (!user || !['DISTRICT_HEAD', 'TREASURER'].includes(user.role)) return;
    hideAlert($('reconcile-error'));
    try {
      const day = $('reconcile-date').value;
      const result = await staffRequest('/api/staff/reconciliation' + officeParam() + '&from=' + day + '&to=' + day);
      const bookings = result.bookings;
      clear($('reconcile-metrics')).append(
        metric('City hall verified', bookings.filter((a) => !!a.counterPayment).length, 'Official receipt recorded'),
        metric('Online demo paid', bookings.filter((a) => a.payment && a.payment.status === 'PAID').length, 'No real money moved'),
        metric('No verified payment', bookings.filter((a) => !a.counterPayment).length, 'Check with treasurer'));
      const rows = clear($('reconcile-rows'));
      bookings.forEach((a) => rows.append(el('tr', {},
        el('td', {}, el('strong', { text: a.referenceNo }), el('small', { text: a.ownerName + ' · ' + a.businessName })),
        el('td', { text: a.payment && a.payment.status === 'PAID' ? 'Paid (demo)' : 'None' }),
        el('td', { text: a.counterPayment ? peso(a.counterPayment.amount) : 'None' }),
        el('td', { text: a.counterPayment ? a.counterPayment.officialReceiptNo : '—' }),
        el('td', { text: a.counterPayment ? a.counterPayment.recordedBy : '—' }))));
    } catch (error) { showAlert($('reconcile-error'), error.message, 'error'); }
  }

  function stopCamera() {
    if (scanTimer) clearTimeout(scanTimer);
    scanTimer = null;
    if (cameraStream) cameraStream.getTracks().forEach((track) => track.stop());
    cameraStream = null;
    $('camera-video').srcObject = null;
    $('camera-box').hidden = true;
    $('camera-toggle').textContent = 'Scan with camera';
  }

  async function startCamera() {
    if (!('BarcodeDetector' in window)) throw new Error('Camera QR scanning is unavailable in this browser. Use your phone camera to open the QR link or enter the booking reference.');
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) throw new Error('Camera access is unavailable. Enter the booking reference instead.');
    const detector = new BarcodeDetector({ formats: ['qr_code'] });
    cameraStream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } });
    $('camera-video').srcObject = cameraStream;
    $('camera-box').hidden = false;
    $('camera-toggle').textContent = 'Stop camera';
    const scan = async () => {
      if (!cameraStream) return;
      try {
        const codes = await detector.detect($('camera-video'));
        if (codes.length) {
          $('checkin-code').value = parseCode(codes[0].rawValue);
          stopCamera();
          await lookup();
          return;
        }
      } catch (error) { /* camera may not have a frame yet */ }
      scanTimer = setTimeout(scan, 250);
    };
    scanTimer = setTimeout(scan, 250);
  }

  $('login-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    hideAlert($('login-error'));
    const button = event.target.querySelector('button[type=submit]');
    button.disabled = true;
    try {
      const response = await api.post('/api/staff/login', { username: $('username').value, password: $('password').value });
      token = response.token;
      sessionStorage.setItem('metromaya.staffToken', token);
      $('password').value = '';
      await openWorkspace(response.user);
    } catch (error) { showAlert($('login-error'), error.message, 'error'); }
    finally { button.disabled = false; }
  });
  $('sign-out').addEventListener('click', signOut);
  document.querySelectorAll('.staff-tab').forEach((button) => button.addEventListener('click', () => showTab(button.dataset.tab)));
  $('date-form').addEventListener('submit', (event) => { event.preventDefault(); loadDay(); });
  $('walkin-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    hideAlert($('walkin-message'));
    const button = event.target.querySelector('button[type=submit]');
    button.disabled = true;
    try {
      const result = await staffRequest('/api/staff/walkins', { officeId: officeId(),
        serviceId: $('walkin-service').value, industryId: $('walkin-industry').value,
        employees: $('walkin-employees').value, businessName: $('walkin-business').value,
        businessAddress: $('walkin-address').value, ownerName: $('walkin-owner').value,
        email: $('walkin-email').value, phone: $('walkin-phone').value });
      showAlert($('walkin-message'), 'Walk-in added · Queue #' + result.appointment.queueNumber + ' · ' + result.appointment.referenceNo, 'ok');
      event.target.reset();
      loadDay();
    } catch (error) { showAlert($('walkin-message'), error.message, 'error'); }
    finally { button.disabled = false; }
  });
  $('month-form').addEventListener('submit', (event) => { event.preventDefault(); loadMonth(); });
  $('reconcile-date-form').addEventListener('submit', (event) => { event.preventDefault(); loadReconciliation(); });
  $('office-select').addEventListener('change', () => { loadDay(); loadMonth(); });
  $('checkin-form').addEventListener('submit', (event) => { event.preventDefault(); lookup(); });
  $('head-move-date').addEventListener('change', loadMoveSlots);
  $('head-move-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    hideAlert($('head-move-message'));
    if (!current || !window.confirm('Move this booking and notify the applicant if email is configured?')) return;
    try {
      const result = await staffRequest('/api/staff/bookings/' + encodeURIComponent(current.referenceNo) + '/move',
        { date: $('head-move-date').value, time: $('head-move-time').value });
      renderCheckin(result.appointment);
      showAlert($('head-move-message'), result.emailSent
        ? 'Booking moved. The applicant was emailed.' : 'Booking moved. No email was sent; contact the applicant directly.', 'ok');
      loadDay();
    } catch (error) { showAlert($('head-move-message'), error.message, 'error'); }
  });
  $('camera-toggle').addEventListener('click', async () => {
    if (cameraStream) { stopCamera(); return; }
    try { await startCamera(); } catch (error) { stopCamera(); showAlert($('checkin-error'), error.message, 'error'); }
  });
  $('accept-booking').addEventListener('click', async () => {
    if (!current || !window.confirm('Confirm the applicant’s identity and accept this booking at the office?')) return;
    try {
      const result = await staffRequest('/api/staff/bookings/' + encodeURIComponent(current.referenceNo) + '/accept', {});
      renderCheckin(result.appointment);
      showAlert($('checkin-success'), 'Booking accepted at the office.', 'ok');
      loadDay();
    } catch (error) { showAlert($('checkin-error'), error.message, 'error'); }
  });
  $('new-role').addEventListener('change', () => { $('new-office').disabled = $('new-role').value === 'ADMIN'; });
  $('manage-action').addEventListener('change', () => {
    const reset = $('manage-action').value === 'reset-password';
    $('manage-password-field').hidden = !reset;
    $('manage-password').required = reset;
  });
  $('manage-account-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    hideAlert($('manage-account-message'));
    const username = $('manage-username').value;
    const action = $('manage-action').value;
    if (!username || !window.confirm(action.replace('-', ' ') + ' for ' + username + '?')) return;
    try {
      await staffRequest('/api/staff/users/' + encodeURIComponent(username) + '/' + action,
        action === 'reset-password' ? { password: $('manage-password').value } : {});
      $('manage-password').value = '';
      showAlert($('manage-account-message'), 'Account updated. Previous sessions no longer work after a password reset or disable.', 'ok');
      loadAccounts();
    } catch (error) { showAlert($('manage-account-message'), error.message, 'error'); }
  });
  $('schedule-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    hideAlert($('schedule-message'));
    try {
      const result = await staffRequest('/api/staff/policy/schedule', { officeId: officeId(),
        times: $('schedule-times').value, capacity: $('schedule-capacity').value });
      renderPolicy(result.policy);
      showAlert($('schedule-message'), 'Schedule saved. Existing appointments retain their booked time.', 'ok');
    } catch (error) { showAlert($('schedule-message'), error.message, 'error'); }
  });
  $('closure-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    hideAlert($('closure-message'));
    try {
      const result = await staffRequest('/api/staff/policy/closure', { officeId: officeId(),
        date: $('closure-date').value, kind: $('closure-kind').value, reason: $('closure-reason').value });
      renderPolicy(result.policy);
      showAffected(result.affectedBookings);
      showAlert($('closure-message'), 'Closure saved. ' + result.emailsSent + ' applicant email(s) sent. Review the affected bookings below.', 'ok');
    } catch (error) { showAlert($('closure-message'), error.message, 'error'); }
  });
  $('checklist-service').addEventListener('change', showChecklist);
  $('checklist-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    hideAlert($('checklist-message'));
    try {
      const result = await staffRequest('/api/staff/policy/checklist', { officeId: officeId(),
        serviceId: $('checklist-service').value, documents: $('checklist-documents').value,
        instructions: $('checklist-instructions').value });
      renderPolicy(result.policy);
      showAlert($('checklist-message'), 'Checklist saved with today’s update date.', 'ok');
    } catch (error) { showAlert($('checklist-message'), error.message, 'error'); }
  });
  $('account-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    hideAlert($('account-message'));
    try {
      const role = $('new-role').value;
      await staffRequest('/api/staff/users', { username: $('new-username').value, password: $('new-password').value,
        role, officeId: role === 'ADMIN' ? '' : $('new-office').value });
      showAlert($('account-message'), 'Account created. Share its temporary password through a secure channel.', 'ok');
      $('new-username').value = '';
      $('new-password').value = '';
      loadAccounts();
    } catch (error) { showAlert($('account-message'), error.message, 'error'); }
  });
  $('seed-heads').addEventListener('click', async () => {
    hideAlert($('seed-message'));
    try {
      const result = await staffRequest('/api/staff/seed-heads', {});
      const box = clear($('seed-result'));
      if (!result.issued.length) { showAlert($('seed-message'), 'Every office already has a district head account.', 'ok'); return; }
      box.hidden = false;
      box.append(el('p', { text: 'Save these credentials now. Passwords will not be shown again.' }));
      result.issued.forEach((item) => box.append(el('div', { class: 'staff-credential' },
        el('strong', { text: officeName(item.officeId) }), el('code', { text: item.username }),
        el('code', { text: item.temporaryPassword }))));
      showAlert($('seed-message'), result.issued.length + ' district head accounts created.', 'ok');
      loadAccounts();
    } catch (error) { showAlert($('seed-message'), error.message, 'error'); }
  });
  $('simulation-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    hideAlert($('simulation-message'));
    const date = $('simulation-date').value;
    if (!window.confirm('Fill all remaining ' + officeName(officeId()) + ' slots on ' + formatDate(date) + ' with demo bookings?')) return;
    try {
      const result = await staffRequest('/api/staff/simulate-full-day', { officeId: officeId(), date });
      showAlert($('simulation-message'), result.created + ' demo bookings created. The day is fully booked.', 'ok');
      $('day-select').value = date;
      loadDay();
    } catch (error) { showAlert($('simulation-message'), error.message, 'error'); }
  });
  $('send-reminders').addEventListener('click', async () => {
    hideAlert($('reminder-message'));
    try {
      const result = await staffRequest('/api/staff/send-reminders', {});
      showAlert($('reminder-message'), result.configured
        ? result.emailsSent + ' reminder email(s) sent. Already sent reminders were skipped.'
        : 'Email is not configured. Set Resend, PUBLIC_BASE_URL, and MANAGEMENT_LINK_KEY on the server.',
      result.configured ? 'ok' : 'error');
    } catch (error) { showAlert($('reminder-message'), error.message, 'error'); }
  });
  async function saveCounterPayment(referenceNo, amount, officialReceiptNo) {
    return staffRequest('/api/staff/bookings/' + encodeURIComponent(referenceNo.trim().toUpperCase()) + '/counter-payment',
      { amount, officialReceiptNo });
  }
  $('counter-payment-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    hideAlert($('counter-message'));
    if (!window.confirm('Have you verified this payment and official receipt in the treasurer’s records?')) return;
    try {
      await saveCounterPayment($('counter-ref').value, $('counter-amount').value, $('counter-receipt').value);
      showAlert($('counter-message'), 'City hall payment verified and recorded.', 'ok');
      event.target.reset();
      loadReconciliation();
    } catch (error) { showAlert($('counter-message'), error.message, 'error'); }
  });
  $('import-receipts').addEventListener('click', async () => {
    hideAlert($('import-message'));
    const file = $('receipt-csv').files[0];
    if (!file) { showAlert($('import-message'), 'Choose a CSV file first.', 'error'); return; }
    if (file.size > 100000) { showAlert($('import-message'), 'Use a CSV under 100 KB.', 'error'); return; }
    const lines = (await file.text()).replace(/\r/g, '').trim().split('\n');
    if (lines.shift().trim() !== 'referenceNo,amount,officialReceiptNo' || lines.length > 200) {
      showAlert($('import-message'), 'Use the header referenceNo,amount,officialReceiptNo and at most 200 rows.', 'error');
      return;
    }
    if (!window.confirm('Import ' + lines.length + ' treasurer-verified receipt records?')) return;
    let saved = 0;
    const errors = [];
    for (const [index, line] of lines.entries()) {
      const fields = line.split(',').map((value) => value.trim());
      if (fields.length !== 3) { errors.push('Row ' + (index + 2) + ': expected three fields'); continue; }
      try { await saveCounterPayment(fields[0], fields[1], fields[2]); saved++; }
      catch (error) { errors.push('Row ' + (index + 2) + ': ' + error.message); }
    }
    showAlert($('import-message'), saved + ' saved; ' + errors.length + ' failed.' +
      (errors.length ? ' ' + errors.slice(0, 5).join(' · ') : ''), errors.length ? 'error' : 'ok');
    loadReconciliation();
  });

  if (token) staffRequest('/api/staff/me').then((result) => openWorkspace(result.user)).catch(() => signOut());
})();
