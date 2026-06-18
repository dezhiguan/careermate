const { test, expect } = require('@playwright/test');
const {
  apiBaseURL,
  assertBackendReady,
  attachDiagnostics,
  createTestPhone,
  gotoApp,
  mobileLoginViaApi,
  seedPageWithToken,
} = require('./e2e-env');

async function putCareerProfile(request, token, data) {
  const response = await request.put(`${apiBaseURL}/profile/career`, {
    headers: { Authorization: `Bearer ${token}` },
    data,
    timeout: 20_000,
  });
  const body = await response.json().catch(() => null);
  expect(response.ok(), `更新画像失败: ${JSON.stringify(body)}`).toBeTruthy();
  expect(body?.code).toBe(0);
}

async function chatCompleteness(page) {
  await gotoApp(page, '/chat');
  await page.reload({ waitUntil: 'networkidle' });
  const text = await page.locator('.profile-aha-main').textContent({ timeout: 20_000 });
  return Number(text.match(/(\d+)%/)?.[1]);
}

async function mineCompleteness(page) {
  await gotoApp(page, '/mine');
  await page.reload({ waitUntil: 'networkidle' });
  const text = await page.locator('.completeness-pct').textContent({ timeout: 20_000 });
  return Number(text.match(/(\d+)%/)?.[1]);
}

test.describe('profile completeness consistency', () => {
  test.beforeAll(async ({ request }) => {
    await assertBackendReady(request);
  });

  test.beforeEach(({ page }) => {
    attachDiagnostics(page);
  });

  test('chat banner and mine progress use the same score after profile updates', async ({ page, request }) => {
    const login = await mobileLoginViaApi(request, createTestPhone());
    await seedPageWithToken(page, login.token, login.user);

    await putCareerProfile(request, login.token, {
      targetRole: 'Java 后端',
      targetCity: '',
      seniority: '',
      workMode: '',
      skillKeywords: [],
    });
    expect(await chatCompleteness(page)).toBe(20);
    expect(await mineCompleteness(page)).toBe(20);

    await putCareerProfile(request, login.token, {
      targetRole: 'Java 后端',
      targetCity: '广州',
      seniority: '3-5年',
      workMode: '',
      skillKeywords: [],
    });
    expect(await chatCompleteness(page)).toBe(60);
    expect(await mineCompleteness(page)).toBe(60);
  });
});
