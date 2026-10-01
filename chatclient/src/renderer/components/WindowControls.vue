<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { Close, CopyDocument, Crop, Minus } from '@element-plus/icons-vue'

const isMaximized = ref(false)
async function toggleMaximize() {
  await window.chatApi.toggleMaximize()
}
async function minimizeWindow() { await window.chatApi.minimizeWindow() }
async function closeWindow() { await window.chatApi.closeWindow() }
let removeMaximizedListener: (() => void) | undefined
onMounted(async () => {
  isMaximized.value = await window.chatApi.isWindowMaximized()
  removeMaximizedListener = window.chatApi.onWindowMaximizedChanged((value) => { isMaximized.value = value })
})
onBeforeUnmount(() => removeMaximizedListener?.())
</script>

<template>
  <div class="window-controls no-drag" @mousedown.stop @dblclick.stop>
    <button type="button" class="window-control-button no-drag" title="最小化" @mousedown.stop @click.stop="minimizeWindow"><el-icon><Minus /></el-icon></button>
    <button type="button" class="window-control-button no-drag" :title="isMaximized ? '还原' : '最大化'" @mousedown.stop @click.stop="toggleMaximize"><el-icon><CopyDocument v-if="isMaximized" /><Crop v-else /></el-icon></button>
    <button type="button" class="window-control-button close-control no-drag" title="关闭" @mousedown.stop @click.stop="closeWindow"><el-icon><Close /></el-icon></button>
  </div>
</template>
