package com.philkes.notallyx.presentation.viewmodel.preference

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Observer
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.toPreservedByteArray
import com.philkes.notallyx.data.model.toPreservedString
import com.philkes.notallyx.presentation.view.misc.NotNullLiveData
import java.security.SecureRandom
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import org.ocpsoft.prettytime.PrettyTime

/**
 * Every Preference can be observed like a [NotNullLiveData].
 *
 * @param titleResId Optional string resource id, if preference can be set via the UI.
 */
abstract class BasePreference<T>(
    private val sharedPreferences: SharedPreferences,
    protected val defaultValue: T,
    val titleResId: Int? = null,
) {
    private var data: NotNullLiveData<T>? = null
    private var cachedValue: T? = null

    val value: T
        get() {
            if (cachedValue == null) {
                cachedValue = getValue(sharedPreferences)
            }
            return cachedValue!!
        }

    protected abstract fun getValue(sharedPreferences: SharedPreferences): T

    private fun getData(): NotNullLiveData<T> {
        if (data == null) {
            data = NotNullLiveData(value)
        }
        return data as NotNullLiveData<T>
    }

    internal fun save(value: T) {
        sharedPreferences.edit(true) { put(value) }
        cachedValue = value
        getData().postValue(value)
    }

    protected abstract fun SharedPreferences.Editor.put(value: T)

    fun observe(lifecycleOwner: LifecycleOwner, observer: Observer<T>) {
        getData().observe(lifecycleOwner, observer)
    }

    fun observeForever(observer: Observer<T>) {
        getData().observeForever(observer)
    }

    fun removeObserver(observer: Observer<T>) {
        getData().removeObserver(observer)
    }

    fun removeObservers(lifecycleOwner: LifecycleOwner) {
        getData().removeObservers(lifecycleOwner)
    }

    fun observeForeverWithPrevious(observer: Observer<Pair<T?, T>>) {
        val mediator = MediatorLiveData<Pair<T?, T>>()
        var previousValue: T? = null

        mediator.addSource(getData()) { currentValue ->
            mediator.value = Pair(previousValue, currentValue!!)
            previousValue = currentValue
        }

        mediator.observeForever(observer)
    }
}

fun <T> BasePreference<T>.observeForeverSkipFirst(observer: Observer<T>) {
    var isFirstEvent = true
    this.observeForever { value ->
        if (isFirstEvent) {
            isFirstEvent = false
        } else {
            observer.onChanged(value)
        }
    }
}

interface TextProvider {
    fun getText(context: Context): String
}

interface StaticTextProvider : TextProvider {
    val textResId: Int

    override fun getText(context: Context): String {
        return context.getString(textResId)
    }
}

class EnumPreference<T>(
    sharedPreferences: SharedPreferences,
    private val key: String,
    defaultValue: T,
    private val enumClass: Class<T>,
    titleResId: Int? = null,
) : BasePreference<T>(sharedPreferences, defaultValue, titleResId) where
T : Enum<T>,
T : TextProvider {

    override fun getValue(sharedPreferences: SharedPreferences): T {
        val storedValue = sharedPreferences.getString(key, null)
        return try {
            storedValue?.let { java.lang.Enum.valueOf(enumClass, it.fromCamelCaseToEnumName()) }
                ?: defaultValue
        } catch (e: IllegalArgumentException) {
            defaultValue
        }
    }

    override fun SharedPreferences.Editor.put(value: T) {
        putString(key, value.name.toCamelCase())
    }
}

class StringSetPreference(
    private val key: String,
    sharedPreferences: SharedPreferences,
    defaultValue: Set<String>,
    titleResId: Int? = null,
) : BasePreference<Set<String>>(sharedPreferences, defaultValue, titleResId) {

    override fun getValue(sharedPreferences: SharedPreferences): Set<String> {
        return sharedPreferences.getStringSet(key, defaultValue)!!
    }

    override fun SharedPreferences.Editor.put(value: Set<String>) {
        putStringSet(key, value)
    }
}

inline fun <reified T> createEnumPreference(
    sharedPreferences: SharedPreferences,
    key: String,
    defaultValue: T,
    titleResId: Int? = null,
): EnumPreference<T> where T : Enum<T>, T : TextProvider {
    return EnumPreference(sharedPreferences, key, defaultValue, T::class.java, titleResId)
}

class IntPreference(
    private val key: String,
    sharedPreferences: SharedPreferences,
    defaultValue: Int,
    val min: Int,
    val max: Int,
    titleResId: Int? = null,
) : BasePreference<Int>(sharedPreferences, defaultValue, titleResId) {

    override fun getValue(sharedPreferences: SharedPreferences): Int {
        return sharedPreferences.getInt(key, defaultValue)
    }

    override fun SharedPreferences.Editor.put(value: Int) {
        putInt(key, value)
    }
}

