import { getVisualizer } from '@app/engine/topicRegistry';
import { useStore } from '@app/engine/store';
import { EventLog } from '@app/primitives/EventLog';

export function VisualizationCanvas() {
  const topic = useStore((s) => s.topic);
  const events = useStore((s) => s.events);
  const stepIndex = useStore((s) => s.stepIndex);
  const setStep = useStore((s) => s.setStep);
  const stepNext = useStore((s) => s.stepNext);
  const stepPrev = useStore((s) => s.stepPrev);

  if (!topic) return null;

  const Visualizer = getVisualizer(topic.id);
  const currentEvent = stepIndex >= 0 && stepIndex < events.length ? events[stepIndex] : null;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div>
        {Visualizer ? (
          <Visualizer event={currentEvent} />
        ) : (
          <div style={{ opacity: 0.6, fontSize: 13 }}>
            No visualizer found for <code>{topic.id}</code>. Showing the event log only.
          </div>
        )}
      </div>

      <div className="playback">
        <button onClick={stepPrev} disabled={events.length === 0 || stepIndex <= 0}>
          ◀ Prev
        </button>
        <button
          onClick={stepNext}
          disabled={events.length === 0 || stepIndex >= events.length - 1}
        >
          Next ▶
        </button>
        <span>
          {events.length > 0 ? `Step ${stepIndex + 1} / ${events.length}` : 'No steps yet'}
        </span>
      </div>

      <div>
        <div className="panel-title" style={{ border: 'none', padding: '4px 0' }}>
          Event log — click a step
        </div>
        <EventLog events={events} currentStep={stepIndex} onSelect={setStep} />
      </div>
    </div>
  );
}
