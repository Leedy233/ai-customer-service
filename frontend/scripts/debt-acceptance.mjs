// 遗留债务清偿验收：七项真值（排队/溯源/工具入参耗时/摘要删除/订单列表/客户资料/物流ETA）
// 入口为 nginx 80（/api 反代），真浏览器 + 真接口断言；跑完需重跑 eval/seed-ui-demo.sql 重置基线
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'

const BASE = 'http://localhost'
const SHOTS = '../docs/screenshots'
mkdirSync(SHOTS, { recursive: true })

const results = []
const consoleErrors = []
function check(name, ok, extra = '') {
  results.push({ name, ok, extra })
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${extra ? ' ' + extra : ''}`)
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

const browser = await chromium.launch()

async function newPage(width = 1440, height = 900) {
  const ctx = await browser.newContext({ viewport: { width, height }, deviceScaleFactor: 1 })
  const page = await ctx.newPage()
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
  if (username === 'admin') await page.goto(`${BASE}/admin`, { waitUntil: 'networkidle' })
}

async function api(page, method, path, body) {
  return page.evaluate(
    async ([m, p, b]) => {
      const res = await fetch(p, {
        method: m,
        credentials: 'include',
        headers: b ? { 'Content-Type': 'application/json' } : undefined,
        body: b ? JSON.stringify(b) : undefined,
      })
      let json = null
      try {
        json = await res.json()
      } catch {
        /* 非 JSON 响应 */
      }
      return { status: res.status, json }
    },
    [method, path, body ?? null],
  )
}

// ---------- D1：登录 + 客户资料脱敏真值 + 订单列表真值 ----------
const { ctx: userCtx, page } = await newPage()
await login(page, 'zhangsan', '123456')
check('d01_login', page.url().includes('/chat'))

const me = await api(page, 'GET', '/api/me')
const phone = me.json?.data?.phone ?? ''
const email = me.json?.data?.email ?? ''
check(
  'd02_me_masked',
  me.json?.code === 200 && /^1\d{2}\*{4}\d{4}$/.test(phone) && /^[^@]\*{3}@/.test(email),
  `phone=${phone} email=${email}`,
)
check('d03_me_tags', Array.isArray(me.json?.data?.tags) && me.json.data.tags.includes('VIP'), JSON.stringify(me.json?.data?.tags))

const orders = await api(page, 'GET', '/api/orders')
const list = orders.json?.data ?? []
check(
  'd04_orders_own_only',
  orders.json?.code === 200 && list.length >= 2 && list.every((o) => o.userId === 1) && !list.some((o) => o.orderNo === '10002'),
  `n=${list.length}`,
)
const shipped = list.find((o) => o.orderNo === '10001')
check('d05_order_eta', !!shipped?.shippedAt && !!shipped?.eta, `eta=${shipped?.eta}`)

// 右栏真值：脱敏手机 / VIP / 本人订单（订单栏不含他人 10002；种子历史会话提及 10002 属演示数据）
check('d06_panel_masked_phone', await page.getByText(phone, { exact: false }).first().isVisible())
await page.getByRole('button', { name: '新建会话' }).click()
await page.waitForTimeout(1200)
const orderSection = page.locator('section', { hasText: '最近订单' })
check(
  'd07_panel_orders_own',
  (await orderSection.getByText('10002').count()) === 0 &&
    (await orderSection.getByText('10001').count()) >= 1 &&
    (await orderSection.getByText('10003').count()) >= 1,
)
check('d08_panel_eta_copy', await page.getByText(`预计 ${shipped.eta.slice(0, 10)} 送达`).isVisible())
await page.screenshot({ path: `${SHOTS}/debt-context.png` })

// ---------- D2：排队真服务（join 幂等 / status / cancel） ----------
const j1 = await api(page, 'POST', '/api/queue/join', { conversationId: 'debt-probe-q' })
const j2 = await api(page, 'POST', '/api/queue/join', { conversationId: 'debt-probe-q' })
const st = await api(page, 'GET', '/api/queue/status?conversationId=debt-probe-q')
check(
  'd09_queue_join_idempotent',
  j1.json?.data?.active === true && j1.json.data.position >= 1 && j2.json?.data?.position === j1.json.data.position,
  `pos=${j1.json?.data?.position}`,
)
check(
  'd10_queue_eta_math',
  st.json?.data?.estimatedWaitSeconds === st.json.data.position * 180,
  `eta=${st.json?.data?.estimatedWaitSeconds}`,
)
const cc = await api(page, 'POST', '/api/queue/cancel', { conversationId: 'debt-probe-q' })
check('d11_queue_cancel', cc.json?.data?.active === false)

// ---------- D3：UI 转人工 → 真排队条（位置+预计等待）→ 取消联动工单 ----------
await page.locator('button', { hasText: '转人工' }).last().click()
await page.waitForTimeout(1200)
check('d12_queue_bar_position', await page.getByText('当前排队 1 人', { exact: false }).isVisible())
check('d13_queue_bar_eta', await page.getByText('预计等待 3 分钟').isVisible())
await page.screenshot({ path: `${SHOTS}/debt-queue.png` })
await page.getByRole('button', { name: '取消排队' }).click()
await page.waitForTimeout(800)
check('d14_queue_bar_gone', (await page.getByText('正在为你转接人工客服').count()) === 0)
const mine = await api(page, 'GET', '/api/tickets/mine')
const lastTransfer = (mine.json?.data ?? []).filter((t) => t.kind === 'TRANSFER').sort((a, b) => b.id - a.id)[0]
check('d15_transfer_cancelled', lastTransfer?.status === 'CANCELLED', `status=${lastTransfer?.status}`)

// ---------- D4：SSE v2 真溯源 + 工具真入参/真耗时（流中 live + 落库真值） ----------
let sawV2 = false
page.on('request', (r) => {
  if (r.url().includes('/chat/stream/v2')) sawV2 = true
})
await page.getByRole('button', { name: '新建会话' }).click()
await page.waitForTimeout(1000)
await page.getByPlaceholder(/输入消息/).fill('订单 10001 的物流到哪了？')
await page.getByPlaceholder(/输入消息/).press('Enter')
await page.getByText('已查询物流').waitFor({ timeout: 30000 })
check('d16_live_tool_bar', true)
await page.getByText('已查询物流').click()
await page.waitForTimeout(400)
const argsText = await page.locator('.el-collapse, [class*=tool]').allTextContents().catch(() => [])
const toolRegion = await page.getByText('返回摘要').locator('xpath=ancestor::div[3]').innerText().catch(() => '')
check('d17_live_tool_args', /orderNo/.test(toolRegion) && /10001/.test(toolRegion), toolRegion.slice(0, 80).replace(/\n/g, '|'))
await page.getByText('依据：《配送与运费规则》').waitFor({ timeout: 60000 })
check('d18_sse_v2_used', sawV2)
await page.getByText('依据：《配送与运费规则》').click()
await page.waitForTimeout(800)
check('d19_drawer_live_tag', await page.getByText('知识库真值').isVisible())
check('d20_drawer_excerpt', await page.getByText('引用来自本次回答实际检索命中的知识库 chunk').isVisible())
await page.screenshot({ path: `${SHOTS}/debt-source.png` })
await page.keyboard.press('Escape')
await page.waitForTimeout(600)

// 落库真值：toolArgs / toolDurationMs / sources
const convs = await api(page, 'GET', '/api/conversations')
const liveConv = (convs.json?.data ?? []).find((c) => /物流/.test(c.title))
const msgs = await api(page, 'GET', `/api/conversations/${liveConv.id}/messages`)
const rows = msgs.json?.data ?? []
const toolRow = rows.find((m) => m.role === 'tool')
const assistRow = rows.filter((m) => m.role === 'assistant').pop()
let parsedArgs = null
let parsedSources = null
try {
  parsedArgs = JSON.parse(toolRow?.toolArgs ?? 'null')
} catch { /* ignore */ }
try {
  parsedSources = JSON.parse(assistRow?.sources ?? 'null')
} catch { /* ignore */ }
check('d21_tool_args_db', parsedArgs?.orderNo === '10001', JSON.stringify(parsedArgs))
check('d22_tool_duration_db', typeof toolRow?.toolDurationMs === 'number' && toolRow.toolDurationMs >= 0, `ms=${toolRow?.toolDurationMs}`)
check('d23_sources_db', Array.isArray(parsedSources) && parsedSources[0]?.doc === '配送与运费规则' && !!parsedSources[0]?.excerpt, parsedSources?.[0]?.section)

// ---------- D5：会话摘要（END 工单触发）+ 真删除（墓碑+列表屏蔽+历史403） ----------
const endT = await api(page, 'POST', '/api/tickets', { conversationId: liveConv.id, kind: 'END' })
check('d24_end_ticket', endT.json?.code === 200)
const convs2 = await api(page, 'GET', '/api/conversations')
const withSummary = (convs2.json?.data ?? []).find((c) => c.id === liveConv.id)
check('d25_summary_server', !!withSummary?.summary && withSummary.summary.length <= 200, withSummary?.summary)
const del = await api(page, 'DELETE', `/api/conversations/${liveConv.id}`)
check('d26_delete_ok', del.json?.code === 200 && del.json?.data?.deleted === true)
const convs3 = await api(page, 'GET', '/api/conversations')
check('d27_delete_list_hidden', !(convs3.json?.data ?? []).some((c) => c.id === liveConv.id))
const msgsAfter = await api(page, 'GET', `/api/conversations/${liveConv.id}/messages`)
check('d28_delete_history_403', msgsAfter.json?.code === 403, `code=${msgsAfter.json?.code}`)
// 本地唯一标题（种子存在同文案会话，须用带消歧后缀的本地标题断言 UI）
const regBefore = await page.evaluate(() => JSON.parse(localStorage.getItem('cs:conversations-v3') || '[]'))
const localTitle = regBefore.find((c) => c.id === liveConv.id)?.title ?? liveConv.id
await page.reload({ waitUntil: 'networkidle' })
await page.waitForTimeout(1500)
const regAfter = await page.evaluate(() => JSON.parse(localStorage.getItem('cs:conversations-v3') || '[]'))
const sumAfter = await page.evaluate(() => Object.keys(JSON.parse(localStorage.getItem('cs:conv-summaries-v4') || '{}')))
const sideCount = await page.locator('nav[aria-label="会话列表"] li', { hasText: localTitle }).count()
check(
  'd29_delete_ui_gone',
  localTitle !== liveConv.id && !regAfter.some((c) => c.id === liveConv.id) && sideCount === 0,
  `title=${localTitle}`,
)

// ---------- D6：管理端关闭 TRANSFER → 排队叫号完成（SERVED） ----------
await api(page, 'POST', '/api/queue/join', { conversationId: 'debt-probe-served' })
const t2 = await api(page, 'POST', '/api/tickets', { conversationId: 'debt-probe-served', kind: 'TRANSFER' })
const { ctx: adminCtx, page: admin } = await newPage()
await login(admin, 'admin', 'Admin@123')
const adminTickets = await api(admin, 'GET', '/api/admin/tickets?page=1&size=50')
const target = (adminTickets.json?.data?.list ?? []).find((t) => t.conversationId === 'debt-probe-served')
check('d30_admin_sees_transfer', !!target, `id=${target?.id}`)
const closed = await api(admin, 'POST', `/api/admin/tickets/${target.id}/close?note=${encodeURIComponent('债务验收叫号')}`)
check('d31_admin_close', closed.json?.code === 200)
const servedStatus = await api(page, 'GET', '/api/queue/status?conversationId=debt-probe-served')
check('d32_queue_served', servedStatus.json?.data?.active === false)

// ---------- 汇总 ----------
check('d33_console_clean', consoleErrors.length === 0, consoleErrors.slice(0, 3).join(' | '))
const failed = results.filter((r) => !r.ok)
console.log(`\n==== debt-acceptance: ${results.length - failed.length}/${results.length} PASS ====`)
if (failed.length) console.log(failed.map((f) => `FAIL ${f.name} ${f.extra}`).join('\n'))
await userCtx.close()
await adminCtx.close()
await browser.close()
process.exit(failed.length ? 1 : 0)