class StringPreference(
    private val key: String,
    sharedPreferences: SharedPreferences,
    defaultValue: String,
    titleResId: Int? = null,
) : BasePreference<String>(sharedPreferences, defaultValue, titleResId) {

    override fun getValue(sharedPreferences: SharedPreferences): String {
        return sharedPreferences.getString(key, defaultValue)!!
    }

    override fun SharedPreferences.Editor.put(value: String) {
        putString(key, value)
    }
}

class BooleanPreference(
    private val key: String,
    sharedPreferences: SharedPreferences,
    defaultValue: Boolean,
    titleResId: Int? = null,
) : BasePreference<Boolean>(sharedPreferences, defaultValue, titleResId) {

    override fun getValue(sharedPreferences: SharedPreferences): Boolean {
        return sharedPreferences.getBoolean(key, defaultValue)
    }

    override fun SharedPreferences.Editor.put(value: Boolean) {
        putBoolean(key, value)
    }
}

class ByteArrayPreference(
    private val key: String,
    sharedPreferences: SharedPreferences,
    defaultValue: ByteArray?,
    titleResId: Int? = null,
) : BasePreference<ByteArray?>(sharedPreferences, defaultValue, titleResId) {

    override fun getValue(sharedPreferences: SharedPreferences): ByteArray? {
        return sharedPreferences.getString(key, null)?.toPreservedByteArray ?: defaultValue
    }

    override fun SharedPreferences.Editor.put(value: ByteArray?) {
        putString(key, value?.toPreservedString)
    }
}

class EncryptedPassphrasePreference(
    private val key: String,
    sharedPreferences: SharedPreferences,
    defaultValue: ByteArray,
    titleResId: Int? = null,
) : BasePreference<ByteArray>(sharedPreferences, defaultValue, titleResId) {

    override fun getValue(sharedPreferences: SharedPreferences): ByteArray {
        return sharedPreferences.getString(key, null)?.toPreservedByteArray ?: defaultValue
    }

    override fun SharedPreferences.Editor.put(value: ByteArray) {
        putString(key, value.toPreservedString)
    }

    fun init(cipher: Cipher): ByteArray {
        val random =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                SecureRandom.getInstanceStrong()
            } else {
                SecureRandom()
            }
        val result = ByteArray(64)

        random.nextBytes(result)

        // filter out zero byte values, as SQLCipher does not like them
        while (result.contains(0)) {
            random.nextBytes(result)
        }

        val encryptedPassphrase = cipher.doFinal(result)
        save(encryptedPassphrase)
        return result
    }
}

enum class NotesView(override val textResId: Int) : StaticTextProvider {
    LIST(R.string.list),
    GRID(R.string.grid),
}

enum class Theme(override val textResId: Int) : StaticTextProvider {
    DARK(R.string.dark),
    LIGHT(R.string.light),
    FOLLOW_SYSTEM(R.string.follow_system),
}

enum class DateFormat : TextProvider {
    NONE,
    RELATIVE,
    ABSOLUTE;

    override fun getText(context: Context): String {
        val date = Date(System.currentTimeMillis() - 86400000)
        return when (this) {
            NONE -> context.getString(R.string.none)
            RELATIVE -> PrettyTime().format(date)
            ABSOLUTE -> java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL).format(date)
        }
    }
}

enum class TextSize(override val textResId: Int) : StaticTextProvider {
    SMALL(R.string.small),
    MEDIUM(R.string.medium),
    LARGE(R.string.large);

    val editBodySize: Float
        get() {
            return when (this) {
                SMALL -> 14f
                MEDIUM -> 16f
                LARGE -> 18f
            }
        }

    val editTitleSize: Float
        get() {
            return when (this) {
                SMALL -> 18f
                MEDIUM -> 20f
                LARGE -> 22f
            }
        }

    val displayBodySize: Float
        get() {
            return when (this) {
                SMALL -> 12f
                MEDIUM -> 14f
                LARGE -> 16f
            }
        }

    val displayTitleSize: Float
        get() {
            return when (this) {
                SMALL -> 14f
                MEDIUM -> 16f
                LARGE -> 18f
            }
        }
}

enum class ListItemSort(override val textResId: Int) : StaticTextProvider {
    NO_AUTO_SORT(R.string.no_auto_sort),
    AUTO_SORT_BY_CHECKED(R.string.auto_sort_by_checked),
}

enum class BiometricLock(override val textResId: Int) : StaticTextProvider {
    ENABLED(R.string.enabled),
    DISABLED(R.string.disabled),
}

object Constants {
    const val PASSWORD_EMPTY = "None"
}

fun String.toCamelCase(): String {
    return this.lowercase()
        .split("_")
        .mapIndexed { index, word ->
            if (index == 0) word
            else
                word.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                }
        }
        .joinToString("")
}

fun String.fromCamelCaseToEnumName(): String {
    return this.fold(StringBuilder()) { acc, char ->
            if (char.isUpperCase() && acc.isNotEmpty()) {
                acc.append("_")
            }
            acc.append(char.uppercase())
        }
        .toString()
}
