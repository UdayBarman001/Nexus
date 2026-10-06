'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  MessageSquare,
  FileText,
  Plus,
  Search,
  Trash2,
  Edit2,
  Check,
  X,
  LogOut,
  Sparkles,
  ChevronLeft,
  ChevronRight,
  ShieldAlert,
  User as UserIcon,
} from 'lucide-react';
import { useAuthStore } from '@/store/useAuthStore';
import { useChatStore } from '@/store/useChatStore';
import { getChatSessionsApi, renameChatSessionApi, deleteChatSessionApi } from '@/api/chat';
import { cn } from '@/lib/utils';
import { toast } from 'sonner';

export function Sidebar() {
  const pathname = usePathname();
  const router = useRouter();
  const queryClient = useQueryClient();
  const { user, logout } = useAuthStore();
  const { sidebarOpen, toggleSidebar, activeSessionId, setActiveSessionId } = useChatStore();

  const [searchQuery, setSearchQuery] = useState('');
  const [editingSessionId, setEditingSessionId] = useState<string | null>(null);
  const [editTitle, setEditTitle] = useState('');

  // Fetch sessions
  const { data: sessionsData, isLoading: sessionsLoading } = useQuery({
    queryKey: ['chatSessions'],
    queryFn: () => getChatSessionsApi(0, 50),
    enabled: !!user,
  });

  // Rename session mutation
  const renameMutation = useMutation({
    mutationFn: ({ id, title }: { id: string; title: string }) =>
      renameChatSessionApi(id, title),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['chatSessions'] });
      setEditingSessionId(null);
      toast.success('Chat renamed');
    },
    onError: () => {
      toast.error('Failed to rename session');
    },
  });

  // Delete session mutation
  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteChatSessionApi(id),
    onSuccess: (_, deletedId) => {
      queryClient.invalidateQueries({ queryKey: ['chatSessions'] });
      toast.success('Chat session deleted');
      if (activeSessionId === deletedId) {
        setActiveSessionId(null);
        router.push('/chat');
      }
    },
    onError: () => {
      toast.error('Failed to delete chat session');
    },
  });

  const handleStartRename = (id: string, currentTitle: string | null, e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setEditingSessionId(id);
    setEditTitle(currentTitle || 'New Conversation');
  };

  const handleSaveRename = (id: string, e: React.MouseEvent | React.FormEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (!editTitle.trim()) return;
    renameMutation.mutate({ id, title: editTitle.trim() });
  };

  const handleCancelRename = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setEditingSessionId(null);
  };

  const handleDeleteSession = (id: string, e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (confirm('Are you sure you want to delete this chat session?')) {
      deleteMutation.mutate(id);
    }
  };

  const handleNewChat = () => {
    setActiveSessionId(null);
    router.push('/chat');
  };

  const handleLogout = () => {
    logout();
    router.push('/login');
    toast.info('Logged out successfully');
  };

  const filteredSessions = (sessionsData?.content || []).filter((s) =>
    (s.title || 'New Conversation').toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <aside
      className={cn(
        'relative flex flex-col h-screen border-r border-slate-800/80 bg-slate-950/80 backdrop-blur-xl transition-all duration-300 z-30',
        sidebarOpen ? 'w-72' : 'w-20'
      )}
    >
      {/* Top Header / Logo */}
      <div className="flex items-center justify-between h-16 px-4 border-b border-slate-800/70">
        <Link href="/chat" className="flex items-center gap-3 overflow-hidden">
          <div className="h-10 w-10 rounded-xl bg-gradient-to-tr from-indigo-600 via-indigo-500 to-purple-500 flex items-center justify-center shadow-lg shadow-indigo-500/20 shrink-0">
            <Sparkles className="w-5 h-5 text-white" />
          </div>
          {sidebarOpen && (
            <div className="flex flex-col">
              <span className="font-bold tracking-tight text-lg bg-gradient-to-r from-white via-slate-100 to-indigo-200 bg-clip-text text-transparent">
                Nexus
              </span>
              <span className="text-[10px] uppercase font-semibold tracking-wider text-indigo-400">
                RAG Intelligence
              </span>
            </div>
          )}
        </Link>
        <button
          onClick={toggleSidebar}
          className="hidden md:flex p-1.5 rounded-lg text-slate-400 hover:text-slate-200 hover:bg-slate-800/60 transition-colors"
          title={sidebarOpen ? 'Collapse sidebar' : 'Expand sidebar'}
        >
          {sidebarOpen ? <ChevronLeft className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
        </button>
      </div>

      {/* Main Navigation Tabs */}
      <div className="p-3 space-y-1">
        <button
          onClick={handleNewChat}
          className={cn(
            'w-full flex items-center justify-center gap-2 px-3 py-2.5 rounded-xl font-medium text-sm transition-all duration-200 shadow-md',
            'bg-gradient-to-r from-indigo-600 to-purple-600 text-white hover:from-indigo-500 hover:to-purple-500 hover:shadow-indigo-500/25 active:scale-[0.98]'
          )}
          title="New Chat"
        >
          <Plus className="w-4 h-4 shrink-0" />
          {sidebarOpen && <span>New Chat</span>}
        </button>

        <div className="pt-2">
          <Link
            href="/chat"
            className={cn(
              'flex items-center gap-3 px-3 py-2 rounded-xl text-sm font-medium transition-colors',
              pathname.startsWith('/chat')
                ? 'bg-slate-800/80 text-white font-semibold shadow-inner'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/60'
            )}
            title="Chat"
          >
            <MessageSquare className="w-4 h-4 shrink-0 text-indigo-400" />
            {sidebarOpen && <span>Chat</span>}
          </Link>

          <Link
            href="/documents"
            className={cn(
              'flex items-center gap-3 px-3 py-2 rounded-xl text-sm font-medium transition-colors',
              pathname.startsWith('/documents')
                ? 'bg-slate-800/80 text-white font-semibold shadow-inner'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/60'
            )}
            title="Document Vault"
          >
            <FileText className="w-4 h-4 shrink-0 text-purple-400" />
            {sidebarOpen && <span>Document Vault</span>}
          </Link>
        </div>
      </div>

      {/* Sessions List Section */}
      {sidebarOpen ? (
        <div className="flex-1 flex flex-col min-h-0 px-3 py-2">
          {/* Search box */}
          <div className="relative mb-2">
            <Search className="w-3.5 h-3.5 absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
            <input
              type="text"
              placeholder="Search chats..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-8 pr-3 py-1.5 text-xs bg-slate-900/80 border border-slate-800 rounded-lg text-slate-200 placeholder-slate-500 focus:outline-none focus:border-indigo-500/50 transition-colors"
            />
          </div>

          <div className="text-[11px] font-semibold uppercase tracking-wider text-slate-500 px-2 py-1">
            Recent Sessions
          </div>

          <div className="flex-1 overflow-y-auto space-y-1 pr-1">
            {sessionsLoading ? (
              <div className="space-y-2 p-2">
                {[1, 2, 3].map((i) => (
                  <div key={i} className="h-8 rounded-lg bg-slate-900/80 animate-pulse" />
                ))}
              </div>
            ) : filteredSessions.length === 0 ? (
              <div className="text-center py-8 px-2 text-xs text-slate-500">
                {searchQuery ? 'No matching chats' : 'No chats yet. Start a new conversation!'}
              </div>
            ) : (
              filteredSessions.map((session) => {
                const isActive = pathname === `/chat/${session.id}`;
                const isEditing = editingSessionId === session.id;

                return (
                  <div
                    key={session.id}
                    className={cn(
                      'group relative flex items-center justify-between rounded-xl px-2.5 py-2 text-xs transition-colors',
                      isActive
                        ? 'bg-indigo-950/40 border border-indigo-500/30 text-indigo-100 font-medium'
                        : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/60'
                    )}
                  >
                    {isEditing ? (
                      <form
                        onSubmit={(e) => handleSaveRename(session.id, e)}
                        className="flex items-center gap-1 w-full"
                      >
                        <input
                          type="text"
                          value={editTitle}
                          onChange={(e) => setEditTitle(e.target.value)}
                          autoFocus
                          className="flex-1 bg-slate-800 text-xs text-white px-2 py-1 rounded border border-indigo-500 focus:outline-none"
                        />
                        <button
                          type="button"
                          onClick={(e) => handleSaveRename(session.id, e)}
                          className="p-1 hover:text-emerald-400"
                        >
                          <Check className="w-3.5 h-3.5" />
                        </button>
                        <button
                          type="button"
                          onClick={handleCancelRename}
                          className="p-1 hover:text-rose-400"
                        >
                          <X className="w-3.5 h-3.5" />
                        </button>
                      </form>
                    ) : (
                      <>
                        <Link
                          href={`/chat/${session.id}`}
                          className="flex-1 truncate pr-2"
                          title={session.title || 'New Conversation'}
                        >
                          {session.title || 'New Conversation'}
                        </Link>
                        <div className="opacity-0 group-hover:opacity-100 flex items-center gap-1 transition-opacity">
                          <button
                            onClick={(e) => handleStartRename(session.id, session.title, e)}
                            className="p-1 text-slate-400 hover:text-indigo-400 rounded hover:bg-slate-800"
                            title="Rename"
                          >
                            <Edit2 className="w-3 h-3" />
                          </button>
                          <button
                            onClick={(e) => handleDeleteSession(session.id, e)}
                            className="p-1 text-slate-400 hover:text-rose-400 rounded hover:bg-slate-800"
                            title="Delete"
                          >
                            <Trash2 className="w-3 h-3" />
                          </button>
                        </div>
                      </>
                    )}
                  </div>
                );
              })
            )}
          </div>
        </div>
      ) : (
        <div className="flex-1" />
      )}

      {/* User Footer Profile */}
      <div className="p-3 border-t border-slate-800/80 bg-slate-950/60">
        {sidebarOpen ? (
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2.5 min-w-0">
              <div className="w-8 h-8 rounded-full bg-indigo-600/30 border border-indigo-500/40 flex items-center justify-center text-indigo-300 font-semibold text-xs shrink-0">
                {user?.email?.charAt(0).toUpperCase() || 'U'}
              </div>
              <div className="min-w-0 flex flex-col">
                <span className="text-xs font-medium text-slate-200 truncate" title={user?.email}>
                  {user?.email || 'User'}
                </span>
                <div className="flex items-center gap-1.5 mt-0.5">
                  <span
                    className={cn(
                      'text-[9px] uppercase tracking-wider font-bold px-1.5 py-0.5 rounded',
                      user?.role === 'ADMIN'
                        ? 'bg-purple-900/50 text-purple-300 border border-purple-500/40'
                        : 'bg-slate-800 text-slate-400'
                    )}
                  >
                    {user?.role || 'USER'}
                  </span>
                </div>
              </div>
            </div>
            <button
              onClick={handleLogout}
              className="p-1.5 text-slate-400 hover:text-rose-400 hover:bg-slate-900 rounded-lg transition-colors"
              title="Logout"
            >
              <LogOut className="w-4 h-4" />
            </button>
          </div>
        ) : (
          <div className="flex flex-col items-center gap-2">
            <div
              className="w-8 h-8 rounded-full bg-indigo-600/30 border border-indigo-500/40 flex items-center justify-center text-indigo-300 font-semibold text-xs"
              title={user?.email}
            >
              {user?.email?.charAt(0).toUpperCase() || 'U'}
            </div>
            <button
              onClick={handleLogout}
              className="p-1.5 text-slate-400 hover:text-rose-400 hover:bg-slate-900 rounded-lg transition-colors"
              title="Logout"
            >
              <LogOut className="w-4 h-4" />
            </button>
          </div>
        )}
      </div>
    </aside>
  );
}
