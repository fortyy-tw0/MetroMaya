// Demo checkout, opened by scanning the payment QR (often on another phone). It works by transaction id and
// only marks the demo payment paid: nothing is charged and no money moves.
(async function () {
  const box = document.getElementById('checkout');
  const txn = new URLSearchParams(location.search).get('txn') || '';
  const tag = () => el('span', { class: 'demo-tag', text: 'Demo payment · no money will move' });

  function show(p) {
    clear(box);
    if (p.status === 'PAID') {
      box.append(tag(),
        el('div', { class: 'checkout-done' },
          el('span', { class: 'checkout-check', 'aria-hidden': 'true' }),
          el('h1', { text: 'Payment sent' }),
          el('p', { class: 'checkout-amount', text: peso(p.amount) }),
          el('p', { class: 'hint', text: p.description }),
          el('p', { class: 'checkout-ref', text: p.receiptNo }),
          el('p', { class: 'hint', text: 'Go back to MetroMaya: your receipt is on your booking.' })));
      return;
    }
    if (!p.bookingActive) {
      box.append(tag(), el('p', { class: 'alert alert-error', text: 'This booking was cancelled, so it can’t be paid.' }));
      return;
    }
    const btn = el('button', { type: 'button', class: 'btn btn-primary btn-lg checkout-pay', text: 'Pay ' + peso(p.amount) + ' (demo)' });
    const alertNode = el('div', { class: 'alert alert-error', hidden: true, role: 'alert' });
    btn.addEventListener('click', async () => {
      btn.disabled = true;
      btn.textContent = 'Paying…';
      try {
        const { payment } = await api.post('/api/pay/' + encodeURIComponent(txn) + '/complete');
        show(payment);
      } catch (e) {
        showAlert(alertNode, e.message, 'error');
        btn.disabled = false;
        btn.textContent = 'Pay ' + peso(p.amount) + ' (demo)';
      }
    });
    box.append(tag(),
      el('p', { class: 'checkout-via', text: 'MetroMaya Pay · ' + p.channel }),
      el('dl', { class: 'checkout-rows' },
        el('dt', { text: 'Pay to' }), el('dd', { text: p.payTo }),
        el('dt', { text: 'For' }), el('dd', { text: p.description }),
        el('dt', { text: 'Booking' }), el('dd', { text: p.referenceNo })),
      el('div', { class: 'checkout-total' }, el('span', { text: 'Amount' }), el('b', { text: peso(p.amount) })),
      alertNode, btn);
  }

  try {
    const { payment } = await api.get('/api/pay/' + encodeURIComponent(txn));
    show(payment);
  } catch (e) {
    clear(box).append(tag(), el('p', { class: 'alert alert-error', text: e.message }));
  }
})();
