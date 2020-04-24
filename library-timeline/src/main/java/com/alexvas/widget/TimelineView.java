package com.alexvas.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.os.Build;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import com.alexvas.widget.timeline.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

public class TimelineView extends View {

    public static final long INTERVAL_MIN_1   =            60 * 1000; //  1 min
    public static final long INTERVAL_MIN_5   =        5 * 60 * 1000; //  5 min
    public static final long INTERVAL_MIN_15  =       15 * 60 * 1000; // 15 min
    public static final long INTERVAL_MIN_30  =       30 * 60 * 1000; // 30 min
    public static final long INTERVAL_HOUR_1  =       60 * 60 * 1000; //  1 hour
    public static final long INTERVAL_HOUR_6  =   6 * 60 * 60 * 1000; //  6 hours
    public static final long INTERVAL_HOUR_12 =  12 * 60 * 60 * 1000; // 12 hours
    public static final long INTERVAL_DAY_1   =  24 * 60 * 60 * 1000; //  1 day
    public static final long INTERVAL_DAY_7   = 168 * 60 * 60 * 1000; //  7 days

    @SuppressWarnings("FieldCanBeLocal")
    private final float STROKE_SELECTED_WIDTH = 2f;
    @SuppressWarnings("FieldCanBeLocal")
    private final float OFFSET_TOP_BOTTOM = 7f;
    @SuppressWarnings("FieldCanBeLocal")
    private final long MIN_INTERVAL = INTERVAL_MIN_1; // 1 min
    @SuppressWarnings("FieldCanBeLocal")
    private final long MAX_INTERVAL = INTERVAL_DAY_7; // 7 days

    private final int ANIMATION_DURATION_MSEC = 150;

    public interface TimeDateFormatter {
        @NonNull String getStringTime(@NonNull Date date);
        @NonNull String getStringDate(@NonNull Date date);
    }

    public interface OnTimelineListener {
        void onTimeSelecting();
        void onTimeSelected(long timestampMsec, @Nullable TimeRecord record);
        void onRequestMoreBackgroundData();
        void onRequestMoreMajor1Data();
        void onRequestMoreMajor2Data();
    }

    public static class TimeRecord {
        public final long timestampMsec; // absolute
        public final long durationMsec;  // relative
        public final Object object;
        public TimeRecord(long startMs, long durationMs, @NonNull Object obj) {
            timestampMsec = startMs;
            durationMsec  = durationMs;
            object = obj;
        }
        @Override
        @NonNull
        public String toString() {
            return String.format(
                    Locale.US,
                    "TimeRecord: {timestamp: %d, duration: %d, object: \"%s\"}",
                    timestampMsec,
                    durationMsec,
                    object.toString());
        }
    }

    private final SimpleDateFormat _formatHourMin = new SimpleDateFormat("HH:mm", Locale.getDefault());
//    private final SimpleDateFormat _formatHourMinSec = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private final SimpleDateFormat _formatShortDate = new SimpleDateFormat("MMM d", Locale.getDefault());

    private class DefaultDateFormatter implements TimeDateFormatter {
//      private final SimpleDateFormat _formatMins = new SimpleDateFormat(
//              getResources().getString(R.string.TIME_FORMAT_TIMELINE_MINS), Locale.getDefault());
        private final SimpleDateFormat _formatHours = new SimpleDateFormat(
                getResources().getString(R.string.TIME_FORMAT_TIMELINE_HOUR), Locale.getDefault());
        private final SimpleDateFormat _formatDateYear = new SimpleDateFormat(
                getResources().getString(R.string.DATE_FORMAT_YEAR), Locale.getDefault());
        @Override
        @NonNull
        public String getStringTime(@NonNull Date date) {
            return _formatHours.format(date);
        }
        @Override
        @NonNull
        public String getStringDate(@NonNull Date date) {
            return _formatDateYear.format(date);
        }
    }

    private ArrayList<TimeRecord> _recordsMajor1 = new ArrayList<>();
    private ArrayList<TimeRecord> _recordsMajor2 = new ArrayList<>();
    private ArrayList<TimeRecord> _recordsBackground = new ArrayList<>();
    private Rect _rectMajor1Selected = null;
    private Rect _rectMajor2Selected = null;
    private final ArrayList<Rect> _rectsMajor1 = new ArrayList<>();
    private final ArrayList<Rect> _rectsMajor2 = new ArrayList<>();
    private final ArrayList<Rect> _rectsBackground = new ArrayList<>();
    private final Rect _rectNoData = new Rect();

    private final Paint _paintMajor1 = new Paint();
    private final Paint _paintMajor2 = new Paint();
    private final Paint _paintSelected1 = new Paint();
    private final Paint _paintSelected2 = new Paint();
    private final Paint _paintBackground = new Paint();
    private final Paint _paintPointer = new Paint();
    private final Paint _paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _paintTextRuler = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _paintTextRulerMain = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _paintNoData = new Paint();

    private ValueAnimator _animator;
    private TimeDateFormatter _timedateFormatter = new DefaultDateFormatter();

    private long _selectedMsec = 0;
    private final Date _selectedMsecDate = new Date(0);
    private long _intervalMsec = INTERVAL_HOUR_1;
    private int _gmtOffsetInMillis = 0;
    private final Rect _rect000000 = new Rect();

