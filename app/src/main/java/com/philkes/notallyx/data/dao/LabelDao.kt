package com.philkes.notallyx.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.philkes.notallyx.data.model.Label

@Dao
interface LabelDao {

    @Insert suspend fun insert(label: Label)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(labels: List<Label>)

    @Query("DELETE FROM Label WHERE value = :value") suspend fun delete(value: String)

    @Query("DELETE FROM Label") suspend fun deleteAll()

    @Query("UPDATE Label SET value = :newValue WHERE value = :oldValue")
    suspend fun update(oldValue: String, newValue: String)

    @Query("SELECT value FROM Label ORDER BY value") fun getAll(): LiveData<List<String>>

    @Query("SELECT value FROM Label ORDER BY value") suspend fun getArrayOfAll(): Array<String>

    @Query("SELECT EXISTS(SELECT 1 FROM Label WHERE value = :value)")
    suspend fun exists(value: String): Boolean
}
