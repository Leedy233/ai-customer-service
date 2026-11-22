// UI 改造验收：三档宽度布局 + 关键状态截图 + 交互断言（Playwright 真视口）
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'

const BASE = 'http://localhost:5173'
const SHOTS = '../docs/screenshots'
mkdirSync(SHOTS, { recursive: true })

const results = []
const consoleErrors = []
function check(name, ok, extra = '') {
  results.push({ name, ok, extra })
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${extra ? ' ' + extra : ''}`)
}

const browser = await chromium.launch()

async function newPage(width, height, mobile = false) {
  const ctx = await browser.newContext({
    viewport: { width, height },
    isMobile: mobile,
    hasTouch: mobile,
    deviceScaleFactor: 1,
  })
  const page = await ctx.newPage()
  page.on('console', (m) => {
    if (m.type() !== 'error') return
    const t = m.text()
    // 登录前守卫 /ai/me 探测 401、新会话历史 404、越权 403 属已文档化业务通道，
    // 浏览器资源日志不计为应用报错；应用级异常（pageerror 等）仍计入
    if (/Failed to load resource: the server responded with a status of 40[134]/.test(t)) return
    consoleErrors.push(t.slice(0, 160))
  })
  page.on('pageerror', (e) => consoleErrors.push(String(e).slice(0, 160)))
  return { ctx, page }
}

async function login(page) {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
  await page.getByPlaceholder('用户名').fill('zhangsan')
  await page.getByPlaceholder('密码').fill('123456')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL('**/chat**', { timeout: 15000 })
  await page.waitForTimeout(1500)
}

const overflow = (page) =>
  page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)

// ---------- 1440：三栏 + 分组 + 搜索 + 工具条 + 四状态 ----------
{
  const { ctx, page } = await newPage(1440, 900)
  await login(page)
  check('u01_three_columns', await page.getByText('客户与业务上下文').isVisible())
  check('u02_no_overflow_1440', !(await overflow(page)))
  const groups = await page.locator('nav[aria-label="会话列表"] h3').allTextContents()
  check('u03_groups', ['今天', '昨天', '更早'].every((g) => groups.includes(g)), groups.join(','))
  const titles = await page.locator('nav[aria-label="会话列表"] li .truncate').allTextContents()
  check('u04_no_test_titles', !titles.some((t) => /123|你好|秘密口令|逐行输出|系统提示/.test(t)), titles.slice(0, 6).join('|'))

  // 搜索即筛 + 无结果空态
  await page.getByLabel('搜索会话').fill('物流')
  await page.waitForTimeout(400)
  const filtered = await page.locator('nav[aria-label="会话列表"] li').count()
  check('u05_search_filter', filtered >= 1 && filtered < 6, `n=${filtered}`)
  await page.getByLabel('搜索会话').fill('zzz-none')
  await page.waitForTimeout(400)
  check('u06_search_empty', await page.getByText('没有找到相关会话').isVisible())
  await page.getByLabel('搜索会话').fill('')
  await page.waitForTimeout(400)

  // 正常对话 + 工具动作条折叠/展开
  await page.getByText('订单 10001 的物流到哪了？', { exact: false }).first().click()
  await page.waitForTimeout(1500)
  check('u07_tool_bar', await page.getByText('已查询物流').isVisible())
  check('u08_citation', await page.getByText('依据：《配送与运费规则》').isVisible())
  await page.getByText('已查询物流').click()
  await page.waitForTimeout(400)
  check('u09_tool_expanded', await page.getByText('返回摘要').isVisible())
  await page.screenshot({ path: `${SHOTS}/ui-chat-tool.png` })

  // 新会话空态（三段式）
  await page.getByRole('button', { name: '新建会话' }).click()
  await page.waitForTimeout(1200)
  check('u10_empty_welcome', await page.getByText('你好，我是你的智能客服').isVisible())
  check('u11_empty_order_card', await page.getByRole('log').getByText('最近订单').isVisible())
  check('u12_empty_quick', await page.getByRole('button', { name: '查物流', exact: true }).isVisible())
  check('u13_no_dev_copy', !(await page.getByText('历史消息来自服务端').count()))
  await page.screenshot({ path: `${SHOTS}/ui-empty.png` })

  // 转人工排队 + 取消
  await page.locator('button', { hasText: '转人工' }).last().click()
  await page.waitForTimeout(600)
  check('u14_queue_bar', await page.getByText('正在为你转接人工客服').isVisible())
  await page.screenshot({ path: `${SHOTS}/ui-queue.png` })
  await page.getByRole('button', { name: '取消排队' }).click()
  await page.waitForTimeout(400)
  check('u15_queue_cancel', !(await page.getByText('正在为你转接人工客服').count()))

  // 结束会话 + 满意度（在示例会话上操作）
  await page.getByText('发票抬头怎么修改？', { exact: false }).first().click()
  await page.waitForTimeout(1200)
  await page.getByLabel('更多操作').click()
  await page.waitForTimeout(400)
  await page.getByText('结束会话', { exact: true }).click()
  await page.waitForTimeout(600)
  check('u16_ticket_no', await page.locator('.el-dialog').getByText(/GD\d{11}/).isVisible())
  await page.getByRole('button', { name: '下一步：评价服务' }).click()
  await page.waitForTimeout(500)
  check('u17_rating', await page.locator('.el-dialog .el-rate').isVisible())
  await page.screenshot({ path: `${SHOTS}/ui-ended-rating.png` })
  await page.getByRole('button', { name: '提交反馈' }).click()
  await page.waitForTimeout(600)
  check('u18_thanks', await page.getByText('感谢反馈').isVisible())
  await page.getByRole('button', { name: '完成', exact: true }).click()
  await page.waitForTimeout(500)
  check('u19_ended_disabled', await page.locator('textarea[disabled]').count() === 1)
  check('u20_ended_status', await page.getByText('已结束', { exact: true }).isVisible())

  // 右栏收起 + 悬浮展开
  await page.getByLabel('收起上下文面板').click()
  await page.waitForTimeout(400)
  check('u21_collapse_fab', await page.getByLabel('展开客户信息面板').isVisible())
  await page.getByLabel('展开客户信息面板').click()
  await page.waitForTimeout(400)
  check('u22_reexpand', await page.getByText('客户与业务上下文').isVisible())
  // 清理验收产生的本地状态，保持首屏干净
  await page.evaluate(() => {
    localStorage.removeItem('ended-sessions-v1')
    localStorage.removeItem('current-conversation-v3')
    const list = JSON.parse(localStorage.getItem('conversations-v3') || '[]')
    localStorage.setItem(
      'conversations-v3',
      JSON.stringify(list.filter((c) => !c.id.startsWith('web-'))),
    )
  })
  await ctx.close()
}

// ---------- 1024：右栏抽屉 ----------
// 限流每 IP 60 次/分钟：各宽度阶段间隔 20s，避免验收脚本自身触发 429
await new Promise((r) => setTimeout(r, 20000))
{
  const { ctx, page } = await newPage(1024, 800)
  await login(page)
  check('u23_right_hidden_1024', !(await page.getByText('客户与业务上下文').isVisible()))
  check('u24_no_overflow_1024', !(await overflow(page)))
  await page.getByLabel('打开客户信息').click()
  await page.waitForTimeout(800)
  check('u25_right_drawer', await page.locator('.el-drawer').getByText('客户与业务上下文').isVisible())
  await page.keyboard.press('Escape')
  await page.waitForTimeout(600)
  check(
    'u26_drawer_esc',
    await page.evaluate(
      () => ![...document.querySelectorAll('.el-overlay')].some((x) => getComputedStyle(x).display !== 'none'),
    ),
  )
  await ctx.close()
}

// ---------- 768：左栏抽屉 + 吸底输入 ----------
await new Promise((r) => setTimeout(r, 20000))
{
  const { ctx, page } = await newPage(768, 900, true)
  await login(page)
  check('u27_left_hidden_768', !(await page.locator('nav[aria-label="会话列表"]').first().isVisible()))
  check('u28_no_overflow_768', !(await overflow(page)))
  await page.getByLabel('打开会话列表').click()
  await page.waitForTimeout(800)
  check('u29_left_drawer', await page.locator('.el-drawer').getByRole('button', { name: '新建会话' }).isVisible())
  await page.keyboard.press('Escape')
  await page.waitForTimeout(600)
  const box = await page.getByLabel('消息输入框').boundingBox()
  check('u30_input_bottom', box !== null && box.y + box.height <= 900 && box.y > 500, `y=${box?.y}`)
  await ctx.close()
}

await browser.close()
const failed = results.filter((r) => !r.ok)
console.log(`SUMMARY ${results.length - failed.length}/${results.length} passed`)
console.log(`CONSOLE_ERRORS ${consoleErrors.length}`)
consoleErrors.slice(0, 8).forEach((e) => console.log('  ', e))
process.exit(failed.length || consoleErrors.length ? 1 : 0)