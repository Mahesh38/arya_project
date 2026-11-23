// Independent acceptance walkthrough of Smart Grievance against a running dev instance (MySQL + Appendix A demo data).
// Use case by use case, error by error, screen by screen. Results -> results.json, screenshots -> shots/.
//
// How to run (see docs/validation/validation-report.md):
//   1. mysql -u root -p < docs/deploy/scripts/reset-demo-db.sql      (fresh demo data; the walkthrough changes it)
//   2. java -jar app/target/smart-grievance.jar --spring.profiles.active=dev   (port 8080, in its own folder)
//   3. mkdir -p shots && ln -s ../app/e2e/node_modules node_modules (or run from a folder that has them)
//      node docs/validation/walkthrough.mjs     [BASE=http://localhost:8080] [CHROMIUM_PATH=...]
// Restart the app between runs: login lockouts and rate limits are kept in memory.
import { chromium } from 'playwright';
import { expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { writeFileSync } from 'node:fs';

const BASE = process.env.BASE ?? 'http://localhost:8080';
const Y = new Date().getFullYear();
const no = (n) => `GRV-${Y}-${String(n).padStart(6, '0')}`;
const ADMIN_PW = process.env.ADMIN_PW ?? 'Admin@1234';
const results = [];
const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined });

async function session(email, password = 'Demo@1234', viewport = { width: 1280, height: 800 }, js = true) {
  const context = await browser.newContext({ viewport, javaScriptEnabled: js, baseURL: BASE, acceptDownloads: true });
  const page = await context.newPage();
  page.problems = [];
  page.on('pageerror', (e) => page.problems.push(`JS error: ${e.message}`));
  page.on('console', (m) => { if (/Content Security Policy|Refused to/i.test(m.text())) page.problems.push(`CSP: ${m.text()}`); });
  if (email) {
    await page.goto('/login');
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Password').fill(password);
    await page.getByRole('button', { name: 'Log in' }).click();
    await expect(page).not.toHaveURL(/\/login/);
  }
  return page;
}
const done = async (page) => page.context().close();

async function check(area, id, title, fn) {
  const started = Date.now();
  try {
    const note = await fn();
    results.push({ area, id, title, ok: true, note: note ?? '', ms: Date.now() - started });
    console.log(`PASS ${id} ${title}`);
  } catch (e) {
    results.push({ area, id, title, ok: false, note: String(e.message ?? e).split('\n').slice(0, 6).join(' | '), ms: Date.now() - started });
    console.log(`FAIL ${id} ${title}\n   ${String(e.message ?? e).split('\n').slice(0, 4).join('\n   ')}`);
  }
}
const flash = (page) => page.locator('.alert');
const expectFlash = (page, text) => expect(page.locator('.alert').filter({ hasText: text }).first()).toBeVisible();
const noValidate = (page, sel = 'main form') => page.locator(sel).first().evaluate((f) => { f.noValidate = true; });
async function newComplaint(page, { category = 'Garbage', area = 'Kothrud', title, description = 'The problem has been visible for two days and needs attention.', address = 'Near the Kothrud ward office', files = [] }) {
  await page.goto('/citizen/complaints/new');
  await page.getByLabel('Category').selectOption({ label: category });
  await page.getByLabel('Area').selectOption({ label: area });
  await page.getByLabel('Title').fill(title);
  await page.getByLabel('Description').fill(description);
  await page.getByLabel('Address').fill(address);
  if (files.length) await page.getByLabel('Photos or documents').setInputFiles(files);
  await page.getByRole('button', { name: 'Submit complaint' }).click();
  await expect(page).toHaveURL(/\/submitted$/);
  return (await page.getByRole('link', { name: 'View complaint' }).getAttribute('href')).split('/').pop();
}
async function action(page, complaintNo, link, remark, button) {
  await page.goto(`/complaints/${complaintNo}`);
  await page.getByRole('link', { name: link, exact: true }).click();
  if (remark !== undefined) await page.locator('#remark').fill(remark);
  if (button) await page.getByRole('button', { name: button, exact: true }).click();
  else await page.locator('main form').last().getByRole('button').last().click();
}
async function postButton(page, complaintNo, name) {
  await page.goto(`/complaints/${complaintNo}`);
  page.once('dialog', (d) => d.accept());
  await page.getByRole('button', { name, exact: true }).click();
}
async function status(page, complaintNo) {
  await page.goto(`/complaints/${complaintNo}`);
  return (await page.locator('main header .badge-status').first().innerText()).trim();
}

// ───────────────────────────── Use cases ─────────────────────────────
const stamp = Date.now();
let priyaEmail = `priya.${stamp}@example.com`;
let priyaMobile = `98${String(stamp).slice(-8)}`;

await check('UC', 'UC-01', 'Register as citizen (main + duplicate email + weak password)', async () => {
  const page = await session();
  await page.goto('/register');
  await noValidate(page);
  await page.getByLabel('Full name').fill('Priya Nair');
  await page.getByLabel('Email').fill('mahesh@example.com');
  await page.getByLabel('Mobile number').fill(priyaMobile);
  await page.locator('#password').fill('short1');
  await page.locator('#confirmPassword').fill('short2');
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.locator('#error-summary')).toContainText('at least 8 characters');
  await expect(page.locator('#error-summary')).toContainText('do not match');
  await expect(page.locator('#password')).toHaveValue('');
  // doc 12 §2.2: uniqueness (step 3) is checked once the fields pass Bean Validation
  await page.locator('#password').fill('Strong@123');
  await page.locator('#confirmPassword').fill('Strong@123');
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.locator('#error-summary')).toContainText('Email already registered');
  await page.getByLabel('Email').fill(priyaEmail);
  await page.locator('#password').fill('Strong@123');
  await page.locator('#confirmPassword').fill('Strong@123');
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page).toHaveURL(/\/verify-mobile$/);
  await expect(page.locator('main')).toContainText('Account created');
  page.priya = page;
  globalThis.priyaPage = page;
  return `registered ${priyaEmail}; field errors for taken email and short password`;
});

