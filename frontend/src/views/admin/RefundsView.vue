<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { notifyError } from '@/api/http'
import * as manage from '@/api/admin/manage'
import PageHead from '@/components/admin/PageHead.vue'
import StatusStamp from '@/components/admin/StatusStamp.vue'
import { usePage } from '@/composables/admin/usePage'
import type { AdminRefundVO } from '@/types/admin'
import { formatMoney } from '@/utils/format'
import { fmtTime } from '@/utils/admin-format'

const status = ref('')
const orderNo = ref('')
const CHIPS = [
  { key: 'PENDING_CONFIRM', label: '待确认' },
  { key: 'REFUNDED', label: '已执行' },
  { key: 'COMPLETED', label: '已完结' },
  { key: 'REJECTED', label: '已驳回' },
]

const { rows, total, page, size, loading, meta, load, reload, onPage } = usePage<AdminRefundVO>((p, s) =>
  manage.refunds({ status: status.value || undefined, orderNo: orderNo.value || undefined, page: p, size: s }),
)

const rejectOpen = ref(false)
const rejectTarget = ref<AdminRefundVO | null>(null)
const rejectNote = ref('')

function openReject(row: AdminRefundVO): void {
  rejectTarget.value = row
  rejectNote.value = ''
  rejectOpen.value = true
}

async function doReject(): Promise<void> {
  if (!rejectTarget.value) return
  try {
    await manage.rejectRefund(rejectTarget.value.id, rejectNote.value.trim() || undefined)
    ElMessage.success('已驳回该退款申请')
    rejectOpen.value = false
    void load()
  } catch (e) {
    notifyError(e)
  }
}

async function onComplete(row: AdminRefundVO): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认完结退款 #${row.id}（订单 ${row.orderNo}）？订单状态将更新为「已退款」。`,
      '完结退款',
      { confirmButtonText: '确认完结', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await manage.completeRefund(row.id)
    ElMessage.success('退款已完结')
    void load()
  } catch (e) {
    notifyError(e)
  }
}

function chipClick(key: string): void {
  status.value = status.value === key ? '' : key
  reload()
}

onMounted(load)
</script>

<template>
  <div class="flex flex-col gap-4">
    <PageHead title="退款管理" desc="两段式退款：用户申请→用户确认执行；管理端可驳回待确认申请、完结已执行退款。">
      <template #actions>
        <el-button size="small" :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHead>

    <div class="flex flex-wrap items-center gap-2 rounded-xl border border-line bg-card p-3">
      <button
        v-for="c in CHIPS"
        :key="c.key"
        class="rounded-lg border px-3 py-1.5 text-xs"
        :class="status === c.key ? 'border-brand-500 bg-brand-50 text-brand-600' : 'border-line bg-card text-ink-2 hover:border-brand-200'"
        @click="chipClick(c.key)"
      >
        {{ c.label }} <span class="mono font-semibold">{{ meta[c.key] ?? 0 }}</span>
      </button>
      <el-input
        v-model="orderNo"
        placeholder="订单号"
        size="small"
        class="!w-36"
        clearable
        aria-label="按订单号筛选退款"
        @keyup.enter="reload"
      />
      <el-button size="small" type="primary" :icon="Search" @click="reload">查询</el-button>
    </div>

    <div class="rounded-xl border border-line bg-card p-4">
      <el-table v-loading="loading" :data="rows" size="small" empty-text="暂无退款申请">
        <el-table-column label="ID" width="70">
          <template #default="{ row }"><span class="mono">#{{ row.id }}</span></template>
        </el-table-column>
        <el-table-column label="订单号" min-width="100">
          <template #default="{ row }"><span class="mono">{{ row.orderNo }}</span></template>
        </el-table-column>
        <el-table-column prop="username" label="用户" min-width="90" />
        <el-table-column label="金额" min-width="90" align="right">
          <template #default="{ row }"><span class="mono font-semibold">{{ formatMoney(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" min-width="90">
          <template #default="{ row }"><StatusStamp :status="row.status" /></template>
        </el-table-column>
        <el-table-column label="申请时间" min-width="140">
          <template #default="{ row }"><span class="mono text-ink-3">{{ fmtTime(row.createdAt) }}</span></template>
        </el-table-column>
        <el-table-column label="备注" min-width="120" show-overflow-tooltip>
          <template #default="{ row }"><span class="text-ink-3">{{ row.adminNote || '--' }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING_CONFIRM'" link type="danger" size="small" @click="openReject(row)">
              驳回
            </el-button>
            <el-button v-if="row.status === 'REFUNDED'" link type="success" size="small" @click="onComplete(row)">
              完结
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="mt-3 flex justify-end">
        <el-pagination
          v-model:current-page="page"
          :page-size="size"
          :total="total"
          layout="total, prev, pager, next"
          small
          @current-change="onPage"
        />
      </div>
    </div>

    <el-dialog v-model="rejectOpen" title="驳回退款申请" width="min(420px, calc(100vw - 32px))">
      <p class="text-sm text-ink-2">
        驳回 #{{ rejectTarget?.id }}（订单 {{ rejectTarget?.orderNo }}），用户侧将不可再确认该申请。
      </p>
      <el-input
        v-model="rejectNote"
        type="textarea"
        :rows="3"
        maxlength="200"
        show-word-limit
        placeholder="驳回理由（可选）"
        class="mt-3"
        aria-label="驳回理由"
      />
      <template #footer>
        <el-button @click="rejectOpen = false">取消</el-button>
        <el-button type="danger" @click="doReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>