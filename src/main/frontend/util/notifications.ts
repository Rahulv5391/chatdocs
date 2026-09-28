import { EndpointError } from '@vaadin/hilla-frontend';
import { Notification } from '@vaadin/react-components';

/** The server's message for endpoint errors (always user-friendly), otherwise the fallback. */
export function errorMessage(e: unknown, fallback: string) {
  return e instanceof EndpointError ? e.message : fallback;
}

export function showError(message: string) {
  Notification.show(message, { theme: 'error', position: 'bottom-end', duration: 5000 });
}

export function showSuccess(message: string) {
  Notification.show(message, { theme: 'success', position: 'bottom-end' });
}
