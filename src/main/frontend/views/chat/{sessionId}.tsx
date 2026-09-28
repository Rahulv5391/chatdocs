import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router';
import { ViewConfig } from '@vaadin/hilla-file-router/types.js';
import type { Subscription } from '@vaadin/hilla-frontend';
import { ChatService, DocumentService } from 'Frontend/generated/endpoints';
import type ChatSessionDto from 'Frontend/generated/com/company/chatdocs/dto/ChatSessionDto';
import type ChatMessageDto from 'Frontend/generated/com/company/chatdocs/dto/ChatMessageDto';
import MessageRole from 'Frontend/generated/com/company/chatdocs/entity/MessageRole';
import Composer from 'Frontend/components/Composer';
import { AssistantMessage, UserMessage } from 'Frontend/components/ChatMessages';
import { useChatSessions } from 'Frontend/components/ChatSessions';
import { LayersIcon } from 'Frontend/components/Icons';
import { errorMessage, showError } from 'Frontend/util/notifications';
import { useTypewriter } from 'Frontend/util/useTypewriter';

export const config: ViewConfig = {
  title: 'Chat',
};

/** Which documents the chat answers from, for the pill in the header. */
async function scopeLabel(session: ChatSessionDto) {
  if (!session.scoped) {
    return 'All documents';
  }
  if (session.documentIds.length === 0) {
    return 'No documents (the chosen ones were deleted)';
  }
  const names = new Map((await DocumentService.list()).map((d) => [d.id, d.fileName]));
  return session.documentIds.map((id) => names.get(id) ?? 'deleted document').join(', ');
}

/** A question whose answer is still streaming in; {@code done} once the server has finished. */
type Pending = { question: string; answer: string; done: boolean };

/** How close to the bottom (px) still counts as "following" the conversation. */
const FOLLOW_THRESHOLD = 80;

