// Shared helpers. Everything user-visible is built with textContent, never innerHTML,
// so names or messages from the server can't inject markup.

async function request(path, options) {
  let res;
  try {
    res = await fetch(path, options);
  } catch (e) {
    throw new Error('Could not reach the server. Please check your connection and try again.');
  }
  let data = null;
  try { data = await res.json(); } catch (e) { /* non-JSON body */ }
  if (!res.ok) {
    throw new Error((data && data.error) || 'Request failed (' + res.status + ').');
  }
  return data;
}

const api = {
  get: (path) => request(path),
  post: (path, data) => request(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams(data || {}).toString(),
  }),
};

function el(tag, props, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props || {})) {
    if (value === false || value == null) continue;
    if (key === 'class') node.className = value;
    else if (key === 'text') node.textContent = value;
    else if (key.startsWith('on') && typeof value === 'function') node.addEventListener(key.slice(2), value);
    else node.setAttribute(key, value === true ? '' : value);
  }
  for (const child of children.flat()) {
    if (child == null || child === false) continue;
    node.append(child.nodeType ? child : document.createTextNode(String(child)));
  }
  return node;
}

function clear(node) { while (node.firstChild) node.removeChild(node.firstChild); return node; }

function money(n) {
  return Number(n).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatDate(iso) {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d).toLocaleDateString('en-US',
    { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' });
}

function formatTime(hhmm) {
  const [h, m] = hhmm.split(':').map(Number);
  const suffix = h >= 12 ? 'PM' : 'AM';
  return ((h + 11) % 12 + 1) + ':' + String(m).padStart(2, '0') + ' ' + suffix;
}

function addDaysISO(iso, days) {
  const [y, m, d] = iso.split('-').map(Number);
  const date = new Date(y, m - 1, d + days);
  return date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0') + '-' + String(date.getDate()).padStart(2, '0');
}

function isWeekend(iso) {
  const [y, m, d] = iso.split('-').map(Number);
  const day = new Date(y, m - 1, d).getDay();
  return day === 0 || day === 6;
}

function showAlert(node, message, kind) {
  node.textContent = message;
  node.className = 'alert ' + (kind === 'ok' ? 'alert-ok' : 'alert-error');
  node.hidden = false;
}

function hideAlert(node) { node.hidden = true; node.textContent = ''; }

function requirementsList(items) {
  return el('ul', { class: 'checklist' }, items.map((r) => el('li', { text: r })));
}

// Renders the time-slot buttons for a date at an office into `container`.
// `onPick(time)` is called with "HH:MM" when a slot is chosen.
async function loadSlots(container, alertNode, officeId, date, selectedTime, onPick) {
  clear(container);
  hideAlert(alertNode);
  if (!date) return;
  try {
    const data = await api.get('/api/slots?officeId=' + encodeURIComponent(officeId) + '&date=' + encodeURIComponent(date));
    data.slots.forEach((slot) => {
      const btn = el('button', {
        type: 'button',
        class: 'slot',
        disabled: !slot.available,
        'aria-pressed': slot.time === selectedTime ? 'true' : 'false',
      },
        el('b', { text: formatTime(slot.time) }),
        el('span', { text: slot.remaining > 0 ? slot.remaining + ' left' : 'Full' }));
      btn.addEventListener('click', () => {
        container.querySelectorAll('.slot').forEach((b) => b.setAttribute('aria-pressed', 'false'));
        btn.setAttribute('aria-pressed', 'true');
        onPick(slot.time);
      });
      container.append(btn);
    });
    if (!data.slots.some((s) => s.available)) {
      showAlert(alertNode, 'No free slots on that day. Please try another date.', 'error');
    }
  } catch (e) {
    showAlert(alertNode, e.message, 'error');
  }
}
