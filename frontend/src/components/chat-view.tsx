'use client';

import React, { useState, useEffect, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import ReactMarkdown from 'react-markdown';
import {
  Send,
  Sparkles,
  Bot,
  User,
  FileText,
  Loader2,
  Copy,
  Check,
  BrainCircuit,
  ArrowRight,
} from 'lucide-react';
import { askQuestionApi, getChatSessionMessagesApi } from '@/api/chat';
import { ChatMessage, ChatResponse } from '@/types';
import { useChatStore } from '@/store/useChatStore';
import { cn } from '@/lib/utils';
import { toast } from 'sonner';

interface ChatViewProps {
  sessionId?: string;
}

interface DisplayMessage {
  id: string;
  senderType: 'USER' | 'ASSISTANT';
  messageBody: string;
  sources?: string[];
  isStreaming?: boolean;
}

const EXAMPLE_PROMPTS = [
  {
    title: 'Summarize Policy',
    desc: 'What are the main security guidelines in our docs?',
    prompt: 'Summarize the main security and compliance guidelines from our uploaded documents.',
  },
  {
    title: 'Milestone Analysis',
    desc: 'Extract key deliverables and dates',
    prompt: 'Extract the key project milestones, deliverables, and deadlines described in the uploaded documents.',
  },
  {
    title: 'Contractual Clauses',
    desc: 'Highlight termination and renewal terms',
    prompt: 'Review the uploaded contracts and highlight the terms regarding termination and renewal.',
  },
  {
    title: 'Document Q&A',
    desc: 'Ask any specific question about your files',
    prompt: 'Provide an overview of all the topics covered across our knowledge base.',
  },
];

export function ChatView({ sessionId }: ChatViewProps) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { setActiveSessionId } = useChatStore();

  const [input, setInput] = useState('');
  const [messages, setMessages] = useState<DisplayMessage[]>([]);
  const [copiedMessageId, setCopiedMessageId] = useState<string | null>(null);
  const [isThinking, setIsThinking] = useState(false);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // Sync activeSessionId in global store
  useEffect(() => {
    setActiveSessionId(sessionId || null);
  }, [sessionId, setActiveSessionId]);

  // Fetch past messages if sessionId is present
  const { data: pastMessagesData, isLoading: messagesLoading } = useQuery({
    queryKey: ['chatMessages', sessionId],
    queryFn: () => getChatSessionMessagesApi(sessionId!),
    enabled: !!sessionId,
  });

  // Sync past messages into local state
  useEffect(() => {
    if (pastMessagesData && Array.isArray(pastMessagesData)) {
      setMessages(
        pastMessagesData.map((m) => ({
          id: m.id,
          senderType: m.senderType,
          messageBody: m.messageBody,
        }))
      );
    } else if (!sessionId) {
      setMessages([]);
    }
  }, [pastMessagesData, sessionId]);

  // Auto-scroll to bottom
  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages, isThinking]);

  // Typewriter streaming effect for assistant response
  // Typewriter streaming effect for assistant response
  const streamText = (
    fullText: string,
    messageId: string,
    sources: string[],
    onComplete?: () => void
  ) => {
    let index = 0;
    const chunkSize = 3; // Characters per tick for smooth, fast 2026 feel
    const intervalTime = 16; // ~60fps

    const timer = setInterval(() => {
      index += chunkSize;
      if (index >= fullText.length) {
        clearInterval(timer);
        setMessages((prev) =>
          prev.map((msg) =>
            msg.id === messageId
              ? { ...msg, messageBody: fullText, isStreaming: false, sources }
              : msg
          )
        );
        onComplete?.();
      } else {
        const partial = fullText.slice(0, index);
        setMessages((prev) =>
          prev.map((msg) =>
            msg.id === messageId ? { ...msg, messageBody: partial } : msg
          )
        );
      }
      scrollToBottom();
    }, intervalTime);
  };

  // Ask question mutation
  const askMutation = useMutation({
    mutationFn: (payload: { question: string; sessionId?: string | null }) =>
      askQuestionApi(payload),
    onSuccess: (data: ChatResponse, variables) => {
      setIsThinking(false);

      // Create empty assistant message placeholder for streaming
      const assistantMsgId = 'assistant-' + Date.now();
      const newAssistantMsg: DisplayMessage = {
        id: assistantMsgId,
        senderType: 'ASSISTANT',
        messageBody: '',
        sources: data.sources,
        isStreaming: true,
      };

      setMessages((prev) => [...prev, newAssistantMsg]);
      streamText(data.answer, assistantMsgId, data.sources || [], () => {
        // If this was a new session, route to the created session URL after streaming finishes
        if (!sessionId && data.sessionId) {
          queryClient.invalidateQueries({ queryKey: ['chatSessions'] });
          router.replace(`/chat/${data.sessionId}`);
        } else {
          // Invalidate session messages query in background to keep cache consistent
          queryClient.invalidateQueries({ queryKey: ['chatMessages', sessionId] });
          queryClient.invalidateQueries({ queryKey: ['chatSessions'] });
        }
      });
    },
    onError: (err: any) => {
      setIsThinking(false);
      const errorMsg =
        err.response?.data?.message || 'Failed to generate answer. Please try again.';
      toast.error(errorMsg);

      // Add error message as assistant bubble
      setMessages((prev) => [
        ...prev,
        {
          id: 'error-' + Date.now(),
          senderType: 'ASSISTANT',
          messageBody: `⚠️ **Error:** ${errorMsg}`,
        },
      ]);
    },
  });

  const handleSend = (textToSend?: string) => {
    const messageText = (textToSend || input).trim();
    if (!messageText || isThinking) return;

    // Optimistically add user message to UI
    const userMsgId = 'user-' + Date.now();
    const newUserMsg: DisplayMessage = {
      id: userMsgId,
      senderType: 'USER',
      messageBody: messageText,
    };

    setMessages((prev) => [...prev, newUserMsg]);
    setInput('');
    setIsThinking(true);

    // Reset textarea height
    if (textareaRef.current) {
      textareaRef.current.style.height = '48px';
    }

    askMutation.mutate({
      question: messageText,
      sessionId: sessionId || null,
    });
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleTextareaInput = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setInput(e.target.value);
    // Auto resize
    const target = e.target;
    target.style.height = 'auto';
    target.style.height = `${Math.min(target.scrollHeight, 180)}px`;
  };

  const handleCopy = (text: string, id: string) => {
    navigator.clipboard.writeText(text);
    setCopiedMessageId(id);
    toast.success('Copied to clipboard');
    setTimeout(() => setCopiedMessageId(null), 2000);
  };

  return (
    <div className="flex flex-col h-[calc(100vh-4rem)] relative">
      {/* Messages Scroll Area */}
      <div className="flex-1 overflow-y-auto px-4 py-6 md:px-8 space-y-6">
        {messagesLoading ? (
          <div className="flex flex-col items-center justify-center h-full gap-3 text-slate-500">
            <Loader2 className="w-6 h-6 animate-spin text-indigo-400" />
            <span className="text-xs">Loading conversation history...</span>
          </div>
        ) : messages.length === 0 && !isThinking ? (
          /* Empty State */
          <div className="h-full flex flex-col items-center justify-center max-w-2xl mx-auto text-center px-4 py-8">
            <div className="w-16 h-16 rounded-3xl bg-gradient-to-tr from-indigo-600 via-indigo-500 to-purple-600 flex items-center justify-center shadow-xl shadow-indigo-500/25 mb-6 ring-8 ring-indigo-500/10">
              <Sparkles className="w-8 h-8 text-white" />
            </div>

            <h2 className="text-2xl sm:text-3xl font-bold tracking-tight text-white mb-2">
              How can Nexus assist you today?
            </h2>
            <p className="text-sm text-slate-400 max-w-lg mb-8">
              Ask anything about your uploaded documents. Nexus searches enterprise vector embeddings,
              cites exact sources, and generates precise answers with RAG.
            </p>

            {/* Suggestions Grid */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 w-full text-left">
              {EXAMPLE_PROMPTS.map((item, idx) => (
                <button
                  key={idx}
                  onClick={() => handleSend(item.prompt)}
                  className="p-4 rounded-2xl bg-slate-900/60 hover:bg-slate-800/80 border border-slate-800 hover:border-indigo-500/40 text-left transition-all duration-200 group flex flex-col justify-between"
                >
                  <div>
                    <div className="font-semibold text-xs text-slate-200 group-hover:text-indigo-300 flex items-center justify-between">
                      {item.title}
                      <ArrowRight className="w-3.5 h-3.5 opacity-0 -translate-x-1 group-hover:opacity-100 group-hover:translate-x-0 transition-all text-indigo-400" />
                    </div>
                    <div className="text-[11px] text-slate-400 mt-1 line-clamp-2">
                      {item.desc}
                    </div>
                  </div>
                </button>
              ))}
            </div>
          </div>
        ) : (
          /* Messages List */
          <div className="max-w-4xl mx-auto space-y-6">
            {messages.map((msg) => {
              const isUser = msg.senderType === 'USER';

              return (
                <div
                  key={msg.id}
                  className={cn('flex items-start gap-3', isUser ? 'justify-end' : 'justify-start')}
                >
                  {/* Assistant Avatar */}
                  {!isUser && (
                    <div className="w-8 h-8 rounded-xl bg-gradient-to-tr from-indigo-600 to-purple-600 flex items-center justify-center text-white shrink-0 mt-1 shadow-md shadow-indigo-500/20">
                      <Bot className="w-4 h-4" />
                    </div>
                  )}

                  <div
                    className={cn(
                      'group relative max-w-[85%] sm:max-w-[78%] rounded-2xl px-4 py-3 text-sm shadow-sm transition-all',
                      isUser
                        ? 'bg-gradient-to-r from-indigo-600 to-indigo-700 text-white rounded-tr-sm'
                        : 'glass-card border border-slate-800/90 text-slate-100 rounded-tl-sm'
                    )}
                  >
                    {/* Copy button */}
                    <button
                      onClick={() => handleCopy(msg.messageBody, msg.id)}
                      className="absolute top-2 right-2 p-1 rounded opacity-0 group-hover:opacity-100 text-slate-400 hover:text-white bg-slate-900/60 backdrop-blur transition-opacity"
                      title="Copy message"
                    >
                      {copiedMessageId === msg.id ? (
                        <Check className="w-3 h-3 text-emerald-400" />
                      ) : (
                        <Copy className="w-3 h-3" />
                      )}
                    </button>

                    {/* Markdown rendering */}
                    <div className="prose prose-invert prose-sm max-w-none break-words leading-relaxed">
                      <ReactMarkdown>{msg.messageBody}</ReactMarkdown>
                      {msg.isStreaming && <span className="cursor-blink" />}
                    </div>

                    {/* Sources Pills */}
                    {msg.sources && msg.sources.length > 0 && (
                      <div className="mt-3 pt-3 border-t border-slate-800/80">
                        <div className="text-[10px] font-semibold tracking-wider text-slate-400 uppercase mb-1.5 flex items-center gap-1">
                          <FileText className="w-3 h-3 text-indigo-400" />
                          <span>Grounded Sources ({msg.sources.length})</span>
                        </div>
                        <div className="flex flex-wrap gap-1.5">
                          {msg.sources.map((src, i) => (
                            <span
                              key={i}
                              className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-indigo-950/60 border border-indigo-500/30 text-indigo-300 text-[11px] font-medium"
                            >
                              <span className="w-1 h-1 rounded-full bg-indigo-400" />
                              {src}
                            </span>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>

                  {/* User Avatar */}
                  {isUser && (
                    <div className="w-8 h-8 rounded-xl bg-slate-800 border border-slate-700 flex items-center justify-center text-slate-300 shrink-0 mt-1">
                      <User className="w-4 h-4" />
                    </div>
                  )}
                </div>
              );
            })}

            {/* Thinking / Vector Search Animated State */}
            {isThinking && (
              <div className="flex items-start gap-3 justify-start animate-in fade-in duration-300">
                <div className="w-8 h-8 rounded-xl bg-gradient-to-tr from-indigo-600 to-purple-600 flex items-center justify-center text-white shrink-0 mt-1 shadow-md shadow-indigo-500/20">
                  <Bot className="w-4 h-4" />
                </div>
                <div className="glass-card border border-indigo-500/30 rounded-2xl rounded-tl-sm px-4 py-3 shadow-md">
                  <div className="flex items-center gap-2.5 text-xs text-indigo-300 font-medium">
                    <BrainCircuit className="w-4 h-4 text-indigo-400 animate-spin" />
                    <span>Searching vector chunks & generating response...</span>
                  </div>
                </div>
              </div>
            )}

            <div ref={messagesEndRef} />
          </div>
        )}
      </div>

      {/* Input Form Bar */}
      <div className="p-4 md:px-8 border-t border-slate-800/80 bg-slate-950/80 backdrop-blur-xl">
        <div className="max-w-4xl mx-auto relative">
          <div className="flex items-end gap-2 bg-slate-900/90 border border-slate-800 focus-within:border-indigo-500/70 rounded-2xl p-2 shadow-xl transition-all">
            <textarea
              ref={textareaRef}
              rows={1}
              value={input}
              onChange={handleTextareaInput}
              onKeyDown={handleKeyDown}
              placeholder="Ask anything about your documents... (Enter to send, Shift+Enter for newline)"
              className="flex-1 bg-transparent text-sm text-white placeholder-slate-500 px-3 py-1.5 resize-none focus:outline-none max-h-40 min-h-[40px] leading-relaxed"
            />
            <button
              onClick={() => handleSend()}
              disabled={!input.trim() || isThinking}
              className={cn(
                'p-2.5 rounded-xl transition-all duration-200 shrink-0 flex items-center justify-center',
                input.trim() && !isThinking
                  ? 'bg-gradient-to-r from-indigo-600 to-purple-600 text-white hover:shadow-lg hover:shadow-indigo-500/25 active:scale-95'
                  : 'bg-slate-800 text-slate-500 cursor-not-allowed'
              )}
              title="Send message"
            >
              {isThinking ? (
                <Loader2 className="w-4 h-4 animate-spin" />
              ) : (
                <Send className="w-4 h-4" />
              )}
            </button>
          </div>
          <div className="text-[11px] text-center text-slate-500 mt-2">
            Nexus searches vector embeddings with strictly tenant-isolated security.
          </div>
        </div>
      </div>
    </div>
  );
}
