const { test, expect } = require('@playwright/test')

test('login redirects to /chat (P0-5)', async ({ page }) => {
  await page.goto('http://127.0.0.1:5174/')
  await page.waitForURL(/#\/login/)
  await page.locator('input[type="tel"]').first().fill('13977833599')
  const sentResp = page.waitForResponse((r) => r.url().includes('/auth/sms/send'))
  await page.getByRole('button', { name: /发送验证码/ }).click()
  await sentResp
  await page.waitForTimeout(1500)
  await page.locator('.sms-row input').first().fill('123456')
  await page.getByRole('button', { name: /登录\/注册/ }).click()
  await page.waitForLoadState('networkidle')
  await page.waitForTimeout(2000)
  expect(page.url()).toContain('/#/chat')
})
