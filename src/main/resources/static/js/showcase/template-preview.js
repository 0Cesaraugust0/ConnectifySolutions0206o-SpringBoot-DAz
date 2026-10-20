(() => {
    'use strict';
    const comparison = document.querySelector('.presentation-comparison');
    const page = document.querySelector('.market-detail-page');
    const layout = page?.querySelector('.market-layout');
    if (!comparison || !layout) return;
    const options = [...comparison.querySelectorAll('[data-preview-template]')];
    function selectTemplate(option, updateUrl) {
        if (!option) return;
        layout.dataset.template = option.dataset.previewTemplate;
        const badge = layout.querySelector('.template-badge');
        if (badge) badge.textContent = option.dataset.previewLabel;
        comparison.querySelector('[data-preview-current]').textContent = option.dataset.previewLabel;
        options.forEach(item => item.setAttribute('aria-current', String(item === option)));
        if (updateUrl) {
            const url = new URL(location.href);
            url.searchParams.set('template', option.dataset.previewTemplate);
            history.replaceState(null, '', url);
        }
    }
    options.forEach(option => option.addEventListener('click', event => {
        if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
        event.preventDefault();
        selectTemplate(option, true);
    }));
    comparison.querySelector('[data-preview-mobile]').addEventListener('click', event => {
        const mobile = page.classList.toggle('preview-mobile');
        event.currentTarget.setAttribute('aria-pressed', String(mobile));
        event.currentTarget.textContent = mobile ? 'Vista escritorio' : 'Vista móvil';
    });
})();
