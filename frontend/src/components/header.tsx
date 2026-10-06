'use client';

import React from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Menu, MessageSquare, FileText, Database, ShieldCheck } from 'lucide-react';
import { useChatStore } from '@/store/useChatStore';
import { useAuthStore } from '@/store/useAuthStore';
import { cn } from '@/lib/utils';

export function Header() {
  const pathname = usePathname();
  const { toggleSidebar } = useChatStore();
  const { user } = useAuthStore();

  const isDocuments = pathname.startsWith('/documents');
  const isChat = pathname.startsWith('/chat');

  return (
    <header className="h-16 border-b border-slate-800/80 bg-slate-950/60 backdrop-blur-xl flex items-center justify-between px-4 sm:px-6 sticky top-0 z-20">
      <div className="flex items-center gap-3">
        <button
          onClick={toggleSidebar}
          className="p-2 -ml-2 text-slate-400 hover:text-slate-100 hover:bg-slate-800/60 rounded-xl md:hidden transition-colors"
          aria-label="Toggle sidebar"
        >
          <Menu className="w-5 h-5" />
        </button>

        <div className="flex items-center gap-2">
          <span className="font-semibold text-sm sm:text-base text-slate-100 flex items-center gap-2">
            {isDocuments ? (
              <>
                <Database className="w-4 h-4 text-purple-400" />
                Document Vault
              </>
            ) : (
              <>
                <MessageSquare className="w-4 h-4 text-indigo-400" />
                Knowledge Chat
              </>
            )}
          </span>
        </div>
      </div>

      {/* Navigation Switcher Pills */}
      <div className="flex items-center gap-2 bg-slate-900/90 border border-slate-800/90 p-1 rounded-xl">
        <Link
          href="/chat"
          className={cn(
            'flex items-center gap-1.5 px-3 py-1 text-xs font-medium rounded-lg transition-all',
            isChat
              ? 'bg-indigo-600/90 text-white shadow-sm'
              : 'text-slate-400 hover:text-slate-200'
          )}
        >
          <MessageSquare className="w-3.5 h-3.5" />
          <span className="hidden sm:inline">Chat</span>
        </Link>
        <Link
          href="/documents"
          className={cn(
            'flex items-center gap-1.5 px-3 py-1 text-xs font-medium rounded-lg transition-all',
            isDocuments
              ? 'bg-purple-600/90 text-white shadow-sm'
              : 'text-slate-400 hover:text-slate-200'
          )}
        >
          <FileText className="w-3.5 h-3.5" />
          <span className="hidden sm:inline">Documents</span>
        </Link>
      </div>

      {/* Status Badges */}
      <div className="flex items-center gap-3">
        <div className="hidden sm:flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-emerald-950/40 border border-emerald-500/30 text-emerald-400 text-xs font-medium">
          <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
          <span>RAG Online</span>
        </div>

        {user?.role === 'ADMIN' && (
          <div className="flex items-center gap-1 px-2.5 py-1 rounded-full bg-purple-950/50 border border-purple-500/40 text-purple-300 text-xs font-semibold">
            <ShieldCheck className="w-3.5 h-3.5" />
            <span>Admin</span>
          </div>
        )}
      </div>
    </header>
  );
}
