/*
Copyright 2025 Fausto Spoto

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package io.mokamint.android.mokaminter.view

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.Fragment
import io.mokamint.android.mokaminter.R
import io.mokamint.android.mokaminter.model.Miner

/**
 * A dialog fragment to ask confirmation about the deletion of a miner.
 */
class DeleteMinerConfirmationDialogFragment: AbstractDialogFragment() {
    private lateinit var miner: Miner

    companion object {
        private val TAG = DeleteMinerConfirmationDialogFragment::class.simpleName
        private const val MINER_KEY = "miner"

        fun show(father: Fragment, miner: Miner) {
            val dialog = DeleteMinerConfirmationDialogFragment()
            val args = Bundle()
            args.putParcelable(MINER_KEY, miner)
            dialog.arguments = args
            dialog.show(father.childFragmentManager, TAG)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        miner = arguments?.getParcelable(MINER_KEY)!!
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(context)
            .setTitle(R.string.delete_miner_question)
            .setIcon(R.drawable.ic_delete)
            .setMessage(getString(
                    R.string.delete_miner_confirmation_message,
                    miner.miningSpecification.name
                ))
            .setNegativeButton(R.string.dismiss) { _, _ -> }
            .setPositiveButton(R.string.delete) { _, _ -> getController().onDeleteRequested(miner) }
            .create()
    }
}