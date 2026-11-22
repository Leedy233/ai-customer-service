// Nginx 统一入口真浏览器冒烟：http://localhost（80）全链路
// 断言：SPA history 刷新、/api 转发、SSE 流式、Cookie 会话、静态缓存头、无直连后端端口
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'

const BASE = process.env.NGINX_BASE || 'http://localhost'
const SHOTS = '../docs/screenshots'
mkdirSync(SHOTS, { recursive: true })

const results = []
const consoleErrors = []
const requests = []
const assetHeaders = []
function check(name, ok, extra = '') {
  results.push({ name, ok, extra })
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${extra ? ' ' + extra : ''}`)
}

const browser = await chromium.launch()
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const page = await ctx.newPage()
page.on('console', (m) => {
  if (m.type() !== 'error') return
  const t = m.text()
  if (/Failed to load resource: the server responded with a status of 40[134]/.test(t)) return
  consoleErrors.push(t.slice(0, 160))
})
page.on('pageerror', (e) => consoleErrors.push(String(e).slice(0, 160)))
page.on('request', (r) => requests.push(r.url()))
page.on('response', async (r) => {
  if (/\/assets\/.*\.js$/.test(r.url())) {
    assetHeaders.push(r.headers()['cache-control'] || '')
  }
})

// n01/n02：首页与 history 路由刷新（未登录直连 /chat 应返回 SPA 而非 404）
const home = await page.goto(`${BASE}/`, { waitUntil: 'networkidle' })
check('n01_home_spa', home.status() === 200 && (await page.title()).includes('AI 智能客服'))
const deep = await page.goto(`${BASE}/chat`, { waitUntil: 'networkidle' })
check('n02_history_refresh', deep.status() === 200 && (deep.headers()['content-type'] || '').includes('text/html'))

// n03：登录（Cookie 经 nginx 同域下发）
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.getByPlaceholder('用户名').fill('zhangsan')
await page.getByPlaceholder('密码').fill('123456')
await page.getByRole('button', { name: '登录', exact: true }).click()
await page.waitForURL('**/chat**', { timeout: 15000 })
await page.waitForTimeout(1500)
check('n03_login_via_nginx', page.url().includes('/chat'))

// n04：SSE 流式经 /api/chat/stream（proxy_buffering off 验证：首帧增量到达）
await page.getByLabel('消息输入框').fill('订单 10001 的物流到哪了？')
await page.getByRole('button', { name: '发送' }).click()
await page.waitForTimeout(1200)
const midLen = await page.evaluate(() => document.body.innerText.length)
await page.waitForTimeout(6000)
const endLen = await page.evaluate(() => document.body.innerText.length)
check('n04_sse_stream_incremental', endLen > midLen, `mid=${midLen} end=${endLen}`)
check('n05_sse_url_api', requests.some((u) => u.includes('/api/chat/stream')))

// n06：订单卡真值
await page.getByLabel('消息输入框').fill('订单 10001 多少钱？')
await page.getByRole('button', { name: '发送' }).click()
await page.waitForTimeout(9000)
check('n06_order_card_amount', await page.getByText('199.00').first().isVisible())
await page.screenshot({ path: `${SHOTS}/nginx-chat.png` })

// n07：刷新后会话保持（Cookie 经 nginx 往返）
await page.reload({ waitUntil: 'networkidle' })
await page.waitForTimeout(1500)
check('n07_session_after_reload', await page.getByText('订单 10001 的物流到哪了？', { exact: false }).first().isVisible())

// n08：静态资源 immutable 缓存头
check('n08_assets_immutable', assetHeaders.some((h) => h.includes('immutable')), assetHeaders[0] || '')

// n09：页面零直连后端端口
const direct = requests.filter((u) => /:(8080|8082|5173)\//.test(u))
check('n09_no_direct_backend', direct.length === 0, direct.slice(0, 3).join('|'))

// n10：/actuator 经 nginx 403
const act = await page.evaluate(async () => (await fetch('/actuator/health', { credentials: 'include' })).status)
check('n10_actuator_blocked', act === 403, `status=${act}`)

// n11：管理端经 nginx（先经 /api/logout 销毁会话，再登录 admin）
await page.evaluate(async () => {
  await fetch('/api/logout', { method: 'POST', credentials: 'include', headers: { 'X-Requested-With': 'XMLHttpRequest' } })
})
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.getByPlaceholder('用户名').fill('admin')
await page.getByPlaceholder('密码').fill('Admin@123')
await page.getByRole('button', { name: '登录', exact: true }).click()
await page.waitForTimeout(2000)
await page.goto(`${BASE}/admin`, { waitUntil: 'networkidle' })
await page.waitForTimeout(2500)
check('n11_admin_via_nginx', (await page.locator('canvas').count()) >= 3)
await page.screenshot({ path: `${SHOTS}/nginx-admin.png` })

await ctx.close()
await browser.close()
const failed = results.filter((r) => !r.ok)
console.log(`SUMMARY ${results.length - failed.length}/${results.length} passed`)
console.log(`CONSOLE_ERRORS ${consoleErrors.length}`)
consoleErrors.slice(0, 8).forEach((e) => console.log('  ', e))
process.exit(failed.length || consoleErrors.length ? 1 : 0)