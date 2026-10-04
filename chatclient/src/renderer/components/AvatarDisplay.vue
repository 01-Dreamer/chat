<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  src?: string | null
  name?: string | null
  size?: number
  radius?: number
}>(), {
  src: null,
  name: '',
  size: 36,
  radius: 5,
})

const loadFailed = ref(false)
watch(() => props.src, () => { loadFailed.value = false })

const hasImage = computed(() => Boolean(props.src?.trim()) && !loadFailed.value)
const initial = computed(() => Array.from(props.name?.trim() || '?')[0] || '?')
const background = computed(() => {
  const palette = ['#72b7d2', '#8ebf9f', '#b69ddd', '#dfad78', '#7fa6df', '#d88fa3', '#79b6aa', '#ba9b78']
  const hash = Array.from(props.name || '').reduce((total, char) => total + (char.codePointAt(0) ?? 0), 0)
  return palette[hash % palette.length]
})
</script>

<template>
  <span class="smart-avatar" :style="{ width: `${size}px`, height: `${size}px`, flexBasis: `${size}px`, borderRadius: `${radius}px`, backgroundColor: background }" :title="name || '未设置名称'">
    <img v-if="hasImage" :src="src!" :alt="name || '头像'" @error="loadFailed = true" />
    <span v-else class="smart-avatar-fallback" :style="{ fontSize: `${Math.max(12, Math.round(size * 0.42))}px` }">{{ initial }}</span>
  </span>
</template>

<style scoped>
.smart-avatar { display: inline-grid; flex: 0 0 auto; place-items: center; overflow: hidden; color: #fff; vertical-align: middle; }
.smart-avatar img { display: block; width: 100%; height: 100%; margin: 0; border-radius: inherit; object-fit: cover; }
.smart-avatar-fallback { font-weight: 500; line-height: 1; user-select: none; }
</style>
