package com.infinity_coder.runner.ui.map

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.infinity_coder.runner.R

class MapFragment: Fragment(R.layout.fragment_map) {

    companion object {
        fun newInstance(): MapFragment {
            return MapFragment()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


    }

}