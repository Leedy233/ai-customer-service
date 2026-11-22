// 管理后台切片 2-10 自验收：九页真实数据 + 写操作闭环 + 越权 + 移动视口（Playwright 真浏览器）
// 限流每 IP 60/min：阶段间隔 20s 编排；运行期间禁止改前端代码（HMR 会打断）
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'

const BASE = 'http://localhost:5173'
const SHOTS = '../docs/screenshots'
mkdirSync(SHOTS, { recursive: true })

const results = []
const consoleErrors = []
const badResponses = []
process.on('exit', () => {
  if (badResponses.length) {
    console.log('BAD_RESPONSES', JSON.stringify(badResponses.slice(-12)))
  }
})
function check(name, ok, extra = '') {
  results.push({ name, ok, extra })
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${extra ? ' ' + extra : ''}`)
  if (!ok && badResponses.length) console.log('  recent_bad:', JSON.stringify(badResponses.slice(-6)))
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

const browser = await chromium.launch()

async function newPage(width, height, mobile = false) {
  const ctx = await browser.newContext({
    viewport: { width, height },
    isMobile: mobile,
    hasTouch: mobile,
    deviceScaleFactor: 1,
  })
  const page = await ctx.newPage()
  page.on('response', (r) => {
    if (r.url().includes('/ai/') && r.status() >= 400) {
      badResponses.push(`${r.status()} ${r.request().method()} ${r.url().replace(BASE, '')}`)
    }
  })
  page.on('console', (m) => {
    if (m.type() !== 'error') return
    const t = m.text()
    if (/Failed to load resource: the server responded with a status of 40[134]/.test(t)) return
    consoleErrors.push(t.slice(0, 160))
  })
  page.on('pageerror', (e) => consoleErrors.push(String(e).slice(0, 160)))
  return { ctx, page }
}

async function login(page, username, password) {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
  await page.getByPlaceholder('用户名').fill(username)
  await page.getByPlaceholder('密码').fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForTimeout(2000)
}

const overflow = (page) =>
  page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)

async function pickOption(page, text) {
  await page.locator('.el-select').first().click()
  await page.locator('.el-select-dropdown__item', { hasText: text }).first().click()
  await page.waitForTimeout(600)
}

const api = (page, method, path, body) =>
  page.evaluate(
    async ({ method, path, body }) => {
      const r = await fetch(path, {
        method,
        credentials: 'include',
        headers: body ? { 'Content-Type': 'application/json' } : undefined,
        body: body ? JSON.stringify(body) : undefined,
      })
      return { status: r.status, json: await r.json().catch(() => null) }
    },
    { method, path, body },
  )

// ---------- A1：admin 登录 + 仪表盘 + 订单 + 退款驳回 ----------
const { ctx: adminCtx, page: admin } = await newPage(1440, 900)
await login(admin, 'admin', 'Admin@123')
await admin.goto(`${BASE}/admin`, { waitUntil: 'networkidle' })
await admin.waitForTimeout(2500)
check('a01_dashboard_charts', (await admin.locator('canvas').count()) >= 3)

await admin.goto(`${BASE}/admin/orders`, { waitUntil: 'networkidle' })
await admin.waitForTimeout(1200)
check('a02_orders_rows', (await admin.locator('.el-table__row').count()) === 3)
check('a03_orders_username', await admin.getByText('zhangsan').first().isVisible())
await pickOption(admin, '待发货')
await admin
  .waitForFunction(() => document.querySelectorAll('.el-table__row').length === 1, null, { timeout: 8000 })
  .catch(() => {})
check('a04_orders_filter', (await admin.locator('.el-table__row').count()) === 1)
await admin.getByRole('button', { name: '发货', exact: true }).click()
await admin.waitForTimeout(400)
await admin.getByRole('button', { name: '确认发货' }).click()
await admin.waitForTimeout(1200)
check('a05_ship_done', await admin.getByText('暂无订单').isVisible())
await admin.screenshot({ path: `${SHOTS}/admin-orders.png` })
await admin.goto(`${BASE}/admin/orders`, { waitUntil: 'networkidle' })
await admin.waitForTimeout(1000)
await admin.getByRole('button', { name: '详情', exact: true }).first().click()
await admin.waitForTimeout(800)
check('a06_order_drawer', await admin.locator('.el-drawer').getByText('退款记录').isVisible())
await admin.keyboard.press('Escape')
await admin.waitForTimeout(500)

await admin.goto(`${BASE}/admin/refunds`, { waitUntil: 'networkidle' })
await admin.waitForTimeout(1200)
check('a07_refund_pending_chip', await admin.getByText('待确认 1').isVisible())
await admin.getByRole('button', { name: '驳回', exact: true }).click()
await admin.waitForTimeout(500)
await admin.getByLabel('驳回理由').fill('验收：驳回待确认申请')
await admin.getByRole('button', { name: '确认驳回' }).click()
await admin.waitForTimeout(1200)
check('a08_refund_rejected', await admin.getByText('已驳回 1').isVisible())
await admin.screenshot({ path: `${SHOTS}/admin-refunds.png` })
await sleep(20000)

// ---------- A2：工单列表 + 知识库 CRUD ----------
await admin.goto(`${BASE}/admin/tickets`, { waitUntil: 'networkidle' })
await admin.waitForTimeout(1200)
check('a09_tickets_seed_rows', (await admin.locator('.el-table__row').count()) >= 2)
await admin.screenshot({ path: `${SHOTS}/admin-tickets.png` })

await admin.goto(`${BASE}/admin/knowledge`, { waitUntil: 'networkidle' })
await admin.waitForTimeout(1200)
check('a10_knowledge_seed_docs', (await admin.locator('.el-table__row').count()) === 3)
await admin.getByRole('button', { name: '新建文档' }).click()
await admin.waitForTimeout(500)
await admin.getByLabel('文档标题').fill('验收临时知识')
await admin.getByLabel('文档内容').fill('验收临时条目：会员日为每月 8 号，全场 95 折。')
await admin.getByRole('button', { name: '保存并同步' }).click()
await admin.waitForTimeout(1500)
check('a11_knowledge_created', (await admin.locator('.el-table__row').count()) === 4)
const newRow = admin.locator('.el-table__row', { hasText: '验收临时知识' })
await newRow.locator('.el-switch').click()
await admin.waitForTimeout(1200)
check('a12_knowledge_disabled_chunks', (await newRow.locator('td').nth(3).innerText()).trim() === '0')
await newRow.locator('.el-switch').click()
await admin.waitForTimeout(1200)
await newRow.getByRole('button', { name: 'chunk', exact: true }).click()
await admin.waitForTimeout(800)
check('a13_knowledge_chunks', await admin.locator('.el-drawer').getByText('#1').isVisible())
await admin.screenshot({ path: `${SHOTS}/admin-knowledge.png` })
await admin.keyboard.press('Escape')
await admin.waitForTimeout(500)
await newRow.getByRole('button', { name: '删除', exact: true }).click()
await admin.waitForTimeout(400)
await admin.getByRole('button', { name: '删除', exact: true }).last().click()
await admin.waitForTimeout(1200)
check('a14_knowledge_deleted', (await admin.locator('.el-table__row').count()) === 3)
await sleep(20000)

// ---------- B：zhangsan 用户侧 API 配合（退款两段式 + 转人工工单） ----------
const { ctx: userCtx, page: user } = await newPage(1280, 800)
await login(user, 'zhangsan', '123456')
const apply = await api(user, 'POST', '/ai/refund/apply?orderNo=10003&conversationId=seed-refund-10003')
check('a15_user_refund_apply', apply.status === 200 && apply.json?.code === 200, `status=${apply.status}`)
const refundId = apply.json?.data?.refundId
const confirm = await api(user, 'POST', `/ai/refund/confirm?refundId=${refundId}`)
check('a16_user_refund_confirm', confirm.json?.code === 200)
const t1 = await api(user, 'POST', '/ai/tickets', { conversationId: 'seed-invoice', kind: 'TRANSFER' })
check('a17_user_transfer_ticket', t1.json?.code === 200 && /GD\d{11}/.test(t1.json?.data?.ticketNo ?? ''))
const t2 = await api(user, 'POST', '/ai/tickets', { conversationId: 'seed-invoice', kind: 'TRANSFER' })
const cancel = await api(user, 'POST', `/ai/tickets/${t2.json?.data?.id}/cancel`)
check('a18_user_ticket_cancel', cancel.json?.data?.status === 'CANCELLED')
const forbidden = await api(user, 'GET', '/ai/admin/orders')
check('a19_user_admin_403', forbidden.status === 403)
await sleep(20000)

// ---------- C：admin 闭环（退款完结/工单接单关闭/审计/日志/Token/耗时/监控） ----------
await admin.goto(`${BASE}/admin/refunds`, { waitUntil: 'networkidle' })
await admin.waitForTimeout(1200)
check('a20_refund_executed_chip', await admin.getByText('已执行 1').isVisible())
await admin.getByRole('button', { name: '完结', exact: true }).click()
await admin.waitForTimeout(400)
await admin.getByRole('button', { name: '确认完结' }).click()
await admin.waitForTimeout(1500)
const refundChips = await admin.locator('.flex.flex-wrap button').allInnerTexts()
check('a21_refund_completed', await admin.getByText('已完结 1').isVisible(), refundChips.join('|'))

await admin.goto(`${BASE}/admin/tickets`, { waitUntil: 'networkidle' })
await admin.waitForTimeout(1500)
const ticketChips = await admin.locator('.flex.flex-wrap button').allInnerTexts()
check('a22_ticket_open_chip', await admin.getByText('待处理 1').isVisible(), ticketChips.join('|'))
await admin.getByRole('button', { name: '接单', exact: true }).first().click()
await admin.waitForTimeout(1200)
check('a23_ticket_processing', await admin.getByText('处理中 1').isVisible())
await admin
  .locator('.el-table__row', { hasText: '处理中' })
  .first()
  .getByRole('button', { name: '详情', exact: true })
  .click()
await admin.waitForTimeout(800)
await admin.getByLabel('关闭备注').fill('验收：人工已答复')
await admin.getByRole('button', { name: '关闭工单' }).click()
await admin.waitForTimeout(1200)
const cancelledChip = await admin.locator('button', { hasText: '已取消' }).first().innerText()
check('a24_ticket_closed_and_cancelled', parseInt(cancelledChip.replace(/\D/g, ''), 10) >= 1, cancelledChip.trim())

await admin.goto(`${BASE}/admin/audit`, { waitUntil: 'networkidle' })
await admin.waitForTimeout(1200)
check('a25_audit_rows', (await admin.locator('.el-table__row').count()) >= 8)
await admin
  .locator('.el-table__row', { hasText: 'seed-logistics-10001' })
  .first()
  .getByRole('button', { name: '全文', exact: true })
  .click()
await admin.waitForTimeout(1000)
check('a26_audit_tool_row', await admin.locator('.el-drawer').getByText('queryLogistics').isVisible())
await admin.screenshot({ path: `${SHOTS}/admin-audit.png` })
await admin.keyboard.press('Escape')
await admin.waitForTimeout(500)

await admin.goto(`${BASE}/admin/logs`, { waitUntil: 'networkidle' })
await admin.waitForTimeout(1200)
const firstId = await admin.locator('.el-table__row').first().innerText()
const totalTxt = await admin.locator('.el-pagination__total').innerText()
const logTotal = parseInt(totalTxt.replace(/\D/g, ''), 10)
await admin.locator('.el-pagination .el-select').first().click()
await admin.locator('.el-select-dropdown__item', { hasText: '5条/页' }).first().click()
await admin.waitForTimeout(1000)
await admin.getByRole('button', { name: '下一页' }).click()
await admin.waitForTimeout(1000)
const secondId = await admin.locator('.el-table__row').first().innerText()
const page2Rows = await admin.locator('.el-table__row').count()
check(
  'a27_logs_pagination',
  firstId !== secondId && page2Rows === Math.min(5, logTotal - 5),
  `total=${logTotal} page2=${page2Rows}`,
)
await admin.locator('.el-switch').first().click()
await admin.waitForTimeout(1000)
check('a28_logs_only_error_empty', await admin.getByText('暂无请求日志').isVisible())

await admin.goto(`${BASE}/admin/tokens`, { waitUntil: 'networkidle' })
await admin.waitForTimeout(2000)
check('a29_tokens_chart_and_user', (await admin.locator('canvas').count()) >= 1 && await admin.getByText('zhangsan').first().isVisible())

await admin.goto(`${BASE}/admin/latency`, { waitUntil: 'networkidle' })
await admin.waitForTimeout(2000)
check('a30_latency_chart_slowest', (await admin.locator('canvas').count()) >= 1 && (await admin.locator('.el-table__row').count()) >= 1)
await admin.getByRole('button', { name: '会话', exact: true }).first().click()
await admin.waitForTimeout(1500)
check('a31_latency_to_audit', admin.url().includes('/admin/audit') && await admin.locator('.el-drawer').getByText('会话全文').isVisible())
await admin.keyboard.press('Escape')
await admin.waitForTimeout(400)

await admin.goto(`${BASE}/admin/monitor`, { waitUntil: 'networkidle' })
await admin.waitForTimeout(1500)
check('a32_monitor_up', await admin.getByText('UP').first().isVisible())
check('a33_monitor_db_tables', await admin.getByText('ai_ticket').isVisible())
await admin.screenshot({ path: `${SHOTS}/admin-monitor.png` })
await sleep(20000)

// ---------- D：390 移动视口管理端 ----------
const { ctx: mobCtx, page: mob } = await newPage(390, 844, true)
await login(mob, 'admin', 'Admin@123')
await mob.goto(`${BASE}/admin/orders`, { waitUntil: 'networkidle' })
await mob.waitForTimeout(1500)
check('a34_mobile_admin_no_overflow', !(await overflow(mob)))
await mobCtx.close()

await userCtx.close()
await adminCtx.close()
await browser.close()
const failed = results.filter((r) => !r.ok)
console.log(`SUMMARY ${results.length - failed.length}/${results.length} passed`)
console.log(`CONSOLE_ERRORS ${consoleErrors.length}`)
consoleErrors.slice(0, 8).forEach((e) => console.log('  ', e))
process.exit(failed.length || consoleErrors.length ? 1 : 0)