(async function () {
  const groupsNode = document.getElementById('service-groups');
  const officesNode = document.getElementById('office-list');

  try {
    const [{ services }, { offices }] = await Promise.all([api.get('/api/services'), api.get('/api/offices')]);

    // Group services by category, keeping the server's order.
    const groups = new Map();
    services.forEach((s) => {
      if (!groups.has(s.category)) groups.set(s.category, { label: s.categoryLabel, items: [] });
      groups.get(s.category).items.push(s);
    });

    clear(groupsNode);
    groups.forEach((group) => {
      groupsNode.append(el('div', { class: 'group' },
        el('h3', { text: group.label }),
        group.items.map((s) => el('article', { class: 'service' },
          el('div', { class: 'service-top' },
            el('h4', { text: s.name }),
            el('a', { class: 'link-book', href: '/book.html?service=' + encodeURIComponent(s.id), text: 'Book →' })),
          el('p', { text: s.description }),
          el('div', { class: 'meta' },
            el('span', { class: 'pill', text: 'Fee ' + money(s.fee) }),
            el('span', { class: 'pill', text: s.durationMinutes + ' min' }),
            s.minimumAge > 0 && el('span', { class: 'pill', text: 'Age ' + s.minimumAge + '+' })),
          el('details', null,
            el('summary', { text: 'What to bring' }),
            requirementsList(s.requirements))))));
    });

    clear(officesNode);
    offices.forEach((o) => {
      officesNode.append(el('div', { class: 'office' },
        el('h3', { text: o.name }),
        el('p', { text: o.address }),
        el('p', { class: 'hint', text: o.serviceIds.length + ' services offered' })));
    });
  } catch (e) {
    clear(groupsNode).append(el('p', { class: 'alert alert-error', text: e.message }));
  }
})();
