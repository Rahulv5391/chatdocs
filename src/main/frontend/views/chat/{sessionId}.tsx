import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { ViewConfig } from '@vaadin/hilla-file-router/types.js';
import { EndpointError } from '@vaadin/hilla-frontend';
import { Button, MessageInput, MessageList, Notification, TextField } from '@vaadin/react-components';
import { ChatService } from 'Frontend/generated/endpoints';
import type ChatMessageDto from 'Frontend/generated/com/company/chatdocs/dto/ChatMessageDto';
import MessageRole from 'Frontend/generated/com/company/chatdocs/entity/MessageRole';
import { useAuth } from 'Frontend/auth';

export const config: ViewConfig = {
  title: 'Chat',
  menu: { exclude: true },
};

function showError(e: unknown, fallback: string) {
  const message = e instanceof EndpointError ? e.message : fallback;
  Notification.show(message, { theme: 'error', position: 'bottom-end', duration: 5000 });
}

export default function ChatSessionView() {
  const { sessionId } = useParams() as { sessionId: string };
  const navigate = useNavigate();
  const { state } = useAuth();
  const [title, setTitle] = useState('');
  const [messages, setMessages] = useState<ChatMessageDto[]>([]);
  const [sending, setSending] = useState(false);

  useEffect(() => {
    Promise.all([ChatService.getSession(sessionId), ChatService.getMessages(sessionId)])
      .then(([session, loaded]) => {
        setTitle(session.title);
        setMessages(loaded);
      })
      .catch((e) => {
        showError(e, 'Could not load the chat.');
        navigate('/chat', { replace: true });
      });
  }, [sessionId, navigate]);

  async function rename(newTitle: string) {
    try {
      const session = await ChatService.renameSession(sessionId, newTitle);
      setTitle(session.title);
    } catch (e) {
      showError(e, 'Could not rename the chat.');
    }
  }

  async function send(text: string) {
    setSending(true);
    try {
      const saved = await ChatService.sendMessage(sessionId, text);
      setMessages((current) => [...current, ...saved]);
    } catch (e) {
      showError(e, 'Could not send the message.');
    } finally {
      setSending(false);
    }
  }

  const items = messages.map((message) => ({
    text: message.content,
    time: new Date(message.createdAt).toLocaleTimeString(),
    userName: message.role === MessageRole.USER ? (state.user?.displayName ?? 'You') : 'Assistant',
    userColorIndex: message.role === MessageRole.USER ? 1 : 3,
  }));

  return (
    <main style={{ display: 'flex', flexDirection: 'column', height: '100%', boxSizing: 'border-box', padding: '1rem' }}>
      <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'baseline' }}>
        <Button theme="tertiary" onClick={() => navigate('/chat')}>
          ← All chats
        </Button>
        <TextField
          aria-label="Chat title"
          value={title}
          style={{ flexGrow: 1 }}
          onChange={(e) => rename(e.target.value)}
        />
      </div>
      <MessageList items={items} style={{ flexGrow: 1, overflow: 'auto' }} />
      {messages.length === 0 && <p>Ask a question about your documents.</p>}
      <MessageInput disabled={sending} onSubmit={(e) => send(e.detail.value)} />
    </main>
  );
}
