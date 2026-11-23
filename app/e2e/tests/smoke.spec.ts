import { resolve } from 'node:path';
import { expect, test } from '@playwright/test';
import { signIn } from './support/session';

// TC-NF-16 (JS off) and TC-NF-17 (JS on): the core flow end to end. The catalogue names mahesh; sneha files here
// because the other specs already use mahesh's daily complaint allowance (BR-88: 10 per 24 h).
test('TC-NF-16, TC-NF-17 a citizen files with a photo, staff assign it and an officer links it as a duplicate', async ({ page, javaScriptEnabled }) => {
  test.setTimeout(120_000);
  const year = new Date().getFullYear();
  const original = `GRV-${year}-000001`;
  const title = `Smoke test leak ${Date.now()}`;
  const violations: string[] = [];
  page.on('console', (message) => {
    if (/Content Security Policy|Refused to/i.test(message.text())) violations.push(message.text());
  });

  await signIn(page, 'sneha@example.com');
  await page.goto('/citizen/complaints');
  const before = await page.locator('main .complaint-no').count();
  await page.goto('/citizen/complaints/new');
  await page.getByLabel('Category').selectOption({ label: 'Water Leakage' });
  await page.getByLabel('Area').selectOption({ label: 'Kothrud' });
  await page.getByLabel('Title').fill(title);
  await page.getByLabel('Description').fill('Water is leaking from the main pipe near the depot gate since morning.');
  await page.getByLabel('Address').fill('Karve Road near Kothrud Depot');
  await page.getByLabel('Photos or documents').setInputFiles(resolve(__dirname, '../../src/test/resources/files/photo.png'));
  const submit = page.getByRole('button', { name: 'Submit complaint' });
  if (javaScriptEnabled) {
    await expect(page.locator('[data-upload-summary]')).toContainText('photo.png');
    await submit.dblclick();
  } else {
    await submit.click();
  }
  await expect(page).toHaveURL(/\/submitted$/);
  const detailUrl = (await page.getByRole('link', { name: 'View complaint' }).getAttribute('href'))!;
  const complaintNo = detailUrl.split('/').pop()!;
  await page.goto(`/citizen/complaints?q=${encodeURIComponent(title)}`);
  await expect(page.locator('main .complaint-no')).toHaveCount(1);
  await page.goto('/citizen/complaints');
  expect(await page.locator('main .complaint-no').count()).toBeLessThanOrEqual(before + 1);

  await signIn(page, 'admin@example.com', 'Admin@1234');
  await page.goto(`/complaints/${complaintNo}`);
  let confirmed = false;
  if (javaScriptEnabled) page.once('dialog', (dialog) => { confirmed = true; dialog.accept(); });
  await page.getByRole('button', { name: 'Start review', exact: true }).click();
  await expect(page.locator('main header')).toContainText('Under Review');
  if (javaScriptEnabled) expect(confirmed).toBe(true);
  await page.getByRole('link', { name: 'Assign', exact: true }).click();
  await expect(page.getByRole('combobox', { name: 'Department', exact: true }).locator('option:checked')).toHaveText('Water Supply');
  await page.getByRole('button', { name: 'Assign', exact: true }).click();
  await expect(page.getByText('Assigned to Water Supply.', { exact: true })).toBeVisible();

  await signIn(page, 'anita.deshmukh@example.com');
  await page.goto(`/complaints/${complaintNo}`);
  await page.getByRole('link', { name: 'Mark as duplicate', exact: true }).click();
  await page.getByRole('textbox', { name: 'Original complaint number', exact: true }).fill(original);
  await page.getByRole('button', { name: 'Find complaint' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Is this the same problem?');
  await page.getByRole('textbox', { name: 'Note to the citizen', exact: true }).fill('Same leak as the complaint already being repaired.');
  await page.getByRole('button', { name: 'Link as duplicate' }).click();
  await expect(page.locator('main header')).toContainText('Duplicate');

  if (javaScriptEnabled) {
    const toggler = page.getByRole('button', { name: 'Toggle navigation' });
    if (await toggler.isVisible()) await toggler.click();
    await page.getByRole('button', { name: /Anita Deshmukh/ }).click();
  }
  // without JavaScript the .no-js rules show the menu, so the same button is reachable directly
  await expect(page.getByRole('button', { name: 'Log out' })).toBeVisible();
  await page.getByRole('button', { name: 'Log out' }).click();
  await expect(page).toHaveURL(/\/login\?logout$/);
  expect(violations).toEqual([]);
});

// TC-SEC-38b: a 27 MB upload without JavaScript reaches the upload guard (swallow size 64 MB) and comes back as a
// friendly message instead of a reset connection.
test('TC-SEC-38b a 27 MB upload with JS off returns to the form with a size message', async ({ page, javaScriptEnabled }) => {
  test.skip(javaScriptEnabled, 'with JavaScript the file pre-check stops the upload in the browser');
  await signIn(page, 'sneha@example.com');
  await page.goto('/citizen/complaints/new');
  await page.getByLabel('Category').selectOption({ label: 'Garbage' });
  await page.getByLabel('Area').selectOption({ label: 'Baner' });
  await page.getByLabel('Title').fill('Oversized upload check');
  await page.getByLabel('Description').fill('This upload is larger than the allowed request size.');
  await page.getByLabel('Address').fill('Baner Road near Balewadi Phata');
  const big = Buffer.alloc(27 * 1024 * 1024, 0x41);
  big.write('\x89PNG\r\n\x1a\n', 0, 'binary');
  await page.getByLabel('Photos or documents').setInputFiles({ name: 'huge.png', mimeType: 'image/png', buffer: big });
  await page.getByRole('button', { name: 'Submit complaint' }).click();
  await expect(page).toHaveURL(/\/citizen\/complaints\/new$/);
  await expect(page.getByRole('alert').filter({ hasText: 'too large' })).toBeVisible();
});
