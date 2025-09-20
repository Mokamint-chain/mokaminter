package io.mokamint.android.mokaminter.view

import android.annotation.SuppressLint
import android.content.res.ColorStateList
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
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.mokamint.android.mokaminter.R
import io.mokamint.android.mokaminter.databinding.FragmentMinersBinding
import io.mokamint.android.mokaminter.databinding.MinerCardBinding
import io.mokamint.android.mokaminter.model.Miner
import io.mokamint.android.mokaminter.model.Miners
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
        onMinersChanged(getModel().getMiners())
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

    @UiThread override fun onMinersChanged(newMiners: Miners) {
        if (newMiners.stream().count() == 0L) {
            // if there are no miners, we create a quick
            // link for the addition of a new miner, as a hint to the user
            binding.addMiner.visibility = VISIBLE
            binding.addMiner.setOnClickListener { navigate(toAddMiner()) }
        }
        else
            binding.addMiner.visibility = GONE

        adapter.setMiners(newMiners)
    }

    @UiThread override fun onMinerDeleted(deleted: Miner) {
        onMinersChanged(getModel().getMiners())
        notifyUser(getString(R.string.deleted_miner, deleted.miningSpecification.name))
    }

    private inner class RecyclerAdapter: RecyclerView.Adapter<RecyclerAdapter.ViewHolder>() {
        private var miners = emptyArray<Miner>()

        @SuppressLint("NotifyDataSetChanged")
        fun setMiners(miners: Miners) {
            this.miners = miners.stream().toArray { i -> arrayOfNulls(i) }
            notifyDataSetChanged()
        }

        private inner class ViewHolder(val binding: MinerCardBinding): RecyclerView.ViewHolder(binding.root) {

            fun bindTo(miner: Miner) {
                setCardBackground(R.color.purple_200)
                binding.name.text = miner.miningSpecification.name
                binding.description.text = miner.miningSpecification.description
                binding.uri.text = miner.uri.toString()
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

            private fun setCardBackground(color: Int) {
                binding.card.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, color))
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