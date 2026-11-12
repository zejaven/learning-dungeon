import { useRef, type MouseEvent, type PointerEvent } from 'react';

/**
 * Props for a `.dialog-backdrop` that closes the dialog on a click outside it,
 * but only when the press STARTED on the backdrop too.
 *
 * A plain `onClick` also fires for presses that began elsewhere: a touch
 * browser (phone, VR headset) sends the click of the tap that OPENED the
 * dialog to whatever is under the finger on release, which is the new
 * backdrop, so the dialog vanished at once; and with a mouse, selecting text
 * inside the dialog and releasing outside it closed the dialog too.
 *
 * @param onClose called on a genuine backdrop click; `undefined` disables it
 *                (e.g. while the dialog is busy)
 */
export function useBackdropClose(onClose: (() => void) | undefined) {
  const pressedOnBackdrop = useRef(false);
  return {
    onPointerDown: (e: PointerEvent<HTMLElement>) => {
      pressedOnBackdrop.current = e.target === e.currentTarget;
    },
    onClick: (e: MouseEvent<HTMLElement>) => {
      const genuine = pressedOnBackdrop.current && e.target === e.currentTarget;
      pressedOnBackdrop.current = false;
      if (genuine) onClose?.();
    },
  };
}
