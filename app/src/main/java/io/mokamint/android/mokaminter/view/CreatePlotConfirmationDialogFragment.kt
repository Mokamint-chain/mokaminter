package io.mokamint.android.mokaminter.view

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.Fragment
import io.mokamint.android.mokaminter.R
import io.mokamint.android.mokaminter.model.Miner
import io.mokamint.plotter.PlotSizes

/**
 * A dialog that asks confirmation before the creation of a plot for a miner,
 * so that the user is aware of the size of the plot.
 */
class CreatePlotConfirmationDialogFragment: AbstractDialogFragment() {

    /**
     * The miner for which the plot should be created.
     */
    private lateinit var miner: Miner

    companion object {
        private val TAG = CreatePlotConfirmationDialogFragment::class.simpleName
        private const val MINER_KEY = "miner"

        fun show(father: Fragment, miner: Miner) {
            val dialog = CreatePlotConfirmationDialogFragment()
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
        val totalSize = PlotSizes.of(miner.getProlog(), miner.size, miner.miningSpecification.hashingForDeadlines).totalSize

        return AlertDialog.Builder(context)
            .setTitle(R.string.create_plot_question)
            .setIcon(R.drawable.ic_plot_file)
            .setMessage(getString(
                    R.string.create_plot_confirmation_message,
                    miner.miningSpecification.name,
                    miner.size,
                    totalSize / 1_000_000_000.0 // we express the size in gigabytes
                ))
            .setNegativeButton(R.string.dismiss) { _, _ -> }
            .setPositiveButton(R.string.create) { _, _ -> getController().onPlotCreationRequested(miner) }
            .create()
    }
}