package io.mokamint.android.mokaminter.view

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.Fragment
import io.mokamint.android.mokaminter.R
import io.mokamint.android.mokaminter.model.Miner

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
            .setPositiveButton(R.string.delete) { _, _ -> getController().requestDelete(miner) }
            .create()
    }
}