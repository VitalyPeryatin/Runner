package com.infinity_coder.runner.utilities.extensions

import androidx.fragment.app.Fragment
import com.infinity_coder.runner.utilities.delegates.FragmentArgumentsDelegate

fun Fragment.argument(key: String, default: Int) =
    FragmentArgumentsDelegate(key, default) { args, value ->
        args.putInt(key, value)
    }

fun Fragment.argument(key: String, default: Boolean) =
    FragmentArgumentsDelegate(key, default) { args, value ->
        args.putBoolean(key, value)
    }

fun Fragment.argument(key: String, default: Long) =
    FragmentArgumentsDelegate(key, default) { args, value ->
        args.putLong(key, value)
    }

fun <T: String?> Fragment.argument(key: String, default: T) =
    FragmentArgumentsDelegate(key, default) { args, value ->
        args.putString(key, value)
    }

fun <T: ArrayList<String>> Fragment.argument(key: String, default: T) =
    FragmentArgumentsDelegate(key, default) { args, value ->
        args.putStringArrayList(key, value)
    }