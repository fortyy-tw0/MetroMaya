(function () {
  const $ = (id) => document.getElementById(id);
  let current = null;       // the booking currently shown
  const resultPrint = document.getElementById('result-print');   // shown with the check-in code, at the top
  const resultStatus = document.getElementById('result-status');   // shown in the check-in slip's corner
  let currentToken = null;  // never part of the printed confirmation or public QR
  let config = null;        // today's date and booking window
  let newDate = '';
  let newTime = '';

  function addRow(dl, label, value) {
    dl.append(el('dt', { text: label }), el('dd', { text: value }));
  }

  // `savedAt`: shown from the copy saved on this device (no signal, or picked from the saved list) - read only
  function render(a, savedAt) {
    current = a;
    const saved = !!savedAt;
    if (!saved) { savedBookings.save(a); renderSaved(); }
    $('result').hidden = false;
    $('result-offline').hidden = !saved;
    if (saved) {
      $('result-offline').textContent = (navigator.onLine === false ? 'You’re offline. ' : '')
        + 'This is the copy saved on this device on ' + savedAtText(savedAt)
        + '. Show the QR code at the office as usual; to see changes, move, cancel or pay, enter your access code above.';
    }
    $('result-ref').textContent = a.referenceNo;
    const badge = resultStatus;
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
    renderCheckInCode($('result-checkin'), a, resultPrint);
    $('result-checkin').querySelector('.checkin-code').append(resultStatus);   // status sits in the slip's corner
    // fees follow the city named in the business address (the booking form always puts it there)
    const city = detectCity(a.businessAddress);
    const quote = city ? scaleQuote(a.quote, city.rate) : a.quote;
    renderFee($('result-fee'), quote.fee);
    renderInspections($('result-insp'), quote.inspections);
    renderDocs($('result-docs'), a.quote.requirements);
    resultPrint.textContent = printActionLabel(a);
    const paid = a.payment && a.payment.status === 'PAID';
    $('result-pay').hidden = saved && !paid;   // paying needs the server; a finished receipt still shows
    if (!$('result-pay').hidden) {
      renderPayment($('result-pay'), a, (updated) => {
        current = updated;
        savedBookings.save(updated);
        resultPrint.textContent = printActionLabel(updated);
      }, currentToken);
    }

    $('actions').hidden = saved || a.status !== 'CONFIRMED' || !!a.acceptedAt;
    $('head-actions').hidden = $('actions').hidden;
    $('reschedule-box').hidden = true;
    hideAlert($('result-alert'));
  }

  resultPrint.addEventListener('click', () => { if (current) printBooking(current); });

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
      // no signal: fall back to the copy saved on this device, if there is one
      const copy = e.offline && savedBookings.find(ref);
      if (copy) { render(copy.appointment, copy.savedAt); return; }
      showAlert($('lookup-alert'), e.message, 'error');
    }
  }

  // ---------- saved on this device ----------

  function renderSaved() {
    const items = savedBookings.list();
    $('saved-card').hidden = !items.length;
    const list = clear($('saved-list'));
    items.forEach(({ appointment: a, savedAt }) => {
      const open = el('button', { type: 'button', class: 'saved-open', onclick: () => {
        hideAlert($('lookup-alert'));
        currentToken = null;
        $('ref').value = a.referenceNo;
        render(a, savedAt);
        $('result').scrollIntoView({ behavior: 'smooth', block: 'start' });
      } },
        el('span', { class: 'saved-ref', text: a.referenceNo }),
        el('span', { class: 'saved-when', text: formatDate(a.date) + ', ' + formatTime(a.time) + (a.status === 'CANCELLED' ? ' (cancelled)' : '') }),
        el('span', { class: 'saved-office', text: a.officeName }));
      const remove = el('button', { type: 'button', class: 'saved-remove', 'aria-label': 'Remove ' + a.referenceNo + ' from this device', title: 'Remove from this device', onclick: () => {
        savedBookings.remove(a.referenceNo);
        if (current && current.referenceNo === a.referenceNo && !$('result-offline').hidden) $('result').hidden = true;
        renderSaved();
      } });
      remove.innerHTML = '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"/></svg>';
      list.append(el('li', null, open, remove));
    });
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
    box.scrollIntoView({ behavior: 'smooth', block: 'start' });   // the button is at the top, the calendar further down
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

  renderSaved();
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
