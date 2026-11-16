import { test, expect } from '@playwright/test'

test('page reaches the real backend and MySQL, navigation and reload work', async ({ page }) => {
  const responsePromise = page.waitForResponse(r => r.url().includes('/api/v1/system/status'))
  await page.goto('/')
  const response = await responsePromise
  expect(response.status()).toBe(200)
  const body = await response.json()
  expect(body.data.databaseVersion).toMatch(/^8\.0\.46/)
  expect(body.data.database).toBe('CONNECTED')
  await expect(page.getByRole('status')).toContainText('后端与数据库已连接')
  await expect(page.getByRole('heading', { name: '流浪动物救助系统', exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.keyboard.press('Tab')
  await page.getByRole('link', { name: '跳至主要内容' }).press('Enter')
  await expect(page.locator('#main')).toBeFocused()
  await page.locator('.module-card').filter({ hasText: '救助线索' }).click()
  await expect(page).toHaveURL(/#\/clue$/)
  await expect(page.getByRole('heading', { name: '救助线索', level: 1, exact: true })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: '救助线索', level: 1, exact: true })).toBeVisible()
  await page.getByRole('link', { name: '返回项目工作台' }).click()
  await expect(page.getByRole('status')).toContainText('后端与数据库已连接')
})

test('dependency failure is visible and retry recovers', async ({ page }) => {
  await page.route('**/api/v1/system/status', route => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 'DEPENDENCY_UNAVAILABLE', message: '服务暂时不可用，请稍后重试', requestId: 'e2e-failure', data: null }) }))
  await page.goto('/')
  await expect(page.getByRole('status')).toContainText('服务暂时不可用')
  await expect(page.getByText('请求号：e2e-failure')).toBeVisible()
  await page.unroute('**/api/v1/system/status')
  await page.getByRole('button', { name: '重新检查' }).click()
  await expect(page.getByRole('status')).toContainText('后端与数据库已连接')
})

test('unknown page offers a route home', async ({ page }) => {
  await page.goto('/#/page-that-does-not-exist')
  await expect(page.getByRole('heading', { name: '没有找到这个页面' })).toBeVisible()
  await page.getByRole('link', { name: '返回项目工作台' }).click()
  await expect(page.getByRole('heading', { name: '流浪动物救助系统', exact: true })).toBeVisible()
})
