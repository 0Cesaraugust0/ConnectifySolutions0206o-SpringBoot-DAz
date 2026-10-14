const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const code = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/checkout-izipay.js'), 'utf8');
function setup(responses, ready = true) {
    const nodes = {};
    for (const id of ['izipay-checkout-form','izipay-pay-button','izipay-payment-status','izipay-status-text',
        'izipay-order-reference','izipay-check-status','izipay-reopen','izipay-sign-in']) {
        nodes[id] = {hidden: true, disabled: false, textContent: '', dataset: {}, handlers: {},
            addEventListener(name, fn) { this.handlers[name] = fn; }, setAttribute() {},
            reportValidity() { return true; }, querySelector() { return this.label ||= {textContent:''}; }};
    }
    const form = nodes['izipay-checkout-form'];
    form.dataset = {ready: String(ready), statusUrl:'/cart/checkout/izipay/status',verifyUrl:'/cart/checkout/izipay/verify'};
    form.action = '/cart/checkout/izipay/session';
    const calls = [], redirects = [];
    let options;
    const sandbox = {
        document:{getElementById:id=>nodes[id]}, sessionStorage:{getItem(){return null;},setItem(){},removeItem(){}},
        URL, AbortSignal, FormData: class {}, console,
        fetch:async (url, opts) => {
            calls.push({url, opts});
            const response = responses.shift();
            if (response instanceof Error) throw response;
            return {ok:true,status:200,redirected:false,headers:{get:()=> 'application/json'},
                json: async () => response};
        },
        window:{Izipay:class {constructor(arg){this.config=arg.config;} LoadForm(arg){options=arg;}},
            clearTimeout(){},setTimeout(){return 1;},addEventListener(){},
            location:{origin:'http://localhost:8080',assign(url){redirects.push(url);}}}
    };
    vm.runInNewContext(code,sandbox);
    return {nodes,calls,redirects, callback:r=>options.callbackResponse(r),
        submit:()=>form.handlers.submit({preventDefault(){}})};
}
const session = {orderNumber:'ABC123',verificationToken:'local-token',authorization:'sdk-token',keyRSA:'public-fixture',
    config:{render:{typeForm:'pop-up'}}};
test('double submit opens one order and only server confirmation redirects', async () => {
    const app=setup([session,{status:'COMPLETED',redirectUrl:'/cart/confirmation/42'}]);
    await app.submit(); await app.submit();
    assert.equal(app.calls.length,1);
    assert.equal(app.redirects.length,0);
    await app.callback({code:'00',payloadHttp:'signed fixture',signature:'signature fixture'});
    assert.equal(app.calls.length,2);
    assert.deepEqual(app.redirects,['/cart/confirmation/42']);
    assert.equal(JSON.parse(app.calls[1].opts.body).verificationToken,'local-token');
});
test('unsigned success/cancel never creates tickets or calls verification', async () => {
    const app=setup([session]);
    await app.submit();
    await app.callback({code:'00'});
    assert.equal(app.calls.length,1);
    assert.equal(app.redirects.length,0);
    assert.equal(app.nodes['izipay-check-status'].hidden,false);
});
test('network loss during signed callback retains order for status recovery', async () => {
    const app=setup([session,new Error('Network unavailable')]);
    await app.submit();
    await app.callback({payloadHttp:'signed fixture',signature:'signature fixture'});
    assert.equal(app.redirects.length,0);
    assert.equal(app.nodes['izipay-check-status'].hidden,false);
    assert.match(app.nodes['izipay-order-reference'].textContent,/ABC123/);
});
test('no credentials means no outgoing payment request', async () => {
    const app=setup([],false);
    await app.submit();
    assert.equal(app.calls.length,0);
});
test('server receipt cannot redirect outside the application', async () => {
    const app=setup([session,{status:'COMPLETED',redirectUrl:'https://example.test'}]);
    await app.submit();
    await app.callback({payloadHttp:'signed fixture',signature:'signature fixture'});
    assert.equal(app.redirects.length,0);
});
