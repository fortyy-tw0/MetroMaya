(function () {
  const $ = (id) => document.getElementById(id);
  let current = null;
  let recoveredToken = null;

  async function officeRequest(path, method) {
    const key = $('office-key').value;
    const res = await fetch(path, {
      method,
      headers: { 'X-Office-Key': key },
      cache: 'no-store',
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || 'Could not check the booking.');
    return data;
  }

  function row(dl, label, value) {
    dl.append(el('dt', { text: label }), el('dd', { text: value }));
  }

  function render(a) {
    current = a;
    $('office-ref').value = a.referenceNo;
    $('office-result').hidden = false;
    $('office-result-ref').textContent = a.referenceNo;
    const badge = $('office-result-status');
    badge.textContent = a.status === 'CANCELLED' ? 'Cancelled' : a.acceptedAt ? 'Accepted at office' : 'Confirmed';
    badge.className = 'badge badge-' + a.status;
    const dl = clear($('office-details'));
    row(dl, 'Service', a.serviceName);
    row(dl, 'Business', a.businessName);
    row(dl, 'Applicant', a.ownerName);
    row(dl, 'Office', a.officeName + ', ' + a.officeAddress);
    row(dl, 'Appointment', formatDate(a.date) + ' at ' + formatTime(a.time));
    row(dl, 'Office check-in', a.acceptedAt ? new Date(a.acceptedAt).toLocaleString('en-PH') : 'Not yet accepted');

    const p = a.payment;
    const payment = $('office-payment');
    clear(payment);
    if (p && p.status === 'PAID') {
      payment.className = 'office-payment office-payment-paid';
      payment.append(el('strong', { text: 'Demo payment recorded: ' + peso(p.amount) }),
        el('p', { text: 'Receipt ' + p.receiptNo + ' · ' + p.channel + ' · ' + new Date(p.paidAt).toLocaleString('en-PH') }),
        el('small', { text: 'Demo only: no money moved. This is not an official receipt.' }));
    } else if (p && p.status === 'PENDING') {
      payment.className = 'office-payment office-payment-pending';
      payment.append(el('strong', { text: 'Payment pending' }),
        el('p', { text: 'No completed payment is recorded. Estimated amount: ' + peso(a.amountDue) + '.' }));
    } else {
      payment.className = 'office-payment office-payment-pending';
      payment.append(el('strong', { text: 'No payment recorded' }),
        el('p', { text: 'Check payment with the applicant. Estimated amount: ' + peso(a.amountDue) + '.' }));
    }
    $('office-accept').disabled = a.status !== 'CONFIRMED' || !!a.acceptedAt;
    $('office-accept').textContent = a.acceptedAt ? 'Already accepted' : 'Accept booking at office';
  }

  async function load() {
    hideAlert($('office-error'));
    hideAlert($('office-success'));
    $('office-result').hidden = true;
    $('office-access-result').hidden = true;
    recoveredToken = null;
    try {
      const ref = $('office-ref').value.trim().toUpperCase();
      const code = new URLSearchParams(location.search).get('code');
      if (!ref && !code) throw new Error('Scan a check-in QR or enter a booking reference.');
      const path = ref ? '/api/office/' + encodeURIComponent(ref) : '/api/office/checkin/' + encodeURIComponent(code);
      render((await officeRequest(path, 'GET')).appointment);
    } catch (e) {
      showAlert($('office-error'), e.message, 'error');
    }
  }

  $('office-form').addEventListener('submit', (event) => { event.preventDefault(); load(); });
  $('office-refresh').addEventListener('click', load);
  $('office-accept').addEventListener('click', async () => {
    if (!current || current.status !== 'CONFIRMED' || current.acceptedAt) return;
    const button = $('office-accept');
    button.disabled = true;
    hideAlert($('office-error'));
    try {
      render((await officeRequest('/api/office/' + encodeURIComponent(current.referenceNo) + '/accept', 'POST')).appointment);
      showAlert($('office-success'), 'Booking accepted at the office. Payment status is shown above.', 'ok');
    } catch (e) {
      showAlert($('office-error'), e.message, 'error');
      button.disabled = false;
    }
  });

  $('office-recover').addEventListener('click', async () => {
    if (!current || !window.confirm('Verify the applicant’s identity before issuing a new private access code. The old code will stop working. Continue?')) return;
    hideAlert($('office-error'));
    const button = $('office-recover');
    button.disabled = true;
    try {
      const result = await officeRequest('/api/office/' + encodeURIComponent(current.referenceNo) + '/reset-management', 'POST');
      render(result.appointment);
      recoveredToken = result.managementToken;
      $('office-new-code').value = recoveredToken;
      $('office-access-result').hidden = false;
    } catch (e) {
      showAlert($('office-error'), e.message, 'error');
    } finally {
      button.disabled = false;
    }
  });

  $('office-copy-link').addEventListener('click', async () => {
    if (!current || !recoveredToken) return;
    try {
      await navigator.clipboard.writeText(location.origin + privateManagementUrl(current.referenceNo, recoveredToken));
      $('office-copy-status').textContent = 'Private link copied.';
    } catch (e) {
      $('office-copy-status').textContent = 'Copy failed. Give the code above instead.';
    }
  });

  const ref = new URLSearchParams(location.search).get('ref');
  if (ref) $('office-ref').value = ref.toUpperCase();
})();
