(function () {
    'use strict';
    const scene = document.querySelector('[data-halloween-scene]');
    if (!scene) return;
    const pause = scene.querySelector('[data-halloween-pause]');
    const close = scene.querySelector('[data-halloween-close]');
    const optics = scene.querySelector('.spider-binoculars');
    const label = scene.querySelector('.spider-halloween-label');
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
    let fallen = false, drag = null, fallAnimation, greetingTimer;
    function clearGreeting() {
        clearTimeout(greetingTimer);
        scene.classList.remove('is-greeting');
    }
    function greet() {
        clearGreeting();
        if (reducedMotion.matches || scene.classList.contains('is-paused')) return;
        scene.classList.add('is-greeting');
        greetingTimer = setTimeout(clearGreeting, 1600);
    }
    const toy = document.createElement('button');
    toy.type = 'button';
    toy.className = 'halloween-fallen-optics';
    toy.hidden = true;
    toy.setAttribute('aria-label', 'Devolver binoculares');
    const toyArt = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    toyArt.setAttribute('viewBox', '72 108 96 60');
    toyArt.setAttribute('aria-hidden', 'true');
    toyArt.append(scene.querySelector('svg defs').cloneNode(true), optics.cloneNode(true));
    // Isolate the duplicate paint and clipping definitions from the illustration.
    toyArt.querySelectorAll('[id]').forEach(node => { node.id = 'fallen-' + node.id; });
    toyArt.querySelectorAll('*').forEach(node => {
        for (const attr of [...node.attributes]) {
            if (attr.value.includes('url(#')) node.setAttribute(attr.name, attr.value.replaceAll('url(#', 'url(#fallen-'));
        }
        node.removeAttribute('tabindex');
        node.removeAttribute('role');
    });
    toy.append(toyArt);
    document.body.append(toy);
    function bounds() {
        const viewport = window.visualViewport;
        return {left: viewport?.offsetLeft || 0, top: viewport?.offsetTop || 0,
            width: viewport?.width || innerWidth, height: viewport?.height || innerHeight};
    }
    function place(x, y) {
        const b = bounds();
        toy.style.left = Math.max(b.left, Math.min(b.left + b.width - toy.offsetWidth, x)) + 'px';
        toy.style.top = Math.max(b.top, Math.min(b.top + b.height - toy.offsetHeight - 2, y)) + 'px';
    }
    function floor() {
        if (!fallen || drag) return;
        const b = bounds();
        place(parseFloat(toy.style.left), b.top + b.height - toy.offsetHeight - 2);
    }
    function giveBack() {
        fallAnimation?.cancel();
        fallen = false;
        toy.hidden = true;
        scene.classList.remove('has-fallen-optics', 'is-return-target');
        optics.removeAttribute('tabindex');
        optics.setAttribute('tabindex', '0');
        optics.focus({preventScroll: true});
        greet();
    }
    function drop() {
        if (fallen) return;
        clearGreeting();
        scene.classList.add('is-interactive', 'has-fallen-optics');
        const origin = optics.getBoundingClientRect();
        fallen = true;
        optics.setAttribute('tabindex', '-1');
        toy.hidden = false;
        toy.style.width = Math.max(76, origin.width) + 'px';
        place(origin.left, origin.top);
        const start = parseFloat(toy.style.top);
        floor();
        const distance = start - parseFloat(toy.style.top);
        if (!reducedMotion.matches) fallAnimation = toy.animate([
            {transform: `translateY(${distance}px) rotate(0deg)`, offset: 0},
            {transform: 'translateY(0) rotate(-9deg)', offset: .78},
            {transform: 'translateY(-16px) rotate(5deg)', offset: .9},
            {transform: 'translateY(0) rotate(0deg)', offset: 1}
        ], {duration: 1000, easing: 'ease-in'});
        toy.focus({preventScroll: true});
    }
    [optics, label].forEach(element => {
        element.setAttribute('role', 'button');
        element.setAttribute('tabindex', '0');
        element.addEventListener('keydown', event => {
            if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                element === optics ? drop() : openParty();
            }
        });
    });
    optics.setAttribute('aria-label', 'Dejar caer los binoculares');
    label.setAttribute('aria-label', 'Halloween: ver celebración');
    label.setAttribute('aria-haspopup', 'dialog');
    optics.addEventListener('click', drop);
    toy.addEventListener('keydown', event => {
        if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); giveBack(); }
    });
    toy.addEventListener('pointerdown', event => {
        if (!fallen || event.button !== 0) return;
        fallAnimation?.cancel();
        const rect = toy.getBoundingClientRect();
        drag = {id: event.pointerId, x: event.clientX - rect.left, y: event.clientY - rect.top};
        toy.setPointerCapture(event.pointerId);
        toy.classList.add('is-dragging');
    });
    function atFace() {
        const a = optics.getBoundingClientRect(), b = toy.getBoundingClientRect();
        return Math.hypot((a.left + a.width / 2) - (b.left + b.width / 2),
            (a.top + a.height / 2) - (b.top + b.height / 2)) < Math.max(45, a.width * .75);
    }
    toy.addEventListener('pointermove', event => {
        if (!drag || drag.id !== event.pointerId) return;
        place(event.clientX - drag.x, event.clientY - drag.y);
        scene.classList.toggle('is-return-target', atFace());
    });
    function endDrag(event) {
        if (!drag || drag.id !== event.pointerId) return;
        drag = null;
        toy.classList.remove('is-dragging');
        scene.classList.remove('is-return-target');
        if (toy.hasPointerCapture(event.pointerId)) toy.releasePointerCapture(event.pointerId);
        if (event.type !== 'pointercancel' && atFace()) giveBack();
        else floor();
    }
    toy.addEventListener('pointerup', endDrag);
    toy.addEventListener('pointercancel', endDrag);
    toy.addEventListener('lostpointercapture', endDrag);
    window.addEventListener('resize', floor);
    window.visualViewport?.addEventListener('resize', floor);
    window.visualViewport?.addEventListener('scroll', floor);

    // Licensed footage is served locally; opening the dialog is the playback gesture.
    const party = document.createElement('dialog');
    party.className = 'halloween-party';
    party.setAttribute('aria-labelledby', 'halloween-party-title');
    party.innerHTML = `<button type="button" class="halloween-party-close" aria-label="Cerrar celebración">×</button>
        <p class="halloween-party-eyebrow">UNA MIRADA A LA FIESTA</p>
        <h2 id="halloween-party-title">¡Feliz Halloween!</h2>
        <p class="halloween-party-subtitle">Aooohhmmm... Un Feliz Halloween</p>
        <div class="halloween-party-optics" aria-hidden="true"></div>
        <p class="halloween-party-caption" role="status">Luces, música y buenos momentos.</p>
        <button type="button" class="halloween-party-retry" hidden>Reintentar video</button>
        <div class="halloween-party-progress" aria-hidden="true"></div>`;
    const lenses = party.querySelector('.halloween-party-optics');
    const clips = [
        {src: '/images/halloween-party-7s.mp4?v=couple-2', poster: '/images/halloween-party-couple.jpg', name: '...Mmmm   me dice para bailar', positions: ['0% 50%', '100% 50%']},
        {src: '/images/halloween-party-toast-7s.mp4', poster: '/images/halloween-party-toast.jpg', name: '...Unas bebidas y a celebrarlo', positions: ['25% 65%', '75% 65%']},
        {src: '/images/halloween-party-confetti-7s.mp4?v=young-2', poster: '/images/halloween-party-confetti.jpg?v=young-2', name: '...Llego Justo en el momento ... Vale x 2', positions: ['45% 50%', '55% 50%']}
    ];
    let nextClip = 0, currentClip = clips[0], progressFrame = 0;
    const videos = [];
    for (let lens = 0; lens < 2; lens++) {
        const view = document.createElement('div');
        view.className = 'halloween-party-lens';
        const video = document.createElement('video');
        video.className = 'party-real-video';
        video.muted = true;
        video.defaultMuted = true;
        video.playsInline = true;
        video.preload = 'none';
        video.setAttribute('disablepictureinpicture', '');
        video.setAttribute('tabindex', '-1');
        videos.push(video);
        view.append(video);
        for (let i = 0; i < 20; i++) {
            const confetti = document.createElement('i');
            confetti.className = 'party-confetti';
            confetti.style.setProperty('--i', i);
            confetti.style.left = `${(i * 37 + lens * 11) % 100}%`;
            view.append(confetti);
        }
        lenses.append(view);
    }
    document.body.append(party);
    const caption = party.querySelector('.halloween-party-caption');
    const retry = party.querySelector('.halloween-party-retry');
    const progress = party.querySelector('.halloween-party-progress');
    let playbackId = 0;
    let savedOverflow = '';
    function stopProgress() {
        cancelAnimationFrame(progressFrame);
        progressFrame = 0;
    }
    function updateProgress() {
        if (!party.open) return;
        const elapsed = videos[0].currentTime;
        const duration = Number.isFinite(videos[0].duration) ? videos[0].duration : 7;
        progress.style.transform = `scaleX(${Math.max(0, 1 - elapsed / duration)})`;
        progressFrame = requestAnimationFrame(updateProgress);
    }
    function playbackFailed(id) {
        if (id !== playbackId || !party.open) return;
        videos.forEach(video => video.pause());
        stopProgress();
        caption.textContent = 'No se pudo reproducir el video.';
        retry.hidden = false;
    }
    function playParty() {
        const id = ++playbackId;
        stopProgress();
        retry.hidden = true;
        caption.textContent = 'Cargando celebración…';
        progress.style.transform = 'scaleX(1)';
        videos.forEach((video, index) => {
            video.pause();
            video.poster = currentClip.poster;
            video.style.objectPosition = currentClip.positions[index];
            if (video.getAttribute('src') !== currentClip.src) video.src = currentClip.src;
            video.currentTime = 0;
            video.play().catch(() => playbackFailed(id));
        });
        progressFrame = requestAnimationFrame(updateProgress);
    }
    videos[0].addEventListener('playing', () => {
        if (party.open) caption.textContent = currentClip.name;
    });
    videos[0].addEventListener('ended', () => { if (party.open) party.close(); });
    videos.forEach(video => video.addEventListener('error', () => playbackFailed(playbackId)));
    retry.addEventListener('click', playParty);
    function openParty() {
        if (party.open) return;
        currentClip = clips[nextClip];
        nextClip = (nextClip + 1) % clips.length;
        savedOverflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        party.showModal();
        playParty();
    }
    label.addEventListener('click', openParty);
    party.querySelector('.halloween-party-close').addEventListener('click', () => party.close());
    party.addEventListener('close', () => {
        playbackId++;
        stopProgress();
        videos.forEach(video => video.pause());
        document.body.style.overflow = savedOverflow;
        if (!scene.hidden) label.focus({preventScroll: true});
    });
    scene.classList.add('has-controls');
    pause.addEventListener('click', function () {
        const paused = scene.classList.toggle('is-paused');
        pause.setAttribute('aria-pressed', String(paused));
        pause.setAttribute('aria-label', paused ? 'Reanudar animación de Halloween' : 'Pausar animación de Halloween');
        pause.title = paused ? 'Reanudar animación' : 'Pausar animación';
        pause.firstElementChild.textContent = paused ? '▷' : 'Ⅱ';
    });
    close.addEventListener('click', function () {
        clearGreeting();
        if (party.open) party.close();
        fallAnimation?.cancel();
        toy.hidden = true;
        scene.hidden = true;
    });
})();
