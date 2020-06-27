package com.alexvas.timeline.demo.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel

private val TAG: String = TimelineViewModel::class.java.getSimpleName()
private const val DEBUG = true
private const val TIMELINE_PARAMS_FILENAME = "timeline_params"

class TimelineViewModel : ViewModel() {

    fun loadParams(context: Context?) {
        if (DEBUG)
            Log.v(TAG, "loadParams()")
        val pref = context?.getSharedPreferences(TIMELINE_PARAMS_FILENAME, Context.MODE_PRIVATE)
    }

    fun saveParams(context: Context?) {
        if (DEBUG) Log.v(TAG, "saveParams()")
        val editor = context?.getSharedPreferences(TIMELINE_PARAMS_FILENAME, Context.MODE_PRIVATE)?.edit()
        editor?.apply()
    }

}