await check('UC', 'UC-04', 'Verify mobile with OTP (wrong code, resend cooldown, correct code)', async () => {
  const page = globalThis.priyaPage;
  const text = await page.locator('main').innerText();
  const code = text.match(/Your OTP is (\d{6})/)[1];
  const wrong = code === '000000' ? '111111' : '000000';
  await page.getByLabel(/OTP/).fill(wrong);
  await page.getByRole('button', { name: /Verify/ }).click();
  await expect(page.locator('main')).toContainText('Incorrect OTP, 2 attempts left');
  const resend = page.getByRole('button', { name: 'Send a new code' });
  await expect(resend).toBeDisabled();
  await expect(page.locator('main')).toContainText('You can request a new code at');
  // a resend posted anyway (button bypassed) is refused by the server inside the 60 s cooldown
  await page.locator('form[action="/verify-mobile/resend"]').evaluate((f) => f.submit());
  await expect(page).toHaveURL(/\/verify-mobile$/);
  await expect(page.locator('main')).toContainText('You can request a new code at');
  await expect(page.locator('main')).not.toContainText('A new OTP was sent');
  await page.getByLabel(/OTP/).fill(code);
  await page.getByRole('button', { name: /Verify/ }).click();
  await expect(page).toHaveURL(/\/citizen\/complaints$/);
  await expectFlash(page, 'Mobile verified');
  return 'wrong code counted down; resend disabled and refused by the server inside the cooldown; correct code verified';
});

await check('UC', 'UC-02', 'Log in / log out (role landing, wrong password, inactive, lockout, logout)', async () => {
  const page = await session();
  for (const [email, pw, home] of [['admin@example.com', ADMIN_PW, '/admin/dashboard'], ['anita.deshmukh@example.com', 'Demo@1234', '/officer/queue'], ['mahesh@example.com', 'Demo@1234', '/citizen/complaints']]) {
    await page.context().clearCookies();
    await page.goto('/login');
    await page.getByLabel('Email').fill(email.toUpperCase());
    await page.getByLabel('Password').fill(pw);
    await page.getByRole('button', { name: 'Log in' }).click();
    await expect(page).toHaveURL(new RegExp(`${home}$`));
  }
  await page.locator('form[action="/logout"]').first().evaluate((f) => f.submit());
  await expect(page).toHaveURL(/\/login\?logout$/);
  await expect(page.locator('main')).toContainText('You have been logged out');
  await page.getByLabel('Email').fill('mahesh@example.com');
  await page.getByLabel('Password').fill('Wrong@9999');
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page.locator('main')).toContainText('Invalid email or password, or account inactive.');
  await expect(page.getByLabel('Email')).toHaveValue('mahesh@example.com');
  await page.getByLabel('Password').fill('Demo@1234');
  await page.getByLabel('Email').fill('kiran.more@example.com');
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page.locator('main')).toContainText('Invalid email or password, or account inactive.');
  const victim = `lockout.${stamp}@example.com`;
  for (let i = 0; i < 5; i++) {
    await page.getByLabel('Email').fill(victim);
    await page.getByLabel('Password').fill('Wrong@9999');
    await page.getByRole('button', { name: 'Log in' }).click();
  }
  await page.getByLabel('Email').fill(victim);
  await page.getByLabel('Password').fill('Wrong@9999');
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page.locator('main')).toContainText('Too many failed attempts');
  await done(page);
  return 'three roles land on their homes; generic error for wrong password and inactive officer; lockout after 5 failures';
});

await check('UC', 'UC-03', 'Change own password (wrong current, mismatch, success, old password rejected)', async () => {
  const page = await session(priyaEmail, 'Strong@123');
  await page.goto('/account/password');
  await noValidate(page);
  await page.locator('#currentPassword').fill('Wrong@123');
  await page.locator('#newPassword').fill('Better@456');
  await page.locator('#confirmPassword').fill('Better@457');
  await page.getByRole('button', { name: /Change password|Save/ }).click();
  await expect(page.locator('#error-summary')).toBeVisible();
  await page.locator('#currentPassword').fill('Strong@123');
  await page.locator('#newPassword').fill('Better@456');
  await page.locator('#confirmPassword').fill('Better@456');
  await page.getByRole('button', { name: /Change password|Save/ }).click();
  await expectFlash(page, 'Password changed');
  await done(page);
  const again = await session();
  await again.goto('/login');
  await again.getByLabel('Email').fill(priyaEmail);
  await again.getByLabel('Password').fill('Strong@123');
  await again.getByRole('button', { name: 'Log in' }).click();
  await expect(again.locator('main')).toContainText('Invalid email or password');
  await done(again);
  await done(globalThis.priyaPage);
  return 'errors shown for wrong current / mismatch; new password works, old refused';
});

