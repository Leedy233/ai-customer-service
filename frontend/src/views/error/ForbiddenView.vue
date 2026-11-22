<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const user = useUserStore()
</script>

<template>
  <div class="flex h-screen items-center justify-center bg-gray-50 p-6">
    <div class="w-full max-w-md rounded-2xl border border-gray-200/70 bg-white p-8 text-center shadow-sm">
      <div class="bg-brand-gradient mx-auto flex h-14 w-14 items-center justify-center rounded-2xl text-xl font-bold text-white">
        403
      </div>
      <h1 class="mt-4 text-lg font-semibold text-gray-800">无管理端权限</h1>
      <p class="mt-2 text-sm text-gray-500">
        当前账号（{{ user.profile?.username ?? '未登录' }}）不是管理员。管理端接口由后端强制校验，无法通过前端绕过。
      </p>
      <div class="mt-6 flex justify-center gap-3">
        <el-button @click="router.push('/chat')">返回用户端</el-button>
        <el-button type="primary" class="bg-brand-gradient" @click="router.push('/login')">
          切换账号
        </el-button>
      </div>
    </div>
  </div>
</template>