    private float _density = 0.0f;
    private boolean _needUpdate = true;
    private GestureDetector _gestureDetector;
    private ScaleGestureDetector _scaleDetector;
    private OnTimelineListener _listener = null;

    public TimelineView(Context context) {
        super(context);
        init(context, null);
    }

    public TimelineView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        init(context, attributeSet);
    }

    public TimelineView(Context context, AttributeSet attributeSet, int defStyleAttr) {
        super(context, attributeSet, defStyleAttr);
        init(context, attributeSet);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(_selectedRunnable);
        super.onDetachedFromWindow();
    }

    private boolean _isTouched = false;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                _isTouched = true;
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                _isTouched = false;
                break;
        }
        if (_gestureListener.isAnimating()) {
            _gestureListener.cancelAnimation();
        } else {
            _gestureDetector.onTouchEvent(event);
            _scaleDetector.onTouchEvent(event);
        }
        return true;
    }

    // https://blog.stylingandroid.com/gesture-navigation-edge-cases/
    private final Rect _boundingBox = new Rect();

    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && changed) {
            _boundingBox.set(left, top, right, bottom);
            setSystemGestureExclusionRects(Collections.singletonList(_boundingBox));
        }
    }

    public void setTimeDateFormatter(@NonNull TimeDateFormatter formatter) {
        _timedateFormatter = formatter;
    }

    public void setOnTimelineListener(@Nullable OnTimelineListener listener) {
        _listener = listener;
    }

    public void setMajor1Records(@NonNull ArrayList<TimeRecord> records) {
        //noinspection ConstantConditions
        if (records == null)
            throw new NullPointerException("List of major1 records is null");
//      checkRecordsDescending(records);
        _recordsMajor1 = records;
        _needUpdate = true;
    }

    public void setMajor2Records(@NonNull ArrayList<TimeRecord> records) {
        //noinspection ConstantConditions
        if (records == null)
            throw new NullPointerException("List of major2 records is null");
//      checkRecordsDescending(records);
        _recordsMajor2 = records;
        _needUpdate = true;
    }

    public void setBackgroundRecords(@NonNull ArrayList<TimeRecord> records) {
        //noinspection ConstantConditions
        if (records == null)
            throw new NullPointerException("List of background records is null");
//      checkRecordsDescending(records);
        _recordsBackground = records;
        _needUpdate = true;
    }

    @NonNull
    public ArrayList<TimeRecord> getMajor1Records() {
        return _recordsMajor1;
    }

//    @NonNull
//    public ArrayList<TimeRecord> getMajor2Records() {
//        return _recordsMajor2;
//    }

    @NonNull
    public ArrayList<TimeRecord> getBackgroundRecords() {
        return _recordsBackground;
    }

