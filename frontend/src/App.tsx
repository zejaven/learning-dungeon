import { useEffect } from 'react';
import { useStore } from './engine/store';
import { HomeScreen } from './screens/HomeScreen';
import { WorkspaceScreen } from './screens/WorkspaceScreen';

export function App() {
  const view = useStore((s) => s.view);
  const loadTopics = useStore((s) => s.loadTopics);

  useEffect(() => {
    loadTopics();
  }, [loadTopics]);

  return view === 'workspace' ? <WorkspaceScreen /> : <HomeScreen />;
}
