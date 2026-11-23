import { expect, test } from '@playwright/test';
import { signIn } from './support/session';

test('staff link a duplicate and the citizen can stop or resume watching its original', async ({ page }, testInfo) => {
  test.setTimeout(90_000);
  const originalNo = 'GRV-2026-000001';
  await signIn(page, 'sneha@example.com');
  await page.goto('/citizen/complaints/new');
  await page.getByLabel('Category').selectOption({ label: 'Water Leakage' });
  await page.getByLabel('Area').selectOption({ label: 'Kothrud' });
  await page.getByLabel('Title').fill(`Water near the depot ${Date.now()}`);
  await page.getByLabel('Description').fill('Water from the main pipe is collecting near the depot entrance.');
  await page.getByLabel('Address').fill('Karve Road near Kothrud Depot');
  await page.getByRole('button', { name: 'Submit complaint' }).click();
  await expect(page).toHaveURL(/\/submitted$/);
  const duplicateUrl = (await page.getByRole('link', { name: 'View complaint' }).getAttribute('href'))!;

  await signIn(page, 'admin@example.com', 'Admin@1234');
  await page.goto(duplicateUrl);
  await page.getByRole('link', { name: 'Mark as duplicate', exact: true }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Mark as duplicate');
  const number = page.getByRole('textbox', { name: 'Original complaint number', exact: true });
  await number.fill('GRV-2026-999999');
  await page.getByRole('button', { name: 'Find complaint' }).click();
  await expect(page.locator('#originalNo-error')).toBeVisible();
  await number.fill(originalNo);
  await page.getByRole('button', { name: 'Find complaint' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Is this the same problem?');
  await page.getByRole('textbox', { name: 'Note to the citizen', exact: true }).fill('This is the same leak already being handled near the depot.');
  await page.getByRole('button', { name: 'Link as duplicate' }).click();
  await expect(page.locator('main header')).toContainText('Duplicate');

  await signIn(page, 'sneha@example.com');
  await page.goto('/citizen/watching');
  await page.getByRole('link', { name: originalNo, exact: true }).click();
  await expect(page.getByText('You are watching this complaint.', { exact: false })).toBeVisible();
  if (testInfo.project.use.javaScriptEnabled) page.once('dialog', dialog => dialog.accept());
  await page.getByRole('button', { name: 'Unwatch', exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`/complaints/${originalNo}$`));
  await expect(page.getByText('You stopped watching this complaint.', { exact: false })).toBeVisible();
  await page.goto('/citizen/watching');
  await expect(page.getByRole('link', { name: originalNo, exact: true })).toHaveCount(0);
  await page.goto(duplicateUrl);
  await page.getByRole('link', { name: originalNo, exact: true }).first().click();
  if (testInfo.project.use.javaScriptEnabled) page.once('dialog', dialog => dialog.accept());
  await page.getByRole('button', { name: 'Watch again', exact: true }).click();
  await page.goto('/citizen/watching');
  await expect(page.getByRole('link', { name: originalNo, exact: true })).toBeVisible();
});
