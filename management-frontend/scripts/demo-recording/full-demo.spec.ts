import { expect, test } from '@playwright/test';

import { beat, loginAs, logout, navTo } from './helpers';

/**
 * Produces the single video embedded in the README. One test, not four, so Playwright writes
 * exactly one video file for the whole walkthrough (see scripts/demo-recording/README.md).
 *
 * The dev database carries no seed data beyond the four staff logins (Flyway's migrations are
 * schema-only), so this script creates its own sample course/student/book — in that order, since
 * the Registrar's enrollment step needs a course to enroll into, and the Librarian's assignment
 * step needs a student to assign to.
 */
const stamp = Date.now().toString().slice(-6);
const courseCode = `DEMO${stamp}`;
const studentCode = `S${stamp}`;
const isbn = `ISBN${stamp}`;
const staffUsername = `demo.tmp${stamp}`;

test('full product walkthrough', async ({ page }) => {
  await page.goto('/login');
  await beat(page, 2000);

  // ------------------------------------------------------------------------------------------
  // Course Administrator — create a course the Registrar will enroll a student into.
  // ------------------------------------------------------------------------------------------
  await loginAs(page, 'COURSE_ADMINISTRATOR');
  await navTo(page, 'Courses');
  await expect(page.getByRole('table')).toBeVisible();
  await page.getByRole('button', { name: 'Create course' }).click();
  await page.locator('input[name="courseCode"]').fill(courseCode);
  await page.locator('input[name="name"]').fill('Intro to Systems Design');
  await page.locator('input[name="credits"]').fill('4');
  await page.getByRole('button', { name: 'Create' }).click();
  await page.getByRole('dialog', { name: 'Create course' }).waitFor({ state: 'hidden' });
  // The dev database already carries a large benchmark dataset, so the fresh course can land on
  // any page of the unfiltered list — search for it by code instead of assuming it's on page one.
  await page.getByPlaceholder('Search courses…').fill(courseCode);
  await expect(page.getByText(courseCode).first()).toBeVisible();
  await beat(page);
  await page.getByText(courseCode).first().click();
  await expect(page).toHaveURL(new RegExp(`/courses/${courseCode}`));
  await beat(page, 1500);
  await logout(page);

  // ------------------------------------------------------------------------------------------
  // Registrar — register a student, browse the catalogue, enroll them, then end the enrollment.
  // ------------------------------------------------------------------------------------------
  await loginAs(page, 'REGISTRAR');
  await navTo(page, 'Students');
  await expect(page.getByRole('table')).toBeVisible();
  await page.getByRole('button', { name: 'Register student' }).click();
  await page.locator('input[name="studentCode"]').fill(studentCode);
  await page.locator('input[name="firstName"]').fill('Alex');
  await page.locator('input[name="lastName"]').fill('Rivera');
  await page.locator('input[name="email"]').fill(`alex.rivera.${stamp}@example.com`);
  await page.locator('input[name="dateOfBirth"]').fill('2001-04-12');
  await page.getByRole('button', { name: 'Register' }).click();
  await expect(page.getByText('Student registered')).toBeVisible();
  await beat(page, 1500);
  await page.getByRole('button', { name: 'Done' }).click();
  await page.getByRole('dialog', { name: 'Student registered' }).waitFor({ state: 'hidden' });
  await page.getByPlaceholder('Search students…').fill(studentCode);
  await expect(page.getByText(studentCode).first()).toBeVisible();
  await beat(page);
  await page.getByText(studentCode).first().click();
  await expect(page).toHaveURL(new RegExp(`/students/${studentCode}`));
  await beat(page, 1500);

  await navTo(page, 'Courses');
  await beat(page, 1500);

  await navTo(page, 'Enrollments');
  // StudentPicker ignores the query change from a one-shot `.fill()` (it skips one search to avoid
  // re-querying its own controlled reset), so type it out instead — and click first, since this
  // field's `autoFocus` races `pressSequentially`'s own focus and can eat the first keystrokes.
  const registrarPicker = page.getByPlaceholder('Search by code, name, or email…');
  await expect(registrarPicker).toBeVisible();
  await registrarPicker.click();
  await registrarPicker.pressSequentially(studentCode, { delay: 60 });
  await page.getByRole('option', { name: new RegExp(studentCode) }).click();
  await beat(page);
  await page.getByRole('button', { name: 'Enroll in a course' }).click();
  await page.getByPlaceholder('Search courses…').fill(courseCode);
  await page.getByText(courseCode).first().click();
  await page.getByRole('button', { name: /^Enroll in 1 course$/ }).click();
  await expect(page.getByText('1 of 1 enrolled')).toBeVisible();
  await beat(page, 1500);
  await page.getByRole('button', { name: 'Close' }).click();
  await expect(page.getByRole('button', { name: 'End' })).toBeVisible();
  await beat(page);
  await page.getByRole('button', { name: 'End' }).click();
  await page.getByRole('button', { name: 'End enrollment' }).click();
  await beat(page, 1500);
  await logout(page);

  // ------------------------------------------------------------------------------------------
  // Librarian — add a book, assign it to the student, then release it.
  // ------------------------------------------------------------------------------------------
  await loginAs(page, 'LIBRARIAN');
  await navTo(page, 'Books');
  await expect(page.getByRole('table')).toBeVisible();
  await page.getByRole('button', { name: 'Add book' }).click();
  await page.locator('input[name="isbn"]').fill(isbn);
  await page.locator('input[name="title"]').fill('Designing Data-Intensive Systems');
  await page.locator('input[name="author"]').fill('Sam Okafor');
  await page.getByRole('button', { name: 'Add' }).click();
  await page.getByRole('dialog', { name: 'Add book' }).waitFor({ state: 'hidden' });
  await page.getByPlaceholder('Search books…').fill(isbn);
  await expect(page.getByText(isbn).first()).toBeVisible();
  await beat(page);
  await page.getByText(isbn).first().click();
  await expect(page).toHaveURL(new RegExp(`/books/${isbn}`));
  await beat(page, 1500);

  const librarianPicker = page.getByPlaceholder('Search by code, name, or email…');
  await librarianPicker.click();
  await librarianPicker.pressSequentially(studentCode, { delay: 60 });
  await page.getByRole('option', { name: new RegExp(studentCode) }).click();
  await page.getByRole('button', { name: 'Assign' }).click();
  await expect(page.getByText(`Held by ${studentCode}`)).toBeVisible();
  await beat(page, 1500);
  await page.getByRole('button', { name: 'Release' }).click();
  await expect(page.getByText('On the shelf')).toBeVisible();
  await beat(page, 1500);
  await logout(page);

  // ------------------------------------------------------------------------------------------
  // System Administrator — provision a staff account, then deactivate / reactivate it.
  // ------------------------------------------------------------------------------------------
  await loginAs(page, 'SYSTEM_ADMINISTRATOR');
  await navTo(page, 'Staff Accounts');
  // Wait for the existing roster to actually render before touching the form: this route's first
  // compile under Turbopack can hot-swap the whole page in shortly after navigation, which would
  // silently wipe out anything already typed into a pre-swap instance of the form.
  await expect(page.getByRole('table')).toBeVisible();
  const usernameField = page.locator('input[name="username"]');
  await usernameField.click();
  await usernameField.pressSequentially(staffUsername, { delay: 40 });
  await page.locator('select[name="role"]').selectOption('LIBRARIAN');
  await page.getByRole('button', { name: 'Create' }).click();
  await expect(page.getByText('Account created — password shown once')).toBeVisible();
  await beat(page, 1500);
  // Staff accounts has no search box, and the dataset already has many rows with the new one
  // landing on an arbitrary page — demonstrate the toggle on whichever row is on screen instead.
  const row = page.getByRole('rowgroup').nth(1).getByRole('row').first();
  const toggleButton = row.getByRole('button', { name: /Deactivate|Reactivate/ });
  const initialLabel = await toggleButton.innerText();
  await toggleButton.click();
  await beat(page, 1200);
  const flippedLabel = initialLabel === 'Deactivate' ? 'Reactivate' : 'Deactivate';
  await row.getByRole('button', { name: flippedLabel }).click();
  await beat(page, 1500);

  await navTo(page, 'Active Sessions');
  await beat(page, 1500);

  await logout(page);
  await beat(page, 2000);
});
