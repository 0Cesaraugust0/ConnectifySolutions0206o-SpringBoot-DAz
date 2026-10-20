const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/gate-scanner.js'), 'utf8')
    .replace(/^import QrScanner from [^\n]+\n/, '');

function setup({secure = true, media = true, start, previous = ''} = {}) {
    const nodes = {};
    for (const id of ['gateScanner','gateScanStart','gateCameraPanel','gateCameraVideo',
        'gateScannerStatus','ticketCode','gateTicketForm','gateResultPanel','gateResultCurrent',
        'gateResultStale','gateCameraClose']) {
        const classes = new Set();
        nodes[id] = {value:'', hidden:false, disabled:false, maxLength:255, dataset:{}, handlers:{}, attributes:{},
            classList:{toggle(name, enabled){enabled ? classes.add(name) : classes.delete(name);}},
            addEventListener(name, handler){this.handlers[name] = handler;},
            setAttribute(name, value){this.attributes[name] = value;},
            focus(){this.focused = true;}, select(){this.selected = true;},
            submit(){throw new Error('Camera must never submit a ticket');},
            requestSubmit(){throw new Error('Camera must never register a ticket');}};
    }
    nodes.gateCameraPanel.hidden = true;
    nodes.gateResultStale.hidden = true;
    nodes.gateResultPanel.dataset.gateResultCode = previous;
    nodes.ticketCode.value = previous;
    const instances = [], tracks = [];
    class Scanner {
        static NO_QR_CODE_FOUND = 'No QR code found';
        constructor(video, callback, options){
            Object.assign(this, {video, callback, options, starts:0, destroyed:0});
            instances.push(this);
        }
        async start(){
            this.starts++;
            if (start) await start();
            const track = {stopped:0, stop(){this.stopped++;}};
            tracks.push(track);
            this.video.srcObject = {getTracks:()=>[track]};
        }
        destroy(){this.destroyed++;}
    }
    const document = {hidden:false, handlers:{}, getElementById:id=>nodes[id],
        addEventListener(name, handler){this.handlers[name] = handler;}};
    const window = {isSecureContext:secure, handlers:{}, addEventListener(name, handler){this.handlers[name] = handler;}};
    const navigator = {mediaDevices: media ? {getUserMedia(){throw new Error('Use the decoder camera API');}} : undefined};
    vm.runInNewContext(source, {QrScanner:Scanner, document, window, navigator,
        fetch(){throw new Error('Scanning must not send any data');}});
    return {nodes, instances, tracks, document, window,
        click:id=>nodes[id].handlers.click(),
        read:data=>instances.at(-1).callback({data})};
}

test('camera waits for an explicit click, prefers rear camera and never submits on detection', async () => {
    const app = setup();
    assert.equal(app.instances.length, 0);
    assert.equal(app.nodes.ticketCode.focused, undefined);
    await app.click('gateScanStart');
    assert.equal(app.instances[0].options.preferredCamera, 'environment');
    assert.equal(app.instances[0].options.returnDetailedScanResult, true);
    assert.equal(app.nodes.gateCameraPanel.hidden, false);
    app.read(' CNF-22-AbCd0123456789abcdef0123456789abcd ');
    assert.equal(app.nodes.ticketCode.value, 'CNF-22-AbCd0123456789abcdef0123456789abcd');
    assert.equal(app.nodes.gateCameraPanel.hidden, true);
    assert.equal(app.nodes.gateScanStart.attributes['aria-expanded'], 'false');
    assert.ok(app.tracks[0].stopped > 0);
    assert.ok(app.instances[0].destroyed > 0);
    assert.match(app.nodes.gateScannerStatus.textContent, /QR leído.*Consultar/);
});

test('focusing the code field stops camera and prevents a late scan from overwriting manual input', async () => {
    const app = setup();
    await app.click('gateScanStart');
    app.nodes.ticketCode.focus();
    app.nodes.ticketCode.handlers.focus();
    assert.equal(app.nodes.ticketCode.focused, true);
    assert.ok(app.tracks[0].stopped > 0);
    app.read('CNF-22-LATE');
    assert.equal(app.nodes.ticketCode.value, '');
});

for (const action of ['close', 'submit', 'pagehide', 'hidden']) {
    test(`${action} releases the camera and ignores further decoded results`, async () => {
        const app = setup();
        await app.click('gateScanStart');
        if (action === 'close') app.click('gateCameraClose');
        if (action === 'submit') app.nodes.gateTicketForm.handlers.submit();
        if (action === 'pagehide') app.window.handlers.pagehide();
        if (action === 'hidden') { app.document.hidden = true; app.document.handlers.visibilitychange(); }
        assert.ok(app.tracks[0].stopped > 0);
        assert.equal(app.nodes.gateCameraVideo.srcObject, null);
        assert.equal(app.nodes.gateCameraPanel.hidden, true);
        app.read('CNF-22-LATE');
        assert.equal(app.nodes.ticketCode.value, '');
    });
}

