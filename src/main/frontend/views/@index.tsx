import { Navigate } from 'react-router';

/** The chat screen is the home page. */
export default function IndexView() {
  return <Navigate to="/chat" replace />;
}
