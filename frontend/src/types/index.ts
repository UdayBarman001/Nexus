export type Role = 'USER' | 'ADMIN';

export interface User {
  id: number;
  email: string;
  role: Role;
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  expiresInMs: number;
  user: User;
}

export type DocumentProcessingStatus = 'PENDING' | 'PROCESSING' | 'EMBEDDED' | 'FAILED';

export interface DocumentItem {
  id: string;
  filename: string;
  contentType: string | null;
  fileSizeBytes: number;
  processingStatus: DocumentProcessingStatus;
  failureReason: string | null;
  chunkCount: number;
  uploaderEmail: string | null;
  createdAt: string;
  updatedAt: string | null;
}

export interface DocumentStats {
  totalDocuments: number;
  embeddedDocuments: number;
  processingDocuments: number;
  pendingDocuments: number;
  failedDocuments: number;
  totalChunks: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface ChatSession {
  id: string;
  title: string | null;
  createdAt: string;
  updatedAt: string | null;
}

export type SenderType = 'USER' | 'ASSISTANT';

export interface ChatMessage {
  id: string;
  senderType: SenderType;
  messageBody: string;
  createdAt: string;
}

export interface ChatResponse {
  sessionId: string;
  answer: string;
  sources: string[];
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors?: Record<string, string>;
}