//    private synchronized void checkRecordsDescending(@NonNull ArrayList<TimeRecord> records) {
//        TimeRecord prevRecord = null;
////        int i = 0;
//        for (TimeRecord record : records) {
////            if (prevRecord != null && prevRecord.timestampMsec < record.timestampMsec)
////                Log.e("ZZZ", "[" + i + "] Timestamp: " + record.timestampMsec + " " + record.object.toString());
////            else if (prevRecord != null && prevRecord.timestampMsec == record.timestampMsec)
////                Log.w("ZZZ", "[" + i + "] Timestamp: " + record.timestampMsec + " " + record.object.toString());
////            else
////                Log.i("ZZZ", "[" + i + "] Timestamp: " + record.timestampMsec + " " + record.object.toString());
////            i++;
//            if (prevRecord != null) {
//                Assert.assertTrue(
//                        "prev " + prevRecord.timestampMsec + " >= " + record.timestampMsec +
//                        " is false. Prev " + prevRecord.object.toString() + ", cur " + record.object.toString(),
//                        prevRecord.timestampMsec >= record.timestampMsec);
//
//            }
//            prevRecord = record;
//        }
//    }

    public void setCurrent(long currentMsec) {
//      Log.i("ZZZ", "currentMsec: " + currentMsec);
//      if (Math.abs(_selectedMsec - currentMsec) > 10000)
//          Log.w("ZZZ", "currentMsec diff: " + Math.abs(_selectedMsec - currentMsec));
        _selectedMsec = Math.min(currentMsec, System.currentTimeMillis());
        _selectedMsecDate.setTime(_selectedMsec);
        _needUpdate = true;
    }

    public void setCurrentWithAnimation(long currentMsec) {
        cancelAnimation();
        long offset = currentMsec - _selectedMsec;
        _animator = ValueAnimator.ofInt(0, (int)offset);
        _animator.setDuration(ANIMATION_DURATION_MSEC);
        _animator.setInterpolator(new AccelerateDecelerateInterpolator());
        final long targetMsec = _selectedMsec;
        _animator.addUpdateListener(animation -> {
            Integer value = (Integer) animation.getAnimatedValue();
            setCurrent(targetMsec + (long)value);
            invalidate();
        });
        _animator.start();
    }

    public long getCurrent() {
        return _selectedMsec;
    }

    private void setInterval(long intervalMsec) {
        intervalMsec = Math.min(MAX_INTERVAL, Math.max(intervalMsec, MIN_INTERVAL));
        if (intervalMsec != _intervalMsec) {
            _intervalMsec = intervalMsec;
            _needUpdate = true;
        }
    }

    public void increaseIntervalWithAnimation() {
        if (_intervalMsec > INTERVAL_DAY_1 - 1) {

            setIntervalWithAnimation(INTERVAL_DAY_7);

        } else if (_intervalMsec > INTERVAL_HOUR_12 - 1) {

            setIntervalWithAnimation(INTERVAL_DAY_1);

        } else if (_intervalMsec > INTERVAL_HOUR_6 - 1) {

            setIntervalWithAnimation(INTERVAL_HOUR_12);

        } else if (_intervalMsec > INTERVAL_HOUR_1 - 1) {

            setIntervalWithAnimation(INTERVAL_HOUR_6);

        } else if (_intervalMsec > INTERVAL_MIN_30 - 1) {

            setIntervalWithAnimation(INTERVAL_HOUR_1);

        } else if (_intervalMsec > INTERVAL_MIN_15 - 1) {

            setIntervalWithAnimation(INTERVAL_MIN_30);

        } else if (_intervalMsec > INTERVAL_MIN_5 - 1) {

            setIntervalWithAnimation(INTERVAL_MIN_15);

        } else if (_intervalMsec > INTERVAL_MIN_1 - 1) {

            setIntervalWithAnimation(INTERVAL_MIN_5);

        } else {

            setIntervalWithAnimation(INTERVAL_MIN_1);

        }
    }

    public void decreaseIntervalWithAnimation() {
        if (_intervalMsec > INTERVAL_DAY_7 - 1) {

            setIntervalWithAnimation(INTERVAL_DAY_1);

        } else if (_intervalMsec > INTERVAL_DAY_1 - 1) {

            setIntervalWithAnimation(INTERVAL_HOUR_12);

        } else if (_intervalMsec > INTERVAL_HOUR_12 - 1) {

            setIntervalWithAnimation(INTERVAL_HOUR_6);

        } else if (_intervalMsec > INTERVAL_HOUR_6 - 1) {

            setIntervalWithAnimation(INTERVAL_HOUR_1);

        } else if (_intervalMsec > INTERVAL_HOUR_1 - 1) {

            setIntervalWithAnimation(INTERVAL_MIN_30);

        } else if (_intervalMsec > INTERVAL_MIN_30 - 1) {

            setIntervalWithAnimation(INTERVAL_MIN_15);

        } else if (_intervalMsec > INTERVAL_MIN_15 - 1) {

            setIntervalWithAnimation(INTERVAL_MIN_5);

        } else if (_intervalMsec > INTERVAL_MIN_5 - 1) {

            setIntervalWithAnimation(INTERVAL_MIN_1);

        }
    }

    private void setIntervalWithAnimation(long intervalMsec) {
        cancelAnimation();
        long offset = intervalMsec - _intervalMsec;
        _animator = ValueAnimator.ofInt(0, (int)offset);
        _animator.setDuration(ANIMATION_DURATION_MSEC);
        _animator.setInterpolator(new AccelerateDecelerateInterpolator());
        final long targetMsec = _intervalMsec;
        _animator.addUpdateListener(animation -> {
            Integer value = (Integer) animation.getAnimatedValue();
            setInterval(targetMsec + (long)value);
            invalidate();
        });
        _animator.start();
    }

    private void cancelAnimation() {
        if (_animator != null) {
            _animator.cancel();
            _animator = null;
        }
    }

    public long getInterval() {
        return _intervalMsec;
    }

    private void update() {
        _rectMajor1Selected = null;
        _rectMajor2Selected = null;
        _rectsMajor1.clear();
        _rectsMajor2.clear();
        _rectsBackground.clear();

        int width  = getWidth();
        int height = getHeight();

        long minValue = _selectedMsec - _intervalMsec / 2;
        long maxValue = _selectedMsec + _intervalMsec / 2;
        float msecInPixels = width / (float)_intervalMsec;

        boolean isLandscape = isLandscape();
        int offsetBackground = (int)((isLandscape ? 2.6 : 3.4) * OFFSET_TOP_BOTTOM * _density);
        int offsetMajor1     = (int)((isLandscape ? 2.6 : 3.4) * OFFSET_TOP_BOTTOM * _density);
        int offsetMajor2     = (int)((isLandscape ? 3.0 : 4.0) * OFFSET_TOP_BOTTOM * _density);

        _rectNoData.set(
                0, // left
                offsetMajor1, // top
                Math.min((int)((System.currentTimeMillis() - minValue) * msecInPixels), width), // right
                height - offsetMajor1); // bottom


        for (TimeRecord record : _recordsMajor1) {
            if ((record.timestampMsec + record.durationMsec) >= minValue &&
                (record.timestampMsec) <= maxValue) {

                Rect rect = new Rect(
                        Math.max((int) ((record.timestampMsec - minValue) * msecInPixels), 0), // left
                        offsetMajor1, // top
                        Math.min((int) ((record.timestampMsec - minValue + record.durationMsec) * msecInPixels), width), // right
                        height - offsetMajor1); // bottom

                if (_rectMajor1Selected == null &&
                    _selectedMsec >= record.timestampMsec &&
                    _selectedMsec < (record.timestampMsec + record.durationMsec)) {

                    _rectMajor1Selected = rect;
                } else {
                    _rectsMajor1.add(rect);
                }
            }
        }
        // Check if we need more older records to load
        if (_recordsMajor1.size() > 0) {
            // Get the last record (oldest one)
            TimeRecord record = _recordsMajor1.get(_recordsMajor1.size() - 1);
            if (minValue < record.timestampMsec) {
                _listener.onRequestMoreMajor1Data();
            }
        }


        for (TimeRecord record : _recordsMajor2) {
            if ((record.timestampMsec + record.durationMsec) >= minValue &&
                (record.timestampMsec) <= maxValue) {

                Rect rect = new Rect(
                        Math.max((int) ((record.timestampMsec - minValue) * msecInPixels), 0), // left
                        offsetMajor2, // top
                        Math.min((int) ((record.timestampMsec - minValue + record.durationMsec) * msecInPixels), width), // right
                        height - offsetMajor2); // bottom

                if (_rectMajor2Selected == null &&
                    _selectedMsec >= record.timestampMsec &&
                    _selectedMsec <= (record.timestampMsec + record.durationMsec)) {

                    _rectMajor2Selected = rect;
                } else {
                    _rectsMajor2.add(rect);
                }
            }
        }
        // Check if we need more older records to load
        if (_recordsMajor2.size() > 0) {
            // Get the last record (oldest one)
            TimeRecord record = _recordsMajor2.get(_recordsMajor2.size() - 1);
            if (minValue < record.timestampMsec) {
                _listener.onRequestMoreMajor2Data();
            }
        }

        for (TimeRecord record : _recordsBackground) {
            if ((record.timestampMsec + record.durationMsec) >= minValue &&
                (record.timestampMsec) <= maxValue) {

                Rect rect = new Rect(
                        Math.max((int)((record.timestampMsec - minValue) * msecInPixels), 0), // left
                        offsetBackground, // top
                        Math.min((int)((record.timestampMsec - minValue + record.durationMsec) * msecInPixels), width), // right
                        height - offsetBackground); // bottom

                _rectsBackground.add(rect);
            }
//            // Skip processing not shown older records
//            if (minValue > record.timestampMsec) {
//                break;
//            }
        }
        // Check if we need more older records to load
        if (_recordsBackground.size() > 0) {
            // Get the last record (oldest one)
            TimeRecord record = _recordsBackground.get(_recordsBackground.size() - 1);
            if (minValue < record.timestampMsec) {
                _listener.onRequestMoreBackgroundData();
            }
        }
    }

