(function () {
    'use strict';
    const viewport = document.querySelector('[data-visits-window]');
    const trigger = document.querySelector('[data-visits-toggle]');
    const panel = document.querySelector('[data-visits-panel]');
    if (!viewport || !trigger || !panel) return;
    const hero = viewport.querySelector('[data-visits-hero]');
    const artistList = panel.querySelector('[data-visits-artists]');
    const holidayList = panel.querySelector('[data-visits-holidays]');
    const status = panel.querySelector('[data-visits-status]');
    let loaded = false;
    let loading = false;

    function element(tag, className, text) {
        const node = document.createElement(tag);
        node.className = className;
        if (text) node.textContent = text;
        return node;
    }

    function row(entry, artist) {
        const date = new Date(entry.date + 'T12:00:00-05:00');
        const item = element('div', 'visits-row');
        const stamp = element('time', 'visits-date');
        stamp.dateTime = entry.date;
        stamp.append(element('strong', '', String(date.getUTCDate()).padStart(2, '0')),
            element('span', '', date.toLocaleDateString('es-PE', {month: 'short', year: 'numeric', timeZone: 'America/Lima'})));
        const details = element('div', 'visits-details');
        details.append(element('strong', '', entry.name), element('small', '', artist ? entry.venue : date.toLocaleDateString('es-PE', {weekday: 'long', timeZone: 'America/Lima'}) + ' · ' + entry.kind));
        item.append(stamp, details);
        return item;
    }

    async function loadAgenda() {
        if (loaded || loading) return;
        loading = true;
        panel.setAttribute('aria-busy', 'true');
        try {
            const response = await fetch(panel.dataset.agendaUrl, {headers: {'Accept': 'application/json'}});
            if (!response.ok) throw new Error('Agenda no disponible');
            const agenda = await response.json();
            const today = new Intl.DateTimeFormat('sv-SE', {timeZone: 'America/Lima'}).format(new Date());
            const upcoming = entries => entries.filter(entry => entry.date >= today && entry.date <= agenda.until).sort((a, b) => a.date.localeCompare(b.date));
            const artists = upcoming(agenda.artists);
            const holidays = upcoming(agenda.holidays);
            artistList.replaceChildren(...artists.map(entry => row(entry, true)));
            holidayList.replaceChildren(...holidays.map(entry => row(entry, false)));
            if (!artists.length) artistList.append(element('p', 'visits-message', 'No hay próximas visitas registradas en este período.'));
            if (!holidays.length) holidayList.append(element('p', 'visits-message', 'No quedan fechas conmemorativas registradas en este período.'));
            const reviewed = new Date(agenda.reviewedAt + 'T12:00:00-05:00').toLocaleDateString('es-PE', {day: 'numeric', month: 'short', year: 'numeric', timeZone: 'America/Lima'});
            status.textContent = 'Revisado el ' + reviewed + ' · Fechas sujetas a cambios del organizador.';
            loaded = true;
        } catch (error) {
            artistList.replaceChildren(element('p', 'visits-message', 'No pudimos cargar la agenda. Vuelve a intentarlo.'));
            holidayList.replaceChildren(element('p', 'visits-message', 'Las fechas estarán disponibles cuando se restablezca la agenda.'));
            const retry = element('button', 'visits-retry', 'Reintentar');
            retry.type = 'button';
            retry.addEventListener('click', loadAgenda);
            artistList.append(retry);
            status.textContent = 'La agenda no está disponible en este momento.';
        } finally {
            loading = false;
            panel.removeAttribute('aria-busy');
        }
    }

    function show(open) {
        viewport.classList.toggle('is-visits-open', open);
        trigger.setAttribute('aria-expanded', String(open));
        panel.setAttribute('aria-hidden', String(!open));
        panel.inert = !open;
        hero.inert = open;
        hero.setAttribute('aria-hidden', String(open));
        if (open) {
            loadAgenda();
            panel.querySelector('h2').focus({preventScroll: true});
            viewport.scrollIntoView({block: 'start', behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth'});
        } else {
            trigger.focus({preventScroll: true});
        }
    }

    trigger.addEventListener('click', function () { show(trigger.getAttribute('aria-expanded') !== 'true'); });
    panel.querySelector('[data-visits-close]').addEventListener('click', function () { show(false); });
    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape' && trigger.getAttribute('aria-expanded') === 'true') show(false);
    });
})();
