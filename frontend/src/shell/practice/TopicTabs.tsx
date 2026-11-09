import { useEffect } from 'react';
import { useCodeTasks } from '@app/engine/codeTaskStore';
import { navigate, routeForQuestion, routeForTasks, routeForTheory } from '@app/engine/router';
import { useStore } from '@app/engine/store';
import { ui, useLang } from '@app/i18n';
import { RegenerateLessonButton } from '@app/shell/lesson/RegenerateLessonButton';
import type { TopicDetail } from '@app/engine/traceTypes';

/**
 * Whether the topic has a Practice tab: it has code tasks, or it could get
 * them generated (a Java topic with a lesson, since every task binds to an atom).
 */
export function hasPractice(topic: TopicDetail): boolean {
  return topic.hasTasks || (topic.domainId === 'java' && topic.hasAtoms);
}

/**
 * Lesson / Reference / Practice switch of a topic, plus the lesson's rarely used
 * regenerate action on the right. Renders for a topic with a lesson or code
 * tasks; a bare theory topic has nothing to switch to. Practice is desktop-only
 * because running code is refused to remote clients.
 */
export function TopicTabs({ questionId, active }: { questionId: string; active: 'lesson' | 'theory' | 'tasks' }) {
  const topic = useStore((s) => s.topic);
  const items = useCodeTasks((s) => s.items);
  const load = useCodeTasks((s) => s.load);
  const lang = useLang((s) => s.lang);

  const topicId = topic && hasPractice(topic) ? topic.id : null;
  useEffect(() => {
    if (topicId) void load(topicId);
  }, [topicId, load]);

  if (!topic || (!hasPractice(topic) && !topic.hasAtoms)) return null;

  const solved = items?.filter((i) => i.progress.solved).length ?? 0;
  return (
    <div className="topic-tabs">
      {topic.hasAtoms && (
        <button
          className={active === 'lesson' ? 'active' : ''}
          onClick={() => navigate(routeForQuestion(questionId))}
        >
          {ui('tabLesson', lang)}
        </button>
      )}
      <button
        className={active === 'theory' ? 'active' : ''}
        onClick={() => navigate(routeForTheory(questionId))}
      >
        {ui('reference', lang)}
      </button>
      {hasPractice(topic) && (
        <button
          className={`desktop-only${active === 'tasks' ? ' active' : ''}`}
          onClick={() => navigate(routeForTasks(questionId))}
        >
          {ui('tabPractice', lang)}
          {items && items.length > 0 && ` (${solved}/${items.length})`}
        </button>
      )}
      {active === 'lesson' && <RegenerateLessonButton topicId={topic.id} />}
    </div>
  );
}
