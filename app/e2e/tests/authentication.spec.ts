import { expect, test } from '@playwright/test';

test('a citizen registers, verifies a demo OTP, changes password and signs back in', async ({ page }, testInfo) => {
  const runId = String(Date.now());
  const fullName = 'Priya Browser';
  const email = `priya-browser-${testInfo.project.name}-${runId}@example.test`;
  const mobile = `78${runId.slice(-8)}`;
  const password = 'Nacre!Cascade77';
  const newPassword = 'Oleander!Moon92';

  if (testInfo.project.use.javaScriptEnabled) {
    await page.clock.install();
  }
  await page.setExtraHTTPHeaders({ 'Accept-Language': 'de-DE,hi-IN' });
  await page.goto('/login');
  await page.getByRole('link', { name: 'Register' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Create a citizen account');
  await page.getByLabel('Full name').fill(fullName);
  await page.getByLabel('Email').fill(email.toUpperCase());
  await page.getByLabel('Mobile number').fill(`${mobile.slice(0, 5)} ${mobile.slice(5)}`);
  await page.locator('#password').fill(password);
  await page.getByLabel('Confirm password').fill(newPassword);
  await page.getByRole('button', { name: 'Create account' }).click();
  const errors = page.locator('#error-summary');
  await expect(errors).toContainText('The passwords do not match.');
  await expect(errors.getByRole('link')).toHaveAttribute('href', '#confirmPassword');
  await expect(page.getByLabel('Email')).toHaveValue(email);
  await expect(page.getByLabel('Mobile number')).toHaveValue(mobile);
  await expect(page.locator('#password')).toHaveValue('');
  await expect(page.getByLabel('Confirm password')).toHaveValue('');
  if (testInfo.project.use.javaScriptEnabled) {
    await expect(errors).toBeFocused();
  }

  await page.locator('#password').fill(password);
  await page.getByLabel('Confirm password').fill(password);
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page).toHaveURL(/\/verify-mobile$/);
  await expect(page.getByText(`******${mobile.slice(-4)}`, { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Send a new code' })).toBeDisabled();
  const demoCode = page.locator('#demo-otp');
  await expect(demoCode).toHaveText(/^\d{6}$/);
  const code = (await demoCode.textContent())!;

  await page.getByLabel('OTP').fill(code === '000000' ? '000001' : '000000');
  await page.getByRole('button', { name: 'Verify', exact: true }).click();
  await expect(page.locator('#error-summary')).toContainText('Incorrect OTP, 2 attempts left.');
  await expect(demoCode).toHaveCount(0);

  await page.getByLabel('OTP').fill(code);
  await page.getByRole('button', { name: 'Verify', exact: true }).click();
  await expect(page).toHaveURL(/\/citizen\/complaints$/);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('My Complaints');
  await expect(page.getByText('Mobile verified, you can now file complaints.')).toBeVisible();

  if (testInfo.project.use.javaScriptEnabled) {
    await page.clock.fastForward('25:00');
    const stayLoggedIn = page.getByRole('button', { name: 'Stay logged in' });
    await expect(stayLoggedIn).toBeVisible();
    const keepAlive = page.waitForResponse(response => response.url().endsWith('/notifications/unread-count'));
    await stayLoggedIn.click();
    const response = await keepAlive;
    expect(response.status()).toBe(200);
    expect(await response.json()).toEqual({ unread: 0 });
    await expect(stayLoggedIn).toBeHidden();
  }

  await page.goto('/account/password');
  await page.getByLabel('Current password').fill(password);
  await page.locator('#newPassword').fill(newPassword);
  await page.getByLabel('Confirm new password').fill(newPassword);
  await page.getByRole('button', { name: 'Change password' }).click();
  await expect(page.getByText('Password changed.')).toBeVisible();
  if (testInfo.project.use.javaScriptEnabled) {
    const toggleNavigation = page.getByRole('button', { name: 'Toggle navigation' });
    if (await toggleNavigation.isVisible()) {
      await toggleNavigation.click();
    }
    await page.getByRole('button', { name: fullName }).click();
  }
  await page.getByRole('button', { name: 'Log out' }).click();
  await expect(page).toHaveURL(/\/login\?logout$/);

  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page.getByRole('alert')).toContainText('Invalid email or password, or account inactive.');
  await expect(page.getByLabel('Email')).toHaveValue(email);
  await expect(page.getByLabel('Password')).toHaveValue('');

  await page.getByLabel('Password').fill(newPassword);
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page).toHaveURL(/\/citizen\/complaints$/);
});

for (const account of [
  { email: 'anita.deshmukh@example.com', password: 'Demo@1234', path: '/officer/queue', heading: 'Department Queue' },
  { email: 'admin@example.com', password: 'Admin@1234', path: '/admin/dashboard', heading: 'Dashboard' },
]) {
  test(`a demo user reaches ${account.heading} after login`, async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email').fill(account.email);
    await page.getByLabel('Password').fill(account.password);
    await page.getByRole('button', { name: 'Log in' }).click();
    await expect(page).toHaveURL(url => url.pathname === account.path);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(account.heading);
  });
}
