import { useEffect } from 'react';
import { currentReviewItem, useReview } from '@app/engine/reviewStore';
import { navigate } from '@app/engine/router';
import { tl, ui, useLang } from '@app/i18n';
import { ExerciseCard } from '@app/shell/lesson/ExerciseCard';
import { LangSwitcher } from '@app/shell/LangSwitcher';
import { ThemeSwitcher } from '@app/shell/ThemeSwitcher';

/**
 * Global review over the practice exercises of fully completed lessons: the
 * pool is shuffled into a server-persisted session; wrong answers re-enter the
 * queue tail until answered correctly. Reachable from the home header (#/review).
 */
export function ReviewScreen() {
  const lang = useLang((s) => s.lang);
  const review = useReview();
  const session = review.session;
  // The cursor stays on the answered item until Continue (the store defers the
  // advance), so during feedback this is still the item just answered.
  const item = currentReviewItem(session);

  useEffect(() => {
    void useReview.getState().start();
    return () => useReview.getState().reset();
  }, []);

  const total = session?.items.length ?? 0;
  const remaining = session ? session.queue.length - session.position : 0;

  return (
    <div className="app">
      <header className="header">
        <button onClick={() => navigate('/')}>{ui('backHome', lang)}</button>
        <h1>{ui('reviewTitle', lang)}</h1>
        <div className="spacer" />
        {session && total > 0 && (
          <span className="review-progress">
            {review.answered} {ui('reviewProgress', lang)} · {remaining} {ui('reviewRemaining', lang)}
          </span>
        )}
        <ThemeSwitcher />
        <LangSwitcher />
      </header>

      <div className="review-main">
        {review.loading && <p className="home-hint">{ui('loading', lang)}</p>}
        {review.error && <p className="home-hint">⚠️ {review.error}</p>}

        {!review.loading && !review.error && session && total === 0 && (
          <p className="home-hint review-empty">{ui('reviewEmpty', lang)}</p>
        )}

        {!review.loading && session && session.finished && total > 0 && (
          <div className="review-finished">
            <div className="lesson-banner done">{ui('reviewFinished', lang)}</div>
            <button
              className="primary"
              onClick={async () => {
                await useReview.getState().abandon();
                void useReview.getState().start();
              }}
            >
              {ui('reviewStartAgain', lang)}
            </button>
          </div>
        )}

        {!review.loading && item && !session?.finished && (
          <div className="review-card">
            <div className="review-topic-title">{tl(item.topicTitle, lang)}</div>
            <ExerciseCard
              key={item.exercise.id}
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
    </div>
  );
}
