import { useEffect } from 'react';
import { currentReviewItem, useReview } from '@app/engine/reviewStore';
import { navigate } from '@app/engine/router';
import { tl, ui, useLang } from '@app/i18n';
import { ExerciseCard } from '@app/shell/lesson/ExerciseCard';
import { LangSwitcher } from '@app/shell/LangSwitcher';
import { ReviewTree } from '@app/shell/ReviewTree';
import { SettingsButton } from '@app/shell/SettingsButton';
import { ThemeSwitcher } from '@app/shell/ThemeSwitcher';

/**
 * Global review over the practice exercises of fully completed lessons. Whether
 * an exercise is in the list is persistent per-exercise server state; this
 * screen walks a shuffled client queue over it (wrong answers requeue).
 *
 * The left tree mirrors the home catalog but only lists topics that currently
 * have pooled exercises; its per-topic checkboxes toggle whether a topic takes
 * part, and the ↺ button returns a topic's answered exercises (see
 * {@link ReviewTree}). A global "start over" returns every answered exercise.
 */
export function ReviewScreen() {
  const lang = useLang((s) => s.lang);
  const review = useReview();
  const item = currentReviewItem(review.queue);

  useEffect(() => {
    void useReview.getState().start();
    return () => useReview.getState().reset();
  }, []);

  const poolExists = review.topics.length > 0;
  const anyEnabled = review.topics.some((t) => t.enabled);
  const remaining = review.queue.length;

  return (
    <div className="app">
      <header className="header">
        <button onClick={() => navigate('/')}>{ui('backHome', lang)}</button>
        <h1>{ui('reviewTitle', lang)}</h1>
        <SettingsButton />
        <div className="spacer" />
        {item && (
          <span className="review-progress">
            {review.answered} {ui('reviewProgress', lang)} · {remaining} {ui('reviewRemaining', lang)}
          </span>
        )}
        <ThemeSwitcher />
        <LangSwitcher />
      </header>

      <div className="home-main">
        {/* Left: the pooled-topics tree with per-topic checkboxes + restart. */}
        <section className="panel home-tree-panel">
          <div className="panel-title tree-panel-title">
            <span>{ui('reviewTopicsTitle', lang)}</span>
            {poolExists && (
              <button
                className="tree-add-btn"
                title={ui('reviewRestartAll', lang)}
                onClick={() => void review.restartAll()}
              >
                ↺
              </button>
            )}
          </div>
          <div className="panel-body tree-body">
            {poolExists ? (
              <ReviewTree />
            ) : (
              <p className="home-hint review-tree-empty">{ui('reviewEmpty', lang)}</p>
            )}
          </div>
        </section>

        {/* Right: the current review exercise. */}
        <section className="panel">
          <div className="review-main">
            {review.loading && <p className="home-hint">{ui('loading', lang)}</p>}
            {review.error && <p className="home-hint">⚠️ {review.error}</p>}

            {!review.loading && !review.error && !item && !poolExists && (
              <p className="home-hint review-empty">{ui('reviewEmpty', lang)}</p>
            )}

            {!review.loading && !review.error && !item && poolExists && !anyEnabled && (
              <p className="home-hint review-empty">{ui('reviewAllOff', lang)}</p>
            )}

            {!review.loading && !review.error && !item && poolExists && anyEnabled && (
              <div className="review-finished">
                <div className="lesson-banner done">{ui('reviewFinished', lang)}</div>
                <button className="primary" onClick={() => void review.restartAll()}>
                  {ui('reviewStartAgain', lang)}
                </button>
              </div>
            )}

            {!review.loading && item && (
              <div className="review-card">
                <div className="review-topic-title">{tl(item.topicTitle, lang)}</div>
                <ExerciseCard
                  key={`${item.topicId}:${item.exercise.id}`}
                  exercise={item.exercise}
                  phase={review.phase}
                  lastCorrect={review.lastCorrect}
                  presetAnswer={review.lastAnswer}
                  onSubmit={(answer) => void review.submit(answer)}
                  onContinue={() => review.next()}
                />
              </div>
            )}
          </div>
        </section>
      </div>
    </div>
  );
}
