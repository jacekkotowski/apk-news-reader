// Tests the Markdown-to-HTML function of app/assets/index.html (the lines between MD-START and MD-END). Run by tests/run.sh when node is installed.
const fs = require('fs');
const src = fs.readFileSync(__dirname + '/../app/assets/index.html', 'utf8');
const code = src.split('//MD-START')[1].split('//MD-END')[0];
const md2html = new Function(code + '; return md2html;')();
const stripFront = new Function(code + '; return stripFront;')();
let fails = 0, runs = 0;
function has(name, cond) { runs++; if (!cond) { fails++; console.log('FAIL md: ' + name); } }
const h = md2html('*Based on the saved article.*\n\n## Key ideas\n- one **bold**\n- two [a link](https://example.org/x?a=1&b=2)\n\n1. first\n2. second\n\n> a quote\n\nPlain `code` text.');
has('italic line', h.includes('<p><em>Based on the saved article.</em></p>'));
has('heading', h.includes('<h3>Key ideas</h3>'));
has('bullet list with bold', h.includes('<ul>\n<li>one <strong>bold</strong></li>'));
has('link with escaped ampersand', h.includes('<a href="https://example.org/x?a=1&amp;b=2">a link</a>'));
has('ordered list', h.includes('<ol>\n<li>first</li>\n<li>second</li>\n</ol>'));
has('quote', h.includes('<blockquote>a quote</blockquote>'));
has('inline code', h.includes('<code>code</code>'));
const bad = md2html('<script>alert(1)</script> [x](javascript:alert(1)) [y](https://a.b/"onmouseover="z)\n\n- <img src=x onerror=alert(1)>');
has('no raw tags', !/<script|<img/.test(bad));
has('no javascript link', !bad.includes('href="javascript'));
has('quote in url cannot leave the attribute', !/href="[^"]*"onmouseover/.test(bad) && !bad.includes('"onmouseover'));
has('code block is escaped', md2html('```\n<b>x</b>\n```').includes('<pre><code>&lt;b&gt;x&lt;/b&gt;</code></pre>'));
has('empty input', md2html('') === '');
has('front matter is removed', stripFront('---\ntitle: "A"\ntopics: []\n---\n\n# A\n\n## Highlights\n\n> q').startsWith('\n# A'));
has('no front matter, text unchanged', stripFront('# A\n\ntext') === '# A\n\ntext');
has('a note renders: quote', md2html(stripFront('---\ntitle: x\n---\n\n# A\n\n## Highlights\n\n> q\n')).includes('<blockquote>q</blockquote>'));
console.log(runs + ' markdown checks, ' + fails + ' failed');
if (fails) process.exit(1);
