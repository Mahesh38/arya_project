// Renders the static mock-ups at 360 px and 1280 px with Playwright/Chromium.
// Usage (from the repository root): ASSETS_DIR=<node_modules dir with bootstrap, bootstrap-icons, chart.js> node docs/ui-mockups/render-screenshots.js
// CDN requests are answered from the local npm packages, so no network is needed.
const { chromium } = require('playwright');
const path = require('path'), fs = require('fs');
const mods = process.env.ASSETS_DIR || 'node_modules';
const dir = __dirname, out = path.join(dir, 'screenshots');
const pages = ['complaint-detail', 'officer-queue', 'admin-dashboard'];
const map = [
  [/bootstrap@5\.3\.8\/dist\/(.*)$/, 'bootstrap/dist/'],
  [/bootstrap-icons@1\.13\.1\/font\/(.*)$/, 'bootstrap-icons/font/'],
  [/chart\.js@4\.4\.7\/dist\/(.*)$/, 'chart.js/dist/'],
];
(async () => {
  fs.mkdirSync(out, { recursive: true });
  const browser = await chromium.launch();
  for (const width of [360, 1280]) {
    const ctx = await browser.newContext({ viewport: { width, height: 800 }, deviceScaleFactor: 1 });
    await ctx.route('https://cdn.jsdelivr.net/**', route => {
      const url = route.request().url().split('?')[0];
      for (const [re, base] of map) { const m = url.match(re); if (m) return route.fulfill({ path: path.join(mods, base, m[1]) }); }
      return route.abort();
    });
    const page = await ctx.newPage();
    const errors = []; page.on('pageerror', e => errors.push(e.message));
    for (const p of pages) {
      await page.goto('file://' + path.join(dir, p + '.html'));
      await page.waitForLoadState('networkidle');
      const scroll = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
      const file = path.join(out, `${p}-${width}.png`);
      await page.screenshot({ path: file, fullPage: true });
      console.log(`${p} @${width}px -> ${path.relative(process.cwd(), file)}  horizontal-scroll=${scroll}  js-errors=${errors.length}`);
    }
    await ctx.close();
  }
  await browser.close();
})();
