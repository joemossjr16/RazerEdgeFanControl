package com.joemo.razeredgefan;

import android.animation.ObjectAnimator;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Small Razer-style motion touches: a punchy press animation, a breathing title glow, and a
 *  fade/slide entrance - kept as static helpers since none of it holds state per-view. */
final class UiAnim {

    private UiAnim() {
    }

    /**
     * Dims a view on press and fades it back on release, on top of its normal click. Animates
     * alpha rather than scale deliberately - scaling a view during an active touch gesture
     * shifts Android's own hit-test math (the matrix inversion used to decide whether the
     * eventual ACTION_UP still lands "inside" the view), and was observed to intermittently
     * swallow clicks entirely. Alpha is a pure rendering property, so it can't do that.
     */
    static void punch(View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().alpha(0.55f).setDuration(80).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().alpha(1f).setDuration(150).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    /** A slow alpha pulse, like Razer Chroma breathing lighting. */
    static void breathe(View view) {
        ObjectAnimator anim = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0.5f);
        anim.setDuration(1400);
        anim.setRepeatMode(ObjectAnimator.REVERSE);
        anim.setRepeatCount(ObjectAnimator.INFINITE);
        anim.start();
    }

    /** Fades and slides a view up into place - call once, right after setContentView. */
    static void enter(View view) {
        view.setAlpha(0f);
        view.setTranslationY(60f);
        view.animate().alpha(1f).translationY(0f).setDuration(450)
                .setInterpolator(new DecelerateInterpolator()).start();
    }
}