export default function ChatSessionView() {
  const { sessionId } = useParams() as { sessionId: string };
  const navigate = useNavigate();
  const location = useLocation();
  const { refresh: refreshSessions } = useChatSessions();
  const [title, setTitle] = useState('');
  const [savedTitle, setSavedTitle] = useState('');
  const [scope, setScope] = useState('');
  const [messages, setMessages] = useState<ChatMessageDto[]>();
  const [pending, setPending] = useState<Pending>();
  const subscription = useRef<Subscription<string>>(undefined);
  const scroller = useRef<HTMLDivElement>(null);
  const following = useRef(true);
  const typed = useTypewriter(pending?.answer ?? '');

  // Reload from the server: the saved answer has its id and citations, and the first question names the chat.
  const reload = useCallback(async () => {
    const [session, loaded] = await Promise.all([
      ChatService.getSession(sessionId),
      ChatService.getMessages(sessionId),
    ]);
    setTitle(session.title);
    setSavedTitle(session.title);
    setMessages(loaded);
    setScope(await scopeLabel(session));
  }, [sessionId]);

  // The server names a new chat from its first question; show that name as soon as the answer starts.
  const refreshTitle = useCallback(async () => {
    const session = await ChatService.getSession(sessionId);
    setTitle(session.title);
    setSavedTitle(session.title);
    refreshSessions();
  }, [sessionId, refreshSessions]);

  const ask = useCallback(
    (question: string) => {
      following.current = true;
      let firstToken = true;
      setPending({ question, answer: '', done: false });
      const finish = () => {
        subscription.current = undefined;
        setPending((current) => current && { ...current, done: true });
        refreshSessions();
      };
      subscription.current = ChatService.ask(sessionId, question)
        .onNext((token) => {
          if (firstToken) {
            firstToken = false;
            refreshTitle();
          }
          setPending((current) => current && { ...current, answer: current.answer + token });
        })
        .onComplete(finish)
        .onError((message) => {
          showError(message || 'Could not get an answer.');
          finish();
        });
    },
    [sessionId, refreshSessions, refreshTitle],
  );

  useEffect(() => {
    setMessages(undefined);
    setPending(undefined);
    reload().catch((e) => {
      showError(errorMessage(e, 'Could not load the chat.'));
      navigate('/chat', { replace: true });
    });
    // Leaving the page stops a running answer.
    return () => subscription.current?.cancel();
  }, [reload, navigate]);

  // The first question comes from the new-chat screen.
  useEffect(() => {
    const question = (location.state as { question?: string } | null)?.question;
    if (question) {
      navigate(location.pathname, { replace: true, state: null });
      ask(question);
    }
  }, [location.state, location.pathname, navigate, ask]);

  // Once the answer is complete and fully typed out, swap in the saved message (with its citations).
  useEffect(() => {
    if (pending?.done && typed.length === pending.answer.length) {
      reload().finally(() => setPending(undefined));
    }
  }, [pending, typed, reload]);

  function stop() {
    subscription.current?.cancel();
    subscription.current = undefined;
    // The server saves the partial answer when it sees the cancel; give it a moment, then show the saved version.
    setTimeout(() => reload().finally(() => setPending(undefined)), 800);
  }

  async function rename() {
    const newTitle = title.trim();
    if (!newTitle || newTitle === savedTitle) {
      setTitle(savedTitle);
      return;
    }
    try {
      const session = await ChatService.renameSession(sessionId, newTitle);
      setTitle(session.title);
      setSavedTitle(session.title);
      refreshSessions();
    } catch (e) {
      setTitle(savedTitle);
      showError(errorMessage(e, 'Could not rename the chat.'));
    }
  }

  // Keep the newest text in view while the user hasn't scrolled up to read something earlier.
  useLayoutEffect(() => {
    const element = scroller.current;
    if (element && following.current) {
      element.scrollTop = element.scrollHeight;
    }
  }, [messages, pending?.question, typed]);

  const isEmpty = messages?.length === 0 && !pending;
  // A reload during streaming may already contain the pending question; show it only once.
  const last = messages?.at(-1);
  const shown =
    pending && last?.role === MessageRole.USER && last.content === pending.question ? messages?.slice(0, -1) : messages;

  return (
    <main className="chat">
      <header className="chat-header">
        <input
          className="chat-title"
          aria-label="Chat title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          onBlur={rename}
          onKeyDown={(e) => {
            if (e.key === 'Enter') e.currentTarget.blur();
            if (e.key === 'Escape') {
              setTitle(savedTitle);
              e.currentTarget.blur();
            }
          }}
        />
        {scope && (
          <span className="scope-pill" title={scope}>
            <LayersIcon size={13} />
            <span>{scope}</span>
          </span>
        )}
      </header>

      <div
        className="chat-scroll"
        ref={scroller}
        onScroll={(e) => {
          const el = e.currentTarget;
          following.current = el.scrollHeight - el.scrollTop - el.clientHeight < FOLLOW_THRESHOLD;
        }}
      >
        <div className="chat-column">
          {isEmpty && (
            <div className="empty-state">
              <h3>Ask your first question</h3>
              <p>Answers come only from your documents, with sources you can open.</p>
            </div>
          )}
          {shown?.map((message) =>
            message.role === MessageRole.USER ? (
              <UserMessage key={message.id} text={message.content} />
            ) : (
              <AssistantMessage
                key={message.id}
                id={message.id}
                text={message.content}
                citations={message.citations}
                time={new Date(message.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
              />
            ),
          )}
          {pending && (
            <>
              <UserMessage text={pending.question} />
              <AssistantMessage id="pending" text={typed} streaming />
            </>
          )}
        </div>
      </div>

      <div className="composer-wrap">
        <Composer onSend={ask} busy={!!pending} onStop={stop} placeholder="Ask a follow-up…" />
        <div className="composer-hint">Answers are generated from your documents and can be wrong. Check the sources.</div>
      </div>
    </main>
  );
}
