import type { ComponentType } from 'react';
import type { VisualizerProps } from './traceTypes';

/**
 * Discovers every topic's visualizer at build time. Adding a new
 * topics/<id>/visualizer.tsx folder makes it appear here automatically (Vite
 * picks it up on reload). Visualizers import primitives/engine via the @app
 * alias, so their location outside the frontend root does not matter.
 */
const modules = import.meta.glob('../../../topics/*/visualizer.tsx', { eager: true });

const registry: Record<string, ComponentType<VisualizerProps>> = {};

for (const [filePath, mod] of Object.entries(modules)) {
  const match = filePath.match(/topics\/([^/]+)\/visualizer\.tsx$/);
  const component = (mod as { default?: ComponentType<VisualizerProps> }).default;
  if (match && component) {
    registry[match[1]] = component;
  }
}

export function getVisualizer(id: string): ComponentType<VisualizerProps> | null {
  return registry[id] ?? null;
}

export function hasVisualizer(id: string): boolean {
  return id in registry;
}
