import { expect, test, type Page } from '@playwright/test';
import { signIn } from './support/session';

async function submitComplaint(page: Page, title: string) {
  await page.goto('/citizen/complaints/new');
  await page.getByLabel('Category').selectOption({ label: 'Garbage' });
  await page.getByLabel('Area').selectOption({ label: 'Kothrud' });
  await page.getByLabel('Title').fill(title);
  await page.getByLabel('Description').fill('The ward collection point has missed pickups this week.');
  await page.getByLabel('Address').fill('Outside the Kothrud ward office');
  await page.getByRole('button', { name: 'Submit complaint' }).click();
  await expect(page).toHaveURL(/\/submitted$/);
  const url = (await page.getByRole('link', { name: 'View complaint' }).getAttribute('href'))!;
  return { url, no: url.split('/').pop()! };
}

test('the bell reflects unread notices, opening marks one read, and mark-all clears the rest', async ({ page }) => {
  test.setTimeout(90_000);
  await signIn(page, 'jayesh@example.com');
  const first = await submitComplaint(page, `Ward notices first ${Date.now()}`);
  await submitComplaint(page, `Ward notices second ${Date.now()}`);
  const bell = page.getByRole('link', { name: /^Notifications,/ });
  await expect(bell).toHaveAccessibleName(/Notifications, [1-9][0-9]* unread/);
  await bell.click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Notifications');
  const readFirst = page.getByRole('button', { name: `Open complaint ${first.no}`, exact: true });
  await readFirst.click();
  await expect(page).toHaveURL(new RegExp(`${first.url}$`));
  await bell.click();
  await expect(page.getByRole('listitem').filter({ has: readFirst })).not.toContainText('New');
  await page.getByRole('button', { name: 'Mark all as read' }).click();
  await expect(bell).toHaveAccessibleName('Notifications, no unread');
  await expect(page.locator('#bell-count')).toBeHidden();
  await expect(page.getByRole('button', { name: 'Mark all as read' })).toHaveCount(0);
});

test('the bell refreshes its unread count when the tab becomes visible', async ({ page, javaScriptEnabled }) => {
  test.skip(!javaScriptEnabled, 'visibility events refresh the browser count when JavaScript is enabled');
  await signIn(page, 'rahul@example.com');
  await page.route('**/notifications/unread-count', route => route.fulfill({ json: { unread: 102 } }));
  await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange')));
  await expect(page.locator('#bell-count')).toHaveText('99+');
  await expect(page.getByRole('link', { name: 'Notifications, 102 unread' })).toBeVisible();
});
