import { expect, test } from '@playwright/test';
import { signIn } from './support/session';

// M8: admin dashboard (R-78, FR-60…64) and CSV export (R-32, FR-65) in a real browser, JS on and off.

test('the admin dashboard shows cards, tables and the chart without CSP violations', async ({ page, javaScriptEnabled }) => {
  const violations: string[] = [];
  page.on('console', (message) => {
    if (/Content Security Policy|Refused to/i.test(message.text())) violations.push(message.text());
  });
  await signIn(page, 'admin@example.com', 'Admin@1234');
  await expect(page).toHaveURL(/\/admin\/dashboard$/);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Dashboard');
  await expect(page.getByRole('link', { name: /^Total: \d+$/ })).toBeVisible();
  await expect(page.getByRole('link', { name: /^Overdue: \d+$/ })).toBeVisible();
  for (const heading of ['By department', 'By category', 'By area']) {
    await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible();
  }

  const chart = page.locator('#department-chart');
  if (javaScriptEnabled) {
    await expect(chart).toBeVisible();
    await expect.poll(() => page.evaluate(() => {
      const canvas = document.getElementById('department-chart') as HTMLCanvasElement;
      const Chart = (window as unknown as { Chart?: { getChart(c: HTMLCanvasElement): unknown } }).Chart;
      return Boolean(Chart?.getChart(canvas)) && canvas.width > 0;
    })).toBe(true);
  } else {
    await expect(chart).toBeHidden();
  }
  expect(violations).toEqual([]);

  const pending = page.getByRole('link', { name: /^Pending: \d+$/ });
  const count = (await pending.getAttribute('aria-label'))!.split(': ')[1];
  await pending.click();
  await expect(page).toHaveURL(/\/admin\/complaints\?status=PENDING$/);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('All Complaints');
  await expect(page.locator('main')).toContainText(new RegExp(`\\b${count}\\b`));
});

test('the export link on All Complaints downloads the filtered list as CSV', async ({ page }) => {
  await signIn(page, 'admin@example.com', 'Admin@1234');
  await page.goto('/admin/complaints?status=PENDING');
  const href = await page.getByRole('link', { name: 'Export CSV' }).getAttribute('href');
  expect(href).toBe('/admin/complaints/export.csv?status=PENDING');
  const response = await page.request.get(href!);
  expect(response.status()).toBe(200);
  expect(response.headers()['content-type']).toBe('text/csv;charset=UTF-8');
  const body = await response.text();
  expect(body.charCodeAt(0)).toBe(0xfeff);
  expect(body).toContain('"Complaint No","Title"');
  expect(body).not.toContain('@example.com');
});
