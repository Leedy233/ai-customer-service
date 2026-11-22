<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lock, User } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { isApiError } from '@/utils/errors'

const user = useUserStore()
const router = useRouter()
const route = useRoute()

const formRef = ref<FormInstance>()
const loading = ref(false)
const errorMsg = ref('')
const form = reactive({ username: '', password: '' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

onMounted(() => {
  // 已登录误入登录页：回 redirect 目标（守卫会再做权限校验），无目标回用户端
  if (user.isLoggedIn) void router.replace((route.query.redirect as string) || '/chat')
})

async function submit(): Promise<void> {
  if (!formRef.value) return
  await formRef.value.validate().catch(() => undefined)
  if (!form.username || !form.password) return
  loading.value = true
  errorMsg.value = ''
  try {
    await user.login(form.username, form.password)
    const redirect = (route.query.redirect as string) || '/chat'
    void router.push(redirect)
  } catch (e) {
    errorMsg.value = isApiError(e) ? e.message : '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="flex min-h-dvh bg-white">
    <!-- 品牌主视觉（桌面端）：浅底 + 线性插画，紫色仅点缀 -->
    <section
      class="relative hidden flex-1 flex-col justify-between overflow-hidden border-r border-line bg-surface p-12 lg:flex"
    >
      <div class="relative flex items-center gap-3">
        <span class="flex h-10 w-10 items-center justify-center rounded-xl bg-brand-50">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path
              d="M7 10.5a5 5 0 0 1 10 0v3.2a2.3 2.3 0 0 1-2.3 2.3H12"
              stroke="#6D5BD0"
              stroke-width="1.6"
              stroke-linecap="round"
            />
            <rect x="5.4" y="10.6" width="2.6" height="4.4" rx="1.3" fill="#6D5BD0" />
            <rect x="16" y="10.6" width="2.6" height="4.4" rx="1.3" fill="#6D5BD0" />
            <circle cx="11.4" cy="16" r="1.2" fill="#5847B8" />
          </svg>
        </span>
        <span class="text-lg font-semibold tracking-wide text-ink">智能客服工作台</span>
      </div>
      <div class="relative max-w-lg">
        <h1 class="text-4xl font-semibold leading-snug text-ink">
          让每一次售后对话<br />都更快、更准、更可信
        </h1>
        <p class="mt-5 text-base leading-relaxed text-ink-2">
          订单查询、物流跟踪、退款政策与售后问答一站式处理；
          资金操作二次确认，对话全程可回溯。
        </p>
        <ul class="mt-8 space-y-3 text-sm text-ink-2">
          <li class="flex items-center gap-2">
            <span class="h-1.5 w-1.5 rounded-full bg-brand-500" />会话记忆持久化，重启不丢上下文
          </li>
          <li class="flex items-center gap-2">
            <span class="h-1.5 w-1.5 rounded-full bg-brand-500" />订单归属校验，越权查询自动拒绝
          </li>
          <li class="flex items-center gap-2">
            <span class="h-1.5 w-1.5 rounded-full bg-brand-500" />资金操作二次确认，安全可审计
          </li>
        </ul>
      </div>
      <p class="relative text-xs text-ink-3">© 2026 智能客服工作台</p>
    </section>

    <!-- 登录表单 -->
    <section class="flex flex-1 items-center justify-center px-6 py-10">
      <div class="w-full max-w-md">
        <div class="mb-8 flex items-center gap-3 lg:hidden">
          <span class="flex h-10 w-10 items-center justify-center rounded-xl bg-brand-50 text-lg font-bold text-brand-600">
            AI
          </span>
          <span class="text-lg font-semibold text-ink">智能客服工作台</span>
        </div>
        <h2 class="text-2xl font-semibold text-ink">欢迎回来</h2>
        <p class="mt-2 text-sm text-ink-3">登录以继续你的智能客服工作台</p>

        <el-alert
          v-if="route.query.reason === 'expired'"
          class="mt-5"
          type="warning"
          title="登录已过期，请重新登录"
          show-icon
          :closable="false"
        />
        <el-alert
          v-if="errorMsg"
          class="mt-5"
          type="error"
          :title="errorMsg"
          show-icon
          @close="errorMsg = ''"
        />

        <el-form
          ref="formRef"
          class="mt-6"
          :model="form"
          :rules="rules"
          size="large"
          @submit.prevent="submit"
        >
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" clearable />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              :prefix-icon="Lock"
              show-password
              @keyup.enter="submit"
            />
          </el-form-item>
          <el-button
            class="w-full"
            size="large"
            type="primary"
            :loading="loading"
            @click="submit"
          >
            登录
          </el-button>
        </el-form>
      </div>
    </section>
  </div>
</template>