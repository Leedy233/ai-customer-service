<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import * as manage from '@/api/admin/manage'
import PageHead from '@/components/admin/PageHead.vue'
import StatusStamp from '@/components/admin/StatusStamp.vue'
import { usePage } from '@/composables/admin/usePage'
import type { RequestLogVO } from '@/types/admin'
import { fmtTime } from '@/utils/admin-format'

const model = ref('')
const onlyError = ref(false)

const { rows, total, page, size, loading, load, reload, onPage, onSize } = usePage<RequestLogVO>((p, s) =>
  manage.logs({ model: model.value || undefined, onlyError: onlyError.value, page: p, size: s }),
)

onMounted(load)
</script>

<template>
  <div class="flex flex-col gap-4">
    <PageHead title="AI 请求日志" desc="每次模型调用的 token / 耗时 / 异常留痕（ai_request_log），只读。">
      <template #actions>
        <el-button size="small" :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHead>

    <div class="flex flex-wrap items-center gap-3 rounded-xl border border-line bg-card p-3">
      <el-input
        v-model="model"
        placeholder="模型名（如 deepseek-chat）"
        size="small"
        class="!w-52"
        clearable
        aria-label="按模型筛选"
        @keyup.enter="reload"
      />
      <label class="flex items-center gap-2 text-xs text-ink-2">
        <el-switch v-model="onlyError" size="small" aria-label="仅看异常" @change="reload" />
        仅看异常
      </label>
      <el-button size="small" type="primary" :icon="Search" @click="reload">查询</el-button>
    </div>

    <div class="rounded-xl border border-line bg-card p-4">
      <el-table v-loading="loading" :data="rows" size="small" empty-text="暂无请求日志">
        <el-table-column label="ID" width="70">
          <template #default="{ row }"><span class="mono">#{{ row.id }}</span></template>
        </el-table-column>
        <el-table-column label="时间" min-width="140">
          <template #default="{ row }"><span class="mono text-ink-3">{{ fmtTime(row.createdAt) }}</span></template>
        </el-table-column>
        <el-table-column prop="username" label="用户" min-width="90" />
        <el-table-column label="会话" min-width="160" show-overflow-tooltip>
          <template #default="{ row }"><span class="mono">{{ row.conversationId }}</span></template>
        </el-table-column>
        <el-table-column prop="model" label="模型" min-width="120" />
        <el-table-column label="提示词/生成" width="150" align="right">
          <template #default="{ row }">
            <span class="mono">{{ row.promptTokens }} / {{ row.completionTokens }}</span>
          </template>
        </el-table-column>
        <el-table-column label="耗时(ms)" width="100" align="right">
          <template #default="{ row }"><span class="mono">{{ row.latencyMs }}</span></template>
        </el-table-column>
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tooltip v-if="row.exception" :content="row.exception" placement="top">
              <StatusStamp status="DOWN" />
            </el-tooltip>
            <StatusStamp v-else status="UP" />
          </template>
        </el-table-column>
      </el-table>
      <div class="mt-3 flex justify-end">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[5, 10, 20]"
          layout="total, sizes, prev, pager, next"
          small
          @current-change="onPage"
          @size-change="onSize"
        />
      </div>
    </div>
  </div>
</template>