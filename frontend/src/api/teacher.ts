import { http } from './http'
import type {
  CreateExamRequest,
  CreateClassRequest,
  CreateOrUpdatePaperRequest,
  CreateOrUpdateQuestionRequest,
  Classroom,
  ClassMember,
  ExamAnalyticsResponse,
  ExamMonitorResponse,
  ExamResponse,
  PagedResult,
  PaperResponse,
  PaperUsage,
  QuestionResponse,
  QuestionBank,
  QuestionBankMember,
  QuestionImportResult,
  TeacherResultResponse,
  StudentImportResult,
  AiAnalysisResponse,
} from '../types/api'

export async function listQuestions(params: {
  bankId?: number
  type?: string
  enabled?: boolean
  keyword?: string
  page?: number
  size?: number
}): Promise<PagedResult<QuestionResponse>> {
  const res = await http.get<PagedResult<QuestionResponse>>('/api/teacher/questions', { params })
  return res.data
}

export async function listQuestionBanks(): Promise<QuestionBank[]> {
  const res = await http.get<QuestionBank[]>('/api/teacher/question-banks')
  return res.data
}

export async function createQuestionBank(data: {
  name: string
  visibility?: string
}): Promise<QuestionBank> {
  const res = await http.post<QuestionBank>('/api/teacher/question-banks', data)
  return res.data
}

export async function listQuestionBankMembers(bankId: number): Promise<QuestionBankMember[]> {
  const res = await http.get<QuestionBankMember[]>(`/api/teacher/question-banks/${bankId}/members`)
  return res.data
}

export async function addQuestionBankMember(
  bankId: number,
  data: { username: string; role?: string },
): Promise<QuestionBankMember> {
  const res = await http.post<QuestionBankMember>(`/api/teacher/question-banks/${bankId}/members`, data)
  return res.data
}

export async function getQuestion(id: number): Promise<QuestionResponse> {
  const res = await http.get<QuestionResponse>(`/api/teacher/questions/${id}`)
  return res.data
}

export async function createQuestion(data: CreateOrUpdateQuestionRequest): Promise<QuestionResponse> {
  const res = await http.post<QuestionResponse>('/api/teacher/questions', data)
  return res.data
}

export async function updateQuestion(
  id: number,
  data: CreateOrUpdateQuestionRequest,
): Promise<QuestionResponse> {
  const res = await http.put<QuestionResponse>(`/api/teacher/questions/${id}`, data)
  return res.data
}

export async function deleteQuestion(id: number): Promise<void> {
  await http.delete(`/api/teacher/questions/${id}`)
}

