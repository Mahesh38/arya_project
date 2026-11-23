import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { signIn } from './support/session';

// M9 quality sweep over the main screens (doc 13 §3.8, D99):
//   TC-NF-03 axe-core WCAG 2.1 AA (JS on), TC-NF-04 no sideways scroll at 360/1280 px and no JS errors,
//   TC-SEC-30a no CSP violations plus an inline-style control, TC-VE-29 error-summary focus.

const WCAG = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];
const year = new Date().getFullYear();
const original = `GRV-${year}-000001`;

type Screen = { user?: string; password?: string; path: string; prepare?: (page: Page) => Promise<void> };
const screens: Screen[] = [
  { path: '/login' },
  { path: '/register' },
  { path: '/images/missing.png' },
  { user: 'rahul@example.com', path: '/verify-mobile' },
  { user: 'mahesh@example.com', path: '/citizen/complaints' },
  { user: 'mahesh@example.com', path: '/citizen/complaints/new',
    prepare: async (page) => {
      // skip the browser's own required-field check (D49) so the server-side error summary renders
      await page.locator('form[enctype]').evaluate((form) => { (form as HTMLFormElement).noValidate = true; });
      await page.getByRole('button', { name: 'Submit complaint' }).click();
      await expect(page.locator('#error-summary')).toBeVisible();
    } },
  { user: 'mahesh@example.com', path: `/complaints/${original}` },
  { user: 'mahesh@example.com', path: '/notifications' },
  { user: 'mahesh@example.com', path: '/account/password' },
  { user: 'anita.deshmukh@example.com', path: `/complaints/${original}` },
  { user: 'anita.deshmukh@example.com', path: '/officer/queue' },
  { user: 'anita.deshmukh@example.com', path: `/officer/complaints/${original}/resolve` },
  { user: 'admin@example.com', password: 'Admin@1234', path: '/admin/dashboard' },
  { user: 'admin@example.com', password: 'Admin@1234', path: '/admin/complaints' },
];

test('TC-NF-03, TC-NF-04, TC-SEC-30a the main screens are accessible, fit the viewport and keep the CSP', async ({ page, javaScriptEnabled }, testInfo) => {
  test.setTimeout(180_000);
  const problems: string[] = [];
  page.on('pageerror', (error) => problems.push(`JS error: ${error.message}`));
  page.on('console', (message) => {
    if (/Content Security Policy|Refused to/i.test(message.text())) problems.push(`CSP: ${message.text()}`);
  });
  let signedInAs: string | undefined;
  for (const screen of screens) {
    if (screen.user !== signedInAs) {
      if (screen.user) await signIn(page, screen.user, screen.password);
      else await page.context().clearCookies();
      signedInAs = screen.user;
    }
    await page.goto(screen.path);
    if (screen.prepare) await screen.prepare(page);
    const where = `${screen.path} as ${screen.user ?? 'visitor'}`;

    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow, `sideways scroll on ${where}`).toBeLessThanOrEqual(0);
    // the verify page uses the public layout without a navbar
    if (screen.user && screen.path !== '/verify-mobile' && page.viewportSize()!.width <= 400) {
      await expect(page.getByRole('link', { name: /^Notifications,/ }), `bell on ${where}`).toBeVisible();
    }
    await page.screenshot({ path: testInfo.outputPath(`${screen.path.replace(/\W+/g, '_')}-${screen.user ?? 'visitor'}.png`), fullPage: true });

    if (javaScriptEnabled) {
      const report = await new AxeBuilder({ page }).withTags(WCAG).analyze();
      expect(report.violations.map((v) => `${v.id}: ${v.help}`), `axe on ${where}`).toEqual([]);
    }
  }
  expect(problems).toEqual([]);
});

test('TC-NF-03 control: axe reports an unlabelled input, so the accessibility check really runs', async ({ page, javaScriptEnabled }) => {
  test.skip(!javaScriptEnabled, 'axe needs JavaScript');
  await page.setContent('<!doctype html><html lang="en"><head><title>Control</title></head><body><main><input type="text"></main></body></html>');
  const report = await new AxeBuilder({ page }).withTags(WCAG).analyze();
  expect(report.violations.map((v) => v.id)).toContain('label');
});

test('TC-SEC-30a control: the CSP blocks an injected inline style', async ({ page, javaScriptEnabled }) => {
  test.skip(!javaScriptEnabled, 'the control injects markup with JavaScript');
  await page.goto('/login');
  const blocked = await page.evaluate(async () => {
    const violation = new Promise<string>((resolve) => {
      document.addEventListener('securitypolicyviolation', (event) => resolve(event.violatedDirective), { once: true });
      setTimeout(() => resolve('none'), 2000);
    });
    document.body.insertAdjacentHTML('beforeend', '<p id="csp-control" style="color: rgb(255, 0, 0)">control</p>');
    const color = getComputedStyle(document.getElementById('csp-control')!).color;
    return { directive: await violation, color };
  });
  expect(blocked.directive).toMatch(/^style-src/);
  expect(blocked.color).not.toBe('rgb(255, 0, 0)');
});

test('TC-VE-29 the error summary takes focus and each link moves focus to its field', async ({ page, javaScriptEnabled }) => {
  test.skip(!javaScriptEnabled, 'focus management is a JavaScript enhancement');
  // the change-password form instead of /register: the registration rate limit belongs to the authentication spec
  await signIn(page, 'jayesh@example.com');
  await page.goto('/account/password');
  const form = page.locator('main form');
  await form.evaluate((element) => { (element as HTMLFormElement).noValidate = true; });
  await form.getByRole('button').last().click();
  const summary = page.locator('#error-summary');
  await expect(summary).toBeFocused();
  const links = summary.locator('a[href^="#"]');
  const count = await links.count();
  expect(count).toBeGreaterThan(0);
  for (let i = 0; i < count; i++) {
    const target = (await links.nth(i).getAttribute('href'))!.slice(1);
    await links.nth(i).click();
    await expect(page.locator(`[id="${target}"]`)).toBeFocused();
  }
  const report = await new AxeBuilder({ page }).withTags(WCAG).analyze();
  expect(report.violations).toEqual([]);
});
