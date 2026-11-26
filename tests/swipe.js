// Tests the swipe code of app/assets/index.html (the lines between SWIPE-START and SWIPE-END) with stand-in page
// elements and finger events. It checks the rules, not how it feels. Run by tests/run.sh when node is installed.
const fs = require('fs');
const src = fs.readFileSync(__dirname + '/../app/assets/index.html', 'utf8');
const code = src.split('//SWIPE-START')[1].split('//SWIPE-END')[0];
let fails = 0, runs = 0;
function is(name, want, got) { runs++; if (JSON.stringify(want) !== JSON.stringify(got)) { fails++; console.log('FAIL swipe: ' + name + ': wanted ' + JSON.stringify(want) + ' got ' + JSON.stringify(got)); } }

function node(name) {
  const classes = new Set(), on = {};
  return {
    name, offsetWidth: 300, style: {}, children: [], parentNode: null,
    classList: {
      add: (...c) => c.forEach(x => classes.add(x)), remove: (...c) => c.forEach(x => classes.delete(x)),
      contains: c => classes.has(c), toggle: (c, v) => { v ? classes.add(c) : classes.delete(c); }
    },
    addEventListener: (t, f) => { on[t] = f; }, fire: (t, e) => on[t] && on[t](e || {}),
    insertBefore(c, next) { c.parentNode = this; this.children.splice(next ? this.children.indexOf(next) : this.children.length, 0, c); },
    removeChild(c) { c.parentNode = null; this.children.splice(this.children.indexOf(c), 1); },
    get nextSibling() { const p = this.parentNode; return p ? p.children[p.children.indexOf(this) + 1] || null : null; }
  };
}

function page(store) {
  const calls = [], els = { swtext: node('swtext'), swundo: node('swundo'), swbar: node('swbar') };
  const list = node('list'), timers = [];
  const env = {
    $: id => els[id], tab: 'feed', document: { querySelector: () => list.children.length > 1 ? list.children[0] : null },
    localStorage: { getItem: k => store[k] || null, setItem: (k, v) => { store[k] = String(v); } },
    A: { swipe: (i, k, s) => calls.push(['swipe', i, k, s]), swipeUndo: () => calls.push(['undo']), swipeCommit: () => calls.push(['commit']) },
    render: () => calls.push(['render']), status: () => {}, feedMore: () => {},
    gone: c => c.parentNode.removeChild(c),
    setTimeout: (f, ms) => { timers.push({ f, ms, off: false }); return timers.length; },
    clearTimeout: n => { if (timers[n - 1]) timers[n - 1].off = true; }
  };
  const names = Object.keys(env);
  const api = new Function(...names, code + '; return {swipeable, swipeEnd, swHint};')(...names.map(n => env[n]));
  const run = ms => timers.filter(t => !t.off && t.ms === ms).forEach(t => { t.off = true; t.f(); });
  const card = (it, kept) => { const c = node('card ' + it.id); list.insertBefore(c, list.children.find(x => x.name === 'end')); api.swipeable(c, it, () => !!kept); return c; };
  list.insertBefore(node('end'), null);
  return { calls, els, list, run, card, api };
}
// one finger from (100,100) along the given points
function drag(c, points) {
  const at = (x, y) => ({ clientX: x, clientY: y });
  c.fire('touchstart', { touches: [at(100, 100)] });
  points.forEach(p => c.fire('touchmove', { touches: [at(100 + p[0], 100 + p[1])] }));
  const last = points[points.length - 1];
  c.fire('touchend', { changedTouches: [at(100 + last[0], 100 + last[1])] });
}
const names = l => l.children.map(c => c.name);

let p = page({}), a = p.card({ id: '1' }), b = p.card({ id: '2', surprise: true, also: [{ id: '3' }, { id: '4' }] });
p.api.swHint();
is('the hint shows once cards are there', 'Swipe right to keep, left for less', p.els.swtext.textContent);
drag(a, [[20, 2], [80, 5]]);
is('less than 30% of the width does nothing', [], p.calls);
is('the card goes back', ['', false, false], [a.style.transform, a.classList.contains('swr'), a.classList.contains('go')]);
drag(a, [[30, 5], [120, 70]]);
is('less than twice as sideways as down does nothing', [], p.calls);
drag(a, [[3, 30], [150, 40]]);
is('a gesture that starts as a scroll stays a scroll', [], p.calls);
a.classList.add('open'); drag(a, [[40, 0], [200, 0]]); a.classList.remove('open');
is('an open card does not swipe', [], p.calls);
a.fire('touchstart', { touches: [{ clientX: 100, clientY: 100 }] });
a.fire('touchmove', { touches: [{ clientX: 220, clientY: 104 }] });
is('the card follows the finger, green to the right', ['translateX(120px)', true, true], [a.style.transform, a.classList.contains('swr'), a.classList.contains('go')]);
a.fire('touchcancel');
is('a cancelled touch does nothing', [[], ''], [p.calls, a.style.transform]);

drag(b, [[-40, 3], [-150, 10]]);
is('left on a surprise with two copies', [['swipe', '2,3,4', false, '2']], p.calls);
is('the card leaves', ['card 1', 'end'], names(p.list));
is('the bar offers Undo', ['Less like this', '', 'flex'], [p.els.swtext.textContent, p.els.swundo.style.display, p.els.swbar.style.display]);
p.calls.length = 0; p.els.swundo.onclick();
is('Undo calls the app', [['undo']], p.calls);
is('and the card is back in its place', ['card 1', 'card 2', 'end'], names(p.list));
is('the hint does not come back', 'none', p.els.swbar.style.display);
p.calls.length = 0; p.run(4000);
is('nothing is committed after Undo', [], p.calls);

drag(a, [[40, 0], [100, 4]]);
is('right keeps', [['swipe', '1', true, '']], p.calls);
is('bar says Kept', 'Kept', p.els.swtext.textContent);
p.calls.length = 0; drag(b, [[40, 0], [100, 4]]);
is('a second swipe makes the first final', [['commit'], ['swipe', '2,3,4', true, '2']], p.calls); // the app counts a surprise only on a left swipe
p.calls.length = 0; p.run(4000);
is('after 4 seconds it is final', [['commit']], p.calls);
is('and the bar goes', 'none', p.els.swbar.style.display);
p.calls.length = 0; p.els.swundo.onclick();
is('Undo after that does nothing', [], p.calls);

p = page({ swhint: '1' }); a = p.card({ id: '5' }, true); p.api.swHint();
is('the hint is shown only once', 'none', p.els.swbar.style.display);
drag(a, [[-40, 0], [-200, 0]]);
is('a kept card cannot be swiped left', [[], ['card 5', 'end']], [p.calls, names(p.list)]);
drag(a, [[40, 0], [200, 0]]);
is('but right clears it', ['end'], names(p.list));
p.list.children.slice().forEach(x => p.list.removeChild(x)); p.calls.length = 0; p.els.swundo.onclick();
is('Undo after the list was redrawn draws it again', [['undo'], ['render']], p.calls);

console.log(runs + ' swipe checks, ' + fails + ' failed');
if (fails) process.exit(1);
