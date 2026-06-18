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

async function timedMarketGet(request, token, path) {
  const start = Date.now();
  const response = await request.get(`${apiBaseURL}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
    timeout: 20_000,
  });
  const durationMs = Date.now() - start;
  const body = await response.json().catch(() => null);
  expect(response.ok(), `${path} failed: ${JSON.stringify(body)}`).toBeTruthy();
  expect(body?.code).toBe(0);
  return { durationMs, body };
}

test.describe('P1 market cache and degraded fallback', () => {
  test.beforeAll(async ({ request }) => {
    await assertBackendReady(request);
  });

  test.beforeEach(({ page }) => {
    attachDiagnostics(page);
  });

  test('second market load is fast and cache miss degrades without 500', async ({ page, request }) => {
    const login = await mobileLoginViaApi(request, createTestPhone());
    await seedPageWithToken(page, login.token, login.user);

    await gotoApp(page, '/market');
    await expect(page.locator('.market-page')).toBeVisible({ timeout: 20_000 });
    await gotoApp(page, '/market');
    await expect(page.locator('.market-page')).toBeVisible({ timeout: 20_000 });

    const paths = [
      '/market/salary-insight?city=广州&role=Java后端&years=3-5年',
      '/market/skill-trends?city=广州&role=Java后端',
      '/market/resume-gap?jdId=default',
    ];

    await Promise.all(paths.map((path) => timedMarketGet(request, login.token, path)));
    const secondRound = await Promise.all(paths.map((path) => timedMarketGet(request, login.token, path)));
    for (const result of secondRound) {
      expect(result.durationMs).toBeLessThan(200);
      expect(result.body?.data?._meta).toBeTruthy();
    }

    const miss = await timedMarketGet(
      request,
      login.token,
      `/market/salary-insight?city=缓存降级城${Date.now()}&role=缓存降级岗&years=3-5年`
    );
    expect(miss.body?.data?._meta?.degraded).toBe(true);
  });
});
