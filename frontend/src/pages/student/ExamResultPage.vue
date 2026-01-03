<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Close } from '@element-plus/icons-vue'
import * as studentApi from '../../api/student'
import { pickErrorMessage } from '../../api/http'
import type { AiChatMessage, StudentResultItem, StudentResultResponse } from '../../types/api'

const route = useRoute()
const router = useRouter()
const examId = computed(() => Number(route.params.id))

const loading = ref(false)
const data = ref<StudentResultResponse | null>(null)

const aiOpen = ref(false)
const aiSending = ref(false)
const aiInput = ref('')
const aiMessages = ref<AiChatMessage[]>([])
const activeQuestionId = ref<number | null>(null)
const activeQuestionIndex = ref<number | null>(null)
const chatBodyRef = ref<HTMLDivElement | null>(null)
const aiAbortController = ref<AbortController | null>(null)

function typeLabel(type: string) {
  if (type === 'SINGLE_CHOICE') return '单选题'
  if (type === 'MULTIPLE_CHOICE') return '多选题'
  if (type === 'TRUE_FALSE') return '判断题'
  return type
}

function scrollChatToBottom() {
  const el = chatBodyRef.value
  if (!el) return
  el.scrollTop = el.scrollHeight
}

function closeAi() {
  aiAbortController.value?.abort()
  aiOpen.value = false
}

