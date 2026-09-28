import { createContext, type ReactNode, useCallback, useContext, useEffect, useState } from 'react';
import { ChatService } from 'Frontend/generated/endpoints';
import type ChatSessionDto from 'Frontend/generated/com/company/chatdocs/dto/ChatSessionDto';

type ChatSessions = {
  sessions: ChatSessionDto[];
  refresh: () => Promise<void>;
};

const ChatSessionsContext = createContext<ChatSessions>({ sessions: [], refresh: async () => {} });

/** The user's chats, shared by the sidebar list and the chat views (which refresh it after changes). */
export function ChatSessionsProvider({ children }: { children: ReactNode }) {
  const [sessions, setSessions] = useState<ChatSessionDto[]>([]);

  const refresh = useCallback(async () => {
    setSessions(await ChatService.listSessions());
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  return <ChatSessionsContext.Provider value={{ sessions, refresh }}>{children}</ChatSessionsContext.Provider>;
}

export function useChatSessions() {
  return useContext(ChatSessionsContext);
}
