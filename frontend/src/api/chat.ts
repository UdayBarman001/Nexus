import { apiClient } from '@/lib/client';
import { ChatMessage, ChatResponse, ChatSession, PageResponse } from '@/types';

export interface AskQuestionPayload {
  question: string;
  sessionId?: string | null;
}

export async function askQuestionApi(payload: AskQuestionPayload): Promise<ChatResponse> {
  const body: Record<string, any> = { query: payload.question };
  if (payload.sessionId) {
    body.sessionId = payload.sessionId;
  }
  const response = await apiClient.post<ChatResponse>('/api/chat/ask', body);
  return response.data;
}

export async function getChatSessionsApi(page = 0, size = 30): Promise<PageResponse<ChatSession>> {
  const response = await apiClient.get<PageResponse<ChatSession>>('/api/chat/sessions', {
    params: { page, size },
  });
  return response.data;
}

export async function getChatSessionMessagesApi(
  sessionId: string
): Promise<ChatMessage[]> {
  const response = await apiClient.get<ChatMessage[]>(
    `/api/chat/sessions/${sessionId}/messages`
  );
  return response.data;
}

export async function renameChatSessionApi(sessionId: string, title: string): Promise<ChatSession> {
  const response = await apiClient.patch<ChatSession>(`/api/chat/sessions/${sessionId}`, {
    title,
  });
  return response.data;
}

export async function deleteChatSessionApi(sessionId: string): Promise<void> {
  await apiClient.delete(`/api/chat/sessions/${sessionId}`);
}
