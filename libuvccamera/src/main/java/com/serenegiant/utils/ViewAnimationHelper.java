package com.serenegiant.utils;
/*
 * libcommon
 * utility/helper classes for myself
 *
 * Copyright (c) 2014-2018 saki t_saki@serenegiant.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import android.util.Log;
import android.view.View;

import com.serenegiant.uvccamera.R;

public class ViewAnimationHelper {
    private static final String TAG = ViewAnimationHelper.class.getSimpleName();

    private static final long DEFAULT_DURATION_MS = 500L;

    public static final int ANIMATION_UNKNOWN = -1;
    public static final int ANIMATION_FADE_OUT = 0;
    public static final int ANIMATION_FADE_IN = 1;
    public static final int ANIMATION_ZOOM_IN = 2;
    public static final int ANIMATION_ZOOM_OUT = 3;

    public interface ViewAnimationListener {
        public void onAnimationStart(@NonNull final Animator animator, @NonNull final View target, final int animationType);

        public void onAnimationEnd(@NonNull final Animator animator, @NonNull final View target, final int animationType);

        public void onAnimationCancel(@NonNull final Animator animator, @NonNull final View target, final int animationType);
    }

    /**
     * Change the alpha value from 0 to 1 (fade the View in)
     *
     * 将 alpha 值从 0 变化到 1（使 View 淡入）
     *
     * @param target
     * @param duration   Default value (0.5 sec) is used if 0 or less / 0 以下则使用默认值（0.5 秒）
     * @param startDelay
     * @param listener
     */
    @SuppressLint("NewApi")
    public static void fadeIn(final View target, final long duration, final long startDelay, final ViewAnimationListener listener) {
//		if (DEBUG) Log.v(TAG, "fadeIn:target=" + target);
        if (target == null) return;
        target.postDelayed(new Runnable() {
            @Override
            public void run() {
                target.setVisibility(View.VISIBLE);
                target.setTag(R.id.anim_type, ANIMATION_FADE_IN);    // marker for fade in / 淡入时的标记
                target.setTag(R.id.anim_listener, listener);
                target.setScaleX(1.0f);
                target.setScaleY(1.0f);
                target.setAlpha(0.0f);
                final ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(target, "alpha", 0f, 1f);
                objectAnimator.addListener(mAnimatorListener);
                // cancel itself if another Animator starts on the same target
                // 若同一目标上启动了其他 Animator，则取消自身
                objectAnimator.setAutoCancel(true);
                objectAnimator.setDuration(duration > 0 ? duration : DEFAULT_DURATION_MS);
                objectAnimator.setStartDelay(startDelay > 0 ? startDelay : 0);    // time until start / 开始前的延迟时间
                objectAnimator.start();                        // start the animation / 开始动画
            }
        }, 100);
    }

    /**
     * Change the alpha value from 1 to 0 (fade the View out)
     *
     * 将 alpha 值从 1 变化到 0（使 View 淡出）
     *
     * @param target
     * @param duration   Default value (0.5 sec) is used if 0 or less / 0 以下则使用默认值（0.5 秒）
     * @param startDelay
     * @param listener
     */
    @SuppressLint("NewApi")
    public static void fadeOut(final View target, final long duration, final long startDelay, final ViewAnimationListener listener) {
//		if (DEBUG) Log.v(TAG, "fadeOut,target=" + target);
        if ((target != null) && (target.getVisibility() == View.VISIBLE)) {
            target.postDelayed(new Runnable() {
                @Override
                public void run() {
                    target.setTag(R.id.anim_type, ANIMATION_FADE_OUT);    // marker for fade out / 淡出时的标记
                    target.setTag(R.id.anim_listener, listener);
                    target.setScaleX(1.0f);
                    target.setScaleY(1.0f);
                    target.setAlpha(1.0f);
                    final ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(target, "alpha", 1f, 0f);
                    objectAnimator.addListener(mAnimatorListener);
                    // cancel itself if another Animator starts on the same target
                    // 若同一目标上启动了其他 Animator，则取消自身
                    objectAnimator.setAutoCancel(true);
                    objectAnimator.setDuration(duration > 0 ? duration : DEFAULT_DURATION_MS);
                    objectAnimator.setStartDelay(startDelay > 0 ? startDelay : 0);    // time until start / 开始前的延迟时间
                    objectAnimator.start();                        // start the animation / 开始动画
                }
            }, 100);
        }
    }

    /**
     * Change the scale from 0 to 1 (zoom the View in)
     *
     * 将缩放比例从 0 变化到 1（使 View 放大进入）
     *
     * @param target
     * @param duration   Default value (0.5 sec) is used if 0 or less / 0 以下则使用默认值（0.5 秒）
     * @param startDelay
     * @param listener
     */
    @SuppressLint("NewApi")
    public static void zoomIn(final View target, final long duration, final long startDelay, final ViewAnimationListener listener) {
//		if (DEBUG) Log.v(TAG, "zoomIn:target=" + target);
        if (target == null) return;
        target.postDelayed(new Runnable() {
            @Override
            public void run() {
                target.setVisibility(View.VISIBLE);
                target.setTag(R.id.anim_type, ANIMATION_ZOOM_IN);    // marker for zoom in / 放大时的标记
                target.setTag(R.id.anim_listener, listener);
                target.setScaleX(0.0f);
                target.setScaleY(0.0f);
                target.setAlpha(1.0f);
                final PropertyValuesHolder scale_x = PropertyValuesHolder.ofFloat("scaleX", 0.01f, 1f);
                final PropertyValuesHolder scale_y = PropertyValuesHolder.ofFloat("scaleY", 0.01f, 1f);
                final ObjectAnimator objectAnimator = ObjectAnimator.ofPropertyValuesHolder(target, scale_x, scale_y);
                objectAnimator.addListener(mAnimatorListener);
                // cancel itself if another Animator starts on the same target
                // 若同一目标上启动了其他 Animator，则取消自身
                objectAnimator.setAutoCancel(true);
                objectAnimator.setDuration(duration > 0 ? duration : DEFAULT_DURATION_MS);
                objectAnimator.setStartDelay(startDelay > 0 ? startDelay : 0);    // time until start / 开始前的延迟时间
                objectAnimator.start();                        // start the animation / 开始动画
            }
        }, 100);
    }

    /**
     * Change the scale from 1 to 0 (zoom the View out)
     *
     * 将缩放比例从 1 变化到 0（使 View 缩小退出）
     *
     * @param target
     * @param duration   Default value (0.5 sec) is used if 0 or less / 0 以下则使用默认值（0.5 秒）
     * @param startDelay
     * @param listener
     */
    @SuppressLint("NewApi")
    public static void zoomOut(final View target, final long duration, final long startDelay, final ViewAnimationListener listener) {
//		if (DEBUG) Log.v(TAG, "zoomIn:target=" + target);
        if (target == null) return;
        target.postDelayed(new Runnable() {
            @Override
            public void run() {
                target.setVisibility(View.VISIBLE);
                target.setTag(R.id.anim_type, ANIMATION_ZOOM_OUT);    // marker for zoom out / 缩小时的标记
                target.setTag(R.id.anim_listener, listener);
                target.setScaleX(1.0f);
                target.setScaleY(1.0f);
                target.setAlpha(1.0f);
                final PropertyValuesHolder scale_x = PropertyValuesHolder.ofFloat("scaleX", 1f, 0f);
                final PropertyValuesHolder scale_y = PropertyValuesHolder.ofFloat("scaleY", 1f, 0f);
                final ObjectAnimator objectAnimator = ObjectAnimator.ofPropertyValuesHolder(target, scale_x, scale_y);
                objectAnimator.addListener(mAnimatorListener);
                // cancel itself if another Animator starts on the same target
                // 若同一目标上启动了其他 Animator，则取消自身
                objectAnimator.setAutoCancel(true);
                objectAnimator.setDuration(duration > 0 ? duration : DEFAULT_DURATION_MS);
                objectAnimator.setStartDelay(startDelay > 0 ? startDelay : 0);    // time until start / 开始前的延迟时间
                objectAnimator.start();                        // start the animation / 开始动画
            }
        }, 100);
    }

    /**
     * Callback listener for animations
     *
     * 动画回调监听器
     */
    private static final Animator.AnimatorListener mAnimatorListener = new Animator.AnimatorListener() {
        @Override
        public void onAnimationStart(final Animator animator) {
            onAnimation(animator, 0);
        }

        @Override
        public void onAnimationEnd(final Animator animator) {
            onAnimation(animator, 1);
        }

        @Override
        public void onAnimationCancel(final Animator animator) {
            onAnimation(animator, 2);
        }

        @Override
        public void onAnimationRepeat(final Animator animation) {
        }
    };

    private static void onAnimation(final Animator animator, final int event) {
        if (animator instanceof ObjectAnimator) {
            final ObjectAnimator anim = (ObjectAnimator) animator;
            final View target = (View) anim.getTarget();
            if (target == null) return;    // this should never happen, but just in case / 理论上不应出现这种情况，但以防万一

            final ViewAnimationListener listener = (ViewAnimationListener) target.getTag(R.id.anim_listener);
            final int animType;
            try {
                animType = (Integer) target.getTag(R.id.anim_type);
            } catch (final Exception e) {
                return;
            }
            if (listener != null) {
                target.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            switch (event) {
                                case 0:
                                    listener.onAnimationStart(anim, target, animType);
                                    break;
                                case 1:
                                    listener.onAnimationEnd(anim, target, animType);
                                    break;
                                case 2:
                                    listener.onAnimationCancel(anim, target, animType);
                                    break;
                            }
                        } catch (final Exception e) {
                            Log.w(TAG, e);
                        }
                    }
                }, 100);
            }
        }
    }
}
