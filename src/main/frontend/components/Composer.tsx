import { type ReactNode, useEffect, useRef, useState } from 'react';
import { ArrowUpIcon, StopIcon } from 'Frontend/components/Icons';

type ComposerProps = {
  onSend: (text: string) => void;
  /** While an answer streams in, the send button becomes a stop button. */
  busy?: boolean;
  onStop?: () => void;
  placeholder?: string;
  /** Extra controls shown at the bottom left, such as the document picker. */
  tools?: ReactNode;
};

/** A chat input like ChatGPT's: grows with the text, Enter sends, Shift+Enter adds a line. */
export default function Composer({ onSend, busy = false, onStop, placeholder, tools }: ComposerProps) {
  const [text, setText] = useState('');
  const input = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    const textarea = input.current;
    if (textarea) {
      textarea.style.height = 'auto';
      textarea.style.height = `${textarea.scrollHeight}px`;
    }
  }, [text]);

  useEffect(() => {
    if (!busy) {
      input.current?.focus();
    }
  }, [busy]);

  function send() {
    const question = text.trim();
    if (!question || busy) return;
    onSend(question);
    setText('');
  }

  const button = busy ? (
    <button className="send-button stop" aria-label="Stop generating" onClick={onStop}>
      <StopIcon size={16} />
    </button>
  ) : (
    <button className="send-button" aria-label="Send" disabled={!text.trim()} onClick={send}>
      <ArrowUpIcon size={18} />
    </button>
  );

  // Without extra tools the button sits next to the text, like ChatGPT's follow-up box.
  return (
    <div className={tools ? 'composer' : 'composer inline'}>
      <textarea
        ref={input}
        rows={1}
        value={text}
        placeholder={placeholder ?? 'Ask anything about your documents…'}
        aria-label="Message"
        onChange={(e) => setText(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
            e.preventDefault();
            send();
          }
        }}
      />
      {tools ? (
        <div className="composer-bar">
          <div className="spacer">{tools}</div>
          {button}
        </div>
      ) : (
        button
      )}
    </div>
  );
}