//    private static void convertRecordsToRects(
//            @NonNull ArrayList<TimeRecord> records,
//            @NonNull ArrayList<Rect> rects,
//                     int offsetTopBottom) {
//
//        rects.clear();
//
//        for (TimeRecord record : records) {
//            if ((record.timestampMsec + record.durationMsec) >= minValue &&
//                    (record.timestampMsec) <= maxValue) {
//
//                Rect rect = new Rect(Math.max((int) ((record.timestampMsec - minValue) * msecInPixels), 0), // left
//                        offsetMajor, // top
//                        Math.min((int) ((record.timestampMsec - minValue + record.durationMsec) * msecInPixels), width), // right
//                        height - offsetMajor); // bottom
//
//                if (_selectedMsec >= record.timestampMsec && _selectedMsec <= (record.timestampMsec + record.durationMsec)) {
//                    _rectMajor1Selected = rect;
//                } else {
//                    _rectsMajor1.add(rect);
//                }
//            }
//        }
//    }

    @Nullable
    private static TimeRecord getRecord(long timestampMsec, @NonNull ArrayList<TimeRecord> records) {
        for (TimeRecord record : records) {
            if (timestampMsec >= record.timestampMsec &&
                timestampMsec < (record.timestampMsec + record.durationMsec)) {
                return record;
            }
        }
        return null;
    }

    @Nullable
    public TimeRecord getNextMajorRecord() {
        return getNextRecord(_selectedMsec, _recordsMajor1);
    }

    @Nullable
    public TimeRecord getPrevMajorRecord() {
        return getPrevRecord(_selectedMsec - 30000 /*magic constant, 30 sec*/, _recordsMajor1);
    }

    @Nullable
    public TimeRecord getNextBackgroundRecord() {
        return getNextRecord(_selectedMsec, _recordsBackground);
    }

    @Nullable
    public TimeRecord getCurrentBackgroundRecord() {
        return getRecord(_selectedMsec, _recordsBackground);
    }

    @Nullable
    private static TimeRecord getNextRecord(long currentMsec, @NonNull ArrayList<TimeRecord> records) {
        TimeRecord prevRecord = null;
//        if (records.size() == 1 && currentMsec < records.get(0).timestampMsec)
//            return records.get(0);
        // Suppose all events sorted
        for (TimeRecord record : records) {
            if (prevRecord != null &&
                currentMsec < prevRecord.timestampMsec &&
                currentMsec >= record.timestampMsec) {

                return prevRecord;
            }
            prevRecord = record;
        }
        return null;
    }

    @Nullable
    private static TimeRecord getPrevRecord(long currentMsec, @NonNull ArrayList<TimeRecord> records) {
        // Suppose all events sorted
        for (TimeRecord record : records) {
            if (record.timestampMsec < currentMsec)
                return record;
        }
        return null;
    }

    private final Runnable _selectingRunnable = () -> {
        if (_listener != null) {
            _listener.onTimeSelecting();
        }
    };

    private final Runnable _selectedRunnable = () -> {
        if (_listener != null) {
            TimeRecord record = getRecord(_selectedMsec, _recordsBackground);
            _listener.onTimeSelected(_selectedMsec, record);
        }
    };

    private final ScaleGestureDetector.SimpleOnScaleGestureListener _scaleListener = new ScaleGestureDetector.SimpleOnScaleGestureListener() {
//        @Override
//        public boolean onScale(ScaleGestureDetector detector) {
//            setInterval((long) (_intervalMsec * (2f - detector.getScaleFactor())));
//            return true;
//        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            if (detector.getScaleFactor() > 1f)
                decreaseIntervalWithAnimation();
            else
                increaseIntervalWithAnimation();
        }
    };

    public boolean isTouched() {
        return _isTouched;
    }

    private final TimelineGestureListener _gestureListener = new TimelineGestureListener();
    private class TimelineGestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                float distanceX, float distanceY) {
//          Log.i("ZZZ", "onScroll()");
            if (_waitForNextActionUp)
                return false;
            float msecInPixels = _intervalMsec / (float)getWidth();
            long offsetInMsec = (long) (msecInPixels * distanceX);
            setCurrent(getCurrent() + offsetInMsec);
            invalidate();
            removeCallbacks(_selectedRunnable);
            post(_selectingRunnable);
            postDelayed(_selectedRunnable, 500);
            return true;
        }

        public boolean onDown(MotionEvent e) {
//          Log.i("ZZZ", "onDown()");
            _waitForNextActionUp = false;
            return true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
//          Log.i("ZZZ", "onSingleTapUp()");
            int offsetInPixels = (int) (e.getX() - getWidth() / 2f);
            float msecInPixels = _intervalMsec / (float) getWidth();
            long offsetInMsec = (long) (msecInPixels * offsetInPixels);

            // Search if clicked on major2 record first
            long newSelectedMsec = _selectedMsec + offsetInMsec;
            boolean foundMajor2 = false;
            for (TimeRecord record : _recordsMajor2) {
                // On event clicked. Search for the beginning of the event.
                if (newSelectedMsec >= record.timestampMsec &&
                    newSelectedMsec <= (record.timestampMsec + record.durationMsec)) {

                    newSelectedMsec = record.timestampMsec;
                    foundMajor2 = true;
                    break;
                }
            }

            // Search if clicked on major1 record or between major1 records
            if (!foundMajor2) {
                TimeRecord prevRecord = null;
                for (TimeRecord record : _recordsMajor1) {
                    // On event clicked. Search for the beginning of the event.
                    if (newSelectedMsec >= record.timestampMsec &&
                        newSelectedMsec <= (record.timestampMsec + record.durationMsec)) {

                        newSelectedMsec = record.timestampMsec;
                        break;

                    // On space clicked. Search for the next event.
                    } else if (prevRecord != null &&
                            newSelectedMsec > (record.timestampMsec + record.durationMsec) &&
                            newSelectedMsec < prevRecord.timestampMsec) {

                        newSelectedMsec = prevRecord.timestampMsec;
                        break;
                    }
                    prevRecord = record;
                }
            }
            setCurrentWithAnimation(newSelectedMsec);
            removeCallbacks(_selectedRunnable);
            postDelayed(_selectedRunnable, 500);
            playSoundEffect(SoundEffectConstants.CLICK);
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
//          Log.i("ZZZ", "onFling() velocityX=" + velocityX);
            runFlingAnimation(velocityX);
            return true;
        }

        private boolean _waitForNextActionUp = false;

        boolean isAnimating() {
            return scrollAnimator != null && scrollAnimator.isRunning();
        }
        void cancelAnimation() {
            if (scrollAnimator != null)
                scrollAnimator.cancel();
        }

        private static final float FLING_X_MULTIPLIER = 0.00018f;
        private static final float FLING_DURATION_MULTIPLIER = 0.15f;
        private final Interpolator INTERPOLATOR = new DecelerateInterpolator(1.4f);
        private ValueAnimator scrollAnimator;
        private long savedValue;

        private void runFlingAnimation(float velocity) {
            int duration = (int) Math.abs(velocity * FLING_DURATION_MULTIPLIER);
            savedValue = getCurrent();
            int endValue = -(int)(velocity * FLING_X_MULTIPLIER * _intervalMsec);
            scrollAnimator = ValueAnimator
                    .ofInt(0, endValue)
                    .setDuration(duration);
            scrollAnimator.setInterpolator(INTERPOLATOR);
            scrollAnimator.addUpdateListener(flingAnimatorListener);
            scrollAnimator.addListener(animatorListener);
            scrollAnimator.start();
            removeCallbacks(_selectedRunnable);
        }

        private ValueAnimator.AnimatorUpdateListener flingAnimatorListener = new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                setCurrent(savedValue + (int)animation.getAnimatedValue());
                invalidate();
            }
        };
        private Animator.AnimatorListener animatorListener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                _waitForNextActionUp = true;
                _selectedRunnable.run();
            }
            @Override
            public void onAnimationEnd(Animator animation) {
                _selectedRunnable.run();
            }
        };
    }

    private void init(@NonNull Context context, @Nullable AttributeSet attrs) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        ((WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getMetrics(displayMetrics);
        _density = displayMetrics.density;

        _paintSelected1.setColor(Color.CYAN);
        _paintSelected1.setStyle(Paint.Style.FILL);
        _paintSelected1.setStrokeWidth(2f * _density);

        _paintSelected2.setColor(Color.GREEN);
        _paintSelected2.setStyle(Paint.Style.FILL);
        _paintSelected2.setStrokeWidth(2f * _density);

        _paintMajor1.setColor(Color.YELLOW);
        _paintMajor1.setStyle(Paint.Style.FILL);
        _paintMajor1.setStrokeWidth(2f * _density);

        _paintMajor2.setColor(Color.BLUE);
        _paintMajor2.setStyle(Paint.Style.FILL);
        _paintMajor2.setStrokeWidth(2f * _density);

        _paintBackground.setColor(Color.DKGRAY);
        _paintBackground.setStyle(Paint.Style.FILL);

        _paintPointer.setColor(Color.RED);
        _paintPointer.setStyle(Paint.Style.FILL);
        _paintPointer.setStrokeWidth((int)(STROKE_SELECTED_WIDTH * _density));

        _paintText.setColor(Color.WHITE);
        _paintText.setTextSize(12f * _density);
        _paintText.getTextBounds("00:00:00", 0, "00:00:00".length(), _rect000000);

        _paintTextRuler.setColor(Color.LTGRAY);
        _paintTextRuler.setTextSize(10f * _density);

        _paintTextRulerMain.setColor(Color.WHITE);
        _paintTextRulerMain.setTextSize(10f * _density);
        _paintTextRulerMain.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        _paintNoData.setColor(Color.DKGRAY);
        _paintNoData.setStyle(Paint.Style.FILL);
//        _paintNoData.setStrokeWidth(2f * _density);

        if (attrs != null) {
            TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.TimelineView);
            _paintSelected1.setColor(array.getColor(R.styleable.TimelineView_timelineColorSelected1, Color.CYAN));
            _paintSelected2.setColor(array.getColor(R.styleable.TimelineView_timelineColorSelected2, Color.GREEN));
            _paintMajor1.setColor(array.getColor(R.styleable.TimelineView_timelineColorMajor1, Color.YELLOW));
            _paintMajor2.setColor(array.getColor(R.styleable.TimelineView_timelineColorMajor2, Color.BLUE));
            _paintBackground.setColor(array.getColor(R.styleable.TimelineView_timelineColorBackground, Color.DKGRAY));
            _paintNoData.setColor(_paintBackground.getColor());
            _paintPointer.setColor(array.getColor(R.styleable.TimelineView_timelineColorPointer, Color.RED));
            _paintNoData.setColor(array.getColor(R.styleable.TimelineView_timelineColorNoData, Color.LTGRAY));
            array.recycle();
        }

        TimeZone tz = TimeZone.getDefault();
        Calendar cal = GregorianCalendar.getInstance(tz);
        _gmtOffsetInMillis = tz.getOffset(cal.getTimeInMillis());

        _gestureDetector = new GestureDetector(context, _gestureListener);
        _scaleDetector = new ScaleGestureDetector(context, _scaleListener);
    }

    private final Rect r = new Rect();
    private final RectF rf = new RectF();

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
//        long l1 = System.currentTimeMillis();
        if (_needUpdate) {
            update();
            _needUpdate = false;
        }
