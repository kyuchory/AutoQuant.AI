import apiClient from './client';
import type { ApiResponse } from '@/types/api';
import type { LoginResponse } from '@/types/auth';

export async function login(provider: string, code: string): Promise<ApiResponse<LoginResponse>> {
  const { data } = await apiClient.post<ApiResponse<LoginResponse>>('/auth/login', {
    provider,
    code,
  });
  return data;
}

export async function logout(): Promise<ApiResponse<null>> {
  const { data } = await apiClient.post<ApiResponse<null>>('/auth/logout');
  return data;
}