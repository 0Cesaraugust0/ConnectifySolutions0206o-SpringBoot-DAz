(function () {
    let activeRequest;

    function syncFilterLinks(page) {
        const currentLinks = document.querySelectorAll('a[data-marketplace-filter]');
        const nextLinks = page.querySelectorAll('a[data-marketplace-filter]');
        currentLinks.forEach(function (link, index) {
            const nextLink = nextLinks[index];
            if (!nextLink) return;
            link.href = nextLink.href;
            link.classList.toggle('active', nextLink.classList.contains('active'));
            link.hidden = nextLink.hidden;
            if (nextLink.hasAttribute('aria-current')) {
                link.setAttribute('aria-current', nextLink.getAttribute('aria-current'));
            } else {
                link.removeAttribute('aria-current');
            }
        });
    }

    function syncFilterSummary(page) {
        document.querySelectorAll('.hero-search-panel [name]').forEach(function (field) {
            const nextField = page.querySelector('.hero-search-panel [name="' + field.name + '"]');
            if (nextField) field.value = nextField.value;
        });
        const currentCount = document.querySelector('.professional-stats span:first-child strong');
        const nextCount = page.querySelector('.professional-stats span:first-child strong');
        if (currentCount && nextCount) currentCount.textContent = nextCount.textContent;
    }

    async function refreshResults(url, updateHistory) {
        const currentResults = document.getElementById('marketplaceCards');
        if (!currentResults) return;

        if (activeRequest) activeRequest.abort();
        activeRequest = new AbortController();
        currentResults.classList.add('is-loading');
        currentResults.setAttribute('aria-busy', 'true');

        try {
            const response = await fetch(url, {
                signal: activeRequest.signal,
                headers: { 'X-Requested-With': 'XMLHttpRequest' }
            });
            if (!response.ok) throw new Error('No se pudieron actualizar los eventos.');

            const page = new DOMParser().parseFromString(await response.text(), 'text/html');
            const nextResults = page.getElementById('marketplaceCards');
            if (!nextResults) throw new Error('La respuesta no contiene resultados.');

            syncFilterLinks(page);
            syncFilterSummary(page);
            currentResults.replaceWith(nextResults);
            if (updateHistory) history.pushState({ marketplaceFilter: true }, '', url);
        } catch (error) {
            if (error.name === 'AbortError') return;
            window.location.assign(url);
        } finally {
            const visibleResults = document.getElementById('marketplaceCards');
            if (visibleResults) {
                visibleResults.classList.remove('is-loading');
                visibleResults.removeAttribute('aria-busy');
            }
        }
    }

    document.addEventListener('click', function (event) {
        const link = event.target.closest('a[data-marketplace-filter]');
        if (!link || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
        event.preventDefault();
        link.parentElement.querySelectorAll('a[data-marketplace-filter]').forEach(function (item) {
            item.classList.toggle('active', item === link);
        });
        refreshResults(link.href, true);
    });

    window.addEventListener('popstate', function () {
        refreshResults(window.location.href, false);
    });
})();
