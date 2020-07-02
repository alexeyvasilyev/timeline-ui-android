package com.alexvas.timeline.demo.ui

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.alexvas.timeline.demo.R
import com.alexvas.widget.TimelineView
import com.alexvas.widget.TimelineView.TimeRecord
import kotlin.math.max

class TimelineFragment : Fragment() {

    private val TAG: String = TimelineFragment::class.java.simpleName
    private val DEBUG = true

    private lateinit var timelineView: TimelineView
    private lateinit var timelineViewModel: TimelineViewModel
    private val recordsBackgroundEvents: ArrayList<TimeRecord> = ArrayList()
    private val recordsMajor1Events: ArrayList<TimeRecord> = ArrayList()
    private val recordsMajor2Events: ArrayList<TimeRecord> = ArrayList()

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        timelineViewModel =
                ViewModelProvider(this).get(TimelineViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_timeline, container, false)
        timelineView = root.findViewById(R.id.timeline)
        timelineView.setOnTimelineListener(object:TimelineView.OnTimelineListener {
            override fun onTimeSelecting() {
            }

            override fun onTimeSelected(timestampMsec: Long, record: TimeRecord?) {
            }

            override fun onRequestMoreMajor1Data() {
            }

            override fun onRequestMoreMajor2Data() {
            }

            override fun onRequestMoreBackgroundData() {
            }
        })
        val zoomIn = root.findViewById<View>(R.id.zoomIn)
        val zoomOut = root.findViewById<View>(R.id.zoomOut)
        zoomIn.setOnClickListener {
            timelineView.decreaseIntervalWithAnimation()
            // isMinInterval will be valid only after animation completed (150 msec)
            Handler(Looper.getMainLooper()).postDelayed({
                zoomIn.isEnabled = !timelineView.isMinInterval
                zoomOut.isEnabled = true
            }, TimelineView.ANIMATION_DURATION_MSEC + 1)
        }
        zoomOut.setOnClickListener {
            timelineView.increaseIntervalWithAnimation()
            // isMaxInterval will be valid only after animation completed (150 msec)
            Handler(Looper.getMainLooper()).postDelayed({
                zoomOut.isEnabled = !timelineView.isMaxInterval
                zoomIn.isEnabled = true
            }, TimelineView.ANIMATION_DURATION_MSEC + 1)
        }
        root.findViewById<View>(R.id.lastEvent).setOnClickListener {
            gotoLastMajor1Record()
        }
        root.findViewById<View>(R.id.prevEvent).setOnClickListener {
            gotoPrevRecord(true)
        }
        root.findViewById<View>(R.id.nextEvent).setOnClickListener {
            gotoNextRecord(true)
        }
        return root
    }

    override fun onStart() {
        if (DEBUG) Log.v(TAG, "onStart()")
        super.onStart()
        timelineViewModel.loadParams(context)
        loadFirstValues()
        updateTimeline()
        gotoLastMajor1Record()
    }

    override fun onStop() {
        if (DEBUG) Log.v(TAG, "onStop()")
        super.onStop()
        timelineViewModel.saveParams(context)
    }

    class EventRecord()

    private fun loadFirstValues() {
        if (DEBUG) Log.v(TAG, "loadFirstValues()")
        val time = System.currentTimeMillis()
        recordsMajor1Events.clear()
        recordsMajor1Events.add(TimeRecord(time - 1000, 0, EventRecord())) // duration can be 0 if unknown
        recordsMajor1Events.add(TimeRecord(time - 2000, 0, EventRecord()))
        recordsMajor1Events.add(TimeRecord(time - 5000, 1000, EventRecord()))
        recordsMajor1Events.add(TimeRecord(time - 30000, 20000, EventRecord()))
        recordsMajor1Events.add(TimeRecord(time - 300000, 20000, EventRecord()))
        recordsMajor1Events.add(TimeRecord(time - 1800000, 100000, EventRecord()))
        recordsMajor1Events.add(TimeRecord(time - 3600000, 70000, EventRecord()))
        recordsMajor1Events.add(TimeRecord(time - 360000000, 150000, EventRecord()))
        recordsMajor1Events.add(TimeRecord(time - 500000000, 1500000, EventRecord()))

        recordsMajor2Events.clear()
        recordsMajor2Events.add(TimeRecord(time - 500, 2000, EventRecord()))
        recordsMajor2Events.add(TimeRecord(time - 5200, 500, EventRecord()))
        // Overwriting default draw color
        recordsMajor2Events.add(TimeRecord(time - 6000, 1000, EventRecord(), Color.YELLOW))

        recordsBackgroundEvents.clear()
        recordsBackgroundEvents.add(TimeRecord(time - 50000, 20000, EventRecord()))
        recordsBackgroundEvents.add(TimeRecord(time - 500000, 500000, EventRecord()))
        recordsBackgroundEvents.add(TimeRecord(time - 1000000, 250000, EventRecord()))
        recordsBackgroundEvents.add(TimeRecord(time - 2500000, 1000000, EventRecord()))
    }

    private fun updateTimeline() {
        if (DEBUG) Log.v(TAG, "updateTimeline()")
        timelineView.setMajor1Records(recordsMajor1Events)
        timelineView.setMajor2Records(recordsMajor2Events)
        timelineView.setBackgroundRecords(recordsBackgroundEvents)
        timelineView.invalidate()
    }

    private fun gotoLastMajor1Record() {
        if (DEBUG) Log.v(TAG, "gotoLastMajor1Record()")
        var records: java.util.ArrayList<TimeRecord> = timelineView.major1Records
        // At least one event exists
        if (records.size > 0) {
            val record = records[0]
            val timestamp: Long = getTimestampFromRecord(record)
            onTimeSelected(timestamp, record)
            timelineView.setCurrentWithAnimation(record.timestampMsec)
            timelineView.invalidate()
        } else {
            // No events found. Show last video recording.
            records = timelineView.backgroundRecords
            if (records.size > 0) {
                val record = records[0]
                // 30 sec before video finished.
                val timestamp = record.timestampMsec + max(record.durationMsec - 30000, 0)
                onTimeSelected(timestamp, record)
                timelineView.setCurrentWithAnimation(record.timestampMsec)
                timelineView.invalidate()
            }
        }
    }

    private fun gotoPrevRecord(animation: Boolean) {
        if (DEBUG) Log.v(TAG, "gotoPrevRecord(animation=$animation)")
        val record: TimeRecord? = timelineView.getPrevMajorRecord()
        if (record != null) {
            Log.i(TAG, record.toString())
            val timestamp = getTimestampFromRecord(record)
            onTimeSelected(timestamp, record)
            if (animation)
                timelineView.setCurrentWithAnimation(record.timestampMsec)
            else
                timelineView.setCurrent(record.timestampMsec)
            timelineView.invalidate()
        }
    }

    private fun gotoNextRecord(animation: Boolean) {
        if (DEBUG) Log.v(TAG, "gotoNextRecord(animation=$animation)")
        val record: TimeRecord? = timelineView.getNextMajorRecord()
        if (record != null) {
            Log.i(TAG, record.toString())
            val timestamp = getTimestampFromRecord(record)
            onTimeSelected(timestamp, record)
            if (animation)
                timelineView.setCurrentWithAnimation(record.timestampMsec)
            else
                timelineView.setCurrent(record.timestampMsec)
            timelineView.invalidate()
        }
    }

    private fun getTimestampFromRecord(record: TimeRecord): Long {
        return record.timestampMsec
    }

    private fun onTimeSelected(timestamp: Long, record: TimeRecord) {
        // TODO: Playback code goes here
    }

}
