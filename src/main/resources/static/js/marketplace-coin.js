const marketplaceCoin = document.querySelector('[data-marketplace-coin]');

marketplaceCoin?.addEventListener('click', () => {
    const isGold = marketplaceCoin.getAttribute('aria-pressed') === 'true';
    marketplaceCoin.setAttribute('aria-pressed', String(!isGold));
});
