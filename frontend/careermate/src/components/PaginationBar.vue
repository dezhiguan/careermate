<template>
  <nav class="pgbar" aria-label="分页导航">
    <span class="pg-info">
      第 <b>{{ rangeStart }}–{{ rangeEnd }}</b> 条 / 共 <b>{{ total }}</b> 条
    </span>
    <span class="pg-ctrl">
      <label class="pg-size">
        每页
        <select :value="size" @change="onSizeChange">
          <option v-for="s in sizeOptions" :key="s" :value="s">{{ s }}</option>
        </select>
        条
      </label>
      <button type="button" class="pg-btn" :disabled="page <= 1" aria-label="上一页" @click="go(page - 1)">‹</button>
      <button
        v-for="(p, i) in pageWindow"
        :key="`${p}-${i}`"
        type="button"
        class="pg-num"
        :class="{ on: p === page, dots: p === '…' }"
        :disabled="p === '…'"
        @click="go(p)"
      >{{ p }}</button>
      <button type="button" class="pg-btn" :disabled="page >= totalPages" aria-label="下一页" @click="go(page + 1)">›</button>
    </span>
  </nav>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  total: { type: Number, default: 0 },
  page: { type: Number, default: 1 },
  size: { type: Number, default: 10 },
  sizeOptions: { type: Array, default: () => [10, 20, 50] },
})
const emit = defineEmits(['update:page', 'update:size'])

const totalPages = computed(() => Math.max(1, Math.ceil((props.total || 0) / (props.size || 10))))
const rangeStart = computed(() => (props.total === 0 ? 0 : (props.page - 1) * props.size + 1))
const rangeEnd = computed(() => Math.min(props.page * props.size, props.total))

// 页码窗口：首尾 + 当前页两侧，省略号补齐
const pageWindow = computed(() => {
  const tp = totalPages.value
  const cur = props.page
  if (tp <= 7) return Array.from({ length: tp }, (_, i) => i + 1)
  const out = [1]
  const from = Math.max(2, cur - 1)
  const to = Math.min(tp - 1, cur + 1)
  if (from > 2) out.push('…')
  for (let p = from; p <= to; p += 1) out.push(p)
  if (to < tp - 1) out.push('…')
  out.push(tp)
  return out
})

function go(p) {
  if (p === '…' || p < 1 || p > totalPages.value || p === props.page) return
  emit('update:page', p)
}
function onSizeChange(evt) {
  const s = Number(evt?.target?.value) || props.size
  if (s !== props.size) emit('update:size', s)
}
</script>

<style scoped>
.pgbar {
  display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap;
  gap: 10px; padding: 10px 14px; margin: 16px auto 0; max-width: 1100px;
  background: #fff; border: 1px solid var(--line, #e2e8f0); border-radius: 12px;
}
.pg-info { font-size: 13px; color: var(--ink2, #475569); }
.pg-info b { color: var(--ink, #0f172a); font-variant-numeric: tabular-nums; font-weight: 700; }
.pg-ctrl { display: inline-flex; align-items: center; gap: 6px; font-size: 13px; color: var(--ink2, #475569); }
.pg-size { display: inline-flex; align-items: center; gap: 4px; margin-right: 4px; }
.pg-size select {
  font: inherit; font-size: 13px; color: var(--ink, #0f172a); background: var(--bg, #f8fafc);
  border: 1px solid var(--line, #e2e8f0); border-radius: 8px; padding: 3px 6px; cursor: pointer;
}
.pg-btn, .pg-num {
  min-width: 30px; height: 30px; padding: 0 6px; font: inherit; font-size: 13px;
  font-variant-numeric: tabular-nums; color: var(--ink2, #475569);
  background: #fff; border: 1px solid var(--line, #e2e8f0); border-radius: 8px; cursor: pointer;
  display: inline-flex; align-items: center; justify-content: center;
}
.pg-btn:disabled { opacity: .45; cursor: not-allowed; }
.pg-num.on { background: #4E5BEF; border-color: #4E5BEF; color: #fff; font-weight: 700; }
.pg-num.dots { border: none; background: transparent; cursor: default; color: var(--ink3, #94a3b8); }
.pg-btn:not(:disabled):hover, .pg-num:not(.on):not(.dots):hover { border-color: #4E5BEF; color: #4E5BEF; }
</style>
