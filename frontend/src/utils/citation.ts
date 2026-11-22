import type { Citation } from '@/types/models'

/**
 * 答案溯源匹配：后端尚未返回引用字段，按回答主题命中知识库文档，
 * 原文片段为示例内容（抽屉内明确标注），接口上线后替换为真实引用。
 */
const RULES: { pattern: RegExp; citation: Citation }[] = [
  {
    pattern: /退款|退货|七天|7\s*天/,
    citation: {
      doc: '退款政策 v2',
      section: '第 3 节',
      excerpt:
        '自签收之日起 7 天内，商品完好且不影响二次销售的，支持无理由退货退款。退款申请提交后进入审核，审核通过后原路退回支付账户，到账时间以支付渠道为准。已发货订单需先拦截或拒收包裹，方可完成退款流程。',
    },
  },
  {
    pattern: /会员日|会员|优惠|折扣/,
    citation: {
      doc: '会员权益说明',
      section: '第 2 节',
      excerpt:
        '每月 8 号为会员日，全场商品享 95 折，仅限普通会员及以上等级参与。会员日折扣不与优惠券叠加使用；生日月可额外领取一张无门槛券，有效期 30 天。',
    },
  },
  {
    pattern: /运费|包邮|发货|时效|快递|物流|送达/,
    citation: {
      doc: '配送与运费规则',
      section: '第 1 节',
      excerpt:
        '单笔订单满 99 元包邮，不足 99 元收取 8 元运费。工作日 16:00 前付款的订单当日发出，其余订单次日发出；默认顺丰快递，偏远地区时效顺延 1–3 天。',
    },
  },
  {
    pattern: /发票/,
    citation: {
      doc: '发票开具指引',
      section: '第 4 节',
      excerpt:
        '订单完成后可在订单详情申请电子普通发票，抬头支持个人与企业；发票于 3 个工作日内发送至预留邮箱。超过 30 天的订单需联系人工客服补开。',
    },
  },
]

export function detectCitation(content: string): Citation | undefined {
  for (const rule of RULES) {
    if (rule.pattern.test(content)) return rule.citation
  }
  return undefined
}