(function () {
  const $ = (id) => document.getElementById(id);
  let current = null;       // the booking currently shown
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
    badge.textContent = a.status === 'CONFIRMED' ? 'Confirmed' : 'Cancelled';
    badge.className = 'badge badge-' + a.status;

    const dl = clear($('result-summary'));
    addRow(dl, 'Service', a.serviceName);
    addRow(dl, 'Business', a.businessName + ' (' + a.industryName.toLowerCase() + ', ' + a.quote.size.label.toLowerCase() + ')');
    addRow(dl, 'Address', a.businessAddress);
    addRow(dl, 'Office', a.officeName + ', ' + a.officeAddress);
    addRow(dl, 'Date', formatDate(a.date));
    addRow(dl, 'Time', formatTime(a.time));
    addRow(dl, 'Owner', a.ownerName);
    // fees follow the city named in the business address (the booking form always puts it there)
    const city = detectCity(a.businessAddress);
    const quote = city ? scaleQuote(a.quote, city.rate) : a.quote;
    renderFee($('result-fee'), quote.fee);
    renderInspections($('result-insp'), quote.inspections);
    renderDocs($('result-docs'), a.quote.requirements);

    $('actions').hidden = a.status !== 'CONFIRMED';
    $('reschedule-box').hidden = true;
    hideAlert($('result-alert'));
  }

  async function lookup(ref) {
    hideAlert($('lookup-alert'));
    $('result').hidden = true;
    try {
      const { appointment } = await api.get('/api/appointments/' + encodeURIComponent(ref.trim()));
      render(appointment);
    } catch (e) {
      showAlert($('lookup-alert'), e.message, 'error');
    }
  }

  $('lookup-form').addEventListener('submit', (e) => {
    e.preventDefault();
    const ref = $('ref').value.trim();
    if (ref) lookup(ref);
  });

  // ---------- cancel ----------

  $('cancel-btn').addEventListener('click', async () => {
    if (!current || !window.confirm('Cancel this booking? This cannot be undone.')) return;
    const btn = $('cancel-btn');
    btn.disabled = true;
    try {
      const { appointment } = await api.post('/api/appointments/' + encodeURIComponent(current.referenceNo) + '/cancel');
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
    const input = $('new-date');
    input.min = config.today;
    input.max = addDaysISO(config.today, config.maxDaysAhead);
    newDate = '';
    newTime = '';
    input.value = '';
    clear($('new-slots'));
    hideAlert($('new-slot-alert'));
    $('reschedule-confirm').disabled = true;
  });

  $('new-date').addEventListener('change', async (e) => {
    newDate = e.target.value;
    newTime = '';
    $('reschedule-confirm').disabled = true;
    if (!newDate) { clear($('new-slots')); return; }
    if (isWeekend(newDate)) {
      clear($('new-slots'));
      showAlert($('new-slot-alert'), 'Offices are closed on weekends. Choose a weekday.', 'error');
      return;
    }
    await loadSlots($('new-slots'), $('new-slot-alert'), current.officeId, newDate, newTime, (time) => {
      newTime = time;
      $('reschedule-confirm').disabled = false;
    });
  });

  $('reschedule-confirm').addEventListener('click', async () => {
    const btn = $('reschedule-confirm');
    btn.disabled = true;
    try {
      const { appointment } = await api.post(
        '/api/appointments/' + encodeURIComponent(current.referenceNo) + '/reschedule',
        { date: newDate, time: newTime });
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
    lookup(ref);
  }
})();
