package com.philkes.notallyx.data.model

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer

/**
 * The instance of LiveData returned by Room only listens for changes while the fragment observing
 * it is alive. Observation of the data stops when the fragment is destroyed and resumes when it's
 * created.
 *
 * Take for example, the following situation
 *
 * User goes to the deleted section User restores 5 notes User returns to the notes section
 *
 * The observation of notes had stopped when the user went to the deleted section and is resumed
 * when the user comes back. This leads to lag and flickering.
 *
 * To stop this, this class acts as a wrapper over the LiveData returned by Room and continuously
 * observes it.
 */
class Content(
    private var liveData: LiveData<List<BaseNote>>,
    private val transform: (List<BaseNote>) -> List<Item>,
) : LiveData<List<Item>>() {
    private var observer: Observer<List<BaseNote>>? = null

    init {
        setObserver(liveData)
    }

    fun setObserver(liveData: LiveData<List<BaseNote>>) {
        observer?.let { this.liveData.removeObserver(it) }
        this.liveData = liveData
        observer = Observer { list -> value = transform(list) }
        this.liveData.observeForever(observer!!)
    }
}
