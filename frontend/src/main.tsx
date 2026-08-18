import React from 'react';
import ReactDOM from 'react-dom/client';
import { registerSW } from 'virtual:pwa-register';
import { App } from './App';
import { ErrorBoundary } from './shell/ErrorBoundary';
import './styles.css';

// Installs the service worker that makes the app open (and the downloaded
// lessons work) without the backend. A no-op in `npm run dev`, which does not
// generate one — use `npm run preview` to exercise it locally.
registerSW({ immediate: true });

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ErrorBoundary>
      <App />
    </ErrorBoundary>
  </React.StrictMode>,
);