function escapeHtml(s: string) {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function renderInlineMarkdownSafe(s: string) {
  let out = escapeHtml(s)
  out = out.replace(/`([^`]+)`/g, '<code class="md-code">$1</code>')
  out = out.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  return out
}

function renderMarkdownSafe(md: string) {
  if (!md) return ''

  const parts: Array<{ type: 'text' | 'code'; value: string; lang?: string }> = []
  const re = /```([a-zA-Z0-9_-]*)\n([\s\S]*?)```/g
  let last = 0
  let m: RegExpExecArray | null
  while ((m = re.exec(md)) !== null) {
    const start = m.index
    const end = re.lastIndex
    if (start > last) {
      parts.push({ type: 'text', value: md.slice(last, start) })
    }
    parts.push({ type: 'code', value: m[2] ?? '', lang: (m[1] ?? '').trim() })
    last = end
  }
  if (last < md.length) {
    parts.push({ type: 'text', value: md.slice(last) })
  }

  const html: string[] = []
  for (const p of parts) {
    if (p.type === 'code') {
      const code = escapeHtml(p.value.replace(/\n$/, ''))
      html.push(`<pre class="md-pre"><code>${code}</code></pre>`)
      continue
    }

    const lines = String(p.value).replace(/\r\n/g, '\n').split('\n')
    let inList = false
    for (const rawLine of lines) {
      const line = rawLine ?? ''
      const trimmed = line.trim()

      const heading = /^(\#{1,6})\s+(.*)$/.exec(trimmed)
      if (heading) {
        if (inList) {
          html.push('</ul>')
          inList = false
        }
        const hashes = heading[1] ?? ''
        const level = Math.min(6, hashes.length)
        html.push(`<h${level} class="md-h${level}">${renderInlineMarkdownSafe(heading[2] ?? '')}</h${level}>`)
        continue
      }

      const li = /^[-*]\s+(.*)$/.exec(trimmed)
      if (li) {
        if (!inList) {
          html.push('<ul class="md-ul">')
          inList = true
        }
        html.push(`<li class="md-li">${renderInlineMarkdownSafe(li[1] ?? '')}</li>`)
        continue
      }

      if (inList) {
        html.push('</ul>')
        inList = false
      }

      if (!trimmed) {
        html.push('<div class="md-space"></div>')
        continue
      }
      html.push(`<div class="md-p">${renderInlineMarkdownSafe(line)}</div>`)
    }
    if (inList) {
      html.push('</ul>')
    }
  }
  return html.join('')
}

async function sendAiMessage(content: string) {
  if (!activeQuestionId.value) return
  aiAbortController.value?.abort()
  aiAbortController.value = new AbortController()

  aiMessages.value.push({ role: 'user', content })
  await nextTick()
  scrollChatToBottom()

  aiSending.value = true
  try {
    const assistantIndex = aiMessages.value.length
    aiMessages.value.push({ role: 'assistant', content: '' })
    await nextTick()
    scrollChatToBottom()

    await studentApi.explainResultQuestionStream(
      examId.value,
      {
        questionId: activeQuestionId.value,
        messages: aiMessages.value,
      },
      (chunk) => {
        const msg = aiMessages.value[assistantIndex]
        if (!msg) return
        msg.content += chunk
        nextTick().then(scrollChatToBottom)
      },
      aiAbortController.value.signal,
    )
  } catch (e) {
    const msg = pickErrorMessage(e)
    if (msg && msg.toLowerCase().includes('aborted')) {
      return
    }
    aiMessages.value.push({ role: 'assistant', content: `请求失败：${msg}` })
  } finally {
    aiSending.value = false
    await nextTick()
    scrollChatToBottom()
  }
}

async function openAi(item: StudentResultItem, index: number) {
  aiOpen.value = true
  if (activeQuestionId.value !== item.questionId) {
    aiAbortController.value?.abort()
    activeQuestionId.value = item.questionId
    activeQuestionIndex.value = index + 1
    aiMessages.value = []
    aiInput.value = ''
  }
  if (aiMessages.value.length === 0) {
    const stem = item.stem ? String(item.stem) : ''
    await sendAiMessage(`帮我解析这道题（第 ${index + 1} 题）：\n${stem}`)
  }
}

async function sendFollowUp() {
  const text = aiInput.value.trim()
  if (!text) return
  aiInput.value = ''
  await sendAiMessage(text)
}

async function load() {
  loading.value = true
  try {
    data.value = await studentApi.myResult(examId.value)
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="layout">
      <div class="left">
        <el-card class="card" v-loading="loading">
          <div style="display: flex; align-items: center; justify-content: space-between">
            <div style="display: flex; align-items: center; gap: 12px">
              <el-button text @click="router.push('/student/exams')">返回列表</el-button>
              <div style="font-size: 16px; font-weight: 700">我的成绩</div>
            </div>
            <el-button type="primary" plain @click="load">刷新</el-button>
          </div>

          <el-divider />

          <div v-if="data" style="display: grid; gap: 12px">
            <el-row :gutter="12" v-if="data.totalScore >= 0">
              <el-col :span="8">
                <el-statistic title="得分" :value="data.totalScore" />
              </el-col>
              <el-col :span="8">
                <el-statistic title="满分" :value="data.maxScore" />
              </el-col>
              <el-col :span="8">
                <el-statistic
                  title="得分率"
                  :value="data.maxScore === 0 ? 0 : Math.round((data.totalScore / data.maxScore) * 100)"
                  suffix="%"
                />
              </el-col>
            </el-row>
            <div
              v-else
              style="padding: 16px; text-align: center; color: #909399; background: #f5f7fa; border-radius: 4px"
            >
              分数暂不可见
            </div>

            <div class="result-list" v-if="data.items && data.items.length > 0">
              <el-card v-for="(item, index) in data.items" :key="item.questionId" class="result-item" shadow="hover">
                <template #header>
                  <div class="result-header">
                    <div class="header-left">
                      <span class="index">第 {{ index + 1 }} 题</span>
                      <el-tag size="small" type="info">{{ typeLabel(item.questionType) }}</el-tag>
                      <el-tag v-if="item.correctAnswer" :type="item.correct ? 'success' : 'danger'" effect="dark">
                        {{ item.correct ? '正确' : '错误' }}
                      </el-tag>
                    </div>
                    <div class="header-right">
                      <span class="score" v-if="data.totalScore >= 0">得分: {{ item.earnedScore }} / {{ item.maxScore }}</span>
                      <el-button
                        size="small"
                        type="primary"
                        plain
                        class="ai-btn"
                        :disabled="!item.correctAnswer"
                        @click="openAi(item, index)"
                      >
                        AI解析
                      </el-button>
                    </div>
                  </div>
                </template>

                <div class="question-content">
                  <div class="stem">{{ item.stem || '题目内容未加载' }}</div>

                  <div v-if="item.options && item.options.length > 0" class="options">
                    <div v-for="(opt, idx) in item.options" :key="idx" class="option">
                      <span class="option-label">{{ String.fromCharCode(65 + idx) }}.</span>
                      <span class="option-text">{{ opt }}</span>
                    </div>
                  </div>

                  <div v-if="item.correctAnswer">
                    <el-divider content-position="left">答案解析</el-divider>

                    <div class="answer-comparison">
                      <div class="answer-box my-answer" :class="{ 'is-error': !item.correct }">
                        <div class="label">我的答案</div>
                        <div class="value">{{ item.answer || '(未作答)' }}</div>
                      </div>
                      <div class="answer-box correct-answer">
                        <div class="label">正确答案</div>
                        <div class="value">{{ item.correctAnswer }}</div>
                      </div>
                    </div>
                  </div>
                  <div v-else style="margin-top: 12px; color: #909399; font-size: 13px">(答案解析暂不可见)</div>
                </div>
              </el-card>
            </div>
            <div v-else style="padding: 32px; text-align: center; color: #909399">暂无题目详情或不允许查看试卷</div>
          </div>
        </el-card>
      </div>

      <transition name="ai-panel">
        <div v-if="aiOpen" class="ai-panel">
          <div class="ai-panel-header">
            <div>
              <div class="ai-title">AI 解析</div>
              <div class="ai-subtitle" v-if="activeQuestionIndex">第 {{ activeQuestionIndex }} 题</div>
            </div>
            <el-button
              circle
              text
              :icon="Close"
              @click="closeAi"
            />
          </div>

          <div class="ai-panel-body" ref="chatBodyRef">
            <div v-if="aiMessages.length === 0" class="ai-empty">
              <div class="ai-empty-title">点击任意题目的 AI解析</div>
              <div class="ai-empty-sub">我会结合你的作答和正确答案进行讲解</div>
            </div>

            <div v-for="(m, idx) in aiMessages" :key="idx" class="msg" :class="m.role">
              <div class="bubble" v-html="renderMarkdownSafe(m.content)"></div>
            </div>

            <div v-if="aiSending" class="msg assistant">
              <div class="bubble thinking">正在思考…</div>
            </div>
          </div>

          <div class="ai-panel-input">
            <el-input
              v-model="aiInput"
              type="textarea"
              :autosize="{ minRows: 1, maxRows: 4 }"
              placeholder="继续追问，例如：为什么 B 不能选？"
              :disabled="aiSending || !activeQuestionId"
              @keydown.enter.exact.prevent="sendFollowUp"
            />
            <el-button
              type="primary"
              :loading="aiSending"
              :disabled="aiSending || !activeQuestionId || !aiInput.trim()"
              class="send-btn"
              @click="sendFollowUp"
            >
              发送
            </el-button>
          </div>
        </div>
      </transition>
    </div>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.left {
  flex: 1;
  min-width: 0;
}
.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.index {
  font-weight: bold;
}
.stem {
  font-size: 16px;
  margin-bottom: 16px;
  line-height: 1.5;
}
.options {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
}
.option {
  display: flex;
  gap: 8px;
  padding: 8px;
  background-color: #f5f7fa;
  border-radius: 4px;
}
.option-label {
  font-weight: bold;
}
.answer-comparison {
  display: flex;
  gap: 24px;
}
.answer-box {
  flex: 1;
  padding: 12px;
  border-radius: 4px;
  background-color: #f0f9eb;
  border: 1px solid #e1f3d8;
}
.answer-box.is-error {
  background-color: #fef0f0;
  border-color: #fde2e2;
}
.answer-box .label {
  font-size: 12px;
  color: #606266;
  margin-bottom: 4px;
}
.answer-box .value {
  font-weight: bold;
  font-size: 16px;
}
.result-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 16px;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.ai-btn {
  border-radius: 10px;
}
.ai-panel {
  width: 400px;
  max-width: 40vw;
  height: calc(100vh - 110px);
  position: sticky;
  top: 16px;
  display: flex;
  flex-direction: column;
  border-radius: 14px;
  border: 1px solid #ebeef5;
  overflow: hidden;
  background: linear-gradient(180deg, #ffffff 0%, #fbfdff 100%);
  box-shadow: 0 14px 40px rgba(15, 23, 42, 0.12);
}
.ai-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  background: linear-gradient(90deg, #ecf5ff 0%, #f0f9eb 100%);
  border-bottom: 1px solid rgba(235, 238, 245, 0.9);
}
.ai-title {
  font-size: 14px;
  font-weight: 800;
  color: #1f2937;
}
.ai-subtitle {
  margin-top: 2px;
  font-size: 12px;
  color: #6b7280;
}
.ai-panel-body {
  flex: 1;
  padding: 14px 12px;
  overflow: auto;
  background: radial-gradient(1200px 400px at 20% 0%, #f3f8ff 0%, rgba(243, 248, 255, 0) 60%),
    radial-gradient(900px 360px at 80% 10%, #f6fffb 0%, rgba(246, 255, 251, 0) 55%), #f5f7fa;
}
.ai-empty {
  border: 1px dashed rgba(96, 98, 102, 0.25);
  border-radius: 12px;
  padding: 14px 12px;
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(6px);
  margin-bottom: 12px;
}
.ai-empty-title {
  font-size: 13px;
  font-weight: 700;
  color: #374151;
}
.ai-empty-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #6b7280;
}
.msg {
  display: flex;
  margin-bottom: 10px;
}
.msg.user {
  justify-content: flex-end;
}
.msg.assistant {
  justify-content: flex-start;
}
.bubble {
  max-width: 88%;
  padding: 10px 12px;
  border-radius: 14px;
  white-space: pre-wrap;
  line-height: 1.6;
  font-size: 13px;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.06);
}
.bubble :deep(.md-p) {
  margin: 0;
}
.bubble :deep(.md-space) {
  height: 10px;
}
.bubble :deep(.md-ul) {
  margin: 6px 0;
  padding-left: 18px;
}
.bubble :deep(.md-li) {
  margin: 3px 0;
}
.bubble :deep(.md-pre) {
  margin: 8px 0;
  padding: 10px 12px;
  border-radius: 12px;
  overflow: auto;
  background: rgba(15, 23, 42, 0.92);
  color: #e5e7eb;
  border: 1px solid rgba(255, 255, 255, 0.08);
}
.bubble :deep(.md-pre code) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 12px;
}
.bubble :deep(.md-code) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 12px;
  padding: 2px 6px;
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.08);
}
.bubble :deep(.md-h1),
.bubble :deep(.md-h2),
.bubble :deep(.md-h3),
.bubble :deep(.md-h4),
.bubble :deep(.md-h5),
.bubble :deep(.md-h6) {
  margin: 6px 0;
  font-size: 13px;
  font-weight: 800;
}
.msg.user .bubble {
  background: linear-gradient(135deg, #409eff 0%, #2b7fff 100%);
  color: #ffffff;
  border-top-right-radius: 6px;
}
.msg.assistant .bubble {
  background: rgba(255, 255, 255, 0.95);
  color: #111827;
  border: 1px solid rgba(235, 238, 245, 0.9);
  border-top-left-radius: 6px;
}
.bubble.thinking {
  color: #6b7280;
}
.ai-panel-input {
  display: flex;
  gap: 10px;
  padding: 12px;
  border-top: 1px solid rgba(235, 238, 245, 0.9);
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
}
.send-btn {
  border-radius: 10px;
  padding: 0 16px;
}
.ai-panel-enter-active,
.ai-panel-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}
.ai-panel-enter-from,
.ai-panel-leave-to {
  opacity: 0;
  transform: translateX(12px);
}
</style>
