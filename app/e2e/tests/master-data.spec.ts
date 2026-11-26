import { expect, test, type Page } from '@playwright/test';

async function adminLogin(page: Page) {
  await page.goto('/login');
  await page.getByLabel('Email').fill('admin@example.com');
  await page.getByLabel('Password').fill('Admin@1234');
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page).toHaveURL(/\/admin\/dashboard$/);
}

test('the administrator manages departments, categories, and areas', async ({ page }, testInfo) => {
  test.setTimeout(60_000);
  const run = String(Date.now());
  const department = `Aar Ward ${run}`;
  const category = `Aar Category ${run}`;
  const area = `Aar Area ${run}`;

  await adminLogin(page);
  await page.goto('/admin/departments');
  await page.getByRole('link', { name: 'Add department' }).click();
  await page.getByLabel('Name').fill(department);
  await page.getByLabel('Description').fill('Maintains the ward service desk');
  await page.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByText('Department saved.')).toBeVisible();

  await page.goto('/admin/departments?size=1&active=true');
  const pagination = page.getByRole('navigation', { name: 'Pagination' });
  await expect(pagination.locator('[aria-current="page"]')).toHaveText('1');
  await pagination.getByRole('link', { name: 'Next ›' }).click();
  await expect(page).toHaveURL(url => url.searchParams.get('active') === 'true'
    && url.searchParams.get('size') === '1' && url.searchParams.get('page') === '2');
  await expect(pagination.locator('[aria-current="page"]')).toHaveText('2');

  await page.goto('/admin/departments/new');
  await page.getByLabel('Name').fill(department.toUpperCase());
  await page.getByLabel('Description').fill('Kept after a duplicate name');
  await page.getByRole('button', { name: 'Save' }).click();
  await expect(page.locator('#error-summary')).toContainText('This name is already in use.');
  await expect(page.getByLabel('Description')).toHaveValue('Kept after a duplicate name');

  await page.goto('/admin/categories/new');
  await page.getByLabel('Name').fill(category);
  await page.getByLabel('Default department').selectOption({ label: department });
  await page.getByLabel('SLA days').fill('7');
  await page.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByText('Category saved.')).toBeVisible();

  await page.goto('/admin/departments');
  const departmentRow = page.getByRole('row').filter({ hasText: department });
  await expect(departmentRow).toContainText('Active');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  if (testInfo.project.use.javaScriptEnabled) {
    page.once('dialog', dialog => void dialog.accept());
  }
  await departmentRow.getByRole('button', { name: 'Deactivate' }).click();
  await expect(page.getByRole('alert')).toContainText(`${department} is the default department of ${category}.`);

  await page.goto('/admin/areas/new');
  await page.getByLabel('Name').fill(area);
  await page.getByLabel('Ward office').fill('Ward office B');
  await page.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByRole('row').filter({ hasText: area })).toContainText('Ward office B');

});

test('the administrator creates and edits an officer', async ({ page }) => {
  test.setTimeout(45_000);
  const run = String(Date.now());
  const email = `officer-browser-${run}@example.test`;
  const department = 'Water Supply';
  await adminLogin(page);
  await page.goto('/admin/officers/new');
  await page.getByLabel('Full name').fill('Asha Browser');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Mobile number').fill(`77${run.slice(-8)}`);
  await page.getByLabel('Department').selectOption({ label: department });
  await page.getByLabel('Initial password').fill('Nacre!Cascade77');
  await page.getByLabel('Confirm password').fill('Nacre!Cascade77');
  await page.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByText('Officer account created.')).toBeVisible();

  await page.getByLabel('Search by name or email').fill(email);
  await page.getByRole('button', { name: 'Apply' }).click();
  const officer = page.getByRole('row').filter({ hasText: email });
  await expect(officer).toContainText(department);
  await officer.getByRole('link', { name: 'Edit' }).click();
  await expect(page.getByLabel('Email')).toHaveAttribute('readonly', '');
  await page.getByLabel('Full name').fill('Asha Kulkarni');
  await page.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByText('Officer updated.')).toBeVisible();
});

test('the administrator verifies a citizen, resets their password, and deactivates the account', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'js-on-1280', 'One project covers citizen administration within the signup rate limit.');
  test.setTimeout(60_000);
  const run = String(Date.now());
  const email = `citizen-admin-browser-${run}@example.test`;
  await page.goto('/register');
  await page.getByLabel('Full name').fill('Priya Browser');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Mobile number').fill(`76${run.slice(-8)}`);
  await page.locator('#password').fill('Nacre!Cascade77');
  await page.getByLabel('Confirm password').fill('Nacre!Cascade77');
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page).toHaveURL(/\/verify-mobile$/);
  await page.getByRole('button', { name: 'Log out' }).click();
  await adminLogin(page);
  await page.goto('/admin/citizens');

  const searchForCitizen = async () => {
    await page.getByLabel('Search by name, email or mobile').fill(email);
    await page.getByRole('button', { name: 'Apply' }).click();
  };
  await searchForCitizen();
  const citizen = page.getByRole('row').filter({ hasText: email });
  await expect(citizen).toContainText('Not verified');
  page.once('dialog', dialog => void dialog.accept());
  await citizen.getByRole('button', { name: 'Mark verified' }).click();
  await expect(page.getByText('Mobile marked as verified.')).toBeVisible();
  await searchForCitizen();
  await expect(citizen.getByRole('button', { name: 'Mark verified' })).toHaveCount(0);

  await citizen.getByRole('link', { name: 'Reset password' }).click();
  await page.locator('#newPassword').fill('Oleander!Moon92');
  await page.getByLabel('Confirm password').fill('Oleander!Moon92');
  await page.getByRole('button', { name: 'Reset password' }).click();
  await expect(page.getByText('Password reset. Give the new password to the user.')).toBeVisible();
  await searchForCitizen();

  page.once('dialog', dialog => void dialog.accept());
  await citizen.getByRole('button', { name: 'Deactivate' }).click();
  await expect(page.getByText('Account deactivated.')).toBeVisible();
  await searchForCitizen();
  await expect(citizen.getByRole('button', { name: 'Activate' })).toBeVisible();
});
