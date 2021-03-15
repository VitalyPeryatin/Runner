package com.infinity_coder.runner.utilities.extensions

import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

fun Fragment.toast(text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

fun Fragment.checkPermission(
    permission: String,
    onGranted: () -> Unit,
    onShowRational: () -> Unit,
    onDenied: () -> Unit
) {

    when {
        ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED -> {
            onGranted.invoke()
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && shouldShowRequestPermissionRationale(permission) -> {
            onShowRational.invoke()
        }
        else -> {
            onDenied.invoke()
        }
    }
}