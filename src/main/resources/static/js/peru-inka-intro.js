(() => {
    'use strict';
    const scene = document.querySelector('#peruInkaScene');
    const triggers = Array.from(document.querySelectorAll('[data-peru-intro]'));
    const trigger = triggers[0];
    if (!scene || !trigger) return;
    const motion = window.matchMedia('(prefers-reduced-motion: reduce)');
    const wave = scene.querySelector('[data-peru-wave]');
    let frame = 0;
    let finish = 0;

    function stop() {
        cancelAnimationFrame(frame);
        clearTimeout(finish);
        scene.classList.remove('is-playing');
        triggers.forEach(item => {
            item.classList.remove('is-playing');
            item.setAttribute('aria-label', 'Repetir presentación Espíritu del Perú');
        });
        if (wave) wave.setAttribute('scale', '0');
    }
    function play(manual = false) {
        stop();
        if (document.hidden || (motion.matches && !manual)) return;
        // Replays restart cleanly, without stacking callbacks or moving the hero layout.
        void scene.offsetWidth;
        scene.classList.add('is-playing');
        triggers.forEach(item => {
            item.classList.add('is-playing');
            item.setAttribute('aria-label', 'Detener presentación Espíritu del Perú');
        });
        const started = performance.now();
        function tick(now) {
            const elapsed = now - started;
            if (elapsed >= 6000) { stop(); return; }
            if (wave) wave.setAttribute('scale', (3 + Math.sin(elapsed / 320) * 3).toFixed(2));
            frame = requestAnimationFrame(tick);
        }
        if (!motion.matches) frame = requestAnimationFrame(tick);
        finish = window.setTimeout(stop, 6000);
    }
    triggers.forEach(item => item.addEventListener('click', () => {
            if (scene.classList.contains('is-playing')) { stop(); return; }
            const closeVisits = document.querySelector('[data-visits-close]');
            if (document.querySelector('[data-visits-window].is-visits-open')) closeVisits?.click();
            play(true);
        }));
    document.addEventListener('visibilitychange', () => { if (document.hidden) stop(); });
    motion.addEventListener('change', stop);
    window.addEventListener('pagehide', stop);
    function startWhenVisible() {
        const welcome = document.querySelector('#launchWelcome');
        if (welcome?.open) welcome.addEventListener('close', () => play(), { once: true });
        else play();
    }
    const preview = trigger.querySelector('img');
    if (preview?.complete && preview.naturalWidth) startWhenVisible();
    else preview?.addEventListener('load', startWhenVisible, { once: true });
})();
