package io.mokamint.android.mokaminter.view

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.mokamint.android.mokaminter.R
import io.mokamint.android.mokaminter.databinding.FragmentMinersBinding
import io.mokamint.android.mokaminter.databinding.MinerCardBinding
import io.mokamint.android.mokaminter.model.Miner
import io.mokamint.android.mokaminter.view.MinersFragmentDirections.toAddMiner
import java.math.BigInteger

class MinersFragment : AbstractFragment<FragmentMinersBinding>() {

    private lateinit var adapter: RecyclerAdapter

    companion object {
        private val TAG = MinersFragment::class.simpleName
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): android.view.View {
        setBinding(FragmentMinersBinding.inflate(inflater, container, false))
        adapter = RecyclerAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = adapter
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        getController().requestReloadOfMiners()
        // we request to fetch the balances only when the fragment is visible,
        // to reduce network congestion
        getController().startRequestingBalances()
    }

    override fun onStop() {
        getController().stopRequestingBalances()
        super.onStop()
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.miners, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_reload -> {
                getController().requestReloadOfMiners()
                true
            }
            R.id.action_add_miner -> {
                navigate(toAddMiner())
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @UiThread override fun onMinersReloaded() {
        adapter.update()
    }

    @UiThread override fun onMinerDeleted(deleted: Miner) {
        adapter.update()
        notifyUser(getString(R.string.deleted_miner, deleted.miningSpecification.name))
    }

    @UiThread override fun onMinerAdded(added: Miner) {
        adapter.update()
        notifyUser(getString(R.string.added_miner, added.miningSpecification.name))
    }

    @UiThread override fun onPlotCreationTick(miner: Miner, percent: Int) {
        adapter.progressPlotCreation(miner, percent)
    }

    @UiThread override fun onPlotCreationCompleted(miner: Miner) {
        adapter.progressStops(miner)
        adapter.update()
        notifyUser(getString(R.string.plot_creation_completed, miner.miningSpecification.name))
    }

    @UiThread override fun onBalanceUpdated(miner: Miner, balance: BigInteger) {
        adapter.updateBalance(miner, balance)
    }

    private inner class RecyclerAdapter: RecyclerView.Adapter<RecyclerAdapter.ViewHolder>() {
        private var miners = emptyArray<Miner>()

        /**
         * Keeps track of the progress of the creation of the miners.
         */
        private val progress = HashMap<Miner, Int>()

        @SuppressLint("NotifyDataSetChanged")
        fun update() {
            miners = getModel().miners.elements()

            if (miners.isEmpty()) {
                // if there are no miners, we create a quick
                // link for the addition of a new miner, as a hint to the user
                binding.addMiner.visibility = VISIBLE
                binding.addMiner.setOnClickListener { navigate(toAddMiner()) }
            }
            else
                binding.addMiner.visibility = GONE

            notifyDataSetChanged()
        }

        fun progressPlotCreation(miner: Miner, percent: Int) {
            progress.put(miner, percent)
            // if the miner whose plot is being created is among those in this adapter,
            // we require a redraw of its item only
            val pos = miners.indexOf(miner)
            if (pos >= 0)
                // by passing a dummy payload, we induce a call to onBindViewHolder with a payload,
                // that does not perform any animation on the updated item; by calling
                // the simpler notifyItemChanged without payload, an ugly flickering effect occurs
                notifyItemChanged(pos, percent)
        }

        fun updateBalance(miner: Miner, balance: BigInteger) {
            // if the miner whose balance has been updated is among those in this adapter,
            // we require a redraw of its item only
            val pos = miners.indexOf(miner)
            if (pos >= 0) {
                // by passing a dummy payload, we induce a call to onBindViewHolder with a payload,
                // that does not perform any animation on the updated item; by calling
                // the simpler notifyItemChanged without payload, an ugly flickering effect occurs
                notifyItemChanged(pos, balance)
                Log.i(TAG, "Updated balance of $miner to $balance")
            }
        }

        fun progressStops(miner: Miner) {
            progress.remove(miner)
        }

        private inner class ViewHolder(val binding: MinerCardBinding): RecyclerView.ViewHolder(binding.root) {

            fun bindTo(miner: Miner) {
                if (miner.hasPlotReady) {
                    binding.card.backgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.mokamint_bright)
                    )
                    binding.plotSize.text = getString(
                        R.string.miner_card_plot_size,
                        resources.getQuantityString(R.plurals.nonces,
                            // the quantity selector must be an Int but we have a Long here...
                            if (miner.size > 1000L) 1000 else miner.size.toInt(),
                            miner.size)
                    )
                }
                else {
                    binding.card.backgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.mokamint_medium)
                    )
                    val percent = progress.get(miner)
                    val done = if (percent == null || percent == 0) 0 else miner.size * percent / 100
                    binding.plotSize.text = getString(
                        R.string.miner_card_plot_size_in_progress,
                        done,
                        resources.getQuantityString(R.plurals.nonces,
                            // the quantity selector must be an Int but we have a Long here...
                            if (miner.size > 1000L) 1000 else miner.size.toInt(),
                            miner.size)
                    )
                }
                binding.name.text = getString(
                    R.string.miner_card_name,
                    miner.miningSpecification.name,
                    miner.miningSpecification.chainId
                )
                binding.description.text = miner.miningSpecification.description
                binding.uri.text = getString(R.string.miner_card_uri, miner.uri)
                binding.publicKey.text = getString(
                    R.string.miner_card_public_key,
                    miner.publicKeyBase58,
                    miner.miningSpecification.signatureForDeadlines.name + ", base58"
                )
                binding.menuButton.setOnClickListener { createMenuForMiner(miner) }
            }

            private fun createMenuForMiner(miner: Miner) {
                val popup = PopupMenu(context, binding.menuButton)
                popup.menuInflater.inflate(R.menu.miner_actions, popup.menu)
                popup.setOnMenuItemClickListener{ item -> clickListenerForMiner(item, miner) }
                popup.show()
            }

            private fun clickListenerForMiner(item: MenuItem, miner: Miner): Boolean {
                return when (item.itemId) {
                    R.id.action_delete_miner -> {
                        DeleteMinerConfirmationDialogFragment.show(this@MinersFragment, miner)
                        true
                    }
                    else -> false
                }
            }
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): ViewHolder {
            return ViewHolder(
                MinerCardBinding.inflate(
                    LayoutInflater.from(viewGroup.context),
                    viewGroup,
                    false
                )
            )
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int, payloads: List<Any?>) {
            viewHolder.bindTo(miners[position])
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            viewHolder.bindTo(miners[position])
        }

        override fun getItemCount(): Int {
            return miners.size
        }
    }
}