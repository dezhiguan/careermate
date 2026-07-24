<template>
  <div class="city-picker" ref="rootRef">
    <button
      type="button"
      :class="triggerClass"
      @click.stop="toggle"
    >
      <span class="cp-trigger-label">{{ triggerLabel }}</span>
      <span v-if="variant === 'select'" class="cp-caret" aria-hidden="true">▾</span>
    </button>

    <div v-if="open" class="cp-panel" @click.stop>
      <div class="cp-search">
        <input
          ref="searchRef"
          v-model="q"
          type="text"
          class="cp-input"
          placeholder="搜索城市"
        >
      </div>
      <div class="cp-scroll">
        <button
          type="button"
          class="cp-item cp-any"
          :class="{ on: modelValue === ANY }"
          @click="choose(ANY)"
        >不限</button>

        <template v-if="!q.trim()">
          <div v-if="hotCities.length" class="cp-group">热门城市</div>
          <div v-if="hotCities.length" class="cp-hot">
            <button
              v-for="c in hotCities"
              :key="'hot-' + c"
              type="button"
              class="cp-hot-item"
              :class="{ on: modelValue === c }"
              @click="choose(c)"
            >{{ c }}</button>
          </div>
          <div class="cp-group">全部城市</div>
        </template>

        <button
          v-for="c in filteredCities"
          :key="c"
          type="button"
          class="cp-item"
          :class="{ on: modelValue === c }"
          @click="choose(c)"
        >{{ c }}</button>

        <div v-if="q.trim() && filteredCities.length === 0" class="cp-empty">无匹配城市</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

const ANY = '不限'
// 热门城市优先展示；仅保留后端目录里实际存在的项，顺序固定。
const HOT = ['北京', '上海', '广州', '深圳', '杭州', '成都', '武汉', '南京']

const props = defineProps({
  modelValue: { type: String, default: ANY },
  options: { type: Array, default: () => [] },
  // 'chip'：机会页筛选胶囊；'select'：冷启动表单下拉。
  variant: { type: String, default: 'chip' },
})
const emit = defineEmits(['update:modelValue'])

const open = ref(false)
const q = ref('')
const rootRef = ref(null)
const searchRef = ref(null)

const cities = computed(() => props.options.filter((c) => c && c !== ANY))
const hotCities = computed(() => HOT.filter((c) => cities.value.includes(c)))
const filteredCities = computed(() => {
  const kw = q.value.trim().toLowerCase()
  if (!kw) return cities.value
  return cities.value.filter((c) => c.toLowerCase().includes(kw))
})

const isActive = computed(() => !!props.modelValue && props.modelValue !== ANY)
const triggerLabel = computed(() => {
  if (props.variant === 'chip') {
    return isActive.value ? `城市·${props.modelValue} ✕` : '城市'
  }
  return props.modelValue || ANY
})
const triggerClass = computed(() => {
  if (props.variant === 'chip') return ['cp-trigger', 'cp-chip', { on: isActive.value }]
  return ['cp-trigger', 'cp-select']
})

function toggle() {
  if (open.value) {
    close()
  } else {
    openPanel()
  }
}
function openPanel() {
  open.value = true
  q.value = ''
  nextTick(() => searchRef.value?.focus())
}
function close() {
  open.value = false
}
function choose(city) {
  emit('update:modelValue', city)
  close()
}
function onDocClick(evt) {
  if (rootRef.value && !rootRef.value.contains(evt.target)) close()
}
function onKeydown(evt) {
  if (evt.key === 'Escape') close()
}

onMounted(() => {
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped>
.city-picker { position: relative; display: inline-flex; }

/* 触发器：chip 变体复刻机会页筛选胶囊；select 变体复刻冷启动表单下拉。 */
.cp-trigger {
  display: inline-flex; align-items: center; gap: 5px;
  font-family: inherit; cursor: pointer; white-space: nowrap;
  background: #fff; border: 1px solid var(--line); color: var(--ink2);
}
.cp-chip { font-size: 12px; border-radius: 16px; padding: 4px 12px; }
.cp-chip.on { background: var(--brand-soft); border-color: var(--brand-line); color: var(--brand); font-weight: 600; }
.cp-select {
  font-size: 13px; border-radius: 10px; padding: 7px 12px;
  color: var(--ink); justify-content: space-between; min-width: 120px;
}
.cp-caret { font-size: 10px; color: var(--ink2); }

.cp-panel {
  position: absolute; top: calc(100% + 6px); left: 0; z-index: 40;
  width: 220px; background: #fff; border: 1px solid var(--line);
  border-radius: 12px; box-shadow: 0 8px 28px rgba(20, 24, 40, .14);
  padding: 8px; display: flex; flex-direction: column;
}
.cp-search { padding: 2px 2px 6px; }
.cp-input {
  width: 100%; box-sizing: border-box; font-family: inherit; font-size: 13px;
  padding: 7px 10px; border: 1px solid var(--line); border-radius: 8px;
  color: var(--ink); outline: none;
}
.cp-input:focus { border-color: var(--brand-line); }

/* 定高滚动：城市再多也只占固定高度。 */
.cp-scroll { max-height: 300px; overflow-y: auto; }

.cp-group { font-size: 11px; color: var(--ink2); padding: 8px 8px 4px; }
.cp-hot { display: flex; flex-wrap: wrap; gap: 6px; padding: 2px 4px 6px; }
.cp-hot-item {
  font-family: inherit; font-size: 12px; cursor: pointer;
  background: var(--bg); border: 1px solid var(--line); border-radius: 14px;
  padding: 4px 10px; color: var(--ink2);
}
.cp-hot-item.on { background: var(--brand-soft); border-color: var(--brand-line); color: var(--brand); font-weight: 600; }

.cp-item {
  display: block; width: 100%; text-align: left; font-family: inherit; font-size: 13px;
  cursor: pointer; background: transparent; border: 0; border-radius: 8px;
  padding: 8px 10px; color: var(--ink);
}
.cp-item:hover { background: var(--bg); }
.cp-item.on { color: var(--brand); font-weight: 600; background: var(--brand-soft); }
.cp-any { color: var(--ink2); }

.cp-empty { font-size: 12px; color: var(--ink2); text-align: center; padding: 14px 0; }
</style>
