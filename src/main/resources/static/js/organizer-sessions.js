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
    wrapper.className = 'wide';
    wrapper.style.cssText = 'grid-column:1/-1;padding:16px;border:1px solid #d9e1ec;border-radius:14px;background:#f7f9fc';
    const heading = document.createElement('h3');
    heading.textContent = 'Funciones y recintos';
    const hint = document.createElement('p');
    hint.textContent = 'Hasta 6 funciones en una misma ficha. Indica la ciudad, fecha y hora, recinto y dirección de cada presentación.';
    const rows = document.createElement('div');
    rows.style.cssText = 'display:grid;gap:12px;margin:12px 0';
    const add = document.createElement('button');
    add.type = 'button';
    add.className = 'btn secondary';
    add.textContent = '+ Añadir función';
    const count = document.createElement('p');
    count.setAttribute('aria-live', 'polite');
    const note = document.createElement('small');
    note.textContent = 'Esta programación describe las funciones. La venta actual corresponde al evento; los cupos por función requieren habilitar venta independiente.';
    wrapper.append(heading, hint, rows, add, count, note);
    source.closest('label').after(wrapper);
    let active = false;
    const clean = value => value.replace(/[|\r\n]/g, ' ').trim();
    function sync() {
        if (!active) return;
        source.value = [...rows.children].map(card => [...card.querySelectorAll('input')].map(input => clean(input.value)).join(' | ')).join('\n');
        source.dispatchEvent(new Event('input', { bubbles: true }));
        add.disabled = rows.children.length >= 6;
        count.textContent = `${rows.children.length} de 6 funciones`;
        [...rows.children].forEach((card, index) => { card.querySelector('strong').textContent = `Función ${index + 1}`; });
    }
    function makeRow(values = ['', '', '', '']) {
        if (rows.children.length >= 6) return;
        const card = document.createElement('div');
        card.style.cssText = 'padding:12px;border:1px solid #d9e1ec;border-radius:10px;background:white;display:grid;gap:8px';
        const title = document.createElement('strong');
        card.append(title);
        ['Ciudad', 'Fecha y hora', 'Recinto', 'Dirección'].forEach((text, index) => {
            const label = document.createElement('label');
            label.textContent = text;
            const input = document.createElement('input');
            input.className = 'search-input';
            input.type = index === 1 ? 'datetime-local' : 'text';
            input.value = values[index] || '';
            input.required = true;
            if (index !== 1) input.maxLength = 180;
            input.addEventListener('input', sync);
            label.append(input);
            card.append(label);
        });
        const actions = document.createElement('div');
        actions.style.cssText = 'display:flex;gap:8px;flex-wrap:wrap';
        const repeat = document.createElement('button');
        repeat.type = 'button'; repeat.className = 'btn secondary compact'; repeat.textContent = 'Repetir local en otra fecha';
        repeat.addEventListener('click', () => {
            const inputs = [...card.querySelectorAll('input')];
            makeRow([inputs[0].value, '', inputs[2].value, inputs[3].value]);
        });
        const remove = document.createElement('button');
        remove.type = 'button'; remove.className = 'btn secondary compact'; remove.textContent = 'Quitar función';
        remove.addEventListener('click', () => { card.remove(); sync(); });
        actions.append(repeat, remove); card.append(actions); rows.append(card);
        const showSessions = document.querySelector('input[name="showSessions"]');
        if (showSessions) showSessions.checked = true;
        sync();
    }
    function activate() {
        active = true;
        source.closest('label').hidden = true;
        if (legacy.length) legacy.forEach(line => makeRow(line.split('|').map(value => value.trim())));
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
            add.textContent = '+ Añadir función'; makeRow();
        }, { once: true });
    }
    add.addEventListener('click', () => { if (active && add.textContent === '+ Añadir función') makeRow(); });
    source.form.addEventListener('submit', () => sync());
})();
