// 移动端真视口验收（Playwright chromium，390x844 isMobile）
// 运行：node scripts/mobile-acceptance.mjs （需 5173 与后端已启动）
import { chromium } from 'playwright'

const BASE = 'http://localhost:5173'
const shots = new URL('../docs/screenshots/', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')
const results = []
const check = (name, ok, extra = '') => {
  results.push({ name, ok, extra })
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${extra ? ' ' + extra : ''}`)
}

const browser = await chromium.launch()
const ctx = await browser.newContext({
  viewport: { width: 390, height: 844 },
  isMobile: true,
  hasTouch: true,
  deviceScaleFactor: 2,
})
const page = await ctx.newPage()
const adminDiag = []
page.on('response', (r) => {
  if (r.url().includes('/admin/') || r.url().includes('/ai/me') || r.url().includes('/ai/login')) {
    adminDiag.push(`${r.status()} ${r.url().replace(BASE, '')}`)
  }
})
const overflow = () => page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)

// 登录
await page.goto(BASE + '/login')
await page.getByPlaceholder('用户名').fill('zhangsan')
await page.getByPlaceholder('密码').fill('123456')
await page.getByRole('button', { name: '登录', exact: true }).click()
await page.waitForURL('**/chat**', { timeout: 15000 })
check('m01_login_redirect_chat', page.url().includes('/chat'))

// 移动端侧栏默认隐藏 + 汉堡打开
const asideVisible = await page.locator('aside').first().isVisible().catch(() => false)
check('m02_sidebar_hidden_mobile', !asideVisible)
await page.locator('header button').first().click()
await page.waitForTimeout(600)
const drawerText = await page.locator('.el-drawer').getByText('新建会话').first().isVisible().catch(() => false)
check('m03_hamburger_opens_sidebar', drawerText)
await page.keyboard.press('Escape')
await page.waitForTimeout(400)

// 发送消息 + SSE + 无横向溢出
await page.getByPlaceholder('输入消息，Enter 发送，Shift+Enter 换行').fill('会员日有什么优惠？')
await page.keyboard.press('Enter')
await page.waitForTimeout(12000)
const bodyText = await page.evaluate(() => document.body.innerText)
check('m04_sse_answer_rendered', /95\s*折|8\s*号/.test(bodyText))
check('m05_no_horizontal_overflow_chat', (await overflow()) <= 0, `delta=${await overflow()}`)
await page.screenshot({ path: shots + 'mobile-chat.png' })

// 订单卡权威金额（修复验证）
await page.getByPlaceholder('输入消息，Enter 发送，Shift+Enter 换行').fill('请使用订单查询工具查询订单10001')
await page.keyboard.press('Enter')
await page.waitForTimeout(12000)
const cardText = await page.evaluate(() => document.body.innerText)
check('m06_order_card_authoritative_amount', cardText.includes('订单卡片') && cardText.includes('¥199.00'))
check('m07_no_hallucinated_299', !cardText.includes('299.00'))

// 退款弹窗适配
await page.getByRole('button', { name: '退款申请' }).first().click()
await page.waitForTimeout(800)
const dlgBox = await page.locator('.el-dialog').first().boundingBox()
check('m08_refund_dialog_fits', !!dlgBox && dlgBox.width <= 390, `w=${dlgBox?.width}`)
await page.locator('.el-dialog header button').first().click()
await page.waitForTimeout(400)

// USER 进管理端 → 403 且适配
await page.goto(BASE + '/admin')
await page.waitForTimeout(1500)
check('m09_user_admin_403', page.url().includes('/403'))
check('m10_no_overflow_403', (await overflow()) <= 0)

// 限流窗口错峰：与上一阶段请求拉开 25s，避免换账号探测触发 429
await new Promise((r) => setTimeout(r, 25000))

// admin 仪表盘移动适配：走真实 UI 登出换账号
await page.goto(BASE + '/login')
await page.waitForTimeout(800)
if (!page.url().includes('/login')) {
  await page.locator('header .el-dropdown').first().click()
  await page.waitForTimeout(600)
  await page.getByText('退出登录').click()
  await page.waitForTimeout(1200)
}
await page.getByPlaceholder('用户名').fill('admin')
await page.getByPlaceholder('密码').fill('Admin@123')
await page.getByRole('button', { name: '登录', exact: true }).click()
await page.waitForTimeout(2000)
await page.goto(BASE + '/admin')
await page.waitForTimeout(4000)
const canvases = await page.locator('canvas').count()
console.log('M11_URL:', page.url())
console.log('M11_DIAG:', JSON.stringify(adminDiag))
console.log('M11_TEXT:', (await page.evaluate(() => document.body.innerText)).slice(0, 120).replace(/\n/g, '|'))
check('m11_admin_charts_render', canvases >= 3, `canvases=${canvases}`)
check('m12_no_overflow_admin', (await overflow()) <= 0, `delta=${await overflow()}`)
await page.screenshot({ path: shots + 'mobile-admin.png', fullPage: false })

await browser.close()
const failed = results.filter((r) => !r.ok)
console.log(`SUMMARY ${results.length - failed.length}/${results.length} passed`)
process.exit(failed.length ? 1 : 0)