//        long l2 = System.currentTimeMillis();

        canvas.drawRect(_rectNoData, _paintNoData);

        // Draw minor rectangles
        for (Rect rect : _rectsBackground) {
            canvas.drawRect(rect, _paintBackground);
        }

//        long l3 = System.currentTimeMillis();
        // Draw major rectangles
        for (Rect rect : _rectsMajor1) {
            canvas.drawRect(rect, _paintMajor1);
            // Draw line on top to be sure that rect is not too narrow
            canvas.drawLine(rect.left, rect.top, rect.left, rect.bottom, _paintMajor1);
        }

//        long l4 = System.currentTimeMillis();
        for (Rect rect : _rectsMajor2) {
            canvas.drawRect(rect, _paintMajor2);
            // Draw line on top to be sure that rect is not too narrow
            canvas.drawLine(rect.left, rect.top, rect.left, rect.bottom, _paintMajor2);
        }
//        long l5 = System.currentTimeMillis();

        // Draw currently selected rectangle
        if (_rectMajor1Selected != null) {
            canvas.drawRect(_rectMajor1Selected, _paintSelected1);
        }

//        long l6 = System.currentTimeMillis();
        if (_rectMajor2Selected != null) {
            canvas.drawRect(_rectMajor2Selected, _paintSelected2);
        }

