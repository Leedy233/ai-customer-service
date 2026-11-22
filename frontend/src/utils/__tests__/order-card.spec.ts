import { describe, expect, it } from 'vitest'
import { parseOrderCard, orderStatusTone } from '@/utils/order-card'

describe('parseOrderCard', () => {
  it('解析「订单号：X」表述', () => {
    const text = '订单号：10001\n商品：无线蓝牙耳机\n金额：199.00\n状态：已发货'
    expect(parseOrderCard(text)).toEqual({
      orderNo: '10001',
      productName: '无线蓝牙耳机',
      amount: '199.00',
      status: '已发货',
    })
  })

  it('解析「订单 X 的…」表述', () => {
    const text = '订单 10002 的信息如下：\n商品：机械键盘\n金额：399.00\n订单状态：待发货'
    const card = parseOrderCard(text)
    expect(card?.orderNo).toBe('10002')
    expect(card?.status).toBe('待发货')
  })

  it('字段命中不足两个时不出卡片', () => {
    expect(parseOrderCard('订单号：10001\n商品：耳机')).toBeUndefined()
  })

  it('无订单号不出卡片', () => {
    expect(parseOrderCard('商品：耳机\n金额：199.00\n状态：已发货')).toBeUndefined()
  })
})

describe('orderStatusTone', () => {
  it('状态映射色调', () => {
    expect(orderStatusTone('已完成')).toBe('success')
    expect(orderStatusTone('已发货')).toBe('warning')
    expect(orderStatusTone('退款中')).toBe('danger')
    expect(orderStatusTone(undefined)).toBe('info')
  })
})