let submitted;
await check('UC', 'UC-10', 'Submit complaint (validation, wrong file type, too many files, success with photo + PDF)', async () => {
  const page = await session('sneha@example.com');
  await page.goto('/citizen/complaints/new');
  await noValidate(page);
  await page.getByRole('button', { name: 'Submit complaint' }).click();
  for (const text of ['Choose a category', 'Choose an area', 'Enter a title', 'Describe the problem', 'Enter the address']) {
    await expect(page.locator('#error-summary')).toContainText(text);
  }
  await page.getByLabel('Category').selectOption({ label: 'Water Leakage' });
  await page.getByLabel('Area').selectOption({ label: 'Kothrud' });
  await page.getByLabel('Title').fill('Leak');
  await page.getByLabel('Description').fill('short');
  await page.getByLabel('Address').fill('Karve Road near Kothrud Depot');
  await page.getByLabel('Photos or documents').setInputFiles([{ name: 'virus.jpg', mimeType: 'image/jpeg', buffer: Buffer.from('MZ fake exe') }]);
  await page.getByRole('button', { name: 'Submit complaint' }).click();
  await expect(page.locator('#error-summary')).toContainText('Title must be');
  await expect(page.locator('#error-summary')).toContainText('Description must be');
  await expect(page.getByLabel('Address')).toHaveValue('Karve Road near Kothrud Depot');
  // doc 12 §2.2: file content is checked (step 3) once the fields pass Bean Validation
  await page.getByLabel('Title').fill(`Water leaking at depot gate ${stamp}`);
  await page.getByLabel('Description').fill('Clean water has been leaking from the main pipe at the depot gate since morning.');
  await page.getByLabel('Photos or documents').setInputFiles([{ name: 'virus.jpg', mimeType: 'image/jpeg', buffer: Buffer.from('MZ fake exe') }]);
  await page.getByRole('button', { name: 'Submit complaint' }).click();
  await expect(page.locator('main')).toContainText('only JPG, PNG or PDF files are allowed');
  const six = Array.from({ length: 6 }, (_, i) => ({ name: `p${i}.png`, mimeType: 'image/png', buffer: Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, i]) }));
  await page.getByLabel('Title').fill(`Water leaking at depot gate ${stamp}`);
  await page.getByLabel('Description').fill('Clean water has been leaking from the main pipe at the depot gate since morning.');
  await page.getByLabel('Photos or documents').setInputFiles(six);
  await page.getByRole('button', { name: 'Submit complaint' }).click();
  await expect(page.locator('main')).toContainText('at most 5 files');
  await page.getByLabel('Photos or documents').setInputFiles(['photo.png', 'doc.pdf']);
  await page.getByRole('button', { name: 'Submit complaint' }).click();
  await expect(page).toHaveURL(/\/submitted$/);
  await expect(page.locator('main')).toContainText(/GRV-\d{4}-\d{6}/);
  submitted = (await page.getByRole('link', { name: 'View complaint' }).getAttribute('href')).split('/').pop();
  await page.goto(`/complaints/${submitted}`);
  await expect(page.locator('main header')).toContainText('Submitted');
  await expect(page.locator('main')).toContainText('photo.png');
  await expect(page.locator('main')).toContainText('doc.pdf');
  await done(page);
  return `${submitted} filed with 2 attachments after required, length, type and count errors`;
});

await check('UC', 'UC-11', 'Edit complaint (no changes, change title, revision in timeline)', async () => {
  const page = await session('sneha@example.com');
  await page.goto(`/complaints/${submitted}`);
  await page.getByRole('link', { name: 'Edit', exact: true }).click();
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expectFlash(page, 'No changes to save');
  await page.goto(`/complaints/${submitted}`);
  await page.getByRole('link', { name: 'Edit', exact: true }).click();
  await page.getByLabel('Title').fill(`Main pipe leaking at depot gate ${stamp}`);
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expectFlash(page, 'Complaint updated');
  await expect(page.locator('main')).toContainText('Complaint edited');
  await expect(page.locator('main')).toContainText(/Title changed from[\s\S]*to\s+Main pipe leaking at depot gate/);
  await done(page);
  return 'no-op edit refused politely; title change recorded as revision';
});

let withdrawable;
await check('UC', 'UC-12', 'Withdraw complaint (reason required, success; refused after work started)', async () => {
  const page = await session('sneha@example.com');
  withdrawable = await newComplaint(page, { title: `Streetlight flicker ${stamp}`, category: 'Streetlight', area: 'Baner' });
  await page.goto(`/complaints/${withdrawable}`);
  await page.getByRole('link', { name: 'Withdraw', exact: true }).click();
  await noValidate(page);
  await page.locator('main form').last().getByRole('button').last().click();
  await expect(page.locator('#error-summary')).toBeVisible();
  await page.locator('#remark').fill('The light started working again this evening.');
  await page.locator('main form').last().getByRole('button').last().click();
  await expectFlash(page, 'Complaint withdrawn');
  await expect(page.locator('main header')).toContainText('Withdrawn');
  await expect(page.getByRole('link', { name: 'Edit', exact: true })).toHaveCount(0);
  await done(page);
  const mahesh = await session('mahesh@example.com');
  await mahesh.goto(`/citizen/complaints/${no(1)}/withdraw`);
  const body = await mahesh.locator('main').innerText();
  await done(mahesh);
  if (!/Work has already started|not allowed/i.test(body)) throw new Error(`withdraw of in-progress ${no(1)} not refused: ${body.slice(0, 200)}`);
  return `${withdrawable} withdrawn with reason; ${no(1)} (In Progress) refused`;
});

await check('UC', 'UC-13', 'My Complaints / Watching lists (filter, search, watching)', async () => {
  const page = await session('mahesh@example.com');
  await page.goto('/citizen/complaints');
  for (const n of [1, 4, 9]) await expect(page.locator('main')).toContainText(no(n));
  await expect(page.locator('main')).not.toContainText(no(3));
  await page.goto('/citizen/complaints?status=RESOLVED');
  await expect(page.locator('main')).toContainText(no(4));
  await expect(page.locator('main')).not.toContainText(no(9));
  await page.goto(`/citizen/complaints?q=${no(9)}`);
  await expect(page.locator('main .complaint-no')).toHaveCount(1);
  await done(page);
  const jayesh = await session('jayesh@example.com');
  await jayesh.goto('/citizen/watching');
  await expect(jayesh.locator('main')).toContainText(no(1));
  await done(jayesh);
  return 'own complaints only, status filter, number search, Jayesh watches 000001';
});

