import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { ViewConfig } from '@vaadin/hilla-file-router/types.js';
import { ChatService, DocumentService } from 'Frontend/generated/endpoints';
import type DocumentDto from 'Frontend/generated/com/company/chatdocs/dto/DocumentDto';
import DocumentStatus from 'Frontend/generated/com/company/chatdocs/entity/DocumentStatus';
import { useAuth } from 'Frontend/auth';
import Composer from 'Frontend/components/Composer';
import DocumentScopePicker from 'Frontend/components/DocumentScopePicker';
import { useChatSessions } from 'Frontend/components/ChatSessions';
import { AlertIcon, SparkIcon } from 'Frontend/components/Icons';
import { errorMessage, showError } from 'Frontend/util/notifications';

export const config: ViewConfig = {
  title: 'New chat',
};

function greeting() {
  const hour = new Date().getHours();
  if (hour < 12) return 'Good morning';
  if (hour < 18) return 'Good afternoon';
  return 'Good evening';
}

function suggestionsFor(documents: DocumentDto[]) {
  const [first, second] = documents.map((d) => d.fileName);
  if (!first) return [];
  return [
    `Summarize ${first} in a few bullet points`,
    `What are the most important rules in ${second ?? first}?`,
    'List any amounts, limits or deadlines mentioned in my documents',
    'What should a new employee know first?',
  ];
}

/** The start screen: pick documents, ask the first question, and the chat is created with it. */
export default function NewChatView() {
  const navigate = useNavigate();
  const { state } = useAuth();
  const { refresh } = useChatSessions();
  const [readyDocuments, setReadyDocuments] = useState<DocumentDto[]>();
  const [scope, setScope] = useState<string[]>([]);
  const [starting, setStarting] = useState(false);

  useEffect(() => {
    DocumentService.list().then((all) => setReadyDocuments(all.filter((d) => d.status === DocumentStatus.READY)));
  }, []);

  async function start(question: string) {
    setStarting(true);
    try {
      const session = await ChatService.createSession(scope);
      await refresh();
      navigate(`/chat/${session.id}`, { state: { question } });
    } catch (e) {
      showError(errorMessage(e, 'Could not start the chat.'));
      setStarting(false);
    }
  }

  const firstName = state.user?.displayName.split(' ')[0];

  return (
    <main className="welcome">
      <div className="welcome-inner">
        <div className="welcome-hero">
          <span className="brand-mark lg">
            <SparkIcon size={26} />
          </span>
          <h1>
            {greeting()}
            {firstName && (
              <>
                , <span className="gradient-text">{firstName}</span>
              </>
            )}
          </h1>
          <p>Ask anything. Answers come only from your documents, with sources.</p>
        </div>

        {readyDocuments?.length === 0 && (
          <div className="notice">
            <AlertIcon size={18} />
            <span>
              You have no ready documents yet, so there is nothing to answer from.{' '}
              <Link to="/documents">Upload documents</Link> first.
            </span>
          </div>
        )}

        <div className="composer-wrap">
          <Composer
            onSend={start}
            busy={starting}
            placeholder="Ask a question about your documents…"
            tools={
              readyDocuments &&
              readyDocuments.length > 0 && <DocumentScopePicker documents={readyDocuments} onChange={setScope} />
            }
          />
        </div>

        {!starting && (
          <div className="suggestions">
            {suggestionsFor(readyDocuments ?? []).map((suggestion) => (
              <button key={suggestion} className="suggestion" onClick={() => start(suggestion)}>
                <SparkIcon size={15} />
                {suggestion}
              </button>
            ))}
          </div>
        )}
      </div>
    </main>
  );
}
