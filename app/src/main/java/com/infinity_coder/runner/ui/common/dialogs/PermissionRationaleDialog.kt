package com.infinity_coder.runner.ui.common.dialogs

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.infinity_coder.runner.R
import com.infinity_coder.runner.utilities.extensions.argument
import kotlinx.android.synthetic.main.dialog_rationale_permission.*

class PermissionRationaleDialog: DialogFragment() {

    companion object {

        private const val TAG = "PermissionRationaleDialog"

        private fun newInstance(permission: String): PermissionRationaleDialog {
            return PermissionRationaleDialog().apply {
                argumentPermission = permission
            }
        }

        fun show(fragmentManager: FragmentManager, permission: String) {
            val dialog = newInstance(permission)
            dialog.show(fragmentManager, TAG)
        }
    }

    private var argumentPermission by argument("permission", "")

    private var onPermissionRationaleListener: OnPermissionRationaleListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_rationale_permission, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cancelButton.setOnClickListener {
            dismiss()
        }

        grantButton.setOnClickListener {
            onPermissionRationaleListener?.onPermissionRationaleGotIt(argumentPermission)
            dismiss()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        val parentFragment = parentFragment
        if (context is OnPermissionRationaleListener) {
            onPermissionRationaleListener = context
        } else if (parentFragment is OnPermissionRationaleListener) {
            onPermissionRationaleListener = parentFragment
        }
    }

    override fun onDetach() {
        super.onDetach()

        onPermissionRationaleListener = null
    }
}

interface OnPermissionRationaleListener {
    fun onPermissionRationaleGotIt(permission: String)
}