//        long l7 = System.currentTimeMillis();

        drawRuler(canvas);

        // Draw selected time
        canvas.drawLine(
                getWidth() >> 1,
                0,
                getWidth() >> 1,
                getHeight(),
                _paintPointer);


        drawCurrentTimeDate(canvas);


//        long l8 = System.currentTimeMillis();
//        Log.i("ZZZ", "onDraw 1: " + (l2 - l1) + " 2: " + (l3 - l2) + " 3: " + (l4 - l3) + " 4: " + (l5 - l4) +
//                " 5: " + (l6 - l5) + " 6: " + (l7 - l6) + " 7: " + (l8 - l7) + " t: " + (l8 - l1));
    }

    private void drawCurrentTimeDate(@NonNull Canvas canvas) {
        final String time;
        final boolean isNow;
        if (System.currentTimeMillis() - _selectedMsec < 5000) {
            time = "Now";
            isNow = true;
        } else {
            time = _timedateFormatter.getStringTime(_selectedMsecDate);
            isNow = false;
        }
        _paintText.getTextBounds(time, 0, time.length(), r);

        // Do not allow changing minor size of red rectangle for different time,
        // e.g. width of "00:00:00" in pixels is bigger than "11:11:11".
        rf.set((!isNow && _rect000000.width() > r.width()) ? _rect000000 : r);

        // Draw rounded red rectangle with current time
        rf.offsetTo((getWidth() >> 1) - rf.width() / 2, 0);
        // Increase red rounded rectangle size
        rf.inset(-4f * _density, -3f * _density);
        rf.offset(0, 3f * _density);
        canvas.drawRoundRect(rf, 4f * _density, 3f * _density, _paintPointer);

        // Draw current time text, e.g. "17:22:42"
        canvas.drawText(
                time,
                (getWidth() >> 1) - rf.width() / 2 + 4f * _density,
                r.height() + 3 * _density, //getHeight() - r.height() / 3,
                _paintText);

        // Draw current date text, e.g. "Today"
        String date = _timedateFormatter.getStringDate(_selectedMsecDate);
        _paintText.getTextBounds(date, 0, date.length(), r);
        canvas.drawText(
                date,
                getWidth() - r.width() - 2 * _density,
                r.height() + _density,
                _paintText);
    }

    // Draw hours
    // 12:00  13:00  14:00  15:00  16:00