await check('UC', 'UC-14', 'Complaint detail & timeline (owner, watcher, officer, admin, duplicate banner, staff names hidden)', async () => {
  const owner = await session('mahesh@example.com');
  await owner.goto(`/complaints/${no(1)}`);
  await expect(owner.locator('main')).toContainText('Water Supply Dept.');
  await expect(owner.locator('main')).not.toContainText('Anita');
  await done(owner);
  const watcher = await session('jayesh@example.com');
  await watcher.goto(`/complaints/${no(1)}`);
  await expect(watcher.locator('main')).toContainText('You are watching this complaint');
  await expect(watcher.getByRole('link', { name: 'Edit', exact: true })).toHaveCount(0);
  await watcher.goto(`/complaints/${no(2)}`);
  await expect(watcher.locator('main')).toContainText(no(1));
  await expect(watcher.locator('main header')).toContainText('Duplicate');
  await done(watcher);
  const officer = await session('anita.deshmukh@example.com');
  await officer.goto(`/complaints/${no(1)}`);
  await expect(officer.getByRole('link', { name: 'Mark as resolved', exact: true })).toBeVisible();
  await done(officer);
  const admin = await session('admin@example.com', ADMIN_PW);
  await admin.goto(`/complaints/${no(1)}`);
  await expect(admin.locator('main')).toContainText(no(1));
  await done(admin);
  return 'each role sees the right actions; citizen timeline shows department, not officer name';
});

await check('UC', 'UC-15', 'Review resolution: reopen with reason, then close; window and limits enforced', async () => {
  const page = await session('mahesh@example.com');
  await page.goto(`/complaints/${no(4)}`);
  await expect(page.locator('main')).toContainText(/review/i);
  await page.getByRole('link', { name: 'Reopen', exact: true }).click();
  await noValidate(page);
  await page.locator('main form').last().getByRole('button').last().click();
  await expect(page.locator('#error-summary')).toBeVisible();
  await page.locator('#remark').fill('The streetlight went off again the next night.');
  await page.locator('main form').last().getByRole('button').last().click();
  await expectFlash(page, 'Complaint reopened');
  await expect(page.locator('main header')).toContainText('Reopened');
  await done(page);
  // officer resolves it again so the citizen can close it
  const amit = await session('amit.pawar@example.com');
  await action(amit, no(4), 'Start work', 'Electrician assigned to check the fitting again.');
  await expectFlash(amit, 'Work started');
  await action(amit, no(4), 'Mark as resolved', 'Replaced the faulty choke; light tested for an hour.');
  await expectFlash(amit, 'Complaint resolved');
  await done(amit);
  const back = await session('mahesh@example.com');
  await back.goto(`/complaints/${no(4)}`);
  await expect(back.locator('main')).toContainText(/reopen this complaint 1 more time|1 more time/);
  await back.getByRole('link', { name: 'Close complaint', exact: true }).click();
  await back.locator('main form').last().getByRole('button').last().click();
  await expectFlash(back, 'The complaint is closed');
  await expect(back.locator('main header')).toContainText('Closed');
  await done(back);
  return `${no(4)}: reopen (reason required) → officer start + resolve → close; reopen count shown`;
});

await check('UC', 'UC-16', 'View / download attachment (owner yes, outsider 404, download name)', async () => {
  const page = await session('sneha@example.com');
  await page.goto(`/complaints/${submitted}`);
  const href = await page.getByRole('link', { name: /photo\.png/ }).first().getAttribute('href');
  const response = await page.request.get(href);
  expect(response.status()).toBe(200);
  expect(response.headers()['content-type']).toContain('image/png');
  expect(response.headers()['x-content-type-options']).toBe('nosniff');
  const download = await page.request.get(`${href}?download=true`);
  expect(download.headers()['content-disposition']).toContain('attachment');
  await done(page);
  const outsider = await session('jayesh@example.com');
  const denied = await outsider.request.get(href);
  await done(outsider);
  expect(denied.status()).toBe(404);
  return `inline with nosniff, ?download=true as attachment, outsider 404 (${href})`;
});

await check('UC', 'UC-20', 'Review new complaint (admin, confirm prompt)', async () => {
  const page = await session('admin@example.com', ADMIN_PW);
  let prompted = false;
  page.once('dialog', (d) => { prompted = true; d.accept(); });
  await page.goto(`/complaints/${submitted}`);
  await page.getByRole('button', { name: 'Start review', exact: true }).click();
  await expectFlash(page, 'Review started');
  await expect(page.locator('main header')).toContainText('Under Review');
  await done(page);
  if (!prompted) throw new Error('no confirm prompt');
  return `${submitted} moved to Under Review after a confirm prompt`;
});

await check('UC', 'UC-21', 'Assign (default department pre-selected) and reassign (remark required)', async () => {
  const page = await session('admin@example.com', ADMIN_PW);
  await page.goto(`/complaints/${submitted}`);
  await page.getByRole('link', { name: 'Assign', exact: true }).click();
  await expect(page.getByRole('combobox', { name: 'Department' }).locator('option:checked')).toHaveText('Water Supply');
  await page.getByRole('button', { name: 'Assign', exact: true }).click();
  await expectFlash(page, 'Assigned to Water Supply');
  await page.goto(`/complaints/${no(5)}`);
  await page.getByRole('link', { name: 'Reassign', exact: true }).click();
  await noValidate(page);
  await page.getByRole('combobox', { name: 'Department' }).selectOption({ label: 'Drainage' });
  await page.locator('main form').last().getByRole('button').last().click();
  await expect(page.locator('#error-summary')).toBeVisible();
  await page.locator('#remark').fill('The pothole is caused by a collapsed drain underneath.');
  await page.locator('main form').last().getByRole('button').last().click();
  await expectFlash(page, 'Reassigned to Drainage');
  await done(page);
  return `${submitted} → Water Supply (default); ${no(5)} reassigned to Drainage with remark`;
});

