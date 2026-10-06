'use client';

import { ChatView } from '@/components/chat-view';

interface ChatSessionPageProps {
  params: {
    sessionId: string;
  };
}

export default function ChatSessionPage({ params }: ChatSessionPageProps) {
  return <ChatView sessionId={params.sessionId} />;
}
