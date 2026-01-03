import { http } from './http'
import type {
  AttemptStartResponse,
  AiAnalysisResponse,
  Classroom,
  ExamResponse,
  JoinClassRequest,
  ProctorMessage,
  StudentAiExplainRequest,
  StudentResultResponse,
  SubmitExamRequest,
  SubmitResponse,
} from '../types/api'

export async function listExams(): Promise<ExamResponse[]> {
  const res = await http.get<ExamResponse[]>('/api/student/exams')
  return res.data
}

export async function getExam(id: number): Promise<ExamResponse> {
  const res = await http.get<ExamResponse>(`/api/student/exams/${id}`)
  return res.data
}

export async function startExam(id: number): Promise<AttemptStartResponse> {
  const res = await http.post<AttemptStartResponse>(`/api/student/exams/${id}/start`)
  return res.data
}

export async function submitExam(id: number, data: SubmitExamRequest): Promise<SubmitResponse> {
  const res = await http.post<SubmitResponse>(`/api/student/exams/${id}/submit`, data)
  return res.data
}

export async function sendHeartbeat(id: number, payload: { attemptId: number }): Promise<void> {
  await http.post(`/api/student/exams/${id}/heartbeat`, {
    attemptId: payload.attemptId,
  })
}

export async function sendProctorEvent(
  id: number,
  payload: { attemptId: number; type: string; payloadJson?: string },
): Promise<void> {
  await http.post(`/api/student/exams/${id}/events`, {
    attemptId: payload.attemptId,
    type: payload.type,
    payloadJson: payload.payloadJson ?? '',
  })
}

export async function pollProctorMessages(
  id: number,
  payload: { attemptId: number; afterEventId?: number },
): Promise<ProctorMessage[]> {
  const res = await http.get<ProctorMessage[]>(`/api/student/exams/${id}/proctor/messages`, {
    params: {
      attemptId: payload.attemptId,
      afterEventId: payload.afterEventId,
    },
  })
  return res.data
}

export async function myResult(id: number): Promise<StudentResultResponse> {
  const res = await http.get<StudentResultResponse>(`/api/student/exams/${id}/result`)
  return res.data
}

export async function explainResultQuestion(id: number, data: StudentAiExplainRequest): Promise<AiAnalysisResponse> {
  const res = await http.post<AiAnalysisResponse>(`/api/student/exams/${id}/result/ai`, data, {
    timeout: 120000,
  })
  return res.data
}

export async function explainResultQuestionStream(
  id: number,
  data: StudentAiExplainRequest,
  onChunk: (text: string) => void,
  signal?: AbortSignal,
): Promise<void> {
  const token = localStorage.getItem('auth.token')
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const res = await fetch(`/api/student/exams/${id}/result/ai/stream`, {
    method: 'POST',
    headers,
    body: JSON.stringify(data),
    signal,
  })

  if (!res.ok) {
    let msg = `${res.status}`
    try {
      msg = await res.text()
    } catch {
      // ignore
    }
    throw new Error(msg)
  }

  const reader = res.body?.getReader()
  if (!reader) {
    return
  }
  const decoder = new TextDecoder('utf-8')
  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    if (value) {
      const text = decoder.decode(value, { stream: true })
      if (text) onChunk(text)
    }
  }
}

export async function joinClass(data: JoinClassRequest): Promise<Classroom> {
  const res = await http.post<Classroom>('/api/student/classes/join', data)
  return res.data
}

export async function listClasses(): Promise<Classroom[]> {
  const res = await http.get<Classroom[]>('/api/student/classes')
  return res.data
}
