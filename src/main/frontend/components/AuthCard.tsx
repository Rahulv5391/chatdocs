import type { ReactNode } from 'react';
import { SparkIcon } from 'Frontend/components/Icons';

/** The centered card used by the login and sign-up pages. */
export default function AuthCard({
  title,
  subtitle,
  footer,
  children,
}: {
  title: string;
  subtitle: string;
  footer: ReactNode;
  children: ReactNode;
}) {
  return (
    <main className="login-page">
      <div className="login-card">
        <span className="brand-mark lg">
          <SparkIcon size={26} />
        </span>
        <h1>{title}</h1>
        <p className="subtitle">{subtitle}</p>
        {children}
        <div className="login-foot">{footer}</div>
      </div>
    </main>
  );
}
