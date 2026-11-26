import AxeBuilder from '@axe-core/playwright';
import { resolve } from 'node:path';
import { expect, test } from '@playwright/test';

test('a verified citizen submits an attachment, edits the complaint, and withdraws it', async ({ page }, testInfo) => {
  test.setTimeout(60_000);
  const title = `Missed waste collection ${Date.now()}`;
  const address = 'Near the Kothrud ward office junction';
  await page.goto('/login');
  await page.getByLabel('Email').fill('mahesh@example.com');
  await page.getByLabel('Password').fill('Demo@1234');
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page).toHaveURL(/\/citizen\/complaints$/);

  await page.goto('/citizen/complaints/new');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('New complaint');
  await page.getByLabel('Category').selectOption({ label: 'Garbage' });
  await page.getByLabel('Area').selectOption({ label: 'Kothrud' });
  await page.getByLabel('Title').fill(title);
  await page.getByLabel('Description').fill('The roadside collection point has been missed for two consecutive days.');
  await page.getByLabel('Address').fill(address);

  const files = page.getByLabel('Photos or documents');
  const note = Buffer.from('Sample note');
  if (testInfo.project.use.javaScriptEnabled) {
    await files.setInputFiles({ name: 'collection-note.txt', mimeType: 'text/plain', buffer: note });
    await expect(page.locator('[data-upload-summary]')).toContainText('only JPG, PNG or PDF files are allowed');
  }
  await files.setInputFiles({ name: 'collection-note.jpg', mimeType: 'image/jpeg', buffer: note });
  await page.getByRole('button', { name: 'Submit complaint' }).click();
  await expect(page.locator('#files-error')).toContainText('only JPG, PNG or PDF files are allowed');
  await expect(files).toHaveAttribute('aria-invalid', 'true');
  await expect(page.getByLabel('Title')).toHaveValue(title);
  await expect(page.getByLabel('Address')).toHaveValue(address);
  if (testInfo.project.use.javaScriptEnabled) {
    await expect(page.locator('#error-summary')).toBeFocused();
    const report = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(report.violations).toEqual([]);
  }

  await files.setInputFiles(resolve(__dirname, '../../src/test/resources/files/photo.png'));
  await page.getByRole('button', { name: 'Submit complaint' }).click();
  await expect(page).toHaveURL(/\/citizen\/complaints\/GRV-\d{4}-\d{6}\/submitted$/);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Your complaint has been registered');
  await expect(page.locator('main .complaint-no')).toHaveText(/^GRV-\d{4}-\d{6}$/);
  await page.getByRole('link', { name: 'View complaint' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(title);
  await expect(page.getByRole('region', { name: 'Attachments' }).getByText('photo.png', { exact: true })).toBeVisible();

  await page.getByRole('link', { name: 'Edit', exact: true }).click();
  await page.getByLabel('Title').fill(title + ' near the junction');
  await expect(page.locator('[data-upload-slots]')).toHaveText('You can add 4 more.');
  await page.getByRole('checkbox', { name: 'Remove photo.png' }).check();
  if (testInfo.project.use.javaScriptEnabled) {
    await expect(page.locator('[data-upload-slots]')).toHaveText('You can add 5 more.');
  }
  await page.getByLabel('Reason for change').fill('Clarified the location and removed an outdated photo.');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(page.getByText('Complaint updated.')).toBeVisible();
  await expect(page.getByRole('region', { name: 'Attachments' }).getByText('photo.png', { exact: true })).toHaveCount(0);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(title + ' near the junction');

  await page.getByRole('link', { name: 'Withdraw', exact: true }).click();
  await page.getByLabel('Reason for withdrawal').fill('The collection crew has cleared the waste.');
  await page.getByRole('button', { name: 'Withdraw complaint' }).click();
  await expect(page.getByText('Complaint withdrawn.')).toBeVisible();
  await expect(page.locator('main header').getByText('Withdrawn', { exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Edit', exact: true })).toHaveCount(0);
});
