(() => {
    'use strict';
    const form = document.getElementById('izipay-checkout-form');
    if (!form) return;
    const pay = document.getElementById('izipay-pay-button');
    const label = pay.querySelector('span');
    const panel = document.getElementById('izipay-payment-status');
    const message = document.getElementById('izipay-status-text');
    const reference = document.getElementById('izipay-order-reference');
    const check = document.getElementById('izipay-check-status');
    const reopen = document.getElementById('izipay-reopen');
    const signIn = document.getElementById('izipay-sign-in');
    const ready = form.dataset.ready === 'true';
    const storageKey = 'connectify.izipay.pendingOrder';
    let activeSession = null;
    let orderNumber = null;
    let busy = false;
    let verifying = false;
    let pollTimer;
    let polls = 0;
    let polling = false;
    let checkout;
    try { orderNumber = sessionStorage.getItem(storageKey); } catch (_) { /* Storage is optional. */ }

    function rememberOrder(value) {
        orderNumber = value;
        reference.textContent = value ? 'Orden: ' + value : '';
        try {
            if (value) sessionStorage.setItem(storageKey, value);
            else sessionStorage.removeItem(storageKey);
        } catch (_) { /* A blocked storage must not interrupt verification. */ }
    }

    function show(text, error = false) {
        panel.hidden = false;
        panel.dataset.state = error ? 'error' : 'info';
        message.textContent = text;
    }

    function stopPolling() {
        polling = false;
        window.clearTimeout(pollTimer);
    }

    function setBusy(value, text) {
        busy = value;
        pay.disabled = value || !ready;
        form.setAttribute('aria-busy', String(value));
        label.textContent = text || 'Proceder al pago';
    }

    async function request(url, options = {}) {
        const response = await fetch(url, {
            ...options, credentials: 'same-origin',
            headers: { Accept: 'application/json', ...(options.headers || {}) },
            signal: AbortSignal.timeout(45000)
        });
        if (response.redirected || response.status === 401 || response.status === 403) {
            signIn.hidden = false;
            throw new Error('Tu sesión ha terminado. Inicia sesión para consultar el estado del pago.');
        }
        if (!(response.headers.get('content-type') || '').includes('application/json')) {
            throw new Error('No pudimos consultar el pago. Vuelve a consultar su estado antes de intentarlo otra vez.');
        }
        const data = await response.json();
        if (!response.ok) {
            if (data.orderNumber) { rememberOrder(data.orderNumber); check.hidden = false; }
            throw new Error(data.message || 'No pudimos preparar el pago. Inténtalo nuevamente.');
        }
        return data;
    }

    function complete(data) {
        if (data.status !== 'COMPLETED') return false;
        // Redirect exclusively to a server-confirmed receipt in this application.
        if (typeof data.redirectUrl !== 'string' || !/^\/cart\/confirmation\/\d+$/.test(data.redirectUrl)) {
            throw new Error('El pago fue confirmado. Consulta tus tickets desde tu panel de cliente.');
        }
        stopPolling();
        rememberOrder(null);
        show('Pago confirmado. Abriendo tus entradas…');
        window.location.assign(data.redirectUrl);
        return true;
    }

    function applyStatus(data) {
        if (complete(data)) return;
        if (data.status === 'FAILED') {
            stopPolling();
            activeSession = null;
            rememberOrder(null);
            check.hidden = true;
            reopen.hidden = true;
            setBusy(false);
            show('El pago no fue aprobado. Puedes revisar tus datos e intentarlo nuevamente.', true);
        } else if (data.status === 'REVIEW') {
            stopPolling();
            reopen.hidden = true;
            setBusy(true, 'Pago en revisión');
            show('Recibimos el pago y estamos revisando la emisión de tus entradas. Conserva el número de orden y contacta a soporte.', true);
        } else {
            show('El pago sigue pendiente de confirmación. Si ya pagaste, consulta su estado antes de volver a intentarlo.');
            check.hidden = false;
            reopen.hidden = !activeSession;
            if (!activeSession) setBusy(false, 'Retomar pago');
        }
    }

    async function checkStatus(silent = false) {
        if (!orderNumber || verifying) return;
        check.disabled = true;
        try {
            const url = new URL(form.dataset.statusUrl, window.location.origin);
            url.searchParams.set('orderNumber', orderNumber);
            const data = await request(url);
            if (!silent || data.status !== 'PENDING') applyStatus(data);
        } catch (error) {
            stopPolling();
            show(error.message, true);
        } finally {
            check.disabled = false;
        }
    }

    function poll() {
        window.clearTimeout(pollTimer);
        if (!polling || !orderNumber || polls >= 24) return;
        pollTimer = window.setTimeout(async () => {
            polls++;
            await checkStatus(true);
            if (orderNumber && !signIn.hidden) return;
            poll();
        }, 5000);
    }

    async function handleResponse(result) {
        if (verifying || !activeSession) return;
        stopPolling();
        // A close/error event is not evidence of either a charge or a successful payment.
        if (!result || typeof result.payloadHttp !== 'string' || typeof result.signature !== 'string') {
            show('El formulario se cerró sin una confirmación de pago. Consulta el estado de tu orden si llegaste a pagar.');
            check.hidden = false;
            reopen.hidden = false;
            return;
        }
        verifying = true;
        reopen.hidden = true;
        show('Verificando el pago con IZIPay…');
        try {
            const data = await request(form.dataset.verifyUrl, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    orderNumber: activeSession.orderNumber,
                    verificationToken: activeSession.verificationToken,
                    result
                })
            });
            applyStatus(data);
        } catch (error) {
            show(error.message + ' Conserva esta orden y consulta su estado.', true);
            check.hidden = false;
        } finally {
            verifying = false;
        }
    }

    function openPayment() {
        if (!activeSession || verifying) return;
        if (typeof window.Izipay !== 'function') throw new Error('No se pudo cargar IZIPay. Revisa tu conexión y recarga esta página.');
        reopen.hidden = true;
        checkout = new window.Izipay({ config: activeSession.config });
        checkout.LoadForm({
            authorization: activeSession.authorization,
            keyRSA: activeSession.keyRSA,
            callbackResponse: handleResponse
        });
        show('Completa el pago en la ventana de IZIPay. Tus tickets se emitirán al confirmar la operación.');
        check.hidden = false;
        polls = 0;
        polling = true;
        poll();
    }

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        if (busy || !ready || !form.reportValidity()) return;
        setBusy(true, 'Preparando pago…');
        show('Preparando tu orden de pago…');
        try {
            if (typeof window.Izipay !== 'function') throw new Error('No se pudo cargar IZIPay. Revisa tu conexión y recarga esta página.');
            const data = await request(form.action, { method: 'POST', body: new FormData(form) });
            if (complete(data)) return;
            activeSession = data;
            rememberOrder(data.orderNumber);
            setBusy(true, 'Pago en curso');
            openPayment();
        } catch (error) {
            show(error.message, true);
            if (activeSession) {
                check.hidden = false;
                reopen.hidden = false;
            } else {
                setBusy(false, orderNumber ? 'Retomar pago' : 'Proceder al pago');
            }
        }
    });

    check.addEventListener('click', () => checkStatus());
    reopen.addEventListener('click', () => {
        try { openPayment(); } catch (error) { show(error.message, true); reopen.hidden = false; }
    });
    window.addEventListener('pagehide', stopPolling);
    if (orderNumber) {
        reference.textContent = 'Orden: ' + orderNumber;
        check.hidden = false;
        show('Consultando el estado de tu último intento de pago…');
        checkStatus();
    }
})();
