(function () {
  const $ = (id) => document.getElementById(id);
  let current = null;       // the booking currently shown
  let currentToken = null;  // never part of the printed confirmation or public QR
  let config = null;        // today's date and booking window
  let newDate = '';
  let newTime = '';

  function addRow(dl, label, value) {
    dl.append(el('dt', { text: label }), el('dd', { text: value }));
  }

  function render(a) {
    current = a;
    $('result').hidden = false;
    $('result-ref').textContent = a.referenceNo;
    const badge = $('result-status');
    badge.textContent = a.status === 'CANCELLED' ? 'Cancelled' : a.acceptedAt ? 'Accepted at office' : 'Confirmed';
    badge.className = 'badge badge-' + a.status;

    const dl = clear($('result-summary'));
    addRow(dl, 'Service', a.serviceName);
    addRow(dl, 'Business', a.businessName + ' (' + a.industryName.toLowerCase() + ', ' + a.quote.size.label.toLowerCase() + ')');
    addRow(dl, 'Address', a.businessAddress);
    addRow(dl, 'Office', a.officeName + ', ' + a.officeAddress);
    addRow(dl, 'Date', formatDate(a.date));
    addRow(dl, 'Time', formatTime(a.time));
    addRow(dl, 'Owner', a.ownerName);
    renderCheckInCode($('result-checkin'), a);
    // fees follow the city named in the business address (the booking form always puts it there)
    const city = detectCity(a.businessAddress);
    const quote = city ? scaleQuote(a.quote, city.rate) : a.quote;
    renderFee($('result-fee'), quote.fee);
    renderInspections($('result-insp'), quote.inspections);
    renderDocs($('result-docs'), a.quote.requirements);
    $('result-print').textContent = printActionLabel(a);
    renderPayment($('result-pay'), a, (updated) => {
      current = updated;
      $('result-print').textContent = printActionLabel(updated);
    }, currentToken);

    $('actions').hidden = a.status !== 'CONFIRMED' || !!a.acceptedAt;
    $('reschedule-box').hidden = true;
    hideAlert($('result-alert'));
  }

  $('result-print').addEventListener('click', () => { if (current) printBooking(current); });

  async function lookup(ref, token) {
    hideAlert($('lookup-alert'));
    $('result').hidden = true;
    current = null;
    currentToken = null;
    try {
      const { appointment } = await api.managedGet('/api/appointments/' + encodeURIComponent(ref.trim()), token);
      currentToken = token;
      render(appointment);
    } catch (e) {
      showAlert($('lookup-alert'), e.message, 'error');
    }
  }

  $('lookup-form').addEventListener('submit', (e) => {
    e.preventDefault();
    const ref = $('ref').value.trim();
    const token = $('access-code').value.trim();
    if (ref && token) lookup(ref, token);
  });

  // ---------- cancel ----------

  $('cancel-btn').addEventListener('click', async () => {
    if (!current || !window.confirm('Cancel this booking? This cannot be undone.')) return;
    const btn = $('cancel-btn');
    btn.disabled = true;
    try {
      const { appointment } = await api.managedPost('/api/appointments/' + encodeURIComponent(current.referenceNo) + '/cancel', {}, currentToken);
      render(appointment);
      showAlert($('result-alert'), 'Your booking has been cancelled.', 'ok');
    } catch (e) {
      showAlert($('result-alert'), e.message, 'error');
    } finally {
      btn.disabled = false;
    }
  });

  // ---------- move to another time ----------

  $('reschedule-toggle').addEventListener('click', async () => {
    const box = $('reschedule-box');
    box.hidden = !box.hidden;
    if (box.hidden) return;
    if (!config) {
      try { config = await api.get('/api/config'); }
      catch (e) { showAlert($('new-slot-alert'), e.message, 'error'); return; }
    }
    $('new-date-hint').textContent = 'Pick a weekday up to ' + config.maxMonthsAhead + ' months ahead, then a time.';
    newDate = '';
    newTime = '';
    $('new-day-status').textContent = 'Choose a day on the calendar to see its open times.';
    mountCalendar($('new-cal'), { officeId: current.officeId, config, onSelect: pickNewDate });
    clear($('new-slots'));
    hideAlert($('new-slot-alert'));
    $('reschedule-confirm').disabled = true;
  });

  async function pickNewDate(iso, day) {
    newDate = iso;
    newTime = '';
    $('new-day-status').textContent = dayStatusText(iso, day);
    $('reschedule-confirm').disabled = true;
    if (!newDate) { clear($('new-slots')); return; }
    const closed = closedReason(newDate, config);
    if (closed) {
      clear($('new-slots'));
      showAlert($('new-slot-alert'), closed, 'error');
      return;
    }
    await loadSlots($('new-slots'), $('new-slot-alert'), current.officeId, newDate, newTime, (time) => {
      newTime = time;
      $('reschedule-confirm').disabled = false;
    });
  }

  $('reschedule-confirm').addEventListener('click', async () => {
    const btn = $('reschedule-confirm');
    btn.disabled = true;
    try {
      const { appointment } = await api.managedPost(
        '/api/appointments/' + encodeURIComponent(current.referenceNo) + '/reschedule',
        { date: newDate, time: newTime }, currentToken);
      render(appointment);
      showAlert($('result-alert'), 'Your booking has moved to the new time.', 'ok');
    } catch (e) {
      showAlert($('new-slot-alert'), e.message, 'error');
      btn.disabled = false;
    }
  });

  // ---------- start ----------

  const ref = new URLSearchParams(location.search).get('ref');
  if (ref) {
    $('ref').value = ref;
    const incoming = sessionStorage.getItem('metromaya.incomingAccess');
    if (incoming) {
      sessionStorage.removeItem('metromaya.incomingAccess');
      $('access-code').value = incoming;
      lookup(ref, incoming);
    }
  }
})();
