import { useEffect, useState } from 'react';
import { domainById } from '@app/domains';
import { useDomain } from '@app/engine/domainStore';
import { formatUntil } from '@app/engine/duration';
import {
  currentReviewItem,
  itemState,
  nextDueAt,
  topicCounts,
  useReview,
} from '@app/engine/reviewStore';
import { navigate } from '@app/engine/router';
import { fmtUi, tl, ui, useLang } from '@app/i18n';
import { ExerciseCard } from '@app/shell/lesson/ExerciseCard';
import { LangSwitcher } from '@app/shell/LangSwitcher';
import { OfflineBadge } from '@app/shell/OfflineBadge';
import { ReviewTree } from '@app/shell/ReviewTree';
import { SettingsButton } from '@app/shell/SettingsButton';
import { ThemeSwitcher } from '@app/shell/ThemeSwitcher';

/**
 * Global review over the practice exercises of fully completed lessons, driven
 * by a spaced-repetition schedule: an exercise comes back on its own date, stays
 * answerable for a window, and leaves the pool for good once it has walked the
 * whole ladder.
 *
 * "Nothing due" is therefore a normal, frequent state, not an empty pool — it is
 * the system working, so the screen says when the next repetition lands instead
 * of offering a way to drag everything back. The left tree toggles which topics
 * take part and offers ▶ to drill one ahead of schedule without affecting it
 * (see {@link ReviewTree}); throwing the schedule away lives in Settings.
 */
export function ReviewScreen() {
  const lang = useLang((s) => s.lang);
  const review = useReview();
  const item = currentReviewItem(review.queue);
  const domainId = useDomain((s) => s.domainId);
  const domain = domainById(domainId);
  // Single-column profile: the topic filter is a sheet over the exercise, not a
  // sidebar. On desktop both panels show and this flag is inert.
  const [showTopics, setShowTopics] = useState(false);

  // Review is scoped to the active domain; restart if it changes.
  useEffect(() => {
    void useReview.getState().start();
    return () => useReview.getState().reset();
  }, [domainId]);

  const poolExists = review.topics.length > 0;
  const anyEnabled = review.topics.some((t) => t.enabled);
  const remaining = review.queue.length;
  const ahead = review.mode === 'ahead';

  // How much of this run's backlog did not fit the batch cap.
  const more = Math.max(0, review.offered - review.queue.length - review.answered);
  const nextDue = nextDueAt(review.topics, review.clock);
  const dueNow = review.topics.reduce(
    (n, t) => (t.enabled ? n + topicCounts(t, review.clock).due : n),
    0,
  );
  const idle = !review.loading && !review.error && !item;
  const state = item ? itemState(item, review.clock) : null;

  return (
    <div className="app">
      <header className="header">
        <button onClick={() => navigate('/')}>{ui('backHome', lang)}</button>
        <h1>
          {ui('reviewTitle', lang)} · {domain.icon} {tl(domain.title, lang)}
        </h1>
        <SettingsButton />
        <OfflineBadge />
        <button
          className="mobile-only"
          title={ui('reviewTopicsTitle', lang)}
          onClick={() => setShowTopics((v) => !v)}
        >
          ☰
        </button>
        <div className="spacer" />
        {item && (
          <span className="review-progress">
            {review.answered} {ui('reviewProgress', lang)} · {remaining} {ui('reviewRemaining', lang)}
          </span>
        )}
        <ThemeSwitcher />
        <LangSwitcher />
      </header>

      <div className={`home-main${showTopics ? '' : ' has-detail'}`}>
        {/* Left: the pooled-topics tree with per-topic checkboxes + practise-ahead. */}
        <section className="panel home-tree-panel">
          <div className="panel-title tree-panel-title">
            <span>{ui('reviewTopicsTitle', lang)}</span>
            <div className="tree-title-actions">
              <button className="mobile-only tree-add-btn" onClick={() => setShowTopics(false)}>
                ✕
              </button>
            </div>
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
        <section className="panel home-detail-panel">
          <div className="review-main">
            {review.loading && <p className="home-hint">{ui('loading', lang)}</p>}
            {review.error && <p className="home-hint">⚠️ {review.error}</p>}

            {idle && !poolExists && <p className="home-hint review-empty">{ui('reviewEmpty', lang)}</p>}

            {idle && poolExists && !anyEnabled && !ahead && (
              <p className="home-hint review-empty">{ui('reviewAllOff', lang)}</p>
            )}

            {/* An ahead-of-schedule run that is over (or had nothing to offer). */}
            {idle && poolExists && ahead && (
              <div className="review-finished">
                <div className="lesson-banner done">
                  {ui(review.answered > 0 ? 'reviewAheadDone' : 'reviewAheadEmpty', lang)}
                </div>
                <button className="primary" onClick={() => void review.start()}>
                  {ui('reviewBackToDue', lang)}
                </button>
              </div>
            )}

            {/* Scheduled run: either the batch is done with a backlog behind it,
                or genuinely nothing is due and the next date is what matters. */}
            {idle && poolExists && anyEnabled && !ahead && (
              <div className="review-finished">
                {more > 0 || dueNow > 0 ? (
                  // The batch is done but a backlog is waiting: claiming
                  // everything is answered would be a lie, so only offer the rest.
                  <button className="primary" onClick={() => void review.continueRun()}>
                    {fmtUi('reviewMore', lang, Math.max(more, dueNow))}
                  </button>
                ) : (
                  <>
                    <div className="lesson-banner done">{ui('reviewFinished', lang)}</div>
                    <p className="home-hint review-empty">{ui('reviewNothingDue', lang)}</p>
                    {nextDue && (
                      <p className="review-next-due">
                        ⏳ {fmtUi('reviewNextDue', lang, formatUntil(nextDue, lang))}
                      </p>
                    )}
                  </>
                )}
              </div>
            )}

            {!review.loading && item && (
              <div className="review-card">
                {ahead && <div className="lesson-banner review-ahead">{ui('reviewAheadBanner', lang)}</div>}
                <div className="review-topic-title">
                  <span>{tl(item.topicTitle, lang)}</span>
                  <span className="review-step">
                    {fmtUi('reviewStep', lang, item.step + 1, item.totalSteps)}
                  </span>
                </div>
                {state === 'lapsed' && (
                  <div className="lesson-banner review-lapsed">{ui('reviewLapsed', lang)}</div>
                )}
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
