(() => {
    'use strict';
    const grid = document.getElementById('art-grid');
    if (!grid) return;
    const cards = [...grid.querySelectorAll('.art-card')];
    const search = document.getElementById('art-search');
    const sort = document.getElementById('art-sort');
    const filters = [...document.querySelectorAll('[data-category][aria-pressed]')];
    const dialog = document.getElementById('art-dialog');
    const preview = document.getElementById('art-dialog-image');
    let category = '', objectUrl = null, opener = null;
    const normalize = text => text.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('es');

    function update() {
        const query = normalize(search.value.trim());
        let visible = 0;
        cards.sort((a, b) => (a.dataset[sort.value] || '').localeCompare(b.dataset[sort.value] || '', 'es', {numeric: true})
            || a.dataset.name.localeCompare(b.dataset.name, 'es')).forEach(card => {
            card.hidden = Boolean((category && category !== card.dataset.category)
                || !normalize(Object.values(card.dataset).join(' ')).includes(query));
            if (!card.hidden) visible++;
            grid.appendChild(card);
        });
        document.getElementById('art-results').textContent = `${visible} de ${cards.length} recursos`;
        document.getElementById('art-empty').hidden = visible !== 0;
    }
    filters.forEach(button => button.addEventListener('click', () => {
        category = button.dataset.category;
        filters.forEach(filter => filter.setAttribute('aria-pressed', String(filter === button)));
        update();
    }));
    search.addEventListener('input', update);
    sort.addEventListener('change', update);
    document.getElementById('art-clear').addEventListener('click', () => {
        search.value = '';
        filters[0].click();
        search.focus();
    });
    cards.forEach(card => card.querySelector('.art-preview').addEventListener('click', event => {
        opener = event.currentTarget;
        const image = card.querySelector('img');
        if (image) preview.src = image.currentSrc || image.src;
        else {
            objectUrl = URL.createObjectURL(new Blob([card.querySelector('svg').outerHTML], {type: 'image/svg+xml'}));
            preview.src = objectUrl;
        }
        preview.alt = card.dataset.name;
        document.getElementById('art-dialog-title').textContent = card.dataset.name;
        document.getElementById('art-dialog-meta').textContent = `${card.dataset.category} · ${card.dataset.format} · ${card.dataset.filename}`;
        dialog.showModal();
    }));
    document.getElementById('art-close').addEventListener('click', () => dialog.close());
    dialog.addEventListener('close', () => {
        preview.removeAttribute('src');
        if (objectUrl) URL.revokeObjectURL(objectUrl);
        objectUrl = null;
        opener?.focus();
    });
    update();
})();
