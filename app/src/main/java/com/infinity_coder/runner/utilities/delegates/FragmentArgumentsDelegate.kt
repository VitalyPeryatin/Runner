package com.infinity_coder.runner.utilities.delegates

import android.os.Bundle
import androidx.fragment.app.Fragment
import kotlin.reflect.KProperty

class FragmentArgumentsDelegate<T>(
    private val key: String,
    private val default: T,
    private val action: (args: Bundle, value: T) -> Unit
) {

    @Suppress("UNCHECKED_CAST")
    operator fun getValue(
        thisRef: Fragment,
        property: KProperty<*>
    ): T {
        return thisRef.arguments?.get(key) as? T ?: default
    }

    operator fun setValue(thisRef: Fragment, property: KProperty<*>, value: T) {
        if (thisRef.arguments?.containsKey(key) == true) {
            throw IllegalStateException("Overwriting the value by key '$key' is prohibited")
        }
        val args = thisRef.arguments ?: Bundle().also(thisRef::setArguments)
        action.invoke(args, value)
    }
}