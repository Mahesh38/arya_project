import { resolve } from 'node:path';
import { expect, test, type Page } from '@playwright/test';

async function signIn(page: Page, email: string, password = 'Demo@1234') {
  await page.context().clearCookies();
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page).not.toHaveURL(/\/login(?:\?|$)/);
}

test('admin reviews and assigns, an officer resolves, and the citizen closes the complaint', async ({ page }, testInfo) => {
  test.setTimeout(90_000);
  const title = `Street cleaning workflow ${Date.now()}`;
  await signIn(page, 'mahesh@example.com');
  await page.goto('/citizen/complaints/new');
  await page.getByLabel('Category').selectOption({ label: 'Garbage' });
  await page.getByLabel('Area').selectOption({ label: 'Kothrud' });
  await page.getByLabel('Title').fill(title);
  await page.getByLabel('Description').fill('The public collection point has been missed this week.');
  await page.getByLabel('Address').fill('Near the Kothrud ward office');
  await page.getByRole('button', { name: 'Submit complaint' }).click();
  await expect(page).toHaveURL(/\/submitted$/);
  const detailUrl = (await page.getByRole('link', { name: 'View complaint' }).getAttribute('href'))!;
  const complaintNo = detailUrl.split('/').pop()!;

  await signIn(page, 'admin@example.com', 'Admin@1234');
  await page.goto('/admin/complaints?status=SUBMITTED');
  await page.getByRole('link', { name: complaintNo, exact: true }).click();
  if (testInfo.project.use.javaScriptEnabled) page.once('dialog', dialog => dialog.accept());
  await page.getByRole('button', { name: 'Start review', exact: true }).click();
  await expect(page.locator('main header')).toContainText('Under Review');
  await page.getByRole('link', { name: 'Assign', exact: true }).click();
  await expect(page.getByRole('combobox', { name: 'Department', exact: true }).locator('option:checked')).toHaveText('Sanitation');
  await page.getByRole('button', { name: 'Assign', exact: true }).click();
  await expect(page.getByText('Assigned to Sanitation.', { exact: true })).toBeVisible();

  await signIn(page, 'suresh.patil@example.com');
  await page.getByRole('link', { name: complaintNo, exact: true }).click();
  await page.getByRole('link', { name: 'Start work', exact: true }).click();
  await page.getByRole('textbox', { name: 'Remark', exact: true }).fill('The ward team will clean the location this afternoon.');
  await page.getByRole('button', { name: 'Start work', exact: true }).click();
  await expect(page.locator('main header')).toContainText('In Progress');
  await page.getByRole('link', { name: 'Mark as resolved', exact: true }).click();
  await page.getByRole('textbox', { name: 'Resolution remark', exact: true }).fill('The ward team collected the bags and cleaned the roadside.');
  await page.getByLabel('Photos or documents').setInputFiles(resolve(__dirname, '../../src/test/resources/files/photo.png'));
  await page.getByRole('button', { name: 'Mark as resolved', exact: true }).click();
  await expect(page.locator('main header')).toContainText('Resolved');
  await expect(page.getByRole('heading', { name: 'Work done' })).toBeVisible();

  await signIn(page, 'mahesh@example.com');
  await page.goto(detailUrl);
  await expect(page.getByText('Please review the resolution by', { exact: false })).toBeVisible();
  await page.getByRole('link', { name: 'Close complaint', exact: true }).click();
  await page.getByLabel('Feedback (optional)').fill('The collection point is clean now.');
  await page.getByRole('button', { name: 'Close complaint', exact: true }).click();
  await expect(page.getByText('Thank you. The complaint is closed.', { exact: true })).toBeVisible();
  await expect(page.locator('main header')).toContainText('Closed');
});