test('denied or unavailable camera leaves manual operation available', async () => {
    const app = setup({start:()=>Promise.reject(new Error('Camera not found.'))});
    await app.click('gateScanStart');
    assert.equal(app.nodes.gateScanStart.disabled, false);
    assert.equal(app.nodes.gateCameraPanel.hidden, true);
    assert.match(app.nodes.gateScannerStatus.textContent, /permiso.*ingresa el código/);
    app.nodes.ticketCode.value = 'CNF-22-MANUAL';
    app.nodes.ticketCode.handlers.input();
    assert.equal(app.nodes.ticketCode.value, 'CNF-22-MANUAL');
});

test('HTTP on a phone and unsupported browsers do not request a camera', async () => {
    for (const config of [{secure:false}, {media:false}]) {
        const app = setup(config);
        await app.click('gateScanStart');
        assert.equal(app.instances.length, 0);
        assert.match(app.nodes.gateScannerStatus.textContent, /HTTPS|navegador/);
    }
});

test('cancelling a pending permission prevents another start and stops a late stream', async () => {
    let allow;
    const app = setup({start:()=>new Promise(resolve=>{allow=resolve;})});
    const pending = app.click('gateScanStart');
    await app.click('gateScanStart');
    assert.equal(app.instances.length, 1);
    app.click('gateCameraClose');
    await app.click('gateScanStart');
    assert.equal(app.instances.length, 1);
    allow();
    await pending;
    assert.ok(app.tracks[0].stopped > 0);
    assert.equal(app.nodes.gateCameraVideo.srcObject, null);
    assert.equal(app.nodes.gateScanStart.disabled, false);
    assert.equal(app.nodes.gateCameraPanel.hidden, true);
    app.read('CNF-22-LATE');
    assert.equal(app.nodes.ticketCode.value, '');
});

test('empty and oversized codes keep scanning without changing the previous ticket', async () => {
    const app = setup({previous:'CNF-22-1234ABCD'});
    await app.click('gateScanStart');
    app.read('   ');
    app.read('X'.repeat(256));
    assert.equal(app.nodes.ticketCode.value, 'CNF-22-1234ABCD');
    assert.equal(app.nodes.gateCameraPanel.hidden, false);
    assert.equal(app.tracks[0].stopped, 0);
});

test('a new typed or scanned code hides the old attendee result until the server checks it', async () => {
    const app = setup({previous:'CNF-22-1234ABCD'});
    app.nodes.ticketCode.value = 'CNF-22-NEW';
    app.nodes.ticketCode.handlers.input();
    assert.equal(app.nodes.gateResultCurrent.hidden, true);
    assert.equal(app.nodes.gateResultStale.hidden, false);
    app.nodes.ticketCode.value = 'CNF-22-1234ABCD';
    app.nodes.ticketCode.handlers.input();
    assert.equal(app.nodes.gateResultCurrent.hidden, false);
    await app.click('gateScanStart');
    app.read('CNF-22-87654321');
    assert.equal(app.nodes.gateResultCurrent.hidden, true);
});

for (const frameError of ['No QR code found', 'Scanner error: No QR code found',
    new Error('Scanner error: No QR code found')]) {
    test(`an empty frame (${String(frameError)}) keeps the camera open for a subsequent QR`, async () => {
        const app = setup();
        await app.click('gateScanStart');
        app.instances[0].options.onDecodeError(frameError);
        assert.equal(app.tracks[0].stopped, 0);
        assert.equal(app.instances[0].destroyed, 0);
        assert.equal(app.nodes.gateCameraPanel.hidden, false);
        assert.match(app.nodes.gateScannerStatus.textContent, /Cámara lista/);
        app.read('CNF-22-AFTER-EMPTY-FRAME');
        assert.equal(app.nodes.ticketCode.value, 'CNF-22-AFTER-EMPTY-FRAME');
        assert.match(app.nodes.gateScannerStatus.textContent, /QR leído/);
    });
}

test('a frame processing failure keeps the preview open and permits a later successful read', async () => {
    const app = setup();
    await app.click('gateScanStart');
    app.instances[0].options.onDecodeError(new Error('Scanner error: timeout'));
    assert.equal(app.tracks[0].stopped, 0);
    assert.equal(app.instances[0].destroyed, 0);
    assert.equal(app.nodes.gateCameraPanel.hidden, false);
    assert.match(app.nodes.gateScannerStatus.textContent, /imagen.*cierra.*cámara/);
    app.read('CNF-22-AFTER-RETRY');
    assert.equal(app.nodes.ticketCode.value, 'CNF-22-AFTER-RETRY');
    assert.ok(app.tracks[0].stopped > 0);
    assert.match(app.nodes.gateScannerStatus.textContent, /QR leído/);
});
