import { create } from 'zustand';

interface ChatUIState {
  sidebarOpen: boolean;
  toggleSidebar: () => void;
  setSidebarOpen: (open: boolean) => void;
  activeSessionId: string | null;
  setActiveSessionId: (id: string | null) => void;
  isStreaming: boolean;
  setIsStreaming: (streaming: boolean) => void;
}

export const useChatStore = create<ChatUIState>((set) => ({
  sidebarOpen: true,
  toggleSidebar: () => set((state) => ({ sidebarOpen: !state.sidebarOpen })),
  setSidebarOpen: (open: boolean) => set({ sidebarOpen: open }),
  activeSessionId: null,
  setActiveSessionId: (id: string | null) => set({ activeSessionId: id }),
  isStreaming: false,
  setIsStreaming: (streaming: boolean) => set({ isStreaming: streaming }),
}));
