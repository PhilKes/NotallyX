package com.philkes.notallyx.presentation.view.misc

import androidx.lifecycle.MutableLiveData

// LiveData that doesn't accept null values
open class NotNullLiveData<T>(value: T) : MutableLiveData<T>(value) {

    override fun getValue(): T {
        return requireNotNull(super.getValue(), { "NotNullLiveData value is null" })
    }
}
