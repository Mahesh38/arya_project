import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

async function signIn(page: Page, email: string) {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill('Demo@1234');
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page).toHaveURL(/\/citizen\/complaints$/);
}

test('a citizen filters their list, reads the timeline, and stays within their visible complaints', async ({ page }, testInfo) => {
  test.setTimeout(60_000);
  await signIn(page, 'mahesh@example.com');
  await expect(page.getByRole('heading', { name: 'My Complaints' })).toBeVisible();
  await page.getByLabel('Status', { exact: true }).selectOption('IN_PROGRESS');
  await page.getByLabel('Search', { exact: true }).fill('Karve');
  await page.getByRole('button', { name: 'Apply' }).click();
  await expect(page.getByText('Showing 1–1 of 1')).toBeVisible();
  await page.getByRole('link', { name: 'GRV-2026-000001', exact: true }).click();
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Pipeline leaking on Karve Road');
  await expect(page.getByRole('heading', { name: 'History', exact: true })).toBeVisible();
  await expect(page.getByText('Complaint submitted', { exact: true })).toBeVisible();
  await expect(page.getByText('Work started', { exact: true })).toBeVisible();
  await expect(page.getByText('Anita Deshmukh')).toHaveCount(0);
  if (testInfo.project.use.javaScriptEnabled) {
    const result = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(result.violations).toEqual([]);
  }
  expect((await page.goto('/complaints/GRV-2026-000005'))?.status()).toBe(404);
  await page.goto('/citizen/watching');
  await expect(page.getByRole('heading', { name: 'Watching', exact: true })).toBeVisible();
  await expect(page.getByLabel('Search', { exact: true })).toHaveCount(0);
});

test('a citizen opens a seeded photo and downloads it by its original name', async ({ page }) => {
  test.setTimeout(60_000);
  await signIn(page, 'sneha@example.com');
  await page.getByRole('link', { name: 'GRV-2026-000003', exact: true }).click();
  const attachments = page.getByRole('region', { name: 'Attachments' });
  const photo = attachments.getByRole('link', { name: 'garbage-magarpatta-1.jpg', exact: true });
  await expect(photo).toBeVisible();
  const response = await page.request.get((await photo.getAttribute('href'))!);
  expect(response.ok()).toBe(true);
  expect(response.headers()['content-type']).toBe('image/jpeg');
  expect(response.headers()['content-disposition']).toContain('inline;');
  const download = page.waitForEvent('download');
  await attachments.getByRole('link', { name: 'Download garbage-magarpatta-1.jpg' }).click();
  expect((await download).suggestedFilename()).toBe('garbage-magarpatta-1.jpg');
});
