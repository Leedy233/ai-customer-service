<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { notifyError } from '@/api/http'
import * as manage from '@/api/admin/manage'
import PageHead from '@/components/admin/PageHead.vue'
import StatusStamp from '@/components/admin/StatusStamp.vue'
import { usePage } from '@/composables/admin/usePage'
import type { AdminOrderDetail, AdminOrderVO } from '@/types/admin'
import { formatMoney } from '@/utils/format'
import { fmtTime } from '@/utils/admin-format'

const orderNo = ref('')
const status = ref('')
const STATUSES = ['待发货', '已发货', '已完成', '退款中', '已退款']

const { rows, total, page, size, loading, load, reload, onPage } = usePage<AdminOrderVO>((p, s) =>
  manage.orders({ orderNo: orderNo.value || undefined, status: status.value || undefined, page: p, size: s }),
)

const detailOpen = ref(false)
const detail = ref<AdminOrderDetail | null>(null)

async function openDetail(no: string): Promise<void> {
  try {
    detail.value = await manage.orderDetail(no)
    detailOpen.value = true
  } catch (e) {
    notifyError(e)
  }
}

async function onShip(row: AdminOrderVO): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认将订单 ${row.orderNo} 标记为已发货？`, '发货确认', {
      confirmButtonText: '确认发货',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await manage.ship(row.orderNo)
    ElMessage.success(`订单 ${row.orderNo} 已发货`)
    void load()
  } catch (e) {
    notifyError(e)
  }
}

onMounted(load)
</script>

<template>
  <div class="flex flex-col gap-4">
    <PageHead title="订单管理" desc="全量订单真值（DB），发货为唯一写操作；金额以数据库为准。">
      <template #actions>
        <el-button size="small" :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHead>

    <div class="flex flex-wrap items-center gap-2 rounded-xl border border-line bg-card p-3">
      <el-input
        v-model="orderNo"
        placeholder="订单号"
        size="small"
        class="!w-40"
        clearable
        aria-label="按订单号筛选"
        @keyup.enter="reload"
      />
      <el-select v-model="status" placeholder="全部状态" size="small" class="!w-32" clearable aria-label="按状态筛选" @change="reload">
        <el-option v-for="s in STATUSES" :key="s" :label="s" :value="s" />
      </el-select>
      <el-button size="small" type="primary" :icon="Search" @click="reload">查询</el-button>
    </div>

    <div class="rounded-xl border border-line bg-card p-4">
      <el-table v-loading="loading" :data="rows" size="small" empty-text="暂无订单">
        <el-table-column label="订单号" min-width="110">
          <template #default="{ row }"><span class="mono">{{ row.orderNo }}</span></template>
        </el-table-column>
        <el-table-column prop="username" label="用户" min-width="90" />
        <el-table-column prop="productName" label="商品" min-width="140" show-overflow-tooltip />
        <el-table-column label="金额" min-width="90" align="right">
          <template #default="{ row }"><span class="mono font-semibold">{{ formatMoney(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" min-width="90">
          <template #default="{ row }"><StatusStamp :status="row.status" /></template>
        </el-table-column>
        <el-table-column label="下单时间" min-width="140">
          <template #default="{ row }"><span class="mono text-ink-3">{{ fmtTime(row.createTime) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row.orderNo)">详情</el-button>
            <el-button
              v-if="row.status === '待发货'"
              link
              type="warning"
              size="small"
              @click="onShip(row)"
            >
              发货
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

    <el-drawer v-model="detailOpen" title="订单详情" size="min(420px, 92vw)">
      <div v-if="detail" class="space-y-4">
        <div class="receipt p-4">
          <div class="flex items-center gap-2">
            <span class="mono text-sm font-semibold text-ink">{{ detail.orderNo }}</span>
            <StatusStamp class="ml-auto" :status="detail.status" />
          </div>
          <dl class="receipt-perf mt-3 space-y-2 pt-3 text-xs">
            <div class="flex justify-between"><dt class="text-ink-3">用户</dt><dd class="text-ink-2">{{ detail.username }}</dd></div>
            <div class="flex justify-between"><dt class="text-ink-3">商品</dt><dd class="text-ink-2">{{ detail.productName }}</dd></div>
            <div class="flex justify-between"><dt class="text-ink-3">金额</dt><dd class="mono font-semibold text-ink">{{ formatMoney(detail.amount) }}</dd></div>
            <div class="flex justify-between"><dt class="text-ink-3">下单时间</dt><dd class="mono text-ink-2">{{ fmtTime(detail.createTime) }}</dd></div>
          </dl>
        </div>
        <div>
          <h3 class="text-sm font-semibold text-ink">退款记录</h3>
          <ul v-if="detail.refunds.length" class="m-0 mt-2 list-none space-y-2 p-0">
            <li v-for="r in detail.refunds" :key="r.id" class="flex items-center gap-2 text-xs">
              <span class="mono text-ink-2">#{{ r.id }}</span>
              <span class="mono text-ink-2">{{ formatMoney(r.amount) }}</span>
              <StatusStamp :status="r.status" />
              <span class="mono ml-auto text-ink-3">{{ fmtTime(r.createdAt) }}</span>
            </li>
          </ul>
          <p v-else class="mt-2 text-xs text-ink-3">暂无退款记录</p>
        </div>
      </div>
    </el-drawer>
  </div>
</template>