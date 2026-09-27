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
  const [pendingQuestion, setPendingQuestion] = useState<string>();

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

  async function ask(question: string) {
    setPendingQuestion(question);
    try {
      const saved = await ChatService.ask(sessionId, question);
      setMessages((current) => [...current, ...saved]);
      // The first question also names the chat.
      setTitle((await ChatService.getSession(sessionId)).title);
    } catch (e) {
      showError(e, 'Could not get an answer.');
    } finally {
      setPendingQuestion(undefined);
    }
  }

  const userName = state.user?.displayName ?? 'You';
  const items = messages.map((message) => ({
    text: message.content,
    time: new Date(message.createdAt).toLocaleTimeString(),
    userName: message.role === MessageRole.USER ? userName : 'Assistant',
    userColorIndex: message.role === MessageRole.USER ? 1 : 3,
  }));
  // Show the question right away, with a placeholder answer, while Gemini is working.
  if (pendingQuestion) {
    items.push(
      { text: pendingQuestion, time: '', userName, userColorIndex: 1 },
      { text: '_Thinking…_', time: '', userName: 'Assistant', userColorIndex: 3 },
    );
  }

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
      <MessageList items={items} markdown style={{ flexGrow: 1, overflow: 'auto' }} />
      {items.length === 0 && <p>Ask a question about your documents.</p>}
      <MessageInput disabled={!!pendingQuestion} onSubmit={(e) => ask(e.detail.value)} />
    </main>
  );
}