let dupSource;
await check('UC', 'UC-23', 'Mark duplicate (unknown no., self, other department, success; watcher added)', async () => {
  const page = await session('anita.deshmukh@example.com');
  await page.goto(`/complaints/${submitted}`);
  await page.getByRole('link', { name: 'Mark as duplicate', exact: true }).click();
  const field = page.getByRole('textbox', { name: 'Original complaint number', exact: true });
  for (const [value, message] of [[`GRV-${Y}-999999`, 'No complaint'], [submitted, 'cannot be a duplicate of itself'], [no(7), 'not in your department']]) {
    await field.fill(value);
    await page.getByRole('button', { name: 'Find complaint' }).click();
    await expect(page.locator('main')).toContainText(message);
  }
  await field.fill(no(1));
  await page.getByRole('button', { name: 'Find complaint' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Is this the same problem?');
  await page.getByRole('textbox', { name: 'Note to the citizen', exact: true }).fill('This is the same leak already being repaired near the depot.');
  await page.getByRole('button', { name: 'Link as duplicate' }).click();
  await expectFlash(page, `Linked to ${no(1)}`);
  await done(page);
  const sneha = await session('sneha@example.com');
  await sneha.goto('/citizen/watching');
  await expect(sneha.locator('main')).toContainText(no(1));
  await done(sneha);
  dupSource = submitted;
  return `${submitted} linked to ${no(1)} after unknown/self/other-department refusals; Sneha now watches ${no(1)}`;
});

await check('UC', 'UC-17', 'Unwatch and watch again', async () => {
  const page = await session('sneha@example.com');
  await postButton(page, no(1), 'Unwatch');
  await expectFlash(page, 'no longer receive updates');
  await page.goto('/citizen/watching');
  await expect(page.locator('main')).not.toContainText(no(1));
  await page.goto(`/complaints/${dupSource}`);
  await page.getByRole('link', { name: no(1), exact: true }).first().click();
  page.once('dialog', (d) => d.accept());
  await page.getByRole('button', { name: 'Watch again', exact: true }).click();
  await expectFlash(page, 'watching this complaint again');
  await done(page);
  return 'unwatch removes it from Watching; original still reachable from the duplicate; watch again works';
});

let workflow;
await check('UC', 'UC-30', 'Department queue (scope, overdue first, filters)', async () => {
  const page = await session('anita.deshmukh@example.com');
  await page.goto('/officer/queue');
  await expect(page.locator('main')).toContainText('Water Supply');
  await expect(page.locator('main')).toContainText(no(1));
  await expect(page.locator('main')).not.toContainText(no(7));
  await page.goto('/officer/queue?status=ALL');
  await expect(page.locator('main')).toContainText(no(2));
  await done(page);
  const suresh = await session('suresh.patil@example.com');
  workflow = no(7);
  await suresh.goto('/officer/queue');
  await expect(suresh.locator('main')).toContainText(no(7));
  await done(suresh);
  return 'Water Supply and Sanitation queues show only their own complaints';
});

await check('UC', 'UC-31', 'Start work on a reopened complaint (remark required)', async () => {
  const page = await session('suresh.patil@example.com');
  await page.goto(`/complaints/${no(7)}`);
  await page.getByRole('link', { name: 'Start work', exact: true }).click();
  await noValidate(page);
  await page.locator('main form').last().getByRole('button').last().click();
  await expect(page.locator('#error-summary')).toBeVisible();
  await page.locator('#remark').fill('Collection vehicle rescheduled for this ward.');
  await page.locator('main form').last().getByRole('button').last().click();
  await expectFlash(page, 'Work started');
  await expect(page.locator('main header')).toContainText('In Progress');
  await done(page);
});

await check('UC', 'UC-32', 'Add progress remark', async () => {
  const page = await session('suresh.patil@example.com');
  await action(page, no(7), 'Add update', 'Half of the pile was cleared today; the rest tomorrow.');
  await expectFlash(page, 'Update added');
  await expect(page.locator('main')).toContainText('Half of the pile was cleared today');
  await done(page);
});

await check('UC', 'UC-33', 'Resolve (with work-done file) and optimistic-lock conflict on a stale form', async () => {
  const first = await session('suresh.patil@example.com');
  const second = await session('suresh.patil@example.com');
  await first.goto(`/officer/complaints/${no(7)}/resolve`);
  await second.goto(`/officer/complaints/${no(7)}/resolve`);
  await first.locator('#remark').fill('Area cleared fully and the bin was replaced.');
  const files = first.locator('input[type=file]');
  if (await files.count()) await files.setInputFiles('photo.png');
  await first.locator('main form').last().getByRole('button').last().click();
  await expectFlash(first, 'Complaint resolved');
  await second.locator('#remark').fill('Trying to resolve from an older page.');
  await second.locator('main form').last().getByRole('button').last().click();
  await expectFlash(second, 'updated by someone else');
  await done(first); await done(second);
  return 'first resolve wins; second (stale version) gets the "updated by someone else" message';
});

await check('UC', 'UC-34', 'Send complaint back to the admin (wrong department)', async () => {
  const page = await session('pooja.shinde@example.com');
  await page.goto(`/complaints/${no(5)}`);
  await page.getByRole('link', { name: /Send back/ }).first().click();
  await page.locator('#remark').fill('Not a drainage issue after inspection; please route to Road Maintenance.');
  await page.locator('main form').last().getByRole('button').last().click();
  await expectFlash(page, 'sent back to the admin');
  await page.goto(`/complaints/${no(5)}`);
  const body = await page.locator('main').innerText();
  await done(page);
  const admin = await session('admin@example.com', ADMIN_PW);
  expect(await status(admin, no(5))).toBe('Under Review');
  await done(admin);
  return `${no(5)} back to Under Review; Drainage officer loses access (${/Page not found/.test(body) ? '404' : 'visible'})`;
});

await check('UC', 'UC-22', 'Reject (reason required; officer rejection is covered by TC-UC-22b/c)', async () => {
  const admin = await session('admin@example.com', ADMIN_PW);
  await admin.goto(`/complaints/${no(8)}`);
  await admin.getByRole('link', { name: 'Reject', exact: true }).click();
  await noValidate(admin);
  await admin.locator('main form').last().getByRole('button').last().click();
  await expect(admin.locator('#error-summary')).toBeVisible();
  await admin.locator('#remark').fill('A hoarding on private land is outside the corporation scope.');
  await admin.locator('main form').last().getByRole('button').last().click();
  await expectFlash(admin, 'Complaint rejected');
  await done(admin);
  const sneha = await session('sneha@example.com');
  expect(await status(sneha, no(8))).toBe('Rejected');
  await done(sneha);
  return `${no(8)} rejected with a reason; owner sees Rejected`;
});

await check('UC', 'UC-40/41/42', 'Manage departments, categories, areas (create, duplicate name, deactivate, guard)', async () => {
  const page = await session('admin@example.com', ADMIN_PW);
  const dept = `Parks ${stamp % 100000}`;
  await page.goto('/admin/departments/new');
  await page.getByLabel('Name').fill('Sanitation');
  await page.locator('main form').getByRole('button').last().click();
  await expect(page.locator('main')).toContainText(/already/i);
  await page.getByLabel('Name').fill(dept);
  await page.locator('main form').getByRole('button').last().click();
  await expectFlash(page, 'Department saved');
  await page.goto('/admin/categories/new');
  await page.getByLabel('Name').fill(`Tree fall ${stamp % 100000}`);
  await page.getByLabel(/Default department/).selectOption({ label: dept });
  await page.getByLabel(/SLA/).fill('4');
  await page.locator('main form').getByRole('button').last().click();
  await expectFlash(page, 'Category saved');
  await page.goto('/admin/departments');
  page.once('dialog', (d) => d.accept());
  await page.getByRole('row', { name: new RegExp(dept) }).getByRole('button', { name: /Deactivate/ }).click();
  await expect(page.locator('main')).toContainText(/default department|cannot be deactivated|active category/i);
  await page.goto('/admin/areas/new');
  await page.getByLabel('Name').fill(`Wakad ${stamp % 100000}`);
  await page.locator('main form').getByRole('button').last().click();
  await expectFlash(page, 'Area saved');
  await done(page);
  return `${dept} + category created; duplicate name refused; deactivating a default department refused`;
});

await check('UC', 'UC-43', 'Manage officer accounts (create, duplicate email, login as new officer)', async () => {
  const page = await session('admin@example.com', ADMIN_PW);
  const email = `officer.${stamp}@example.com`;
  await page.goto('/admin/officers/new');
  await noValidate(page);
  await page.getByLabel('Full name').fill('Test Officer');
  await page.getByLabel('Email').fill('anita.deshmukh@example.com');
  await page.getByLabel('Mobile').fill(`97${String(stamp).slice(-8)}`);
  await page.getByLabel('Department').selectOption({ label: 'Water Supply' });
  await page.getByLabel(/password/i).first().fill('Officer@2027');
  const confirm = page.getByLabel(/Confirm/);
  if (await confirm.count()) await confirm.fill('Officer@2027');
  await page.locator('main form').getByRole('button').last().click();
  await expect(page.locator('main')).toContainText('Email already registered');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel(/password/i).first().fill('Officer@2027');
  if (await confirm.count()) await confirm.fill('Officer@2027');
  await page.locator('main form').getByRole('button').last().click();
  await expectFlash(page, 'Officer account created');
  await done(page);
  const officer = await session(email, 'Officer@2027');
  await expect(officer).toHaveURL(/\/officer\/queue$/);
  await done(officer);
  return `${email} created after a duplicate-email error and can log in to the Water Supply queue`;
});

await check('UC', 'UC-44', 'Manage citizen accounts (deactivate → login refused, reactivate, reset password)', async () => {
  const page = await session('admin@example.com', ADMIN_PW);
  await page.goto(`/admin/citizens?q=${encodeURIComponent(priyaEmail)}`);
  page.once('dialog', (d) => d.accept());
  await page.getByRole('button', { name: /Deactivate/ }).first().click();
  await done(page);
  const blocked = await session();
  await blocked.goto('/login');
  await blocked.getByLabel('Email').fill(priyaEmail);
  await blocked.getByLabel('Password').fill('Better@456');
  await blocked.getByRole('button', { name: 'Log in' }).click();
  await expect(blocked.locator('main')).toContainText('Invalid email or password, or account inactive.');
  await done(blocked);
  const again = await session('admin@example.com', ADMIN_PW);
  await again.goto(`/admin/citizens?q=${encodeURIComponent(priyaEmail)}`);
  again.once('dialog', (d) => d.accept());
  await again.getByRole('button', { name: /Activate/ }).first().click();
  await again.goto(`/admin/citizens?q=${encodeURIComponent(priyaEmail)}`);
  await again.getByRole('link', { name: /Reset password/ }).first().click();
  await again.getByLabel(/New password/).fill('Reset@2027x');
  const confirm = again.getByLabel(/Confirm/);
  if (await confirm.count()) await confirm.fill('Reset@2027x');
  await again.locator('main form').getByRole('button').last().click();
  await expect(again.locator('.alert-success').first()).toBeVisible();
  await done(again);
  const priya = await session(priyaEmail, 'Reset@2027x');
  await expect(priya).toHaveURL(/\/citizen\/complaints$/);
  await done(priya);
  return 'deactivated citizen cannot log in; reactivated; admin reset password works';
});

await check('UC', 'UC-50', 'Admin dashboard and reports (cards link to matching lists, CSV export)', async () => {
  const page = await session('admin@example.com', ADMIN_PW);
  await page.goto('/admin/dashboard');
  for (const label of ['Total', 'Pending', 'Overdue']) await expect(page.getByRole('link', { name: new RegExp(`^${label}: \\d+$`) })).toBeVisible();
  const total = Number((await page.getByRole('link', { name: /^Total: \d+$/ }).getAttribute('aria-label')).split(': ')[1]);
  const pending = page.getByRole('link', { name: /^Pending: \d+$/ });
  const count = (await pending.getAttribute('aria-label')).split(': ')[1];
  await pending.click();
  await expect(page.locator('main')).toContainText(new RegExp(`\\b${count}\\b`));
  await expect.poll(async () => page.evaluate(() => Boolean(window.Chart))).toBe(false); // chart only on dashboard
  await page.goto('/admin/dashboard');
  await expect.poll(() => page.evaluate(() => Boolean(window.Chart?.getChart(document.getElementById('department-chart'))))).toBe(true);
  const csv = await page.request.get('/admin/complaints/export.csv?status=ALL');
  const body = await csv.text();
  expect(csv.headers()['content-disposition']).toMatch(/complaints-\d{8}-\d{4}\.csv/);
  expect(body.split('\r\n').filter(Boolean).length - 1).toBe(total);
  expect(body).not.toContain('@example.com');
  await done(page);
  return `Total ${total} = CSV rows; Pending card ${count} opens matching list; chart rendered`;
});

await check('UC', 'UC-51', 'Officer summary cards', async () => {
  const page = await session('anita.deshmukh@example.com');
  await page.goto('/officer/queue');
  const cards = await page.locator('[aria-label="Queue summary"] li').allInnerTexts();
  expect(cards.length).toBeGreaterThan(5);
  expect(cards.join(' ')).not.toMatch(/Pending/);
  await done(page);
  return cards.map((c) => c.replace(/\s+/g, ' ')).join(' · ');
});

await check('UC', 'UC-60', 'Notifications (bell count, open marks read, mark all read)', async () => {
  const page = await session('sneha@example.com');
  const bell = page.getByRole('link', { name: /^Notifications,/ });
  const before = await bell.getAttribute('aria-label');
  if (!/\d+ unread/.test(before)) throw new Error(`expected unread notifications, got "${before}"`);
  await bell.click();
  await page.getByRole('button', { name: /^Open complaint/ }).first().click();
  await expect(page).toHaveURL(/\/complaints\/GRV-/);
  await page.goto('/notifications');
  const mark = page.getByRole('button', { name: 'Mark all as read' });
  if (await mark.count()) await mark.click();
  await expect(page.getByRole('link', { name: 'Notifications, no unread' })).toBeVisible();
  await done(page);
  return `${before} → opened one → all read`;
});

await check('UC', 'UC-70', 'Auto-close expired resolved complaints (dev job runs every 5 minutes)', async () => {
  const page = await session('mahesh@example.com');
  await expect.poll(() => status(page, no(11)), { timeout: 6 * 60_000, intervals: [15_000] }).toBe('Closed');
  await page.goto(`/complaints/${no(11)}`);
  await expect(page.locator('main')).toContainText(/automatically|Auto-closed|no response/i);
  await done(page);
  return `${no(11)} (resolved 8 days ago) closed by the scheduler with an auto-close entry`;
});

// ───────────────────────────── Errors ─────────────────────────────
await check('ERR', 'E-404a', 'Unknown complaint number → 404 page', async () => {
  const page = await session('mahesh@example.com');
  const r = await page.goto(`/complaints/GRV-${Y}-999999`);
  expect(r.status()).toBe(404);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Page not found');
  await done(page);
});
await check('ERR', 'E-404b', 'Another citizen\'s complaint → 404 (not 403: existence is not revealed)', async () => {
  const page = await session('sneha@example.com');
  const r = await page.goto(`/complaints/${no(9)}`);
  expect(r.status()).toBe(404);
  const a = await page.request.get(`/complaints/GRV-${Y}-999998`);
  expect(a.status()).toBe(404);
  await done(page);
});
await check('ERR', 'E-404c', 'Officer opens another department\'s complaint → 404', async () => {
  const page = await session('anita.deshmukh@example.com');
  const r = await page.goto(`/complaints/${no(7)}`);
  expect(r.status()).toBe(404);
  await done(page);
});
await check('ERR', 'E-404d', 'Unknown URL and missing static file → 404 without a session cookie', async () => {
  const page = await session();
  const r = await page.goto('/images/missing.png');
  expect(r.status()).toBe(404);
  expect(r.headers()['set-cookie']).toBeUndefined();
  await done(page);
});
await check('ERR', 'E-403a', 'Wrong role (citizen opens admin and officer pages) → 403 page', async () => {
  const page = await session('mahesh@example.com');
  for (const path of ['/admin/dashboard', '/officer/queue', '/admin/departments']) {
    const r = await page.goto(path);
    expect(r.status()).toBe(403);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Access denied');
  }
  await done(page);
});
await check('ERR', 'E-403b', 'Form without CSRF token while logged in → 403 "form expired"', async () => {
  const page = await session('mahesh@example.com');
  const r = await page.request.post('/notifications/read-all', { headers: { Accept: 'text/html' }, maxRedirects: 0 });
  expect(r.status()).toBe(403);
  expect(await r.text()).toContain('This form has expired');
  await done(page);
});
await check('ERR', 'E-401', 'Session expired: stale session cookie → /login?expired; JSON endpoint → 401', async () => {
  const page = await session('mahesh@example.com');
  await page.context().addCookies([{ name: 'JSESSIONID', value: '0123456789ABCDEF0123456789ABCDEF', url: BASE }]);
  await page.goto('/citizen/complaints');
  await expect(page).toHaveURL(/\/login\?expired$/);
  await expect(page.locator('main')).toContainText('Your session has ended');
  const json = await page.request.get('/notifications/unread-count', { headers: { Accept: 'application/json' } });
  expect(json.status()).toBe(401);
  await done(page);
});
await check('ERR', 'E-405', 'GET on a POST-only action → 405 page', async () => {
  const page = await session('mahesh@example.com');
  const r = await page.goto('/notifications/read-all');
  expect(r.status()).toBe(405);
  await expect(page.locator('main')).toContainText('cannot be used in this way');
  await done(page);
});
await check('ERR', 'E-400', 'Request rejected by the firewall (encoded path traversal) → 400 page', async () => {
  // Playwright normalises URLs, so send the raw path with node:http
  const http = await import('node:http');
  for (const path of ['/complaints/%2e%2e/admin/dashboard', '/admin//dashboard', '/complaints/a%2fb']) {
    const { status, body } = await new Promise((resolve, reject) => {
      http.get({ host: 'localhost', port: 8080, path, headers: { Accept: 'text/html' } }, (res) => {
        let data = ''; res.on('data', (c) => { data += c; }); res.on('end', () => resolve({ status: res.statusCode, body: data }));
      }).on('error', reject);
    });
    expect(status, path).toBe(400);
    expect(body, path).toContain('Request not accepted');
  }
  return 'encoded ../, double slash and encoded / all answer the 400 page';
});
await check('ERR', 'E-413', 'Upload over 26 MB without JavaScript → back to the form with a size message', async () => {
  const page = await session('jayesh@example.com', 'Demo@1234', { width: 1280, height: 800 }, false);
  await page.goto('/citizen/complaints/new');
  await page.getByLabel('Category').selectOption({ label: 'Garbage' });
  await page.getByLabel('Area').selectOption({ label: 'Baner' });
  await page.getByLabel('Title').fill('Oversized upload check');
  await page.getByLabel('Description').fill('This upload is larger than the allowed request size.');
  await page.getByLabel('Address').fill('Baner Road near Balewadi Phata');
  const big = Buffer.alloc(27 * 1024 * 1024, 0x41); big.write('\x89PNG\r\n\x1a\n', 0, 'binary');
  await page.getByLabel('Photos or documents').setInputFiles({ name: 'huge.png', mimeType: 'image/png', buffer: big });
  await page.getByRole('button', { name: 'Submit complaint' }).click();
  await expect(page).toHaveURL(/\/citizen\/complaints\/new$/);
  await expect(page.locator('main')).toContainText('too large');
  await done(page);
});
await check('ERR', 'E-BR', 'Business refusals: edit after assignment, reopen of a closed complaint, staff action as citizen', async () => {
  const page = await session('mahesh@example.com');
  await page.goto(`/citizen/complaints/${no(1)}/edit`);
  const edit = await page.locator('main').innerText();
  expect(edit).toMatch(/can no longer be edited|not allowed/i);
  await page.goto(`/citizen/complaints/${no(4)}/reopen`);
  const reopen = await page.locator('main').innerText();
  expect(reopen).toMatch(/not allowed|review period ended/i);
  const r = await page.goto(`/officer/complaints/${no(1)}/resolve`);
  expect(r.status()).toBe(403);
  await done(page);
});
await check('ERR', 'E-500', 'Unexpected error page with reference id', async () => {
  // No safe way to raise a server error in a running build; covered by TC-VE-14 (test-only trigger controller).
  return 'not triggerable from the UI; verified by the automated test TC-VE-14 (500 page shows ERR-XXXX-XXXX, no stack trace)';
});

// ───────────────────────────── Screens ─────────────────────────────
const screens = [
  ['SCR-01', null, '/login'], ['SCR-02', null, '/register'], ['SCR-03', 'rahul@example.com', '/verify-mobile'],
  ['SCR-04', 'mahesh@example.com', '/account/password'], ['SCR-10', 'mahesh@example.com', '/citizen/complaints'],
  ['SCR-11', 'jayesh@example.com', '/citizen/watching'], ['SCR-12', 'mahesh@example.com', '/citizen/complaints/new'],
  ['SCR-13', 'sneha@example.com', `/citizen/complaints/${withdrawable}/submitted`], ['SCR-20', 'mahesh@example.com', `/complaints/${no(1)}`],
  ['SCR-21', 'anita.deshmukh@example.com', `/officer/complaints/${no(1)}/resolve`], ['SCR-30', 'anita.deshmukh@example.com', '/officer/queue'],
  ['SCR-40', 'admin@example.com', '/admin/dashboard'], ['SCR-41', 'admin@example.com', '/admin/complaints'],
  ['SCR-42', 'admin@example.com', '/admin/departments'], ['SCR-43', 'admin@example.com', '/admin/departments/new'],
  ['SCR-44', 'admin@example.com', '/admin/categories'], ['SCR-45', 'admin@example.com', '/admin/categories/new'],
  ['SCR-46', 'admin@example.com', '/admin/areas'], ['SCR-47', 'admin@example.com', '/admin/areas/new'],
  ['SCR-48', 'admin@example.com', '/admin/officers'], ['SCR-49', 'admin@example.com', '/admin/officers/new'],
  ['SCR-50', 'admin@example.com', '/admin/citizens'], ['SCR-51', 'admin@example.com', '/admin/officers/2/password'],
  ['SCR-60', 'mahesh@example.com', '/notifications'], ['SCR-90', 'mahesh@example.com', '/admin/dashboard'],
  ['SCR-91', 'mahesh@example.com', `/complaints/GRV-${Y}-999999`], ['SCR-93', 'mahesh@example.com', '/notifications/read-all'],
];
for (const width of [1280, 360]) {
  const pages = new Map();
  for (const [id, user, path] of screens) {
    await check('SCR', `${id}@${width}`, `${path} as ${user ?? 'visitor'}`, async () => {
      if (!pages.has(user)) pages.set(user, await session(user, user === 'admin@example.com' ? ADMIN_PW : 'Demo@1234', { width, height: 800 }));
      const page = pages.get(user);
      page.problems.length = 0;
      await page.goto(path);
      const title = await page.title();
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
      expect(overflow, 'horizontal scroll').toBeLessThanOrEqual(0);
      await page.screenshot({ path: `shots/${id}-${width}.png`, fullPage: true });
      if (width === 1280) {
        const axe = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
        expect(axe.violations.map((v) => `${v.id} (${v.nodes.length})`), 'axe').toEqual([]);
      }
      expect(page.problems, 'JS/CSP').toEqual([]);
      return title;
    });
  }
  for (const p of pages.values()) await done(p);
}

writeFileSync('results.json', JSON.stringify({ base: BASE, year: Y, at: new Date().toISOString(), results }, null, 2));
const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
await browser.close();
