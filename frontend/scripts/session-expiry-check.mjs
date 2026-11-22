// 会话过期恢复链路验收：过期后点管理后台 → 整页落登录页(reason=expired) → 重登回目标；匿名无误报；USER 403
import { chromium } from 'playwright'

const BASE = 'http://localhost'
const results = []
const check = (n, ok, x = '') => {
  results.push(ok)
  console.log((ok ? 'PASS ' : 'FAIL ') + n + (x ? ' ' + x : ''))
}

const b = await chromium.launch()
// P1-P3：会话过期后点管理后台 → 整页落登录页带 reason → 重登回 /admin
{
  const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } })
  const p = await ctx.newPage()
  await p.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
  await p.getByPlaceholder('用户名').fill('admin')
  await p.getByPlaceholder('密码').fill('Admin@123')
  await p.getByRole('button', { name: '登录', exact: true }).click()
  await p.waitForTimeout(1800)
  await p.goto(`${BASE}/chat`, { waitUntil: 'networkidle' })
  await p.waitForTimeout(1000)
  await ctx.clearCookies() // 模拟 Session 过期（等价于后端重启失效）
  await p.locator('header').getByText('admin', { exact: false }).first().click()
  await p.waitForTimeout(400)
  await p.getByText('管理后台', { exact: true }).click()
  await p.waitForTimeout(2500)
  check('p1_expired_hard_login', p.url().includes('/login') && p.url().includes('reason=expired'), p.url())
  check('p2_expired_alert', await p.getByText('登录已过期，请重新登录').isVisible())
  await p.getByPlaceholder('用户名').fill('admin')
  await p.getByPlaceholder('密码').fill('Admin@123')
  await p.getByRole('button', { name: '登录', exact: true }).click()
  await p.waitForTimeout(2500)
  check(
    'p3_relogin_back_admin',
    p.url().endsWith('/admin') && (await p.getByText('数据仪表盘').first().isVisible()),
    p.url(),
  )
  await ctx.close()
}
// P4：匿名直连 /admin → 登录页且无过期误报
{
  const ctx = await b.newContext()
  const p = await ctx.newPage()
  await p.goto(`${BASE}/admin`, { waitUntil: 'networkidle' })
  await p.waitForTimeout(1500)
  check(
    'p4_anon_login_no_toast',
    p.url().includes('/login') && (await p.getByText('登录已过期，请重新登录').count()) === 0,
    p.url(),
  )
  await ctx.close()
}
// P5：zhangsan 直连 /admin → 403
{
  const ctx = await b.newContext()
  const p = await ctx.newPage()
  await p.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
  await p.getByPlaceholder('用户名').fill('zhangsan')
  await p.getByPlaceholder('密码').fill('123456')
  await p.getByRole('button', { name: '登录', exact: true }).click()
  await p.waitForTimeout(1500)
  await p.goto(`${BASE}/admin`, { waitUntil: 'networkidle' })
  await p.waitForTimeout(1200)
  check('p5_user_403', p.url().includes('/403'), p.url())
  await ctx.close()
}
console.log(`==== session-expiry-check: ${results.filter(Boolean).length}/${results.length} PASS ====`)
await b.close()
process.exit(results.every(Boolean) ? 0 : 1)