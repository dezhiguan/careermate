const { test, expect } = require('@playwright/test');
const {
  apiBaseURL,
  createTestPhone,
  mobileLoginViaApi,
  MOCK_SMS_CODE,
} = require('./e2e-env');

test.describe('local backend startup contract', () => {
  test('JDK 21 dev server keeps authenticated APIs out of 500', async ({ request }) => {
    const login = await mobileLoginViaApi(request, createTestPhone(), MOCK_SMS_CODE);
    const headers = { Authorization: `Bearer ${login.token}` };

    const endpoints = [
      '/opportunity/list',
      '/agent/sessions',
      '/market/salary-insight?role=Java%E5%90%8E%E7%AB%AF&city=%E5%B9%BF%E5%B7%9E&years=3-5%E5%B9%B4',
      '/market/skill-trends?role=Java%E5%90%8E%E7%AB%AF',
      '/resumes',
      '/artifacts/recent',
    ];

    for (const endpoint of endpoints) {
      const response = await request.get(`${apiBaseURL}${endpoint}`, { headers, timeout: 20_000 });
      const body = await response.text().catch(() => '');
      expect(response.status(), `${endpoint} should not return 500: ${body}`).not.toBe(500);
      expect(response.ok(), `${endpoint} failed with HTTP ${response.status()}: ${body}`).toBeTruthy();
    }
  });
});
