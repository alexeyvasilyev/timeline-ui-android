package com.alexvas.timeline.demo.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.alexvas.timeline.demo.R
import com.alexvas.widget.TimelineView
import com.alexvas.widget.TimelineView.TimeRecord

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

    class EventRecord(videoOffset: Int)

    private fun loadFirstValues() {
        if (DEBUG) Log.v(TAG, "loadFirstValues()")
        val time = System.currentTimeMillis()
        recordsMajor1Events.clear()
        recordsMajor1Events.add(TimeRecord(time - 5000, 1000, EventRecord(0)))
        recordsMajor1Events.add(TimeRecord(time - 30000, 20000, EventRecord(0)))
        recordsMajor1Events.add(TimeRecord(time - 300000, 20000, EventRecord(0)))
        recordsMajor1Events.add(TimeRecord(time - 1800000, 100000, EventRecord(0)))
        recordsMajor1Events.add(TimeRecord(time - 3600000, 70000, EventRecord(0)))
        recordsMajor1Events.add(TimeRecord(time - 360000000, 150000, EventRecord(0)))
        recordsMajor1Events.add(TimeRecord(time - 500000000, 1500000, EventRecord(0)))

        recordsMajor2Events.clear()
        recordsMajor2Events.add(TimeRecord(time - 500, 500, EventRecord(0)))

        recordsBackgroundEvents.clear()
        recordsBackgroundEvents.add(TimeRecord(time - 500000, 500000, EventRecord(0)))
        recordsBackgroundEvents.add(TimeRecord(time - 1000000, 250000, EventRecord(0)))
        recordsBackgroundEvents.add(TimeRecord(time - 2500000, 1000000, EventRecord(0)))
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
        var records: java.util.ArrayList<TimeRecord> = timelineView.getMajor1Records()
        // At least one event exists
        if (records.size > 0) {
            val record = records[0]
            val timestamp: Long = getTimestampFromRecord(record)
            onTimeSelected(timestamp, record)
            timelineView.setCurrentWithAnimation(record.timestampMsec)
            timelineView.invalidate()
        } else {
            // No events found. Show last video recording.
            records = timelineView.getBackgroundRecords()
            if (records.size > 0) {
                val record = records[0]
                // 30 sec before video finished.
                val timestamp = record.timestampMsec + Math.max(record.durationMsec - 30000, 0)
                onTimeSelected(timestamp, record)
                timelineView.setCurrentWithAnimation(record.timestampMsec)
                timelineView.invalidate()
            }
        }
    }

    private fun getTimestampFromRecord(record: TimeRecord): Long {
        return record.timestampMsec
    }

    private fun onTimeSelected(timestamp: Long, record: TimeRecord) {
        // TODO: Playback code goes here
    }

}
