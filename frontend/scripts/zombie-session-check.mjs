// 僵尸会话治理验证：连点新建复用空壳 / 切走销毁 / 历史空壳清理
import { chromium } from 'playwright'

const BASE = process.env.NGINX_BASE || 'http://localhost'
const results = []
const consoleErrors = []
function check(name, ok, extra = '') {
  results.push({ name, ok })
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${extra ? ' ' + extra : ''}`)
}
const draftTitles = (page) =>
  page.locator('nav[aria-label="会话列表"] li .truncate.text-sm').allInnerTexts()
    .then((ts) => ts.filter((t) => t.startsWith('新会话')))

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
page.on('console', (m) => {
  if (m.type() !== 'error') return
  const t = m.text()
  if (/Failed to load resource: the server responded with a status of 40[134]/.test(t)) return
  consoleErrors.push(t.slice(0, 160))
})
page.on('pageerror', (e) => consoleErrors.push(String(e).slice(0, 160)))

await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.getByPlaceholder('用户名').fill('zhangsan')
await page.getByPlaceholder('密码').fill('123456')
await page.getByRole('button', { name: '登录', exact: true }).click()
await page.waitForURL('**/chat**', { timeout: 15000 })
await page.waitForTimeout(2500)

// z01：连点三次新建 → 仅一个空壳（复用）
for (let i = 0; i < 3; i++) {
  await page.getByRole('button', { name: '新建会话' }).first().click()
  await page.waitForTimeout(600)
}
check('z01_reuse_on_repeat_new', (await draftTitles(page)).length === 1, JSON.stringify(await draftTitles(page)))

// z02：切走到有消息的会话 → 空壳被销毁
const seeded = page.locator('nav[aria-label="会话列表"] li .truncate.text-sm').filter({ hasText: /^(?!新会话)/ }).first()
await seeded.click()
await page.waitForTimeout(1200)
check('z02_discard_on_switch', (await draftTitles(page)).length === 0)

// z03：再新建一个空壳后刷新 → 当前空壳保留（不误删正在输入的会话）
await page.getByRole('button', { name: '新建会话' }).first().click()
await page.waitForTimeout(600)
await page.reload({ waitUntil: 'networkidle' })
await page.waitForTimeout(2500)
check('z03_keep_current_draft_on_reload', (await draftTitles(page)).length === 1)

// z04：注入两条历史僵尸空壳 + 当前指向有消息会话 → 刷新后清理
await page.evaluate(() => {
  const list = JSON.parse(localStorage.getItem('conversations-v3') || '[]')
  const now = Date.now()
  list.push({ id: 'web-zombie-a', title: '新会话 11-22 14:03', createdAt: now, updatedAt: now })
  list.push({ id: 'web-zombie-b', title: '新会话 11-22 14:05', createdAt: now, updatedAt: now })
  localStorage.setItem('conversations-v3', JSON.stringify(list))
})
await seeded.click()
await page.waitForTimeout(1200)
await page.reload({ waitUntil: 'networkidle' })
await page.waitForTimeout(2500)
check('z04_purge_legacy_zombies', (await draftTitles(page)).length === 0, JSON.stringify(await draftTitles(page)))

// z05：治理后新建→发送链路不受影响（空壳发首条消息后改名留存）
await page.getByRole('button', { name: '新建会话' }).first().click()
await page.waitForTimeout(600)
await page.getByLabel('消息输入框').fill('你好')
await page.getByRole('button', { name: '发送' }).click()
await page.waitForTimeout(6000)
check('z05_send_renames_and_keeps', (await draftTitles(page)).length === 0)

await browser.close()
const failed = results.filter((r) => !r.ok)
console.log(`SUMMARY ${results.length - failed.length}/${results.length} passed`)
console.log(`CONSOLE_ERRORS ${consoleErrors.length}`)
consoleErrors.slice(0, 8).forEach((e) => console.log('  ', e))
process.exit(failed.length || consoleErrors.length ? 1 : 0)