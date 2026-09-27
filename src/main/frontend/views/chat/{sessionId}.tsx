import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { ViewConfig } from '@vaadin/hilla-file-router/types.js';
import { EndpointError, type Subscription } from '@vaadin/hilla-frontend';
import { Button, Markdown, Message, MessageInput, Notification, TextField } from '@vaadin/react-components';
import { ChatService } from 'Frontend/generated/endpoints';
import type ChatMessageDto from 'Frontend/generated/com/company/chatdocs/dto/ChatMessageDto';
import MessageRole from 'Frontend/generated/com/company/chatdocs/entity/MessageRole';
import type Citation from 'Frontend/generated/com/company/chatdocs/dto/Citation';
import CitationChips from 'Frontend/components/CitationChips';
import { useAuth } from 'Frontend/auth';

export const config: ViewConfig = {
  title: 'Chat',
  menu: { exclude: true },
};

function showError(message: string) {
  Notification.show(message, { theme: 'error', position: 'bottom-end', duration: 5000 });
}

function errorMessage(e: unknown, fallback: string) {
  return e instanceof EndpointError ? e.message : fallback;
}

/** A question whose answer is still streaming in. */
type Pending = { question: string; answer: string };

export default function ChatSessionView() {
  const { sessionId } = useParams() as { sessionId: string };
  const navigate = useNavigate();
  const { state } = useAuth();
  const [title, setTitle] = useState('');
  const [messages, setMessages] = useState<ChatMessageDto[]>([]);
  const [pending, setPending] = useState<Pending>();
  const subscription = useRef<Subscription<string>>(undefined);
  const bottom = useRef<HTMLDivElement>(null);

  // Reload from the server: the saved answer has its id and citations, and the first question names the chat.
  const reload = useCallback(async () => {
    const [session, loaded] = await Promise.all([
      ChatService.getSession(sessionId),
      ChatService.getMessages(sessionId),
    ]);
    setTitle(session.title);
    setMessages(loaded);
  }, [sessionId]);

  useEffect(() => {
    reload().catch((e) => {
      showError(errorMessage(e, 'Could not load the chat.'));
      navigate('/chat', { replace: true });
    });
    // Leaving the page stops a running answer.
    return () => subscription.current?.cancel();
  }, [reload, navigate]);

  async function rename(newTitle: string) {
    try {
      const session = await ChatService.renameSession(sessionId, newTitle);
      setTitle(session.title);
    } catch (e) {
      showError(errorMessage(e, 'Could not rename the chat.'));
    }
  }

  function ask(question: string) {
    setPending({ question, answer: '' });
    subscription.current = ChatService.ask(sessionId, question)
      .onNext((token) => setPending((current) => current && { ...current, answer: current.answer + token }))
      .onComplete(() => {
        subscription.current = undefined;
        reload().finally(() => setPending(undefined));
      })
      .onError((message) => {
        subscription.current = undefined;
        showError(message || 'Could not get an answer.');
        reload().finally(() => setPending(undefined));
      });
  }

  function stop() {
    subscription.current?.cancel();
    subscription.current = undefined;
    // The server saves the partial answer when it sees the cancel; give it a moment, then show the saved version.
    setTimeout(() => reload().finally(() => setPending(undefined)), 800);
  }

  const userName = state.user?.displayName ?? 'You';
  type Item = { key: string; role: MessageRole; text: string; time: string; citations: Citation[] };
  const items: Item[] = messages.map((message) => ({
    key: message.id,
    role: message.role,
    text: message.content,
    time: new Date(message.createdAt).toLocaleTimeString(),
    citations: message.citations,
  }));
  if (pending) {
    items.push(
      { key: 'pending-q', role: MessageRole.USER, text: pending.question, time: '', citations: [] },
      { key: 'pending-a', role: MessageRole.ASSISTANT, text: pending.answer || '_Thinking…_', time: '', citations: [] },
    );
  }

  // Keep the newest message in view while an answer streams in.
  useEffect(() => {
    bottom.current?.scrollIntoView({ block: 'end' });
  }, [items.length, pending?.answer]);

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
      <div style={{ flexGrow: 1, overflow: 'auto' }}>
        {items.map((item) => (
          <Message
            key={item.key}
            userName={item.role === MessageRole.USER ? userName : 'Assistant'}
            userColorIndex={item.role === MessageRole.USER ? 1 : 3}
            time={item.time}
          >
            <Markdown>{item.text}</Markdown>
            <CitationChips citations={item.citations} idPrefix={item.key} />
          </Message>
        ))}
        <div ref={bottom} />
      </div>
      {items.length === 0 && <p>Ask a question about your documents.</p>}
      <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
        <MessageInput style={{ flexGrow: 1 }} disabled={!!pending} onSubmit={(e) => ask(e.detail.value)} />
        {pending && (
          <Button theme="error" onClick={stop}>
            Stop
          </Button>
        )}
      </div>
    </main>
  );
}
