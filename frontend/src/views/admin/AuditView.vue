<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Refresh, Search } from '@element-plus/icons-vue'
import { notifyError } from '@/api/http'
import * as manage from '@/api/admin/manage'
import PageHead from '@/components/admin/PageHead.vue'
import { usePage } from '@/composables/admin/usePage'
import type { AuditConvVO, AuditMessageVO } from '@/types/admin'
import { fmtTime } from '@/utils/admin-format'

const route = useRoute()
const keyword = ref('')
const username = ref('')

const { rows, total, page, size, loading, load, reload, onPage } = usePage<AuditConvVO>((p, s) =>
  manage.auditConversations({
    keyword: keyword.value || undefined,
    username: username.value || undefined,
    page: p,
    size: s,
  }),
)

const detailOpen = ref(false)
const detailConv = ref<AuditConvVO | null>(null)
const messages = ref<AuditMessageVO[]>([])
const msgLoading = ref(false)

async function openConv(row: AuditConvVO): Promise<void> {
  detailConv.value = row
  detailOpen.value = true
  msgLoading.value = true
  try {
    messages.value = await manage.auditMessages(row.conversationId)
  } catch (e) {
    notifyError(e)
  } finally {
    msgLoading.value = false
  }
}

function roleLabel(role: string): string {
  if (role === 'user') return '用户'
  if (role === 'assistant') return 'AI'
  return '工具'
}

function roleClass(role: string): string {
  if (role === 'user') return 'text-brand-600'
  if (role === 'assistant') return 'text-success'
  return 'text-warning'
}

onMounted(async () => {
  await load()
  const conv = route.query.conv
  if (typeof conv === 'string') {
    const hit = rows.value.find((r) => r.conversationId === conv)
    if (hit) {
      void openConv(hit)
    } else {
      // 目标会话不在第一页：直接拉全文展示
      detailConv.value = {
        conversationId: conv,
        userId: 0,
        username: '--',
        messageCount: 0,
        toolCount: 0,
        preview: '',
        lastActiveAt: '',
      }
      detailOpen.value = true
      msgLoading.value = true
      try {
        messages.value = await manage.auditMessages(conv)
      } catch (e) {
        notifyError(e)
      } finally {
        msgLoading.value = false
      }
    }
  }
})
</script>

<template>
  <div class="flex flex-col gap-4">
    <PageHead title="AI 对话审计" desc="ADMIN 全量只读视角：含工具调用行；不修改任何聊天数据。">
      <template #actions>
        <el-button size="small" :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHead>

    <div class="flex flex-wrap items-center gap-2 rounded-xl border border-line bg-card p-3">
      <el-input
        v-model="keyword"
        placeholder="消息内容关键词"
        size="small"
        class="!w-48"
        clearable
        aria-label="按内容关键词筛选会话"
        @keyup.enter="reload"
      />
      <el-input
        v-model="username"
        placeholder="归属用户"
        size="small"
        class="!w-32"
        clearable
        aria-label="按归属用户筛选会话"
        @keyup.enter="reload"
      />
      <el-button size="small" type="primary" :icon="Search" @click="reload">查询</el-button>
    </div>

    <div class="rounded-xl border border-line bg-card p-4">
      <el-table v-loading="loading" :data="rows" size="small" empty-text="暂无会话">
        <el-table-column label="会话 ID" min-width="180" show-overflow-tooltip>
          <template #default="{ row }"><span class="mono">{{ row.conversationId }}</span></template>
        </el-table-column>
        <el-table-column prop="username" label="归属" min-width="90" />
        <el-table-column label="消息/工具" width="100" align="right">
          <template #default="{ row }">
            <span class="mono">{{ row.messageCount }} / {{ row.toolCount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="preview" label="首条用户消息" min-width="200" show-overflow-tooltip />
        <el-table-column label="最近活跃" min-width="140">
          <template #default="{ row }"><span class="mono text-ink-3">{{ fmtTime(row.lastActiveAt) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openConv(row)">全文</el-button>
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

    <el-drawer v-model="detailOpen" title="会话全文" size="min(560px, 94vw)">
      <div v-if="detailConv" class="space-y-3">
        <p class="mono text-xs text-ink-3">{{ detailConv.conversationId }}</p>
        <div v-loading="msgLoading" class="space-y-3">
          <div v-for="m in messages" :key="m.id" class="rounded-lg border border-line bg-surface p-3">
            <div class="flex items-center gap-2 text-xs">
              <span class="stamp" :class="roleClass(m.role)">{{ roleLabel(m.role) }}</span>
              <span v-if="m.toolName" class="mono text-ink-3">{{ m.toolName }}</span>
              <span class="mono ml-auto text-ink-3">{{ fmtTime(m.createdAt) }}</span>
            </div>
            <p class="mt-2 whitespace-pre-wrap text-xs leading-5 text-ink-2">{{ m.content }}</p>
          </div>
          <p v-if="!msgLoading && !messages.length" class="text-xs text-ink-3">该会话暂无消息。</p>
        </div>
      </div>
    </el-drawer>
  </div>
</template>