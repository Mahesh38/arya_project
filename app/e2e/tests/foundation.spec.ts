import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

test('the public layout remains usable at this viewport with or without JavaScript', async ({ page }) => {
  const missingAssets: string[] = [];
  page.on('response', response => {
    if (/\/(webjars|css|js)\//.test(response.url()) && !response.ok()) {
      missingAssets.push(response.url());
    }
  });
  await page.goto('/');
  await expect(page).toHaveURL(/\/login$/);
  await expect(page).toHaveTitle('Log in · Smart Grievance');
  await expect(page.getByRole('navigation', { name: 'Main' })).toBeVisible();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Log in');
  await expect(page.getByRole('contentinfo')).toContainText('Times are shown in IST (Asia/Kolkata)');
  await page.keyboard.press('Tab');
  await expect(page.getByRole('link', { name: 'Skip to main content' })).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('main')).toBeFocused();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(page.viewportSize()!.width);
  expect(missingAssets).toEqual([]);
});

test('a missing public resource renders the shared 404 page', async ({ page }) => {
  const response = await page.goto('/images/missing.png');
  expect(response?.status()).toBe(404);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Page not found');
  await expect(page.getByRole('link', { name: 'Log in' })).toHaveAttribute('href', '/login');
  await expect(page.locator('form')).toHaveCount(0);
  expect(response?.headers()['set-cookie']).toBeUndefined();
});

test('the login and 404 pages meet automated accessibility checks', async ({ page, javaScriptEnabled }) => {
  test.skip(!javaScriptEnabled, 'axe runs in a JavaScript-enabled browser context');
  for (const path of ['/login', '/images/missing.png']) {
    await page.goto(path);
    const report = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(report.violations).toEqual([]);
  }
});
