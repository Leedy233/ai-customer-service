import { describe, expect, it } from 'vitest'
import { formatMoney, truncate } from '@/utils/format'

describe('formatMoney', () => {
  it('数字与字符串统一两位小数', () => {
    expect(formatMoney(199)).toBe('¥199.00')
    expect(formatMoney('399.00')).toBe('¥399.00')
  })
  it('空值显示占位', () => {
    expect(formatMoney(undefined)).toBe('--')
    expect(formatMoney('')).toBe('--')
  })
  it('非法数字原样返回', () => {
    expect(formatMoney('abc')).toBe('abc')
  })
})

describe('truncate', () => {
  it('超长截断加省略号', () => {
    expect(truncate('abcdefghij', 5)).toBe('abcde…')
  })
  it('不超长原样', () => {
    expect(truncate('abc', 5)).toBe('abc')
  })
})