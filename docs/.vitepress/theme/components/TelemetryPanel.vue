<script setup>
/**
 * 首页尾部的实时功率面板（示意数据）。
 * 数值按 QPT 实测量级做随机游走，纯前端演示，不联网、不读设备。
 */
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'

// 双语标签：中文页用默认值，英文页传入 locale="en"
const props = defineProps({
  locale: { type: String, default: 'zh' },
})

const L = {
  zh: {
    title: 'SoC 分区功率',
    live: '实时',
    total: 'SoC 总功率',
    note: '示意数据 · 实际以设备读取结果为准',
    aria: '实时功耗面板示意（非真实数据）',
  },
  en: {
    title: 'SoC ZONE POWER',
    live: 'LIVE',
    total: 'SoC total',
    note: 'Illustrative data · actual values come from your device',
    aria: 'Live power panel illustration (not real data)',
  },
}

const t = computed(() => L[props.locale] ?? L.zh)

const SCALE_MAX = 4500 // 柱条满刻度（mW）

const zones = reactive([
  { key: 'soc', name: 'SOC', value: 2431, swing: 170, min: 1400, max: 4200 },
  { key: 'cpu-l', name: 'CPU-L', value: 862, swing: 240, min: 200, max: 2600 },
  { key: 'cpu-m', name: 'CPU-M', value: 341, swing: 90, min: 80, max: 900 },
  { key: 'gpu', name: 'GPU', value: 1187, swing: 420, min: 120, max: 3800 },
  { key: 'nsp', name: 'NSP', value: 178, swing: 50, min: 40, max: 520 },
])

const gpuMHz = ref(845)
const fps = ref(120)
const temp = ref(41.3)

const total = computed(() => zones.reduce((sum, z) => sum + z.value, 0))

const fmt = (n) => Math.round(n).toLocaleString('zh-CN')

const walk = (z) =>
  Math.min(z.max, Math.max(z.min, z.value + (Math.random() - 0.5) * 2 * z.swing))

const tick = () => {
  for (const z of zones) z.value = walk(z)

  gpuMHz.value = Math.min(985, Math.max(320, gpuMHz.value + (Math.random() - 0.5) * 160))
  fps.value = Math.random() < 0.9 ? 120 : 119 + Math.round(Math.random() * 2)
  temp.value = Math.min(44.5, Math.max(38.5, temp.value + (Math.random() - 0.5) * 0.6))
}

let timer = null

onMounted(() => {
  // 尊重系统减少动效设置：静态渲染一次，不启动定时器
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  if (reduced) return
  timer = window.setInterval(tick, 1200)
})

onUnmounted(() => {
  if (timer !== null) window.clearInterval(timer)
})
</script>

<template>
  <section class="telemetry" :aria-label="t.aria">
    <div class="telemetry-panel">
      <header class="panel-head">
        <span class="panel-title">{{ t.title }}</span>
        <span class="live"><span class="live-dot" aria-hidden="true" />{{ t.live }}</span>
      </header>

      <div class="panel-total">
        <span class="total-label">{{ t.total }}</span>
        <span class="total-value">{{ (total / 1000).toFixed(2) }}<span class="unit"> W</span></span>
      </div>

      <div class="zones">
        <div v-for="z in zones" :key="z.key" class="zone">
          <span class="zone-name">{{ z.name }}</span>
          <span class="zone-track">
            <span
              class="zone-fill"
              :style="{ width: Math.min(100, (z.value / SCALE_MAX) * 100).toFixed(1) + '%' }"
            />
          </span>
          <span class="zone-val">{{ fmt(z.value) }} mW</span>
        </div>
      </div>

      <div class="panel-stats">
        <div class="pstat"><span class="pstat-v">{{ Math.round(gpuMHz) }}</span><span class="pstat-l">GPU MHz</span></div>
        <div class="pstat"><span class="pstat-v">{{ fps }}</span><span class="pstat-l">FPS</span></div>
        <div class="pstat"><span class="pstat-v">{{ temp.toFixed(1) }}</span><span class="pstat-l">CPU ℃</span></div>
      </div>

      <p class="panel-note">{{ t.note }}</p>
    </div>
  </section>
