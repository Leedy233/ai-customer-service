<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Delete, EditPen, Plus, Search } from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'
import { useConversationStore } from '@/stores/conversation'
import * as contentApi from '@/api/user/content'
import { groupOf, listTime, summarize, type ConversationGroup } from '@/utils/conversation-group'
import { truncate } from '@/utils/format'
import EmptyState from '@/components/common/EmptyState.vue'

const app = useAppStore()
const conversations = useConversationStore()
const router = useRouter()

const query = ref('')

const filtered = computed(() => {
  const q = query.value.trim().toLowerCase()
  const all = conversations.sorted()
  return q ? all.filter((c) => c.title.toLowerCase().includes(q)) : all
})

const groups = computed(() => {
  const order: ConversationGroup[] = ['今天', '昨天', '更早']
  const map = new Map<ConversationGroup, typeof filtered.value>()
  for (const c of filtered.value) {
    const g = groupOf(c.updatedAt)
    if (!map.has(g)) map.set(g, [])
    map.get(g)!.push(c)
  }
  return order.filter((g) => map.has(g)).map((g) => ({ label: g, items: map.get(g)! }))
})

function newChat(): void {
  const conv = conversations.create()
  app.sidebarOpen = false
  void router.push(`/chat/${conv.id}`)
}

function open(id: string): void {
  conversations.select(id)
  app.sidebarOpen = false
  void router.push(`/chat/${id}`)
}

async function remove(id: string): Promise<void> {
  // 真删除走服务端（清消息+墓碑）；失败回退本地隐藏
  await conversations.removeRemote(id)
  if (!conversations.currentId) void router.push('/chat')
}

async function rename(id: string, current: string): Promise<void> {
  try {
    const { value } = await ElMessageBox.prompt('请输入新的会话名称（20 字以内）', '重命名会话', {
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputValue: current,
      inputValidator: (v: string) => (v && v.trim().length > 0 && v.trim().length <= 20) || '名称需为 1–20 个字符',
    })
    conversations.rename(id, value)
  } catch {
    // 用户取消
  }
}

/** 摘要：拉取历史消息取最后一条非工具消息首行（服务端无摘要字段，前端推导） */
async function loadSummaries(): Promise<void> {
  for (const c of conversations.sorted()) {
    if (conversations.summaries[c.id]) continue
    try {
      const ms = await contentApi.messages(c.id)
      const text = summarize(ms.filter((m) => m.role !== 'tool').map((m) => m.content))
      conversations.setSummary(c.id, truncate(text, 30))
    } catch {
      // 失败不写缓存，下次挂载重试；避免限流期间把空摘要固化
    }
  }
}

onMounted(() => void loadSummaries())
watch(
  () => conversations.list.length,
  () => void loadSummaries(),
)
</script>

<template>
  <nav class="flex h-full flex-col bg-surface" aria-label="会话列表">
    <div class="space-y-2 p-3 pb-2">
      <button
        class="flex h-10 w-full items-center justify-center gap-1.5 rounded-lg bg-brand-500 text-sm font-medium text-white transition hover:bg-brand-600"
        @click="newChat"
      >
        <el-icon :size="16"><Plus /></el-icon>
        新建会话
      </button>
      <el-input
        v-model="query"
        placeholder="搜索会话"
        aria-label="搜索会话"
        :prefix-icon="Search"
        clearable
        size="default"
      />
    </div>

    <div class="min-h-0 flex-1 overflow-y-auto px-2 pb-3">
      <!-- 骨架屏 -->
      <div v-if="conversations.listLoading" class="space-y-2 px-1 pt-1" aria-label="会话加载中">
        <div v-for="i in 5" :key="i" class="rounded-lg bg-card p-3">
          <div class="skeleton h-3.5 w-3/4" />
          <div class="skeleton mt-2 h-3 w-1/2" />
        </div>
      </div>

      <template v-else>
        <section v-for="g in groups" :key="g.label" class="mt-2 first:mt-0">
          <div class="flex items-baseline px-2 pb-1 pt-2">
            <h3 class="text-xs text-ink-3">{{ g.label }}</h3>
            <span class="mono ml-auto text-xs text-ink-3">{{ g.items.length }}</span>
          </div>
          <ul class="m-0 list-none space-y-0.5 p-0">
            <li v-for="conv in g.items" :key="conv.id">
              <div
                class="group relative flex cursor-pointer items-center gap-2 rounded-lg px-3 py-2.5 transition"
                :class="
                  conv.id === conversations.currentId
                    ? 'bg-brand-50'
                    : 'hover:bg-card'
                "
                role="button"
                tabindex="0"
                :aria-current="conv.id === conversations.currentId ? 'true' : undefined"
                @click="open(conv.id)"
                @keydown.enter="open(conv.id)"
              >
                <span
                  v-if="conv.id === conversations.currentId"
                  class="absolute left-0 top-2 bottom-2 w-[3px] rounded-full bg-brand-500"
                />
                <div class="min-w-0 flex-1">
                  <div
                    class="truncate text-sm"
                    :class="conv.id === conversations.currentId ? 'font-medium text-brand-700' : 'text-ink'"
                  >
                    {{ conv.title }}
                  </div>
                  <div class="mt-0.5 flex items-center gap-2">
                    <span class="min-w-0 flex-1 truncate text-xs text-ink-3">
                      {{ conversations.summaries[conv.id] || '暂无消息' }}
                    </span>
                    <span class="mono shrink-0 text-xs text-ink-3">{{ listTime(conv.updatedAt) }}</span>
                  </div>
                </div>
                <div
                  class="flex shrink-0 items-center opacity-0 transition group-hover:opacity-100 group-focus-within:opacity-100"
                >
                  <button
                    class="tap-target flex h-7 w-7 items-center justify-center rounded-md text-ink-3 hover:bg-brand-100 hover:text-brand-600"
                    aria-label="重命名会话"
                    @click.stop="rename(conv.id, conv.title)"
                  >
                    <el-icon :size="14"><EditPen /></el-icon>
                  </button>
                  <el-popconfirm
                    title="删除该会话？删除后不再出现在列表中"
                    confirm-button-text="删除"
                    cancel-button-text="取消"
                    width="220"
                    @confirm="remove(conv.id)"
                  >
                    <template #reference>
                      <button
                        class="tap-target flex h-7 w-7 items-center justify-center rounded-md text-ink-3 hover:bg-red-50 hover:text-danger"
                        aria-label="删除会话"
                        @click.stop
                      >
                        <el-icon :size="14"><Delete /></el-icon>
                      </button>
                    </template>
                  </el-popconfirm>
                </div>
              </div>
            </li>
          </ul>
        </section>

        <EmptyState
          v-if="filtered.length === 0 && query.trim()"
          title="没有找到相关会话"
          description="换个关键词试试"
        />
        <EmptyState
          v-else-if="filtered.length === 0"
          title="暂无会话"
          description="点击上方按钮开始第一段对话"
        />
      </template>
    </div>
  </nav>
</template>