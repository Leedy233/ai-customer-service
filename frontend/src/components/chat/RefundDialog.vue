<script setup lang="ts">
import { ref, watch } from 'vue'
import { applyRefund, confirmRefund } from '@/api/refund'
import { notifyError } from '@/api/http'
import type { RefundApplyResult, RefundConfirmResult } from '@/types/models'
import { formatMoney } from '@/utils/format'

const props = defineProps<{
  modelValue: boolean
  convId: string
  initialOrderNo?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'confirmed', result: RefundConfirmResult): void
}>()

type Step = 'form' | 'confirm' | 'done'
const step = ref<Step>('form')
const orderNo = ref('')
const applying = ref(false)
const confirming = ref(false)
const applyResult = ref<RefundApplyResult | null>(null)
const confirmResult = ref<RefundConfirmResult | null>(null)

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      step.value = 'form'
      orderNo.value = props.initialOrderNo ?? ''
      applyResult.value = null
      confirmResult.value = null
    }
  },
)

function close(): void {
  emit('update:modelValue', false)
}

async function submitApply(): Promise<void> {
  const no = orderNo.value.trim()
  if (!no) return
  applying.value = true
  try {
    applyResult.value = await applyRefund(props.convId, no)
    step.value = 'confirm'
  } catch (e) {
    notifyError(e)
  } finally {
    applying.value = false
  }
}

async function submitConfirm(): Promise<void> {
  if (!applyResult.value) return
  confirming.value = true
  try {
    confirmResult.value = await confirmRefund(applyResult.value.refundId)
    step.value = 'done'
    emit('confirmed', confirmResult.value)
  } catch (e) {
    notifyError(e)
  } finally {
    confirming.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="退款申请"
    width="min(440px, calc(100vw - 32px))"
    :close-on-click-modal="false"
    @update:model-value="close"
  >
    <!-- 第一步：填写订单号 -->
    <template v-if="step === 'form'">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="两步确认机制"
        description="提交申请后生成待确认退款单，需再次显式确认才会生效。"
      />
      <el-form class="mt-4" label-position="top" @submit.prevent>
        <el-form-item label="订单号" required>
          <el-input v-model="orderNo" placeholder="请输入订单号，如 10001" clearable size="large" />
        </el-form-item>
      </el-form>
    </template>

    <!-- 第二步：显式确认 -->
    <template v-else-if="step === 'confirm' && applyResult">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="请确认退款信息"
        :description="`订单 ${applyResult.orderNo} · 退款金额 ${formatMoney(applyResult.amount)}。确认后订单进入「退款中」，该操作不可撤销。`"
      />
      <div class="mt-4 rounded-xl bg-surface px-4 py-3 text-sm text-gray-600">
        {{ applyResult.message }}
      </div>
    </template>

    <!-- 完成 -->
    <template v-else-if="step === 'done' && confirmResult">
      <el-result icon="success" title="退款已确认" :sub-title="confirmResult.message" />
    </template>

    <template #footer>
      <template v-if="step === 'form'">
        <el-button round @click="close">取消</el-button>
        <el-button
          round
          type="primary"
          :loading="applying"
          :disabled="!orderNo.trim()"
          @click="submitApply"
        >
          提交申请
        </el-button>
      </template>
      <template v-else-if="step === 'confirm'">
        <el-button round @click="step = 'form'">返回修改</el-button>
        <el-button round type="danger" :loading="confirming" @click="submitConfirm">
          确认退款
        </el-button>
      </template>
      <template v-else>
        <el-button round type="primary" @click="close">完成</el-button>
      </template>
    </template>
  </el-dialog>
</template>