package io.mokamint.android.mokaminter.view

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.mokamint.android.mokaminter.R
import io.mokamint.android.mokaminter.databinding.FragmentMinersBinding
import io.mokamint.android.mokaminter.databinding.MinerCardBinding
import io.mokamint.android.mokaminter.model.Miner
import io.mokamint.android.mokaminter.view.MinersFragmentDirections.toAddMiner

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
        adapter.redraw()
    }

    @UiThread override fun onMinerDeleted(deleted: Miner) {
        adapter.redraw()
        notifyUser(getString(R.string.deleted_miner, deleted.miningSpecification.name))
    }

    @UiThread override fun onMinerAdded(added: Miner) {
        adapter.redraw()
        notifyUser(getString(R.string.added_miner, added.miningSpecification.name))
    }

    private inner class RecyclerAdapter: RecyclerView.Adapter<RecyclerAdapter.ViewHolder>() {
        private var miners = emptyArray<Miner>()

        @SuppressLint("NotifyDataSetChanged")
        fun redraw() {
            miners = getModel().miners.stream().toArray { i -> arrayOfNulls(i) }

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

        private inner class ViewHolder(val binding: MinerCardBinding): RecyclerView.ViewHolder(binding.root) {

            fun bindTo(miner: Miner) {
                binding.name.text = miner.miningSpecification.name
                binding.description.text = miner.miningSpecification.description
                binding.uri.text = getString(R.string.miner_card_uri, miner.uri)
                binding.plotSize.text = getString(
                    R.string.miner_card_plot_size,
                    resources.getQuantityString(R.plurals.nonces, miner.size, miner.size)
                )
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
                    R.id.action_show_or_edit_miner -> {
                        notifyUser("Show or edit miner")
                        true
                    }
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

        override fun onBindViewHolder(viewHolder: ViewHolder, i: Int) {
            viewHolder.bindTo(miners[i])
        }

        override fun getItemCount(): Int {
            return miners.size
        }
    }
}