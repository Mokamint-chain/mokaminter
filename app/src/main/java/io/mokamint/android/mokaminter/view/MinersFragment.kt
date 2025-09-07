package io.mokamint.android.mokaminter.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import io.mokamint.android.mokaminter.databinding.FragmentMinersBinding

class MinersFragment : AbstractFragment<FragmentMinersBinding>() {

    companion object {
        private const val TAG = "MinersFragment"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): android.view.View {
        setBinding(FragmentMinersBinding.inflate(inflater, container, false))
        return binding.root
    }

    override fun onStart() {
        super.onStart()
    }
}