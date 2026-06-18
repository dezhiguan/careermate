const { test, expect } = require('@playwright/test');
const {
  assertBackendReady,
  attachDiagnostics,
  createTestPhone,
  gotoApp,
  loginViaSmsUi,
  MOCK_SMS_CODE,
  waitStable,
} = require('./e2e-env');

test.describe('P0 login default route', () => {
  test.beforeAll(async ({ request }) => {
    await assertBackendReady(request);
  });

  test.beforeEach(({ page }) => {
    attachDiagnostics(page);
  });

  test('mock SMS login lands on chat via root redirect', async ({ page }) => {
    await gotoApp(page, '/login');
    await waitStable(page);

    await loginViaSmsUi(page, createTestPhone(), MOCK_SMS_CODE);

    expect(page.url()).toContain('/#/chat');
  });
});
