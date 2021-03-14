package com.infinity_coder.runner.utilities.extensions

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

fun Activity.toast(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}

fun Activity.checkPermissions(permission: String, onGrantedPermission: () -> Unit) {
    if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
        onGrantedPermission.invoke()
    }
}

fun AppCompatActivity.checkPermission(
    permission: String,
    onGranted: () -> Unit,
    onShowRational: () -> Unit,
    onDenied: () -> Unit
) {

    when {
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
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