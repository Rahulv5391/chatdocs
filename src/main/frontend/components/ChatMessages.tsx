import { type RefObject, useEffect, useRef, useState } from 'react';
import { Markdown, Tooltip } from '@vaadin/react-components';
import type Citation from 'Frontend/generated/com/company/chatdocs/dto/Citation';
import CitationChips from 'Frontend/components/CitationChips';
import { CheckIcon, CopyIcon, SparkIcon } from 'Frontend/components/Icons';

export function UserMessage({ text }: { text: string }) {
  return (
    <div className="msg user">
      <div className="bubble">{text}</div>
    </div>
  );
}

type AssistantMessageProps = {
  id: string;
  text: string;
  citations?: Citation[];
  time?: string;
  /** Shows a blinking caret while the answer is still coming in. */
  streaming?: boolean;
};

export function AssistantMessage({ id, text, citations = [], time, streaming = false }: AssistantMessageProps) {
  const answer = useRef<HTMLDivElement>(null);
  // The answer element only exists once text has arrived.
  useStreamingCaret(answer, streaming && text.length > 0);

  return (
    <div className="msg assistant">
      <span className="avatar ai">
        <SparkIcon size={16} />
      </span>
      <div className="body">
        {text ? (
          <div className="answer" ref={answer}>
            <Markdown>{text}</Markdown>
          </div>
        ) : (
          <TypingIndicator />
        )}
        {!streaming && <CitationChips citations={citations} idPrefix={id} />}
        {!streaming && text && (
          <div className="msg-actions">
            <CopyButton id={`${id}-copy`} text={text} />
            {time && <span className="time">{time}</span>}
          </div>
        )}
      </div>
    </div>
  );
}

/**
 * Keeps a blinking caret right after the last word of a streaming answer, even inside lists or bold text.
 * The markdown is re-rendered on every update, so the caret is put back whenever the content changes.
 */
function useStreamingCaret(container: RefObject<HTMLElement | null>, streaming: boolean) {
  useEffect(() => {
    const root = container.current;
    if (!streaming || !root) return;

    const placeCaret = () => {
      const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
        acceptNode: (node) => (node.textContent?.trim() ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_SKIP),
      });
      let lastText: Node | null = null;
      while (walker.nextNode()) lastText = walker.currentNode;
      const existing = root.querySelector('.caret');
      if (!lastText || (existing && lastText.nextSibling === existing)) return;
      existing?.remove();
      const caret = document.createElement('span');
      caret.className = 'caret';
      lastText.parentNode?.insertBefore(caret, lastText.nextSibling);
    };

    placeCaret();
    const observer = new MutationObserver(placeCaret);
    observer.observe(root, { childList: true, subtree: true, characterData: true });
    return () => {
      observer.disconnect();
      root.querySelector('.caret')?.remove();
    };
  }, [container, streaming]);
}

function TypingIndicator() {
  return (
    <div className="typing" role="status">
      <span className="dots">
        <span />
        <span />
        <span />
      </span>
      <span className="label">Searching your documents…</span>
    </div>
  );
}

function CopyButton({ id, text }: { id: string; text: string }) {
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!copied) return;
    const timer = setTimeout(() => setCopied(false), 1500);
    return () => clearTimeout(timer);
  }, [copied]);

  return (
    <>
      <button
        id={id}
        className="icon-button"
        aria-label="Copy answer"
        onClick={() => navigator.clipboard.writeText(text).then(() => setCopied(true))}
      >
        {copied ? <CheckIcon size={15} /> : <CopyIcon size={15} />}
      </button>
      <Tooltip for={id} text={copied ? 'Copied' : 'Copy'} position="bottom" />
    </>
  );
}
