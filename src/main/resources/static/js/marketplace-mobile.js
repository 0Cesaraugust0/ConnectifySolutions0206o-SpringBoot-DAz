(() => {
    'use strict';
    const menuButton = document.querySelector('[data-marketplace-menu]');
    const navigation = document.querySelector('#marketplacePrimaryNav');
    const filterButton = document.querySelector('[data-marketplace-mobile-filters]');
    const searchPanel = document.querySelector('.hero-search-panel');
    const mobile = window.matchMedia('(max-width: 720px)');

    function closeMenu() {
        if (!menuButton || !navigation) return;
        navigation.classList.remove('is-open');
        menuButton.setAttribute('aria-expanded', 'false');
    }

    menuButton?.addEventListener('click', () => {
        const open = !navigation.classList.contains('is-open');
        navigation.classList.toggle('is-open', open);
        menuButton.setAttribute('aria-expanded', String(open));
    });

    navigation?.addEventListener('click', event => {
        if (event.target.closest('a, button')) closeMenu();
    });

    filterButton?.addEventListener('click', () => {
        const open = !searchPanel.classList.contains('mobile-filters-open');
        searchPanel.classList.toggle('mobile-filters-open', open);
        filterButton.setAttribute('aria-expanded', String(open));
        filterButton.querySelector('b').textContent = open ? '−' : '＋';
    });

    document.addEventListener('click', event => {
        if (!mobile.matches || !navigation?.classList.contains('is-open')) return;
        if (!navigation.contains(event.target) && !menuButton.contains(event.target)) closeMenu();
    });

    window.addEventListener('resize', () => {
        if (!mobile.matches) {
            closeMenu();
            searchPanel?.classList.remove('mobile-filters-open');
            filterButton?.setAttribute('aria-expanded', 'false');
        }
    });
})();
