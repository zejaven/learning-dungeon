import { useEffect } from 'react';
import { useGeneration } from './engine/generationStore';
import { useStore } from './engine/store';
import { useUsage } from './engine/usageStore';
import { HomeScreen } from './screens/HomeScreen';
import { WorkspaceScreen } from './screens/WorkspaceScreen';

export function App() {
  const view = useStore((s) => s.view);
  const loadTopics = useStore((s) => s.loadTopics);
  const refreshActiveGenerations = useGeneration((s) => s.refreshActive);
  const startUsagePolling = useUsage((s) => s.start);

  useEffect(() => {
    loadTopics();
    // Reattach to any generation still running from before a reload.
    refreshActiveGenerations();
    // Begin polling Claude usage for the header meter (idempotent).
    startUsagePolling();
  }, [loadTopics, refreshActiveGenerations, startUsagePolling]);

  return view === 'workspace' ? <WorkspaceScreen /> : <HomeScreen />;
}
