(function () {
  const $ = (id) => document.getElementById(id);
  let current = null;

  async function officeRequest(path, method) {
    const key = $('office-key').value;
    const res = await fetch(path, {
      method,
      headers: { 'X-Office-Key': key },
      cache: 'no-store',
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || 'Could not check the booking.');
    return data.appointment;
  }

  function row(dl, label, value) {
    dl.append(el('dt', { text: label }), el('dd', { text: value }));
  }

  function render(a) {
    current = a;
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
    try {
      render(await officeRequest('/api/office/' + encodeURIComponent($('office-ref').value.trim().toUpperCase()), 'GET'));
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
      render(await officeRequest('/api/office/' + encodeURIComponent(current.referenceNo) + '/accept', 'POST'));
      showAlert($('office-success'), 'Booking accepted at the office. Payment status is shown above.', 'ok');
    } catch (e) {
      showAlert($('office-error'), e.message, 'error');
      button.disabled = false;
    }
  });

  const ref = new URLSearchParams(location.search).get('ref');
  if (ref) $('office-ref').value = ref.toUpperCase();
})();
