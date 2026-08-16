import { Component, type ReactNode } from 'react';
import { ui, useLang } from '@app/i18n';

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
}

interface State {
  error: Error | null;
}

/**
 * Catches render errors so one broken visualizer or panel does not unmount the
 * whole app tree (React 18 removes the entire tree on an uncaught render
 * error). Wrap a panel and pass a `key` that changes with its input to let the
 * user recover by navigating. Language is read via getState() — fine for a
 * static error screen.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  render() {
    if (this.state.error) {
      if (this.props.fallback) return this.props.fallback;
      const lang = useLang.getState().lang;
      return (
        <div style={{ padding: 16, fontSize: 13, opacity: 0.8 }}>
          {ui('errorBoundary', lang)}{' '}
          <button onClick={() => this.setState({ error: null })}>{ui('errorRetry', lang)}</button>
        </div>
      );
    }
    return this.props.children;
  }
}
