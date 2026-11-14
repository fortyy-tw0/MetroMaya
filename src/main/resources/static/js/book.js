(async function () {
  const STEP_LABELS = ['Service', 'Business', 'Office', 'Date and time', 'Review'];
  const PANEL_IDS = ['panel-service', 'panel-business', 'panel-office', 'panel-when', 'panel-review'];

  const state = {
    step: 0, services: [], industries: [], offices: [], config: null,
    serviceId: null, form: null, quote: null, city: null, officeId: null, officeChecklist: null, date: '', time: '', verifyPass: null,
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
    // on a phone the bar scrolls sideways: keep the current step in view
    const cur = $('stepper').querySelector('.current');
    if (cur) $('stepper').scrollLeft = Math.max(0, cur.offsetLeft - 16);
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

  // Address: Line 1 is typed; City → Barangay → ZIP code are picked from lists that depend on each other.
  // Barangays come from the PSA's PSGC list and ZIP codes from PHLPost's directory (see tools/places).
  const citySelect = $('cityId');
  const brgySelect = $('barangay');
  const zipInput = $('zip');
  const cityNote = $('city-note');
  const zipNote = $('zip-note');
  NCR_CITIES.forEach((c) => citySelect.append(el('option', { value: c.id, text: c.municipality ? c.name + ' (municipality)' : cityPlace(c) })));

  const fold = (t) => String(t).normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()
    .replace(/\bsta\.?\s/g, 'santa ').replace(/\bsto\.?\s/g, 'santo ').replace(/\(pob\.\)/g, '').replace(/[^a-z0-9]+/g, ' ').trim();

  function fillSelect(sel, placeholder, options) {
    clear(sel).append(el('option', { value: '', selected: true, disabled: true, text: placeholder }));
    options.forEach((o) => sel.append(o));
    sel.disabled = !options.length;
  }

  function renderBarangays() {
    const places = NCR_PLACES[citySelect.value];
    zipNote.hidden = true;
    if (!places) {
      fillSelect(brgySelect, 'Choose a city first', []);
      suggestZip();
      return;
    }
    if (citySelect.value === 'manila') {
      // Manila's 897 barangays are numbered, so they're grouped by district
      const groups = {};
      places.barangays.forEach((b) => { (groups[b.district] = groups[b.district] || []).push(b); });
      fillSelect(brgySelect, 'Choose your barangay', Object.keys(groups).map((d) => el('optgroup', { label: d },
        groups[d].map((b) => el('option', { value: b.name, 'data-district': d, text: b.name })))));
    } else {
      fillSelect(brgySelect, 'Choose your barangay', places.barangays.map((b) => el('option', { value: b.name, text: b.name })));
    }
    suggestZip();
  }

  // The ZIP code is typed by the user. When the barangay (or, in Manila, its district) clearly matches a
  // PHLPost area, its code is offered as the placeholder and a one-click hint, but nothing is filled in.
  function suggestZip() {
    const places = NCR_PLACES[citySelect.value];
    zipNote.hidden = true;
    clear(zipNote);
    zipInput.placeholder = '4 digits';
    if (!places) return;
    const brgy = places.barangays.find((b) => b.name === brgySelect.value);
    let match = null;
    if (brgy && brgy.zips) {
      const zips = places.zips.filter((z) => brgy.zips.includes(z.code));
      if (zips.length === 1) match = zips[0];
    } else if (brgy) {
      const name = fold(brgy.name);
      match = places.zips.find((z) => fold(z.area).split(/\s*(?:,|&|\/|\+|-)\s*|\s+and\s+/).includes(name))
        || places.zips.find((z) => (' ' + fold(z.area) + ' ').includes(' ' + name + ' '))
        // barangays named after their area, e.g. "Almanza Uno" in PHLPost's "Almanza"
        || places.zips.find((z) => fold(z.area).split(/\s*(?:,|&|\/|\+|-)\s*|\s+and\s+/).some((part) => part.length >= 5 && name.startsWith(part + ' ')))
        || null;
    }
    if (match) {
      zipInput.placeholder = 'e.g. ' + match.code;
      if (zipInput.value.trim() !== match.code) {
        const use = el('button', { type: 'button', class: 'link-btn', text: 'Use ' + match.code });
        use.addEventListener('click', () => { zipInput.value = match.code; zipNote.hidden = true; });
        zipNote.append('PHLPost lists ' + match.code + ' for ' + match.area + '. ', use);
        zipNote.hidden = false;
      }
    }
  }

  // Typing a city into Line 1 still picks it for you.
  let lastDetected = null;
  function detectFromAddress() {
    const found = detectCity($('addressLine1').value);
    if (found && found.id !== lastDetected) {
      lastDetected = found.id;
      if (citySelect.value !== found.id) {
        citySelect.value = found.id;
        renderBarangays();
        cityNote.textContent = 'Found ' + cityPlace(found) + ' in your address.';
        cityNote.hidden = false;
        refreshLive();
      }
    } else if (!found) {
      lastDetected = null;
    }
  }
  $('addressLine1').addEventListener('input', debounce(detectFromAddress, 200));
  citySelect.addEventListener('change', () => { cityNote.hidden = true; renderBarangays(); refreshLive(); });
  brgySelect.addEventListener('change', suggestZip);
  zipInput.addEventListener('input', () => {
    zipInput.value = zipInput.value.replace(/\D/g, '').slice(0, 4);
    if (zipInput.value.length === 4) zipNote.hidden = true;
  });

  // Mobile number: digits only, at most 11 (09XXXXXXXXX)
  $('phone').addEventListener('input', (e) => {
    const digits = e.target.value.replace(/\D/g, '').slice(0, 11);
    if (digits !== e.target.value) e.target.value = digits;
  });

  const employeesOk = (v) => /^\d+$/.test(v) && Number(v) >= 1;

  // The estimate only appears once the three things it depends on are filled in.
  function missingForEstimate() {
    const missing = [];
    if (!$('industryId').value) missing.push('type of business');
    if (!employeesOk($('employees').value.trim())) missing.push('number of employees');
    if (!citySelect.value) missing.push('city');
    return missing;
  }

  function showWaiting(missing) {
    const list = missing.length > 1 ? missing.slice(0, -1).join(', ') + ' and ' + missing[missing.length - 1] : missing[0];
    clear(liveBody).append(
      el('p', { class: 'live-empty', text: 'Add your ' + list + ' to see your estimate.' }),
      el('ul', { class: 'live-todo' }, ['type of business', 'number of employees', 'city'].map((item) =>
        el('li', { class: missing.includes(item) ? '' : 'done', text: item.charAt(0).toUpperCase() + item.slice(1) }))));
  }

  async function refreshLive() {
    const missing = missingForEstimate();
    if (!state.serviceId) return;
    if (missing.length) { ++liveTicket; showWaiting(missing); return; }
    const industryId = $('industryId').value;
    const employees = $('employees').value.trim();
    const city = cityById(citySelect.value);
    const ticket = ++liveTicket;
    const qs = new URLSearchParams({ serviceId: state.serviceId, industryId, employees });
    try {
      const { quote: raw } = await api.get('/api/quote?' + qs.toString());
      if (ticket !== liveTicket) return;
      const quote = scaleQuote(raw, city.rate);
      clear(liveBody).append(
        el('p', { class: 'hint', text: quote.size.label + ' business, ' + quote.size.range + ', in ' + cityPlace(city) }),
        el('div', { class: 'fee-total', style: 'border-top:0; padding-top:0; margin-bottom:.9rem; font-size:1.15rem;' },
          el('span', { text: 'Estimated total' }), el('span', { text: peso(quote.fee.total) })),
        el('div', { id: 'live-fee' }),
        el('p', { class: 'hint', style: 'margin:.5rem 0 0;',
          text: quote.inspections.length
            ? 'Inspections: ' + quote.inspections.map((i) => shortInspection(i.label)).join(', ') + '.'
            : 'No inspection needed for this service.' }),
        el('p', { class: 'hint live-note', text: 'Sample rates for a course project, not official LGU fees.' }));
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
    const city = cityById(form.cityId || '');

    const problems = [];
    if (form.businessName.length < 2) problems.push('Enter your business name.');
    else if ((form.addressLine1 || '').length < 3) problems.push('Enter Address Line 1: unit or block number, street and subdivision.');
    else if (!city) problems.push('Choose the city your business is in.');
    else if (!form.barangay) problems.push('Choose your barangay.');
    else if (!/^\d{4}$/.test(form.zip || '')) problems.push('Enter your 4-digit ZIP code.');
    else if (!form.industryId) problems.push('Choose your type of business.');
    else if (!employeesOk(form.employees)) problems.push('Enter the number of employees as a whole number, 1 or more.');
    else if (state.config.verify && !state.verifyPass) problems.push('Verify it’s you first: reload the page to start again.');
    else if (form.ownerName.length < 2) problems.push('Enter the owner’s full name.');
    else if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(form.email)) problems.push('Enter a valid email address.');
    else if (!/^09\d{9}$/.test(form.phone)) problems.push('Enter an 11-digit mobile number starting with 09, e.g. 09171234567.');
    if (problems.length) { showAlert(alertNode, problems[0], 'error'); return; }

    // one address line for the booking, ending in the city and ZIP, e.g.
    // "Unit 3 Ruby Bldg, 12 Rizal St, Kapitolyo, Pasig City 1603". Track a booking reads the city from it.
    const brgyLabel = /^barangay\s/i.test(form.barangay) ? form.barangay : 'Brgy. ' + form.barangay;
    form.businessAddress = [form.addressLine1, brgyLabel, cityPlace(city) + ' ' + form.zip].join(', ');
    ['addressLine1', 'cityId', 'barangay', 'zip'].forEach((k) => delete form[k]);

    try {
      const qs = new URLSearchParams({ serviceId: state.serviceId, industryId: form.industryId, employees: form.employees });
      const { quote } = await api.get('/api/quote?' + qs.toString());
      if (!state.form || state.form.industryId !== form.industryId || state.form.employees !== form.employees) {
        state.officeId = null; state.date = ''; state.time = '';
      }
      state.form = form;
      state.city = city;
      state.quote = scaleQuote(quote, city.rate);
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

  // Only the office for the city entered in step 2 is offered: a business permit is filed with
  // the city (or municipality) the business is in. It's chosen for the user, who just confirms it.
  function renderOffices() {
    const box = clear($('office-choices'));
    const next = panels[2].querySelector('[data-next]');
    const city = state.city;
    const mine = state.offices.filter((o) => city && o.id === city.id);
    $('office-hint').textContent = city
      ? 'Your business is in ' + cityPlace(city) + ', so your ' + serviceById(state.serviceId).name.toLowerCase()
        + ' is handled at ' + (city.municipality ? 'its municipal hall.' : 'its city hall.')
      : '';
    if (state.officeId && !mine.some((o) => o.id === state.officeId)) { state.officeId = null; state.date = ''; state.time = ''; }
    if (!state.officeId && mine.length === 1 && !officeProblem(mine[0])) state.officeId = mine[0].id;

    if (!mine.length) {
      box.append(el('p', { class: 'alert alert-error', text: 'There is no office set up for ' + (city ? cityPlace(city) : 'your city') + ' yet. Go back and check the city.' }));
    }
    mine.forEach((o) => {
      const problem = officeProblem(o);
      const input = el('input', { type: 'radio', name: 'office', value: o.id, checked: o.id === state.officeId, disabled: !!problem });
      input.addEventListener('change', () => {
        if (state.officeId !== o.id) { state.date = ''; state.time = ''; }
        state.officeId = o.id;
        loadOfficeChecklist();
        next.disabled = false;
      });
      const maps = directionsUrl(city, o);
      box.append(el('label', { class: 'choice' + (problem ? ' disabled' : '') }, input,
        el('div', null,
          el('strong', { text: o.name }),
          el('small', { text: o.address }),
          city && city.note ? el('small', { text: city.note }) : null,
          problem && el('small', { text: problem }),
          el('a', { class: 'directions', href: maps, target: '_blank', rel: 'noopener', text: 'Open in Google Maps (live traffic) →' }))));
    });
    box.append(el('p', { class: 'hint office-other', text: 'Wrong city? Go back to the Business step and change it.' }));
    next.disabled = !state.officeId;
    renderMap(mine[0], city);
    loadOfficeChecklist();
  }

  async function loadOfficeChecklist() {
    const box = clear($('office-checklist'));
    state.officeChecklist = null;
    box.hidden = !state.officeId;
    if (!state.officeId) return;
    const officeId = state.officeId, serviceId = state.serviceId;
    box.append(el('h3', { text: 'Office instructions' }), el('p', { text: 'Loading the current office checklist…' }));
    try {
      const { checklist } = await api.get('/api/checklist?officeId=' + encodeURIComponent(officeId)
        + '&serviceId=' + encodeURIComponent(serviceId));
      if (state.officeId !== officeId || state.serviceId !== serviceId) return;
      state.officeChecklist = checklist;
      clear(box).append(el('h3', { text: 'Office instructions' }));
      if (checklist.instructions) box.append(el('p', { text: checklist.instructions }));
      if (checklist.documents.length) {
        box.append(el('h4', { text: 'Additional documents required here' }));
        const list = el('ul', { class: 'doc-list' });
        checklist.documents.forEach((document) => list.append(el('li', { text: document })));
        box.append(list);
      }
      box.append(el('p', { class: 'hint', text: checklist.updatedAt
        ? 'Checklist last updated ' + formatDate(checklist.updatedAt) : 'No extra documents or instructions set by this office.' }));
      if (!panels[4].hidden) renderDocs($('review-docs'), [...new Set([...state.quote.requirements, ...checklist.documents])]);
    } catch (error) { clear(box).append(el('p', { class: 'hint', text: 'Office instructions are temporarily unavailable.' })); }
  }

  // ---------- the office map: where you are, and the fastest route there ----------
  // Leaflet + OpenStreetMap tiles show the office (each city's hall has fixed coordinates in NCR_CITIES).
  // "Use my location" asks the browser for the user's position, and OSRM (OpenStreetMap's router) returns
  // driving routes; the quickest one is drawn with its distance and time. If location was already allowed
  // on this site, it's used straight away. The position is never sent to MetroMaya's server or stored.

  let map = null, officeDot = null, meDot = null, routeLine = null, me = null, routeTicket = 0;
  const routeInfo = (text) => { $('route-info').textContent = text; };

  function directionsUrl(city, office) {
    if (city && city.at) {
      const dest = city.at.join(',');
      return 'https://www.google.com/maps/dir/?api=1&travelmode=driving&destination=' + dest
        + (me ? '&origin=' + me.map((n) => n.toFixed(5)).join(',') : '');
    }
    return 'https://www.google.com/maps/search/?api=1&query=' + encodeURIComponent(office.address);
  }

  function renderMap(office, city) {
    const panel = $('route-panel');
    if (!office || !city || !city.at || !window.L) { panel.hidden = true; return; }
    panel.hidden = false;
    if (!map) {
      map = L.map('office-map', { scrollWheelZoom: false, zoomControl: true });
      L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19, attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
      }).addTo(map);
    }
    if (officeDot) officeDot.remove();
    officeDot = L.circleMarker(city.at, { radius: 10, color: '#1B2248', weight: 3, fillColor: '#2C3A72', fillOpacity: 1 })
      .addTo(map).bindTooltip(office.name, { direction: 'top', offset: [0, -8] });
    map.invalidateSize();
    if (me) {
      drawRoute(city);
    } else {
      map.setView(city.at, 16);
      routeInfo('This is ' + office.name + '. Use your location to see the fastest way there.');
      autoLocate();
    }
  }

  // Sets the starting point (from the browser or a typed place) and draws the route from it.
  function setOrigin(pos, label) {
    me = pos;
    if (meDot) meDot.remove();
    meDot = L.circleMarker(me, { radius: 9, color: '#fff', weight: 3, fillColor: '#EDB24A', fillOpacity: 1, className: 'me-dot' })
      .addTo(map).bindTooltip(label, { direction: 'top', offset: [0, -8] });
    drawRoute(state.city);
  }

  // Laptops have no GPS, so a high-accuracy request can time out or come back "unavailable";
  // one retry with normal (Wi-Fi) accuracy and more time usually works.
  function locate() {
    if (!navigator.geolocation) { routeInfo('Your browser can’t share your location. Type where you’re coming from below.'); return; }
    const btn = $('locate-btn');
    btn.disabled = true;
    routeInfo('Finding where you are…');
    const done = (pos) => {
      btn.disabled = false;
      btn.textContent = 'Update my location';
      setOrigin([pos.coords.latitude, pos.coords.longitude], 'You are here');
    };
    const fail = (err) => {
      btn.disabled = false;
      routeInfo(err.code === 1
        ? 'Location is blocked for this site. Click the icon at the left of the address bar to allow it, or type where you’re coming from below.'
        : err.code === 2
          ? 'Your device couldn’t work out where you are. On a Mac, turn on Location Services for your browser (System Settings → Privacy & Security → Location Services), or type where you’re coming from below.'
          : 'Finding your location took too long. Try again, or type where you’re coming from below.');
    };
    navigator.geolocation.getCurrentPosition(done, (err) => {
      if (err.code === 1) { fail(err); return; }
      navigator.geolocation.getCurrentPosition(done, fail, { enableHighAccuracy: false, timeout: 25000, maximumAge: 300000 });
    }, { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 });
  }

  // Fallback: a typed place, looked up with OpenStreetMap's geocoder (one search per click, as its policy asks).
  $('route-from').addEventListener('submit', async (e) => {
    e.preventDefault();
    const q = $('route-from-input').value.trim();
    if (q.length < 3) { routeInfo('Type a place, landmark or address to start from.'); return; }
    const btn = e.target.querySelector('button');
    btn.disabled = true;
    routeInfo('Looking up “' + q + '”…');
    try {
      const url = 'https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&countrycodes=ph'
        + '&viewbox=120.85,14.85,121.20,14.30&q=' + encodeURIComponent(q);
      const hits = await (await fetch(url, { headers: { Accept: 'application/json' } })).json();
      if (!hits.length) { routeInfo('Couldn’t find “' + q + '”. Try a landmark, mall or street with the city.'); return; }
      setOrigin([Number(hits[0].lat), Number(hits[0].lon)], hits[0].display_name.split(',').slice(0, 2).join(','));
    } catch (err) {
      routeInfo('Couldn’t look that place up right now. Try again, or use Google Maps.');
    } finally {
      btn.disabled = false;
    }
  });

  $('locate-btn').addEventListener('click', locate);

  // If the user already allowed location for this site, show their route without another click.
  async function autoLocate() {
    try {
      const status = await navigator.permissions.query({ name: 'geolocation' });
      if (status.state === 'granted' && !me) locate();
    } catch (e) { /* permissions API unavailable: wait for the button */ }
  }

  async function drawRoute(city) {
    const to = city.at;
    const ticket = ++routeTicket;
    routeInfo('Finding the fastest route…');
    $('office-choices').querySelectorAll('.directions').forEach((a) => { a.href = directionsUrl(city, null); });
    try {
      const url = 'https://router.project-osrm.org/route/v1/driving/' + me[1] + ',' + me[0] + ';' + to[1] + ',' + to[0]
        + '?overview=full&geometries=geojson&alternatives=true';
      const res = await fetch(url);
      const data = await res.json();
      if (ticket !== routeTicket) return;
      if (data.code !== 'Ok' || !data.routes || !data.routes.length) throw new Error('no route');
      const best = data.routes.reduce((a, b) => (b.duration < a.duration ? b : a));
      if (routeLine) routeLine.remove();
      routeLine = L.geoJSON(best.geometry, { style: { color: '#2C3A72', weight: 6, opacity: 0.85 } }).addTo(map);
      map.fitBounds(routeLine.getBounds().extend(me).extend(to), { padding: [28, 28] });
      const km = best.distance / 1000;
      routeInfo('Fastest route: about ' + minutesText(Math.max(1, Math.round(best.duration / 60))) + ' by car ('
        + (km < 10 ? km.toFixed(1) : Math.round(km)) + ' km)'
        + (data.routes.length > 1 ? ', the quickest of ' + data.routes.length + ' routes.' : '.'));
    } catch (e) {
      if (ticket !== routeTicket) return;
      if (routeLine) { routeLine.remove(); routeLine = null; }
      map.fitBounds(L.latLngBounds([me, to]), { padding: [40, 40] });
      routeInfo('Couldn’t load a route right now. Open Google Maps for directions from where you are.');
    }
  }

  // ---------- step 4: date & time ----------

  function setupWhen() {
    $('date-hint').textContent = 'Pick a weekday up to ' + state.config.maxMonthsAhead + ' months ahead, then a time. '
      + 'Expect about ' + minutesText(state.quote.estimatedMinutes)
      + (state.serviceId === 'inspection' ? ' on site.' : ' at the counter.');
    $('day-status').textContent = state.date ? '' : 'Choose a day on the calendar to see its open times.';
    // the whole month for this office; picking a day shows its times below
    mountCalendar($('cal'), {
      officeId: state.officeId, config: state.config, selected: state.date,
      onSelect: (iso, day) => {
        state.date = iso;
        state.time = '';
        $('day-status').textContent = dayStatusText(iso, day);
        panels[3].querySelector('[data-next]').disabled = true;
        refreshSlots();
      },
    });
    panels[3].querySelector('[data-next]').disabled = !(state.date && state.time);
    refreshSlots();
  }

  function refreshSlots() {
    const next = panels[3].querySelector('[data-next]');
    const alertNode = $('slot-alert');
    if (!state.date) { clear($('slots')); hideAlert(alertNode); return; }
    const closed = closedReason(state.date, state.config);
    if (closed) {
      clear($('slots'));
      showAlert(alertNode, closed, 'error');
      next.disabled = true;
      return;
    }
    loadSlots($('slots'), alertNode, state.officeId, state.date, state.time, (time) => {
      state.time = time;
      next.disabled = false;
    });
  }

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
    renderDocs($('review-docs'), [...new Set([...state.quote.requirements,
      ...(state.officeChecklist ? state.officeChecklist.documents : [])])]);
    hideAlert($('submit-alert'));
  }

  $('confirm-btn').addEventListener('click', async () => {
    const btn = $('confirm-btn');
    btn.disabled = true;
    btn.textContent = 'Booking…';
    hideAlert($('submit-alert'));
    try {
      const { appointment, managementToken } = await api.post('/api/appointments', {
        serviceId: state.serviceId,
        officeId: state.officeId,
        date: state.date,
        time: state.time,
        ...state.form,
        ...(state.verifyPass ? { verifyPass: state.verifyPass } : {}),
      });
      showDone(appointment, managementToken);
    } catch (e) {
      showAlert($('submit-alert'), e.message, 'error');
      // an expired pass: verify again, then come straight back to Review
      if (state.config.verify && /verify again/i.test(e.message)) showVerify(() => showStep(4));
    } finally {
      btn.disabled = false;
      btn.textContent = 'Confirm booking';
    }
  });

  function showDone(a, managementToken) {
    doneAppointment = a;
    doneManagementToken = managementToken;
    panels.forEach((p) => { p.hidden = true; });
    $('stepper').hidden = true;
    donePanel.hidden = false;
    $('done-ref').textContent = a.referenceNo;
    $('done-manage').href = privateManagementUrl(a.referenceNo, managementToken);
    $('done-access-code').value = managementToken;
    const dl = clear($('done-summary'));
    addRow(dl, 'Service', a.serviceName);
    addRow(dl, 'Business', a.businessName);
    addRow(dl, 'Office', a.officeName + ', ' + a.officeAddress);
    addRow(dl, 'Date', formatDate(a.date));
    addRow(dl, 'Time', formatTime(a.time));
    renderCheckInCode($('done-checkin'), a);
    const doneQuote = state.city ? scaleQuote(a.quote, state.city.rate) : a.quote;
    renderFee($('done-fee'), doneQuote.fee);
    renderInspections($('done-insp'), doneQuote.inspections);
    renderDocs($('done-docs'), a.quote.requirements);
    $('done-print').textContent = printActionLabel(a);
    renderPayment($('done-pay'), a, (updated) => {
      doneAppointment = updated;
      $('done-print').textContent = printActionLabel(updated);
    }, managementToken);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
  let doneAppointment = null;
  let doneManagementToken = null;
  $('done-print').addEventListener('click', () => { if (doneAppointment) printBooking(doneAppointment); });
  $('copy-manage-link').addEventListener('click', async () => {
    if (!doneAppointment || !doneManagementToken) return;
    try {
      await navigator.clipboard.writeText(location.origin + privateManagementUrl(doneAppointment.referenceNo, doneManagementToken));
      $('copy-manage-status').textContent = 'Private link copied.';
    } catch (e) {
      $('copy-manage-status').textContent = 'Copy failed. Save the access code above instead.';
    }
  });

  // ---------- step 0: verify it's you (only when the server has eGov settings) ----------
  // Two ways: eGovPH SSO (the "Login as eGov" widget gives an exchange code the server redeems), or a photo
  // of a government ID that the server OCRs and an LLM sorts into fields. Either way the server returns a signed 30-minute
  // pass the booking must carry; the booker's name comes from the pass, so the name field is locked.

  const EGOV_WIDGET = 'https://widgets.e.gov.ph/v1.0.0/egov-login.min.js';
  let afterVerify = null;

  function showVerify(then) {
    afterVerify = then;
    panels.forEach((p) => { p.hidden = true; });
    donePanel.hidden = true;
    $('stepper').hidden = true;
    $('panel-verify').hidden = false;
    hideAlert($('verify-alert'));
    if (window.EgovLogin && state.config.verify.egov) renderEgov();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function setupVerify(cfg) {
    // Data Privacy Act consent comes first: both ways to verify stay inert until it's given
    const consent = $('verify-consent');
    const syncConsent = () => {
      $('verify-options').inert = !consent.checked;
      $('consent-hint').hidden = consent.checked;
    };
    consent.addEventListener('change', syncConsent);
    syncConsent();
    $('ownerName').readOnly = true;
    $('ownerName').placeholder = 'Filled in when you verify';
    if (cfg.egov) {
      $('verify-egov').hidden = false;
      const script = el('script', { src: EGOV_WIDGET });
      script.addEventListener('load', renderEgov);
      script.addEventListener('error', () => showAlert($('verify-alert'),
        'Could not load eGovPH sign-in. Check your connection and reload the page'
        + (cfg.idScan ? ', or scan your ID instead.' : '.'), 'error'));
      document.head.append(script);
    }
    if (cfg.idScan) {
      $('verify-id').hidden = false;
      $('id-file').addEventListener('change', (e) => {
        const file = e.target.files[0];
        e.target.value = '';
        if (file) scanId(file);
      });
    }
  }

  function renderEgov() {
    clear($('egov-login'));
    window.EgovLogin.render({
      target: '#egov-login',
      partnerCode: state.config.verify.egov.partnerCode,
      host: state.config.verify.egov.host,
      partnerName: 'MetroMaya',
      onSuccess: ({ exchangeCode }) => verified(api.post('/api/verify/egov', { exchangeCode })),
    });
  }

  function scanId(file) {
    if (!/^image\/(jpeg|png|webp)$/.test(file.type)) {
      showAlert($('verify-alert'), 'Upload a photo of your ID (JPG, PNG or WebP).', 'error');
      return;
    }
    if (file.size > 8 * 1024 * 1024) {
      showAlert($('verify-alert'), 'That file is too large. Use a photo under 8 MB.', 'error');
      return;
    }
    const btn = $('id-file-btn');
    btn.classList.add('busy');
    btn.firstChild.textContent = 'Reading your ID…';
    verified(request('/api/verify/id', { method: 'POST', body: file })).finally(() => {
      btn.classList.remove('busy');
      btn.firstChild.textContent = 'Take or upload a photo';
    });
  }

  // Prefills the business address from the ID / eGovPH address, only into fields that are still empty,
  // and only for a Metro Manila address (the City list is NCR only). The user can change any of it.
  function fillAddress(a) {
    if (!a) return;
    const city = detectCity([a.city, a.province].filter(Boolean).join(', '));
    if (!city) return;
    const filled = [];
    if (a.line1 && !$('addressLine1').value.trim()) { $('addressLine1').value = a.line1; filled.push('address'); }
    if (!citySelect.value) { citySelect.value = city.id; renderBarangays(); filled.push('city'); }
    if (citySelect.value === city.id && a.barangay && !brgySelect.value) {
      const bare = (t) => fold(String(t).replace(/^\s*(brgy\.?|barangay)\s*/i, ''));
      const opt = [...brgySelect.querySelectorAll('option')].find((o) => o.value && bare(o.value) === bare(a.barangay));
      if (opt) { brgySelect.value = opt.value; suggestZip(); filled.push('barangay'); }
    }
    if (a.zip && !zipInput.value.trim()) { zipInput.value = a.zip; zipNote.hidden = true; filled.push('ZIP code'); }
    if (filled.length) {
      cityNote.textContent = 'Filled in from your ' + (state.verifyMethod || 'ID') + '. Change it if your business is somewhere else.';
      cityNote.hidden = false;
      lastDetected = city.id;
      refreshLive();
    }
  }

  async function verified(pending) {
    hideAlert($('verify-alert'));
    try {
      const v = await pending;
      state.verifyPass = v.pass;
      state.verifyMethod = v.method === 'eGovPH' ? 'eGovPH profile' : 'ID';
      $('ownerName').value = v.fullName;
      if (v.email && !$('email').value.trim()) $('email').value = v.email;
      if (v.mobile && !$('phone').value.trim()) $('phone').value = v.mobile;
      fillAddress(v.address);
      if (state.form) state.form.ownerName = v.fullName;
      $('verified-as').textContent = 'Verified as ' + v.fullName + ' (' + v.method + ').';
      $('verified-box').hidden = false;
      $('panel-verify').hidden = true;
      const then = afterVerify;
      afterVerify = null;
      then();
    } catch (e) {
      showAlert($('verify-alert'), e.message, 'error');
      if (window.EgovLogin && state.config.verify.egov) renderEgov();
    }
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
    const startWizard = () => { showStep(state.serviceId ? 1 : 0); refreshLive(); };
    if (config.verify) {
      setupVerify(config.verify);
      showVerify(startWizard);
    } else {
      startWizard();
    }
  } catch (e) {
    clear($('service-choices')).append(el('p', { class: 'alert alert-error', text: e.message }));
    renderStepper();
  }
})();
