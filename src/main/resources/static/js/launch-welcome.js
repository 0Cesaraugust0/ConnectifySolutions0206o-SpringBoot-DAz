(function () {
    'use strict';
    const dialog = document.getElementById('launchWelcome');
    if (!dialog || typeof dialog.showModal !== 'function') return;
    const seenKey = 'connectify.launch.2026-10-14.seen';
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
    let stopCelebration = function () {};
    let returnFocus;

    function celebrate() {
        if (reducedMotion.matches) return;
        const canvas = dialog.querySelector('.launch-confetti');
        const context = canvas.getContext('2d');
        if (!context) return;
        const width = dialog.clientWidth;
        const height = dialog.scrollHeight;
        const ratio = Math.min(window.devicePixelRatio || 1, 2);
        canvas.width = Math.round(width * ratio);
        canvas.height = Math.round(height * ratio);
        context.scale(ratio, ratio);
        const colors = ['#ebbb66', '#9b95e7', '#6bbcc6', '#de8a99', '#ffffff'];
        const particles = Array.from({ length: 86 }, function (_, i) {
            const side = i % 2 === 0 ? 1 : -1;
            return {
                x: side === 1 ? width * .12 : width * .88,
                y: 140,
                vx: side * (90 + Math.random() * 190),
                vy: -110 - Math.random() * 210,
                size: 3 + Math.random() * 4,
                angle: Math.random() * Math.PI,
                spin: (Math.random() - .5) * 8,
                color: colors[i % colors.length],
                delay: (i % 3) * .18
            };
        });
        let frame;
        let started;
        let previous;
        function draw(now) {
            if (!started) started = previous = now;
            const elapsed = (now - started) / 1000;
            const delta = Math.min((now - previous) / 1000, .035);
            previous = now;
            context.clearRect(0, 0, width, height);
            particles.forEach(function (particle) {
                if (elapsed < particle.delay) return;
                particle.x += particle.vx * delta;
                particle.y += particle.vy * delta;
                particle.vy += 210 * delta;
                particle.angle += particle.spin * delta;
                context.save();
                context.globalAlpha = Math.max(0, Math.min(1, (3.8 - elapsed) / .8));
                context.translate(particle.x, particle.y);
                context.rotate(particle.angle);
                context.fillStyle = particle.color;
                context.fillRect(-particle.size / 2, -particle.size / 2, particle.size, particle.size * .55);
                context.restore();
            });
            if (elapsed < 3.8 && dialog.open && !reducedMotion.matches) frame = requestAnimationFrame(draw);
            else context.clearRect(0, 0, width, height);
        }
        frame = requestAnimationFrame(draw);
        stopCelebration = function () {
            cancelAnimationFrame(frame);
            context.clearRect(0, 0, width, height);
        };
    }

    function openWelcome() {
        if (dialog.open) return;
        returnFocus = document.activeElement;
        dialog.showModal();
        document.documentElement.classList.add('launch-welcome-open');
        try { sessionStorage.setItem(seenKey, 'true'); } catch (ignored) { /* Storage can be disabled. */ }
        celebrate();
    }

    document.querySelectorAll('[data-launch-open]').forEach(function (button) {
        button.addEventListener('click', openWelcome);
    });
    dialog.querySelectorAll('[data-launch-close]').forEach(function (button) {
        button.addEventListener('click', function () { dialog.close(); });
    });
    dialog.addEventListener('click', function (event) {
        if (event.target !== dialog) return;
        const bounds = dialog.getBoundingClientRect();
        if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) dialog.close();
    });
    dialog.addEventListener('close', function () {
        stopCelebration();
        document.documentElement.classList.remove('launch-welcome-open');
        if (returnFocus && typeof returnFocus.focus === 'function') returnFocus.focus({ preventScroll: true });
    });
    let alreadySeen = false;
    try { alreadySeen = sessionStorage.getItem(seenKey) === 'true'; } catch (ignored) { /* Show once for this page if storage is disabled. */ }
    if (!alreadySeen) openWelcome();
})();
