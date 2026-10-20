import QrScanner from './vendor/qr-scanner-1.4.2/qr-scanner.min.js';

// A camera read only fills the form. Admission remains a deliberate server-side action.
(() => {
    const root = document.getElementById('gateScanner');
    if (!root) return;
    const byId = id => document.getElementById(id);
    const startButton = byId('gateScanStart');
    const cameraPanel = byId('gateCameraPanel');
    const video = byId('gateCameraVideo');
    const status = byId('gateScannerStatus');
    const input = byId('ticketCode');
    const form = byId('gateTicketForm');
    const resultPanel = byId('gateResultPanel');
    const resultCurrent = byId('gateResultCurrent');
    const resultStale = byId('gateResultStale');
    let scanner = null;
    let generation = 0;
    let starting = false;

    function message(text, kind = '') {
        status.textContent = text;
        status.classList.toggle('is-error', kind === 'error');
        status.classList.toggle('is-success', kind === 'success');
    }

    function updateResult() {
        const changed = input.value.trim() !== (resultPanel.dataset.gateResultCode || '').trim();
        resultCurrent.hidden = changed;
        resultStale.hidden = !changed;
    }

    function releaseVideo() {
        const stream = video.srcObject;
        if (stream && typeof stream.getTracks === 'function') {
            stream.getTracks().forEach(track => track.stop());
        }
        video.srcObject = null;
    }

    function stopCamera() {
        generation++;
        const previous = scanner;
        scanner = null;
        // Release tracks immediately; the decoder's own stop has a short grace period.
        releaseVideo();
        if (previous) previous.destroy();
        cameraPanel.hidden = true;
        startButton.setAttribute('aria-expanded', 'false');
        startButton.disabled = starting;
    }

    async function startCamera() {
        if (starting || scanner) return;
        if (!window.isSecureContext) {
            message('Para usar la cámara, abre esta página por HTTPS. Puedes ingresar el código manualmente.', 'error');
            return;
        }
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            message('Este navegador no permite abrir la cámara. Usa Chrome o Safari actualizado, o ingresa el código.', 'error');
            return;
        }
        const cycle = ++generation;
        starting = true;
        startButton.disabled = true;
        startButton.setAttribute('aria-expanded', 'true');
        cameraPanel.hidden = false;
        message('Permite usar la cámara y apunta al QR de la entrada.');
        let instance;
        try {
            instance = new QrScanner(video, result => {
                if (cycle !== generation || scanner !== instance) return;
                const code = typeof result === 'string' ? result.trim() : String(result?.data || '').trim();
                if (!code || code.length > input.maxLength) {
                    message('Ese QR no contiene un código de entrada válido. Prueba otro QR o ingresa el código.', 'error');
                    return;
                }
                input.value = code;
                updateResult();
                stopCamera();
                message('QR leído. Elige Consultar o Validar y registrar ingreso.', 'success');
            }, {
                preferredCamera: 'environment',
                returnDetailedScanResult: true,
                maxScansPerSecond: 8,
                onDecodeError(error) {
                    if (cycle !== generation) return;
                    // Native BarcodeDetector wraps an empty frame; the worker returns the plain message.
                    const detail = String(error?.message || error).replace(/^Scanner error:\s*/i, '').trim();
                    if (detail === String(QrScanner.NO_QR_CODE_FOUND)) return;
                    // A frame error must not tear down a working camera while the operator aims the QR.
                    message('No se pudo leer la imagen. Mantén el QR dentro del marco; si continúa, cierra y vuelve a abrir la cámara.', 'error');
                }
            });
            scanner = instance;
            await instance.start();
            if (cycle !== generation || document.hidden) {
                instance.destroy();
                releaseVideo();
                if (scanner === instance) stopCamera();
                return;
            }
            message('Cámara lista. Centra el QR dentro del marco.');
        } catch (error) {
            if (cycle === generation) {
                stopCamera();
                message('No se pudo abrir la cámara. Revisa el permiso del navegador, cierra otras apps que la usen o ingresa el código.', 'error');
            }
        } finally {
            starting = false;
            startButton.disabled = false;
        }
    }

    startButton.addEventListener('click', startCamera);
    byId('gateCameraClose').addEventListener('click', () => {
        stopCamera();
        message('Cámara cerrada. Puedes escanear otra entrada o ingresar su código.');
    });
    input.addEventListener('focus', () => {
        if (!scanner && !starting) return;
        stopCamera();
        message('Ingresa el código y elige Consultar o Validar y registrar ingreso.');
    });
    input.addEventListener('input', updateResult);
    form.addEventListener('submit', stopCamera);
    window.addEventListener('pagehide', stopCamera);
    document.addEventListener('visibilitychange', () => {
        if (!document.hidden || (!scanner && !starting)) return;
        stopCamera();
        message('La cámara se detuvo al salir de la pantalla. Pulsa Escanear QR para volver a abrirla.');
    });
})();