//    private void drawHours(@NonNull Canvas canvas, float msecInPixels) {
//        long minValue = _selectedMsec - _intervalMsec / 2;
//        long maxValue = _selectedMsec + _intervalMsec / 2;
//        if (minValue + _selectedMsec % INTERVAL_HOUR_1 < maxValue) {
//            // Number of hours to be drawn
//            long numHours = _intervalMsec / INTERVAL_HOUR_1;
//            long offsetHour = minValue % INTERVAL_HOUR_1;
//
//            // Check that we can draw not overlaid hour:min.
//            _paintText.getTextBounds("00:00", 0, 5, r);
//            if (numHours * r.width() < getWidth()) {
//
//                long offsetInPixels = (long) (offsetHour * msecInPixels);
//                long intervalInPixels = (long) (_intervalMsec * msecInPixels);
//                long hourInPixels = (long) (INTERVAL_HOUR_1 * msecInPixels);
//
//                // 12:00  13:00  14:00  15:00  16:00
//                for (int i = 0; i < numHours; i++) {
//                    Date startDate = new Date(minValue - offsetHour + (i + 1) * INTERVAL_HOUR_1);
//                    String text = _formatHourMin.format(startDate);
//                    _paintText.getTextBounds(text, 0, text.length(), r);
//                    canvas.drawText(
//                            text,
//                            intervalInPixels - (offsetInPixels + (numHours - i - 1) * hourInPixels) - r.width() / 2,
//                            getHeight() - r.height(),
//                            _paintTextRuler);
//                }
//            }
//        }
//    }

    // Draw minutes
    // 10:00  10:01  10:02  10:03  10:04
    private boolean drawHoursMinutes(@NonNull Canvas canvas, float msecInPixels, long interval) {
        long minValue = _selectedMsec - _intervalMsec / 2 + _gmtOffsetInMillis;
        long maxValue = _selectedMsec + _intervalMsec / 2 + _gmtOffsetInMillis;
//        boolean drawSeconds = (interval == INTERVAL_MIN_1);
        if (minValue + _selectedMsec % interval < maxValue) {
            // Number of hours to be drawn
            long numToDraw = _intervalMsec / interval;
            long offsetInterval = minValue % interval;

            // Check that we can draw not overlaid hour:min.
            _paintText.getTextBounds("__00:00__", 0, 9, r);
            if (numToDraw * r.width() < getWidth()) {

                long offsetInPixels = (long) (offsetInterval * msecInPixels);
                long curIntervalInPixels = (long) (_intervalMsec * msecInPixels);
                long intervalInPixels = (long) (interval * msecInPixels);
                int height = getHeight();
                float offsetTopBottomDensity = OFFSET_TOP_BOTTOM * _density;

                // 12:00  13:00  14:00  15:00  16:00
                //   |      |      |      |      |
                for (int i = 0; i < numToDraw; i++) {
                    Date startDate = new Date(minValue - offsetInterval + (i + 1) * interval - _gmtOffsetInMillis);
                    String text = _formatHourMin.format(startDate);
                    Paint paint = _paintTextRuler;
                    // Show "Nov 1" instead of "00:00"
                    if ("00:00".equals(text)) {
                        text = _formatShortDate.format(startDate);
                        paint = _paintTextRulerMain;
                    }
                    _paintText.getTextBounds(text, 0, text.length(), r);

                    float x = curIntervalInPixels - (offsetInPixels + (numToDraw - i - 1) * intervalInPixels);
                    // Draw "12:00"
                    canvas.drawText(
                            text,
                            x - r.width() / 2f,
                            height - r.height(),
                            paint);

                    // Draw ruler
                    canvas.drawLine(
                            x,
                            height -offsetTopBottomDensity / 3,
                            x,
                            height - offsetTopBottomDensity,
                            _paintText);

                    x -= intervalInPixels / 2f;
                    if (x > 0) {
                        canvas.drawLine(
                                x,
                                height - offsetTopBottomDensity / 1.5f,
                                x,
                                height - offsetTopBottomDensity / 2f,
                                _paintText);
                    }

                    if (i == numToDraw - 1)
                        x += intervalInPixels;

                    canvas.drawLine(
                            x,
                            height - offsetTopBottomDensity / 1.5f,
                            x,
                            height - offsetTopBottomDensity / 2f,
                            _paintText);
                }
                return true;
            }
        }
        return false;
    }

    private void drawRuler(@NonNull Canvas canvas) {
        float msecInPixels = getWidth() / (float)_intervalMsec;
//        long sliceInMsec   = (long)(_intervalMsec / 10f);
//        long sliceInPixels = (long)(_intervalMsec / 10f * msecInPixels);

//        long offsetInPixels = (long)((_selectedMsec % sliceInMsec) * msecInPixels);

//        // Draw time ruler
//        for (int i = 0; i < 10; i++) {
//            canvas.drawLine(
//                    sliceInPixels - offsetInPixels + i * sliceInPixels,
//                    getHeight() - OFFSET_TOP_BOTTOM * _density / 2,
//                    sliceInPixels - offsetInPixels + i * sliceInPixels,
//                    getHeight() - OFFSET_TOP_BOTTOM * _density,
//                    _paintText);
//        }

        // Draw every minute
        if (!drawHoursMinutes(canvas, msecInPixels, INTERVAL_MIN_1))
            // Draw every 5 minutes
            if (!drawHoursMinutes(canvas, msecInPixels, INTERVAL_MIN_5))
                // Draw every 15 minutes
                if (!drawHoursMinutes(canvas, msecInPixels, INTERVAL_MIN_15))
                    // Draw every 30 minutes
                    if (!drawHoursMinutes(canvas, msecInPixels, INTERVAL_MIN_30))
                        // Draw every hour
                        if (!drawHoursMinutes(canvas, msecInPixels, INTERVAL_HOUR_1))
                            // Draw every 6 hours
                            if (!drawHoursMinutes(canvas, msecInPixels, INTERVAL_HOUR_6))
                                // Draw every 12 hours
                                if (!drawHoursMinutes(canvas, msecInPixels, INTERVAL_HOUR_12))
                                    // Draw every 24 hours
                                    drawHoursMinutes(canvas, msecInPixels, INTERVAL_DAY_1);

        // Draw ruler scale, e.g. "5 days", "30 min"
        String scale = getRulerScale();
        _paintText.getTextBounds(scale, 0, scale.length(), r);
//        float offsetX = getWidth() - r.width() - _density;
//        Paint paint = new Paint();
//        paint.setColor(0xFF212121);
//        paint.setStyle(Paint.Style.FILL);
//        // Override rectangle below text
//        canvas.drawRect(
//                offsetX - _density,
//                getHeight() - (2 * r.height() + _density),
//                offsetX + r.width() + _density,
//                getHeight(),
//                paint);
        // Draw text
        canvas.drawText(
                scale,
                2 * _density,
                _density + r.height(),
                _paintTextRuler);
    }

    @NonNull
    private String getRulerScale() {
        if (_intervalMsec > INTERVAL_DAY_7 - 1) {
            return "7 days";
        }  else if (_intervalMsec > INTERVAL_DAY_1 - 1) {
            return "1 day";
        } else if (_intervalMsec > INTERVAL_HOUR_12 - 1) {
            return "12 hours";
        } else if (_intervalMsec > INTERVAL_HOUR_6 - 1) {
            return "6 hours";
        } else if (_intervalMsec > INTERVAL_HOUR_1 - 1) {
            return "1 hour";
        } else if (_intervalMsec > INTERVAL_MIN_30 - 1) {
            return "30 min";
        } else if (_intervalMsec > INTERVAL_MIN_15 - 1) {
            return "15 min";
        } else if (_intervalMsec > INTERVAL_MIN_5 - 1) {
            return "5 min";
        } else if (_intervalMsec > INTERVAL_MIN_1 - 1) {
            return "1 min";
        } else {
            return "";
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        _needUpdate = true;
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

}
