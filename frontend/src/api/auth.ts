import { http } from './http'
import type { LoginRequest, LoginResponse, MeResponse, RegisterRequest } from '../types/api'

export async function login(data: LoginRequest): Promise<LoginResponse> {
  const res = await http.post<LoginResponse>('/api/auth/login', data)
  return res.data
}

export async function register(data: RegisterRequest): Promise<LoginResponse> {
  const res = await http.post<LoginResponse>('/api/auth/register', data)
  return res.data
}

export async function logout(): Promise<void> {
  await http.post('/api/auth/logout')
}

export async function me(): Promise<MeResponse> {
  const res = await http.get<MeResponse>('/api/me')
  return res.data
}
