import { apiClient } from '@/lib/client';
import { AuthResponse, User } from '@/types';

export interface LoginPayload {
  email: string;
  password: string;
}

export interface RegisterPayload {
  email: string;
  password: string;
}

export async function loginApi(payload: LoginPayload): Promise<AuthResponse> {
  const response = await apiClient.post<AuthResponse>('/api/auth/login', payload);
  return response.data;
}

export async function registerApi(payload: RegisterPayload): Promise<AuthResponse> {
  const response = await apiClient.post<AuthResponse>('/api/auth/register', payload);
  return response.data;
}

export async function getMeApi(): Promise<User> {
  const response = await apiClient.get<User>('/api/auth/me');
  return response.data;
}
