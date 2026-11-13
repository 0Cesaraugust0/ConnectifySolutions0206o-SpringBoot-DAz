/* Structured editing of the existing presentation schedule; preserves legacy text. */
(() => {
    const source = document.querySelector('textarea[name="sessionText"]');
    if (!source) return;
    const legacy = source.value.trim().split(/\r?\n/).filter(Boolean);
    const validRow = line => {
        const parts = line.split('|').map(value => value.trim());
        return parts.length === 4 && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(parts[1]);
    };
    const wrapper = document.createElement('section');
    wrapper.className = 'wide tour-editor';
    const heading = document.createElement('h3');
    heading.textContent = 'Funciones en el mismo local';
    const hint = document.createElement('p');
    hint.textContent = 'Añade hasta seis funciones que comparten recinto. Para otra ciudad, crea su propia ficha y enlázala debajo.';
    const rows = document.createElement('div');
    rows.style.cssText = 'display:grid;gap:12px;margin:12px 0';
    const add = document.createElement('button');
    add.type = 'button';
    add.className = 'btn secondary compact';
    add.textContent = '+ Añadir sesión';
    const count = document.createElement('small');
    count.setAttribute('aria-live', 'polite');
    const note = document.createElement('small');
    note.textContent = 'Esta programación describe las funciones. La venta actual corresponde al evento; los cupos por función requieren habilitar venta independiente.';
    const footer = document.createElement('div');
    footer.className = 'tour-function-footer';
    footer.append(add, count);
    wrapper.append(heading, hint, rows, footer, note);
    source.closest('label').after(wrapper);
    let active = false;
    const clean = value => value.replace(/[|\r\n]/g, ' ').trim();
    function sync() {
        if (!active) return;
        source.value = [...rows.children].map(card => [...card.querySelectorAll('input')].map(input => clean(input.value)).join(' | ')).join('\n');
        source.dispatchEvent(new Event('input', { bubbles: true }));
        add.disabled = rows.children.length >= 6;
        count.textContent = `${rows.children.length} de 6 sesiones`;
        [...rows.children].forEach((card, index) => {
            card.querySelector('strong').textContent = `Sesión ${index + 1}`;
            card.querySelector('.tour-function-remove').setAttribute('aria-label', `Quitar sesión ${index + 1}`);
        });
    }
    function makeRow(values = [source.form.elements.city.value, '', source.form.elements.location.value, ''], sameVenue = true) {
        if (rows.children.length >= 6) return;
        const card = document.createElement('div');
        card.className = 'tour-function';
        if (sameVenue) card.dataset.sameVenue = 'true';
        const title = document.createElement('strong');
        const header = document.createElement('header');
        header.className = 'tour-function-heading';
        const remove = document.createElement('button');
        remove.type = 'button';
        remove.className = 'btn secondary compact tour-function-remove';
        remove.textContent = 'Quitar sesión';
        remove.addEventListener('click', () => { card.remove(); sync(); });
        header.append(title, remove);
        card.append(header);
        ['Ciudad', 'Fecha y hora', 'Recinto', 'Dirección'].forEach((text, index) => {
            const label = document.createElement('label');
            label.textContent = text;
            const input = document.createElement('input');
            input.className = 'search-input';
            input.type = index === 1 ? 'datetime-local' : 'text';
            input.value = values[index] || '';
            input.required = true;
            if (index !== 1) {
                input.disabled = true;
                input.required = false;
            }
            if (index === 3) {
                label.className = 'tour-function-address';
                input.placeholder = 'Sin dirección registrada';
            }
            if (index !== 1) input.maxLength = 180;
            input.addEventListener('input', sync);
            label.append(input);
            card.append(label);
        });
        rows.append(card);
        const showSessions = document.querySelector('input[name="showSessions"]');
        if (showSessions) showSessions.checked = true;
        sync();
    }
    function activate() {
        active = true;
        source.closest('label').hidden = true;
        if (legacy.length) legacy.forEach(line => makeRow(line.split('|').map(value => value.trim()), false));
        sync();
    }
    if (legacy.length <= 6 && legacy.every(validRow)) activate();
    else {
        hint.textContent = 'Esta ficha ya tiene una programación escrita. Se conserva íntegra para que puedas revisarla antes de sustituirla por funciones con campos separados.';
        add.textContent = 'Crear programación con campos separados';
        add.addEventListener('click', (event) => {
            if (active) return;
            event.stopImmediatePropagation();
            // Explicit conversion keeps the old text visible for reference.
            const reference = document.createElement('pre');
            reference.textContent = source.value;
            reference.style.cssText = 'white-space:pre-wrap;font-size:12px';
            wrapper.prepend(reference);
            active = true; source.closest('label').hidden = true;
            add.textContent = '+ Añadir sesión'; makeRow();
        }, { once: true });
    }
    add.addEventListener('click', () => {
        if (active) {
            const previousAddress = rows.lastElementChild?.querySelectorAll('input')[3]?.value ?? '';
            makeRow([source.form.elements.city.value, '', source.form.elements.location.value, previousAddress]);
            rows.lastElementChild?.querySelector('input[type="datetime-local"]')?.focus();
        }
    });
    function refreshVenue() {
        rows.querySelectorAll('[data-same-venue]').forEach(row => {
            const inputs = row.querySelectorAll('input');
            inputs[0].value = source.form.elements.city.value;
            inputs[2].value = source.form.elements.location.value;
        });
        sync();
    }
    source.form.elements.city.addEventListener('input', refreshVenue);
    source.form.elements.location.addEventListener('input', refreshVenue);
    source.form.addEventListener('submit', refreshVenue);

    const picker = document.getElementById('tourPicker');
    const city = source.form.elements.city;
    const options = [...picker.querySelectorAll('.tour-option')];
    const maxLinks = Number(picker.dataset.max);
    function updateTourOptions() {
        const currentCity = city.value.trim().toLocaleLowerCase('es');
        const eligible = options.filter(option => {
            option.hidden = option.dataset.city?.trim().toLocaleLowerCase('es') === currentCity;
            return !option.hidden;
        });
        const selected = eligible.filter(option => option.querySelector('input').checked).length;
        options.forEach(option => {
            const checkbox = option.querySelector('input');
            checkbox.disabled = option.hidden || (!checkbox.checked && selected >= maxLinks);
        });
        document.getElementById('tourPickerEmpty').hidden = eligible.length > 0;
        document.getElementById('tourSelectionCount').textContent = `${selected} de ${maxLinks} enlazados`;
    }
    document.getElementById('openTourPicker').addEventListener('click', () => { updateTourOptions(); picker.showModal(); });
    document.getElementById('closeTourPicker').addEventListener('click', () => picker.close());
    picker.addEventListener('change', updateTourOptions);
    city.addEventListener('input', updateTourOptions);
    updateTourOptions();
})();
