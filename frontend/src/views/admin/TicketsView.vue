<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { notifyError } from '@/api/http'
import * as tickets from '@/api/admin/tickets'
import PageHead from '@/components/admin/PageHead.vue'
import StatusStamp from '@/components/admin/StatusStamp.vue'
import { usePage } from '@/composables/admin/usePage'
import type { TicketVO } from '@/types/admin'
import { fmtTime } from '@/utils/admin-format'

const router = useRouter()
const status = ref('')
const kind = ref('')
const CHIPS = [
  { key: 'OPEN', label: '待处理' },
  { key: 'PROCESSING', label: '处理中' },
  { key: 'CLOSED', label: '已完成' },
  { key: 'CANCELLED', label: '已取消' },
]

const { rows, total, page, size, loading, meta, load, reload, onPage } = usePage<TicketVO>((p, s) =>
  tickets.list({ status: status.value || undefined, kind: kind.value || undefined, page: p, size: s }),
)

const detailOpen = ref(false)
const detail = ref<TicketVO | null>(null)
const closeNote = ref('')

async function openDetail(row: TicketVO): Promise<void> {
  try {
    detail.value = await tickets.detail(row.id)
    closeNote.value = ''
    detailOpen.value = true
  } catch (e) {
    notifyError(e)
  }
}

async function onProcess(row: TicketVO): Promise<void> {
  try {
    await tickets.process(row.id)
    ElMessage.success(`已接单 ${row.ticketNo}`)
    void load()
    if (detail.value?.id === row.id) detail.value = await tickets.detail(row.id)
  } catch (e) {
    notifyError(e)
  }
}

async function onClose(row: TicketVO): Promise<void> {
  try {
    await tickets.close(row.id, closeNote.value.trim() || undefined)
    ElMessage.success(`工单 ${row.ticketNo} 已关闭`)
    detailOpen.value = false
    void load()
  } catch (e) {
    notifyError(e)
  }
}

function chipClick(key: string): void {
  status.value = status.value === key ? '' : key
  reload()
}

function toAudit(convId: string): void {
  void router.push({ path: '/admin/audit', query: { conv: convId } })
}

onMounted(load)
</script>

<template>
  <div class="flex flex-col gap-4">
    <PageHead title="人工客服工单" desc="转人工排队与结束会话回执的真实工单；接单→关闭留痕 assignee 与备注。">
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
      <el-select v-model="kind" placeholder="全部类型" size="small" class="!w-32" clearable aria-label="按工单类型筛选" @change="reload">
        <el-option label="转人工" value="TRANSFER" />
        <el-option label="结束回执" value="END" />
      </el-select>
    </div>

    <div class="rounded-xl border border-line bg-card p-4">
      <el-table v-loading="loading" :data="rows" size="small" empty-text="暂无工单">
        <el-table-column label="工单号" min-width="140">
          <template #default="{ row }"><span class="mono">{{ row.ticketNo }}</span></template>
        </el-table-column>
        <el-table-column prop="username" label="用户" min-width="90" />
        <el-table-column label="类型" min-width="90">
          <template #default="{ row }"><StatusStamp :status="row.kind" /></template>
        </el-table-column>
        <el-table-column prop="category" label="分类" min-width="90">
          <template #default="{ row }">{{ row.category || '--' }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="90">
          <template #default="{ row }"><StatusStamp :status="row.status" /></template>
        </el-table-column>
        <el-table-column label="满意度" min-width="80">
          <template #default="{ row }">
            <span v-if="row.rating" class="mono text-warning">{{ '★'.repeat(row.rating) }}</span>
            <span v-else class="text-ink-3">--</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="140">
          <template #default="{ row }"><span class="mono text-ink-3">{{ fmtTime(row.createdAt) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'OPEN'" link type="warning" size="small" @click="onProcess(row)">
              接单
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

    <el-drawer v-model="detailOpen" title="工单详情" size="min(440px, 92vw)">
      <div v-if="detail" class="space-y-4">
        <div class="receipt p-4">
          <div class="flex items-center gap-2">
            <span class="mono text-sm font-semibold text-ink">{{ detail.ticketNo }}</span>
            <StatusStamp class="ml-auto" :status="detail.status" />
          </div>
          <dl class="receipt-perf mt-3 space-y-2 pt-3 text-xs">
            <div class="flex justify-between"><dt class="text-ink-3">用户</dt><dd class="text-ink-2">{{ detail.username }}</dd></div>
            <div class="flex justify-between"><dt class="text-ink-3">类型</dt><dd><StatusStamp :status="detail.kind" /></dd></div>
            <div class="flex justify-between"><dt class="text-ink-3">分类</dt><dd class="text-ink-2">{{ detail.category || '--' }}</dd></div>
            <div class="flex justify-between"><dt class="text-ink-3">是否解决</dt><dd class="text-ink-2">{{ detail.resolved == null ? '--' : detail.resolved ? '已解决' : '未解决' }}</dd></div>
            <div class="flex justify-between"><dt class="text-ink-3">满意度</dt><dd class="mono text-warning">{{ detail.rating ? '★'.repeat(detail.rating) : '--' }}</dd></div>
            <div class="flex justify-between"><dt class="text-ink-3">处理人</dt><dd class="text-ink-2">{{ detail.assignee || '--' }}</dd></div>
            <div class="flex justify-between"><dt class="text-ink-3">创建</dt><dd class="mono text-ink-2">{{ fmtTime(detail.createdAt) }}</dd></div>
            <div class="flex justify-between"><dt class="text-ink-3">关闭</dt><dd class="mono text-ink-2">{{ fmtTime(detail.closedAt) }}</dd></div>
          </dl>
          <p v-if="detail.summary" class="mt-3 whitespace-pre-wrap border-t border-dashed border-line pt-3 text-xs leading-5 text-ink-2">
            {{ detail.summary }}
          </p>
          <p v-if="detail.feedback" class="mt-2 text-xs leading-5 text-ink-3">用户反馈：{{ detail.feedback }}</p>
          <p v-if="detail.closeNote" class="mt-2 text-xs leading-5 text-ink-3">关闭备注：{{ detail.closeNote }}</p>
        </div>
        <el-button size="small" @click="toAudit(detail.conversationId)">查看会话审计</el-button>
        <div v-if="detail.status === 'OPEN' || detail.status === 'PROCESSING'" class="space-y-2">
          <el-input
            v-model="closeNote"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            placeholder="关闭备注（可选）"
            aria-label="关闭备注"
          />
          <div class="flex gap-2">
            <el-button v-if="detail.status === 'OPEN'" size="small" type="warning" @click="onProcess(detail)">
              接单
            </el-button>
            <el-button size="small" type="primary" @click="onClose(detail)">关闭工单</el-button>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>