export async function importQuestionsExcel(params: {
  file: File
  bankId?: number
  tags?: string
}): Promise<QuestionImportResult> {
  const formData = new FormData()
  if (typeof params.bankId === 'number') {
    formData.append('bankId', String(params.bankId))
  }
  if (params.tags) {
    formData.append('tags', params.tags)
  }
  formData.append('file', params.file)
  const res = await http.post<QuestionImportResult>('/api/teacher/questions/import', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
  return res.data
}

export async function exportQuestionsExcel(params: {
  bankId?: number
  type?: string
  enabled?: boolean
  keyword?: string
}): Promise<Blob> {
  const res = await http.get('/api/teacher/questions/export.xlsx', { params, responseType: 'blob' })
  return res.data as Blob
}

export async function listPapers(params: {
  keyword?: string
  page?: number
  size?: number
}): Promise<PagedResult<PaperResponse>> {
  const res = await http.get<PagedResult<PaperResponse>>('/api/teacher/papers', { params })
  return res.data
}

export async function getPaper(id: number): Promise<PaperResponse> {
  const res = await http.get<PaperResponse>(`/api/teacher/papers/${id}`)
  return res.data
}

export async function createPaper(data: CreateOrUpdatePaperRequest): Promise<PaperResponse> {
  const res = await http.post<PaperResponse>('/api/teacher/papers', data)
  return res.data
}

export async function updatePaper(id: number, data: CreateOrUpdatePaperRequest): Promise<PaperResponse> {
  const res = await http.put<PaperResponse>(`/api/teacher/papers/${id}`, data)
  return res.data
}

export async function deletePaper(id: number): Promise<void> {
  await http.delete(`/api/teacher/papers/${id}`)
}

export async function getPaperUsage(id: number): Promise<PaperUsage> {
  const res = await http.get<PaperUsage>(`/api/teacher/papers/${id}/usage`)
  return res.data
}

export async function exportPaperExcel(id: number): Promise<Blob> {
  const res = await http.get(`/api/teacher/papers/${id}/export.xlsx`, { responseType: 'blob' })
  return res.data as Blob
}

export async function exportPaperWord(id: number): Promise<Blob> {
  const res = await http.get(`/api/teacher/papers/${id}/export.docx`, { responseType: 'blob' })
  return res.data as Blob
}

export async function listExams(): Promise<ExamResponse[]> {
  const res = await http.get<ExamResponse[]>('/api/teacher/exams')
  return res.data
}

export async function getExam(id: number): Promise<ExamResponse> {
  const res = await http.get<ExamResponse>(`/api/teacher/exams/${id}`)
  return res.data
}

export async function createExam(data: CreateExamRequest): Promise<ExamResponse> {
  const res = await http.post<ExamResponse>('/api/teacher/exams', data)
  return res.data
}

export async function updateExam(id: number, data: CreateExamRequest): Promise<ExamResponse> {
  const res = await http.put<ExamResponse>(`/api/teacher/exams/${id}`, data)
  return res.data
}

export async function deleteExam(id: number): Promise<void> {
  await http.delete(`/api/teacher/exams/${id}`)
}

export async function getExamResults(id: number): Promise<TeacherResultResponse[]> {
  const res = await http.get<TeacherResultResponse[]>(`/api/teacher/exams/${id}/results`)
  return res.data
}

export async function getExamAnalytics(id: number): Promise<ExamAnalyticsResponse> {
  const res = await http.get<ExamAnalyticsResponse>(`/api/teacher/exams/${id}/analytics`)
  return res.data
}

export async function getExamAiAnalysis(id: number): Promise<AiAnalysisResponse> {
  const res = await http.get<AiAnalysisResponse>(`/api/teacher/exams/${id}/analytics/ai`, {
    timeout: 120000,
  })
  return res.data
}

export async function getExamMonitor(id: number): Promise<ExamMonitorResponse> {
  const res = await http.get<ExamMonitorResponse>(`/api/teacher/exams/${id}/monitor`)
  return res.data
}

export async function remindExamStudent(id: number, data: { username: string; message: string }): Promise<void> {
  await http.post(`/api/teacher/exams/${id}/proctor/remind`, data)
}

export async function forceSubmitExamForStudent(id: number, data: { username: string }): Promise<void> {
  await http.post(`/api/teacher/exams/${id}/proctor/force-submit`, data)
}

export async function reopenExamForStudent(id: number, data: { username: string }): Promise<void> {
  await http.post(`/api/teacher/exams/${id}/proctor/reopen`, data)
}

export async function exportExamCsv(id: number): Promise<Blob> {
  const res = await http.get(`/api/teacher/exams/${id}/export.csv`, { responseType: 'blob' })
  return res.data as Blob
}

export async function exportExamExcel(id: number): Promise<Blob> {
  const res = await http.get(`/api/teacher/exams/${id}/export.xlsx`, { responseType: 'blob' })
  return res.data as Blob
}

export async function createClass(data: CreateClassRequest): Promise<Classroom> {
  const res = await http.post<Classroom>('/api/teacher/classes', data)
  return res.data
}

export async function listClasses(): Promise<Classroom[]> {
  const res = await http.get<Classroom[]>('/api/teacher/classes')
  return res.data
}

export async function listClassMembers(classId: number): Promise<ClassMember[]> {
  const res = await http.get<ClassMember[]>(`/api/teacher/classes/${classId}/members`)
  return res.data
}

export async function addClassMember(classId: number, data: { username: string; studentNo: string; fullName: string }): Promise<void> {
  await http.post(`/api/teacher/classes/${classId}/members`, data)
}

export async function removeClassMember(classId: number, username: string): Promise<void> {
  await http.delete(`/api/teacher/classes/${classId}/members/${username}`)
}

export async function updateClassMember(
  classId: number,
  username: string,
  data: { studentNo: string; fullName: string; username?: string },
): Promise<void> {
  await http.put(`/api/teacher/classes/${classId}/members/${username}`, data)
}

export async function resetClassMemberPassword(classId: number, username: string): Promise<void> {
  await http.post(`/api/teacher/classes/${classId}/members/${username}/reset-password`)
}

export async function deleteClass(id: number, deleteMembers: boolean = false): Promise<{ exclusiveMembers: string[] }> {
  const res = await http.delete<{ exclusiveMembers: string[] }>(`/api/teacher/classes/${id}`, {
    params: { deleteMembers }
  })
  return res.data
}

export async function getClassExclusiveMembers(id: number): Promise<string[]> {
  const res = await http.get<string[]>(`/api/teacher/classes/${id}/exclusive-members`)
  return res.data
}

export async function importStudentsToClass(classId: number, file: File): Promise<StudentImportResult> {
  const formData = new FormData()
  formData.append('classId', String(classId))
  formData.append('file', file)
  const res = await http.post<StudentImportResult>('/api/teacher/students/import', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
  return res.data
}

export async function exportMonitorData(
  id: number,
  data: { markedUsernames: string[]; statusFilter: 'ALL' | 'IN_PROGRESS' | 'SUBMITTED'; keyword?: string },
): Promise<Blob> {
  const res = await http.post(`/api/teacher/exams/${id}/monitor/export`, data, { responseType: 'blob' })
  return res.data as Blob
}