</template>

<style scoped>
.telemetry {
  max-width: 620px;
  margin: 8px auto 56px;
  padding: 0 24px;
}

.telemetry-panel {
  position: relative;
  border: 1px solid var(--vp-c-divider);
  border-radius: 16px;
  background:
    repeating-linear-gradient(0deg, transparent 0 27px, rgba(127, 127, 127, 0.055) 27px 28px),
    repeating-linear-gradient(90deg, transparent 0 27px, rgba(127, 127, 127, 0.055) 27px 28px),
    var(--vp-c-bg-soft);
  padding: 22px 24px 16px;
  overflow: hidden;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.panel-title {
  font-family: var(--vp-font-family-mono);
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.14em;
  color: var(--vp-c-text-2);
}

.live {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-family: var(--vp-font-family-mono);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.16em;
  color: var(--vp-c-brand-1);
}

.live-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--vp-c-brand-1);
  animation: livePulse 2.2s ease-out infinite;
}

@keyframes livePulse {
  0% { box-shadow: 0 0 0 0 var(--vp-c-brand-soft); }
  70% { box-shadow: 0 0 0 9px transparent; }
  100% { box-shadow: 0 0 0 0 transparent; }
}

.panel-total {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--vp-c-divider);
  margin-bottom: 16px;
}

.total-label {
  font-size: 13px;
  color: var(--vp-c-text-2);
}

.total-value {
  font-family: var(--vp-font-family-mono);
  font-size: 40px;
  font-weight: 600;
  line-height: 1;
  font-variant-numeric: tabular-nums;
  color: var(--vp-c-text-1);
}

.total-value .unit {
  font-size: 16px;
  font-weight: 500;
  color: var(--vp-c-text-2);
  margin-left: 4px;
}

.zones {
  display: grid;
  gap: 12px;
}

.zone {
  display: grid;
  grid-template-columns: 52px 1fr 76px;
  align-items: center;
  gap: 12px;
}

.zone-name {
  font-family: var(--vp-font-family-mono);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: var(--vp-c-text-2);
}

.zone-track {
  height: 10px;
  border-radius: 1000px;
  background: var(--vp-c-default-soft);
  overflow: hidden;
}

.zone-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--vp-c-brand-2), var(--vp-c-brand-3));
  transition: width 0.9s cubic-bezier(0.22, 1, 0.36, 1);
}

.zone-val {
  font-family: var(--vp-font-family-mono);
  font-size: 12.5px;
  font-variant-numeric: tabular-nums;
  color: var(--vp-c-text-2);
  text-align: right;
}

.panel-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  border-top: 1px solid var(--vp-c-divider);
  margin-top: 16px;
  padding-top: 12px;
}

.pstat {
  display: grid;
  gap: 2px;
  text-align: center;
}

.pstat + .pstat {
  border-left: 1px solid var(--vp-c-divider);
}

.pstat-v {
  font-family: var(--vp-font-family-mono);
  font-size: 18px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--vp-c-text-1);
}

.pstat-l {
  font-family: var(--vp-font-family-mono);
  font-size: 10.5px;
  letter-spacing: 0.12em;
  color: var(--vp-c-text-3);
}

.panel-note {
  margin: 12px 0 0;
  text-align: right;
  font-family: var(--vp-font-family-mono);
  font-size: 11px;
  letter-spacing: 0.06em;
  color: var(--vp-c-text-3);
}

@media (max-width: 480px) {
  .total-value { font-size: 32px; }
  .zone { grid-template-columns: 46px 1fr 70px; gap: 8px; }
}

@media (prefers-reduced-motion: reduce) {
  .live-dot { animation: none; }
  .zone-fill { transition: none; }
}
</style>
