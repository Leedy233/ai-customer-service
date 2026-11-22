<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { notifyError } from '@/api/http'
import * as knowledge from '@/api/admin/knowledge'
import PageHead from '@/components/admin/PageHead.vue'
import StatusStamp from '@/components/admin/StatusStamp.vue'
import type { KnowledgeDetail, KnowledgeDocVO } from '@/types/admin'
import { fmtTime } from '@/utils/admin-format'

const rows = ref<KnowledgeDocVO[]>([])
const loading = ref(false)

async function load(): Promise<void> {
  loading.value = true
  try {
    rows.value = await knowledge.list()
  } catch (e) {
    notifyError(e)
  } finally {
    loading.value = false
  }
}

const editOpen = ref(false)
const editId = ref<number | null>(null)
const form = ref({ title: '', content: '' })
const saving = ref(false)

function openCreate(): void {
  editId.value = null
  form.value = { title: '', content: '' }
  editOpen.value = true
}

async function openEdit(row: KnowledgeDocVO): Promise<void> {
  try {
    const d = await knowledge.detail(row.id)
    editId.value = row.id
    form.value = { title: d.title, content: d.content }
    editOpen.value = true
  } catch (e) {
    notifyError(e)
  }
}

async function save(): Promise<void> {
  if (!form.value.title.trim() || !form.value.content.trim()) {
    ElMessage.warning('标题与内容不能为空')
    return
  }
  saving.value = true
  try {
    if (editId.value == null) {
      await knowledge.create({ title: form.value.title.trim(), content: form.value.content.trim() })
      ElMessage.success('已创建并同步向量库')
    } else {
      await knowledge.update(editId.value, { title: form.value.title.trim(), content: form.value.content.trim() })
      ElMessage.success('已更新并同步向量库')
    }
    editOpen.value = false
    void load()
  } catch (e) {
    notifyError(e)
  } finally {
    saving.value = false
  }
}

async function onToggle(row: KnowledgeDocVO): Promise<void> {
  try {
    const res = await knowledge.toggle(row.id)
    ElMessage.success(res.enabled ? '已启用并重建向量' : '已停用并移除向量')
    void load()
  } catch (e) {
    notifyError(e)
    void load()
  }
}

async function onDelete(row: KnowledgeDocVO): Promise<void> {
  try {
    await ElMessageBox.confirm(`删除知识文档「${row.title}」？其向量 chunk 将一并移除。`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await knowledge.remove(row.id)
    ElMessage.success('已删除')
    void load()
  } catch (e) {
    notifyError(e)
  }
}

const chunksOpen = ref(false)
const chunks = ref<KnowledgeDetail | null>(null)

async function openChunks(row: KnowledgeDocVO): Promise<void> {
  try {
    chunks.value = await knowledge.detail(row.id)
    chunksOpen.value = true
  } catch (e) {
    notifyError(e)
  }
}

onMounted(load)
</script>

<template>
  <div class="flex flex-col gap-4">
    <PageHead title="RAG 知识库" desc="知识源存 DB，变更实时同步本地 ONNX 向量库；停用即从检索中移除。">
      <template #actions>
        <el-button size="small" :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button size="small" type="primary" :icon="Plus" @click="openCreate">新建文档</el-button>
      </template>
    </PageHead>

    <div class="rounded-xl border border-line bg-card p-4">
      <el-table v-loading="loading" :data="rows" size="small" empty-text="暂无知识文档">
        <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
        <el-table-column label="来源" min-width="90">
          <template #default="{ row }"><StatusStamp :status="row.source" /></template>
        </el-table-column>
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled" size="small" aria-label="启用开关" @change="onToggle(row)" />
          </template>
        </el-table-column>
        <el-table-column label="chunk 数" width="90" align="right">
          <template #default="{ row }"><span class="mono">{{ row.chunkCount }}</span></template>
        </el-table-column>
        <el-table-column label="字数" width="80" align="right">
          <template #default="{ row }"><span class="mono text-ink-3">{{ row.contentLength }}</span></template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="140">
          <template #default="{ row }"><span class="mono text-ink-3">{{ fmtTime(row.updatedAt) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openChunks(row)">chunk</el-button>
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog
      v-model="editOpen"
      :title="editId == null ? '新建知识文档' : '编辑知识文档'"
      width="min(640px, calc(100vw - 32px))"
    >
      <el-input v-model="form.title" placeholder="标题" maxlength="100" aria-label="文档标题" />
      <el-input
        v-model="form.content"
        type="textarea"
        :rows="12"
        placeholder="知识内容（markdown 纯文本），保存后自动切 chunk 并写入向量库"
        class="mt-3"
        aria-label="文档内容"
      />
      <template #footer>
        <el-button @click="editOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存并同步</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="chunksOpen" title="chunk 预览" size="min(520px, 92vw)">
      <div v-if="chunks" class="space-y-3">
        <p class="text-xs text-ink-3">{{ chunks.title }} · 共 {{ chunks.chunks.length }} 个 chunk</p>
        <div
          v-for="(c, i) in chunks.chunks"
          :key="i"
          class="rounded-lg border border-line bg-surface p-3 text-xs leading-5 text-ink-2 whitespace-pre-wrap"
        >
          <span class="mono mr-2 text-ink-3">#{{ i + 1 }}</span>{{ c }}
        </div>
        <p v-if="!chunks.chunks.length" class="text-xs text-ink-3">文档已停用或内容为空，无 chunk。</p>
      </div>
    </el-drawer>
  </div>
</template>