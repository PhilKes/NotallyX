package com.omgodse.notally.changehistory

abstract class ListBooleanChange(newValue: Boolean, id: Int) :
    ListIdValueChange<Boolean>(newValue, !newValue, id)
