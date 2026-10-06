import { apiClient } from '@/lib/client';
import { DocumentItem, DocumentProcessingStatus, DocumentStats, PageResponse } from '@/types';

export async function getDocumentStatsApi(): Promise<DocumentStats> {
  const response = await apiClient.get<DocumentStats>('/api/documents/stats');
  return response.data;
}

export async function uploadDocumentApi(file: File): Promise<DocumentItem> {
  const formData = new FormData();
  formData.append('file', file);

  const response = await apiClient.post<DocumentItem>('/api/documents/upload', formData);
  return response.data;
}

export async function getDocumentsApi(
  page = 0,
  size = 10,
  status?: DocumentProcessingStatus
): Promise<PageResponse<DocumentItem>> {
  const params: Record<string, any> = { page, size };
  if (status) {
    params.status = status;
  }
  const response = await apiClient.get<PageResponse<DocumentItem>>('/api/documents', { params });
  return response.data;
}

export async function getDocumentApi(id: string): Promise<DocumentItem> {
  const response = await apiClient.get<DocumentItem>(`/api/documents/${id}`);
  return response.data;
}

export async function downloadDocumentApi(id: string, filename: string): Promise<void> {
  const response = await apiClient.get(`/api/documents/${id}/download`, {
    responseType: 'blob',
  });

  // Create download link
  const contentType = (response.headers['content-type'] as string) || 'application/octet-stream';
  const blob = new Blob([response.data], {
    type: contentType,
  });
  const downloadUrl = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = downloadUrl;
  link.setAttribute('download', filename);
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(downloadUrl);
}

export async function deleteDocumentApi(id: string): Promise<void> {
  await apiClient.delete(`/api/documents/${id}`);
}
