(() => {
    'use strict';

    const toggle = document.getElementById('include-billing-address');
    const fields = document.getElementById('billing-address-fields');
    if (!toggle || !fields) return;

    const state = toggle.closest('.checkout-billing-toggle')?.querySelector('.billing-toggle-state');

    function applyState() {
        const active = toggle.checked;
        fields.hidden = !active;
        fields.disabled = !active;
        toggle.setAttribute('aria-expanded', String(active));
        if (state) state.textContent = active ? 'Activado' : 'Añadir';
        if (active) fields.querySelector('input')?.focus({preventScroll: true});
    }

    toggle.addEventListener('change', applyState);
    applyState();
})();
