import { expect, type Page } from '@playwright/test';

export async function signIn(page: Page, email: string, password = 'Demo@1234') {
  await page.context().clearCookies();
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page).not.toHaveURL(/\/login(?:\?|$)/);
}
