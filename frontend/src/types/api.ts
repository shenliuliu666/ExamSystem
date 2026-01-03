export type Role = 'STUDENT' | 'TEACHER' | 'ADMIN'

export type LoginRequest = {
  username: string
  password: string
}

export type RegisterRequest = {
  username: string
  password: string
}

export type LoginResponse = {
  token: string
  username: string
  roles: Role[]
}

export type MeResponse = {
  username: string
  roles: string[]
  fullName?: string
  studentNo?: string
}

export type AiAnalysisResponse = {
  content: string
}

export type AiChatMessage = {
  role: 'user' | 'assistant'
  content: string
}

export type StudentAiExplainRequest = {
  questionId: number
  messages: AiChatMessage[]
}

export type PagedResult<T> = {
  items: T[]
  total: number
  page: number
  size: number
}

export type ExamSettings = {
  autoGradeObjective?: boolean
  requireManualReview?: boolean
  allowPartialScore?: boolean
  scoreVisibleMode?: string
  paperReviewMode?: string
  reviewAvailableAt?: string | null
  durationMinutes?: number | null
  attemptLimit?: number | null
  shuffleQuestions?: boolean
  shuffleOptions?: boolean
  allowResume?: boolean
  enableHeartbeat?: boolean
  recordTabSwitch?: boolean
  forceSubmitOnLeaveSeconds?: number | null
  // New fields
  autoSubmitOnTimeout?: boolean
  allowReviewPaper?: boolean
  showAnswersStrategy?: 'NONE' | 'AFTER_SUBMISSION' | 'AFTER_DEADLINE'
  showScore?: boolean
}

export type ExamResponse = {
  id: number
  name: string
  paperId: number
  classId?: number
  className?: string
  startAt: string
  endAt: string
  status: 'NOT_STARTED' | 'IN_PROGRESS' | 'ENDED'
  createdAt?: string
  updatedAt?: string
  settings?: ExamSettings
  myStatus?: 'NOT_STARTED' | 'IN_PROGRESS' | 'SUBMITTED' | 'GRADED'
  attemptId?: number
  hasResult?: boolean
  submittedCount?: number
  unsubmittedCount?: number
}

export type CreateExamRequest = {
  name: string
  paperId: number
  classId: number
  startAt: string
  endAt: string
  settings?: ExamSettings
}

export type QuestionSnapshot = {
  id: number
  type: 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE'
  stem: string
  options: string[]
  score: number
}

export type AttemptStartResponse = {
  attemptId: number
  examId: number
  paperId: number
  status: 'IN_PROGRESS' | 'SUBMITTED' | 'AUTO_SUBMITTED'
  startedAt: string
  endAt: string
  questions: QuestionSnapshot[]
  studentName?: string
  studentNo?: string
  className?: string
}

export type SubmitExamRequest = {
  attemptId: number
  answers: Array<{ questionId: number; answer: string }>
}

export type SubmitResponse = {
  attemptId: number
  status: 'SUBMITTED' | 'AUTO_SUBMITTED'
  submittedAt: string | null
}

export type StudentResultItem = {
  questionId: number
  questionType: string
  answer: string
  correctAnswer: string
  maxScore: number
  earnedScore: number
  correct: boolean
  stem?: string
  options?: string[]
}

export type StudentResultResponse = {
  resultId: number
  examId: number
  attemptId: number
  totalScore: number
  maxScore: number
  items: StudentResultItem[]
  createdAt: string
}

export type QuestionType = 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE'

export type QuestionResponse = {
  id: number
  bankId?: number
  type: QuestionType
  stem: string
  options: string[]
  tags?: string[]
  correctAnswer: string
  analysis: string
  score: number
  difficulty: string
  knowledgePoint: string
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export type CreateOrUpdateQuestionRequest = {
  bankId?: number
  type: QuestionType
  stem: string
  options?: string[]
  correctAnswer: string
  analysis?: string
  score: number
  difficulty?: string
  knowledgePoint?: string
  enabled?: boolean
  tags?: string[]
}

export type QuestionImportFailure = {
  row: number
  reason: string
}

export type QuestionImportResult = {
  successCount: number
  failedCount: number
  failures: QuestionImportFailure[]
  questions: QuestionResponse[]
}

export type QuestionBank = {
  id: number
  name: string
  ownerUsername: string
  visibility: string
  createdAt: string
  updatedAt: string
}

export type QuestionBankMember = {
  id: number
  bankId: number
  username: string
  role: string
  joinedAt: string
}

export type PaperResponse = {
  id: number
  name: string
  questionIds: number[]
  difficulty: number
  createdAt: string
  updatedAt: string
}

export type PaperUsage = {
  examCount: number
  attemptCount: number
  examIds: number[]
  exams: { id: number; name: string }[]
  totalExamCount?: number
  totalAttemptCount?: number
  blockedExamCount?: number
  blockedAttemptCount?: number
}

export type CreateOrUpdatePaperRequest = {
  name: string
  questionIds: number[]
}

export type TeacherResultResponse = {
  resultId: number
  examId: number
  attemptId: number
  studentUsername: string
  fullName?: string
  studentNo?: string
  totalScore: number
  maxScore: number
  createdAt: string
}

export type ExamAnalyticsQuestionStat = {
  questionId: number
  questionType: string
  maxScore: number
  correctCount: number
  totalCount: number
  correctRate: number
}

export type ExamAnalyticsResponse = {
  participants: number
  maxScore: number
  maxTotalScore: number
  minTotalScore: number
  avgTotalScore: number
  passRate: number
  passLineRatio: number
  questionStats: ExamAnalyticsQuestionStat[]
}

export type ProctorEventBrief = {
  id: number
  attemptId: number
  username: string
  type: string
  createdAt: string
}

export type HeartbeatBrief = {
  attemptId: number
  username: string
  ts: string
}

export type ExamMonitorResponse = {
  startedCount: number
  inProgressCount: number
  submittedCount: number
  resultsCount: number
  inProgressUsers: string[]
  submittedUsers: string[]
  events: ProctorEventBrief[]
  latestHeartbeats: HeartbeatBrief[]
  studentInfos: Record<string, { fullName: string; studentNo: string }>
}

export type ProctorMessage = {
  id: number
  type: string
  message: string
  createdAt: string
}

export type Classroom = {
  id: number
  name: string
  inviteCode: string
  ownerUsername: string
  createdAt: string
  updatedAt: string
  memberCount?: number
}

export type ClassMember = {
  id: number
  classId: number
  username: string
  joinedAt: string
  fullName?: string
  studentNo?: string
}

export type CreateClassRequest = {
  name: string
}

export type JoinClassRequest = {
  inviteCode: string
  studentNo: string
  fullName: string
}

export type StudentImportFailure = {
  row: number
  reason: string
}

export type StudentImportResult = {
  successCount: number
  failedCount: number
  failures: StudentImportFailure[]
}
