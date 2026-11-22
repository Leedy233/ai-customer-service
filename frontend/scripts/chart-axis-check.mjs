// 图表三问题修复核验：Tooltip 不遮 X 轴 / 标签不重叠 / 双 Y 轴网格线对齐（截图人工复核）/ 轴标签高亮
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'

const BASE = 'http://localhost'
const SHOTS = '../docs/screenshots'
mkdirSync(SHOTS, { recursive: true })
const results = []
function check(name, ok, extra = '') {
  results.push({ name, ok })
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${extra ? ' ' + extra : ''}`)
}

const browser = await chromium.launch()
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const page = await ctx.newPage()
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.getByPlaceholder('用户名').fill('admin')
await page.getByPlaceholder('密码').fill('Admin@123')
await page.getByRole('button', { name: '登录', exact: true }).click()
await page.waitForTimeout(2000)
await page.goto(`${BASE}/admin`, { waitUntil: 'networkidle' })
await page.getByText('近 30 天', { exact: true }).click()
await page.waitForTimeout(1800)

const chart = page.locator('div[_echarts_instance_]').first()
const box = await chart.boundingBox()
// 悬浮到折线中部触发 tooltip + axisPointer
await page.mouse.move(box.x + box.width * 0.55, box.y + box.height * 0.45)
await page.waitForTimeout(700)

const tip = await page.evaluate(() => {
  const host = document.querySelector('div[_echarts_instance_]')
  if (!host) return null
  const el = [...host.querySelectorAll('div')].find(
    (d) => d.style.position === 'absolute' && d.innerText.includes('请求数'),
  )
  if (!el) return null
  const r = el.getBoundingClientRect()
  return { top: r.top, bottom: r.bottom, left: r.left, right: r.right }
})
check('c01_tooltip_visible', !!tip)
check(
  'c02_tooltip_above_xaxis',
  !!tip && tip.bottom <= box.y + box.height - 24,
  `tipBottom=${tip?.bottom?.toFixed(0)} axisZoneTop=${(box.y + box.height - 24).toFixed(0)}`,
)
check(
  'c03_tooltip_confined',
  !!tip && tip.left >= box.x - 1 && tip.right <= box.x + box.width + 1,
)
// 轴指针徽章画在 canvas 上：扫描底部标签带是否出现主题色(109,91,208)像素
const badge = await page.evaluate(() => {
  const host = document.querySelector('div[_echarts_instance_]')
  const canvas = host?.querySelector('canvas')
  if (!canvas) return false
  const g = canvas.getContext('2d')
  const h = canvas.height
  const band = g.getImageData(0, h - 60, canvas.width, 60).data
  let n = 0
  for (let i = 0; i < band.length; i += 4) {
    if (Math.abs(band[i] - 109) < 12 && Math.abs(band[i + 1] - 91) < 12 && Math.abs(band[i + 2] - 208) < 12) n++
  }
  return n > 40
})
check('c04_axispointer_label', badge)
await page.screenshot({ path: `${SHOTS}/dashboard-trend-fixed.png`, clip: { x: box.x - 16, y: box.y - 40, width: box.width + 32, height: box.height + 56 } })
// 移开鼠标截无 tooltip 全图（复核标签隐藏与网格线对齐）
await page.mouse.move(10, 10)
await page.waitForTimeout(500)
await page.screenshot({ path: `${SHOTS}/dashboard-trend-clean.png`, clip: { x: box.x - 16, y: box.y - 40, width: box.width + 32, height: box.height + 56 } })

// ---------- 每日 Token 构成：图例与标题同行、绘图区外、X 轴带无遮挡 ----------
await page.goto(`${BASE}/admin/tokens`, { waitUntil: 'networkidle' })
await page.getByText('近 30 天', { exact: true }).click()
await page.waitForTimeout(1500)
const tchart = page.locator('div[_echarts_instance_]').first()
const tbox = await tchart.boundingBox()
const bands = await page.evaluate(() => {
  const host = document.querySelector('div[_echarts_instance_]')
  const canvas = host?.querySelector('canvas')
  if (!canvas) return null
  const g = canvas.getContext('2d')
  const w = canvas.width
  const h = canvas.height
  const count = (data, pred) => {
    let n = 0
    for (let i = 0; i < data.length; i += 4) {
      if (pred(data[i], data[i + 1], data[i + 2])) n++
    }
    return n
  }
  const purple = (r, gg, b) => Math.abs(r - 109) < 12 && Math.abs(gg - 91) < 12 && Math.abs(b - 208) < 12
  const orange = (r, gg, b) => Math.abs(r - 247) < 12 && Math.abs(gg - 144) < 12 && Math.abs(b - 9) < 12
  const dark = (r, gg, b) => r < 60 && gg < 60 && b < 60
  const nameGray = (r, gg, b) => Math.abs(r - 134) < 10 && Math.abs(gg - 144) < 10 && Math.abs(b - 156) < 10
  const half = Math.floor(w / 2)
  const topRight = g.getImageData(half, 0, w - half, 26).data
  const topLeft = g.getImageData(0, 0, half, 26).data
  const bottom = g.getImageData(0, h - 24, w, 24).data
  const titleBand = g.getImageData(0, 0, half, 20).data
  const nameBand = g.getImageData(0, 32, half, 22).data
  return {
    legendTopRight: count(topRight, purple) > 20 && count(topRight, orange) > 20,
    titleTopLeft: count(topLeft, dark) > 20,
    bottomClean: count(bottom, purple) < 5 && count(bottom, orange) < 5,
    nameClearOfTitle: count(titleBand, nameGray) === 0 && count(nameBand, nameGray) > 20,
  }
})
check('c05_legend_top_right', !!bands?.legendTopRight)
check('c06_title_top_left', !!bands?.titleTopLeft)
check('c07_xaxis_band_clean', !!bands?.bottomClean)
check('c13_yaxis_name_clear_of_title', !!bands?.nameClearOfTitle)
await page.screenshot({
  path: `${SHOTS}/tokens-legend-fixed.png`,
  clip: { x: tbox.x - 16, y: tbox.y - 150, width: tbox.width + 32, height: tbox.height + 166 },
})

// ---------- 零数据日悬浮：tooltip 吸附类目中心 / 阴影带可见 / 无数据文案 / 不遮 X 轴 ----------
await page.getByText('近 7 天', { exact: true }).click()
await page.waitForTimeout(1500)
const zbox = await tchart.boundingBox()
const zband = (zbox.width - 56 - 24) / 7
const readTip = () =>
  page.evaluate(() => {
    const host = document.querySelector('div[_echarts_instance_]')
    if (!host) return null
    const el = [...host.querySelectorAll('div')].find(
      (d) => d.style.position === 'absolute' && (d.innerText.includes('无请求数据') || d.innerText.includes('生成')),
    )
    if (!el) return null
    const r = el.getBoundingClientRect()
    return { top: r.top, bottom: r.bottom, left: r.left, right: r.right, text: el.innerText }
  })
let zero = null
for (let idx = 0; idx < 7 && !zero; idx++) {
  const cx = zbox.x + 56 + zband * (idx + 0.5)
  await page.mouse.move(cx, zbox.y + zbox.height - 40)
  await page.waitForTimeout(400)
  const t = await readTip()
  if (t?.text.includes('无请求数据')) zero = { idx, cx, tip: t }
}
check('c08_zero_day_tooltip_found', !!zero)
check(
  'c09_tooltip_snapped_to_category',
  !!zero && Math.abs((zero.tip.left + zero.tip.right) / 2 - zero.cx) <= 8,
  zero ? `offset=${(((zero.tip.left + zero.tip.right) / 2) - zero.cx).toFixed(1)}px` : '',
)
check(
  'c10_zero_tooltip_above_xaxis',
  !!zero && zero.tip.bottom <= zbox.y + zbox.height - 24,
)
const shadow = zero
  ? await page.evaluate(({ x, y }) => {
      const canvas = document.querySelector('div[_echarts_instance_] canvas')
      if (!canvas) return false
      const g = canvas.getContext('2d')
      // canvas 透明底：阴影带 = 低 alpha 主题色像素（premultiplied 反算 rgb 略有偏差），带外全透明
      const d = g.getImageData(Math.round(x), Math.round(y), 1, 1).data
      const o = g.getImageData(20, Math.round(y), 1, 1).data
      const tint =
        d[3] >= 8 && d[3] <= 40 &&
        Math.abs(d[0] - 109) < 15 && Math.abs(d[1] - 91) < 15 && Math.abs(d[2] - 208) < 15
      return tint && o[3] < 4
    }, { x: zero.cx - zbox.x, y: 40 + (zbox.height - 68) / 10 })
  : false
check('c11_shadow_band_visible_on_zero_day', shadow)
if (zero) {
  await page.screenshot({
    path: `${SHOTS}/tokens-zero-hover.png`,
    clip: { x: zbox.x - 16, y: zbox.y - 16, width: zbox.width + 32, height: zbox.height + 32 },
  })
}

// ---------- 按模型 / 按用户：请求数与 Tokens 独立两列、互不重合 ----------
await page.mouse.move(10, 10)
await page.waitForTimeout(400)
const tables = await page.evaluate(() => {
  const rect = (el) => {
    const b = el.getBoundingClientRect()
    return { text: el.innerText.trim(), left: b.left, right: b.right, width: b.width }
  }
  return [...document.querySelectorAll('.el-table')].map((t) => ({
    cells: [...t.querySelectorAll('thead th')].map(rect),
    row: [...t.querySelectorAll('tbody tr:first-child td')].map(rect),
  }))
})
const disjoint = (cs) =>
  cs.length === 3 &&
  cs[0].right <= cs[1].left + 1 &&
  cs[1].right <= cs[2].left + 1 &&
  cs[1].width >= 80 &&
  cs[2].width >= 100
check(
  'c12_tables_two_columns_disjoint',
  tables.length === 2 && tables.every((t) => disjoint(t.cells) && disjoint(t.row)),
)
const gbox = await page.evaluate(() => {
  const g = document.querySelector('.el-table').closest('.grid')
  const b = g.getBoundingClientRect()
  return { x: b.x, y: b.y, width: b.width, height: b.height }
})
await page.screenshot({
  path: `${SHOTS}/tokens-tables.png`,
  clip: { x: gbox.x - 12, y: gbox.y - 12, width: gbox.width + 24, height: gbox.height + 24 },
})

const failed = results.filter((r) => !r.ok)
console.log(`\n==== chart-axis-check: ${results.length - failed.length}/${results.length} PASS ====`)
await browser.close()
process.exit(failed.length ? 1 : 0)