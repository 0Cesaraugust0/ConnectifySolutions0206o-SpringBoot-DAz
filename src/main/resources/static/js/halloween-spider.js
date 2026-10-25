(function () {
    'use strict';
    const scene = document.querySelector('[data-halloween-scene]');
    if (!scene) return;
    const pause = scene.querySelector('[data-halloween-pause]');
    const close = scene.querySelector('[data-halloween-close]');
    scene.classList.add('has-controls');
    pause.addEventListener('click', function () {
        const paused = scene.classList.toggle('is-paused');
        pause.setAttribute('aria-pressed', String(paused));
        pause.setAttribute('aria-label', paused ? 'Reanudar animación de Halloween' : 'Pausar animación de Halloween');
        pause.title = paused ? 'Reanudar animación' : 'Pausar animación';
        pause.firstElementChild.textContent = paused ? '▷' : 'Ⅱ';
    });
    close.addEventListener('click', function () { scene.hidden = true; });
})();
