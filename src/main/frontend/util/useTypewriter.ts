import { useEffect, useRef, useState } from 'react';

/**
 * Reveals streamed text a few characters per frame, so the answer types out smoothly even when Gemini sends it in
 * large pieces. The further behind it is, the faster it types.
 * <p>
 * Browsers pause animation frames in background tabs, so there the full text is shown at once; otherwise an answer
 * would stay unfinished (and unsaved in the view) until the user came back.
 */
export function useTypewriter(target: string) {
  const [length, setLength] = useState(0);
  const targetLength = useRef(target.length);
  targetLength.current = target.length;

  useEffect(() => {
    if (length > target.length || (length < target.length && document.hidden)) {
      setLength(target.length);
      return;
    }
    if (length === target.length) return;
    const frame = requestAnimationFrame(() => {
      const backlog = target.length - length;
      setLength(length + Math.max(2, Math.ceil(backlog / 18)));
    });
    return () => cancelAnimationFrame(frame);
  }, [length, target]);

  // Leaving the tab mid-answer would freeze a pending frame; catch up instead.
  useEffect(() => {
    const onVisibilityChange = () => {
      if (document.hidden) setLength(targetLength.current);
    };
    document.addEventListener('visibilitychange', onVisibilityChange);
    return () => document.removeEventListener('visibilitychange', onVisibilityChange);
  }, []);

  return target.slice(0, length);
}
