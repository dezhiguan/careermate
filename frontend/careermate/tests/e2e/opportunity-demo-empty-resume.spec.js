const { test, expect } = require('@playwright/test');
const {
  assertBackendReady,
  attachDiagnostics,
  apiBaseURL,
  createTestPhone,
  gotoApp,
  mobileLoginViaApi,
  seedPageWithToken,
} = require('./e2e-env');

async function clearUserResumes(request, token) {
  const headers = { Authorization: `Bearer ${token}` };
  const listRes = await request.get(`${apiBaseURL}/resumes`, { headers, timeout: 20_000 });
  const listBody = await listRes.json().catch(() => null);
  expect(listRes.ok(), `list resumes failed: ${JSON.stringify(listBody)}`).toBeTruthy();
  const resumes = Array.isArray(listBody?.data) ? listBody.data : [];

  for (const resume of resumes) {
    const id = resume.id || resume.resumeId;
    if (id == null) continue;
    const deleteRes = await request.delete(`${apiBaseURL}/resumes/${encodeURIComponent(String(id))}`, {
      headers,
      timeout: 20_000,
    });
    expect(deleteRes.ok(), `delete resume ${id} failed`).toBeTruthy();
  }
}

test.describe('P0 opportunity demo zero state', () => {
  test.beforeAll(async ({ request }) => {
    await assertBackendReady(request);
  });

  test.beforeEach(({ page }) => {
    attachDiagnostics(page);
  });

  test('empty resume user sees demo JD cards without fake match score', async ({ page, request }) => {
    const login = await mobileLoginViaApi(request, createTestPhone());
    await clearUserResumes(request, login.token);
    await seedPageWithToken(page, login.token, login.user);

    await gotoApp(page, '/opportunity');

    const cards = page.locator('.jd-card');
    await expect(cards.nth(4)).toBeVisible({ timeout: 30_000 });
    await expect(page.locator('.empty-state')).toHaveCount(0);

    const count = await cards.count();
    expect(count).toBeGreaterThanOrEqual(5);

    for (let i = 0; i < Math.min(count, 5); i += 1) {
      await expect(cards.nth(i).locator('.demo-badge')).toContainText('示例');
      await expect(cards.nth(i).locator('.match-score')).toHaveCount(0);
    }
    await expect(page.locator('.match-score')).toHaveCount(0);

    await cards.first().locator('.demo-badge').click();
    await expect(page).toHaveURL(/#\/mine/);
  });
});
