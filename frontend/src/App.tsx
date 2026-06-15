import { useEffect } from 'react';
import { useGeneration } from './engine/generationStore';
import { useStore } from './engine/store';
import { HomeScreen } from './screens/HomeScreen';
import { WorkspaceScreen } from './screens/WorkspaceScreen';

export function App() {
  const view = useStore((s) => s.view);
  const loadTopics = useStore((s) => s.loadTopics);
  const refreshActiveGenerations = useGeneration((s) => s.refreshActive);

  useEffect(() => {
    loadTopics();
    // Reattach to any generation still running from before a reload.
    refreshActiveGenerations();
  }, [loadTopics, refreshActiveGenerations]);

  return view === 'workspace' ? <WorkspaceScreen /> : <HomeScreen />;
}
