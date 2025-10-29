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

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.drawable.AnimationDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.UiThread
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.mokamint.android.mokaminter.R
import io.mokamint.android.mokaminter.databinding.FragmentMinersBinding
import io.mokamint.android.mokaminter.databinding.MinerCardBinding
import io.mokamint.android.mokaminter.model.Miner
import io.mokamint.android.mokaminter.model.MinerStatus
import io.mokamint.android.mokaminter.model.MinersSnapshot
import io.mokamint.android.mokaminter.view.MinersFragmentDirections.toAddMiner
import java.time.Instant

/**
 * The fragment that shows the list of the existing miners to the user.
 */
class MinersFragment : AbstractFragment<FragmentMinersBinding>() {
    private lateinit var adapter: RecyclerAdapter
    private lateinit var viewsLayoutManager: LinearLayoutManager

    companion object {
        private val TAG = MinersFragment::class.simpleName
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onResume() {
        super.onResume()
        getController().onMinersVisible()
    }

    override fun onPause() {
        getController().onMinersInvisible()
        super.onPause()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): android.view.View {
        setBinding(FragmentMinersBinding.inflate(inflater, container, false))
        adapter = RecyclerAdapter()
        viewsLayoutManager = LinearLayoutManager(context)
        binding.recyclerView.layoutManager = viewsLayoutManager
        binding.recyclerView.adapter = adapter

        return binding.root
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.fragment_miners, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_reload_balances -> {
                getController().onBalancesReloadRequested()
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

    @UiThread override fun onRedrawMiners() {
        adapter.update()
    }

    @UiThread override fun onDeleted(deleted: Miner) {
        adapter.update()
        notifyUser(getString(R.string.deleted_miner, deleted.miningSpecification.name))
    }

    @UiThread override fun onAdded(added: Miner) {
        adapter.update()
        notifyUser(getString(R.string.added_miner, added.miningSpecification.name))
    }

    @UiThread override fun onPlotCreationTick(miner: Miner, percent: Int) {
        adapter.progressPlotCreation(miner, percent)
    }

    @UiThread override fun onPlotCreationCompleted(miner: Miner) {
        adapter.progressStops(miner)
        adapter.update(miner)
        notifyUser(getString(R.string.plot_creation_completed, miner.miningSpecification.name))
    }

    @UiThread override fun onBalanceChanged(miner: Miner) {
        adapter.update(miner)
    }

    @UiThread override fun onTurnedOn(miner: Miner) {
        adapter.update(miner)
    }

    @UiThread override fun onTurnedOff(miner: Miner) {
        adapter.update(miner)
    }

    @UiThread override fun onConnected(miner: Miner) {
        adapter.update(miner)
    }

    @UiThread override fun onDisconnected(miner: Miner) {
        adapter.update(miner)
    }

    @UiThread override fun onDeadlineComputed(miner: Miner) {
        adapter.showHeartFor(miner)
    }

    private inner class RecyclerAdapter: RecyclerView.Adapter<RecyclerAdapter.ViewHolder>() {
        private var snapshot = MinersSnapshot.empty()

        /**
         * Keeps track of the progress of the creation of the miners.
         */
        private val progress = HashMap<Miner, Int>()

        @SuppressLint("NotifyDataSetChanged")
        fun update() {
            snapshot = getModel().miners.snapshot()

            if (snapshot.size() == 0) {
                // if there are no miners, we create a quick
                // link for the addition of a new miner, as a hint to the user
                binding.addMiner.visibility = VISIBLE
                binding.addMiner.setOnClickListener { navigate(toAddMiner()) }
            }
            else
                binding.addMiner.visibility = GONE

            notifyDataSetChanged()
        }

        fun update(miner: Miner) {
            // if the miner whose status has been updated is among those in this adapter,
            // we require a redraw of its item only
            val pos = snapshot.indexOf(miner)
            if (pos >= 0)
                notifyItemChanged(pos)
        }

        fun showHeartFor(miner: Miner) {
            val pos = snapshot.indexOf(miner)
            if (pos >= 0) {
                // we identify the view of the card #pos and look for the image view
                // whose background we can set to the heart animation
                viewsLayoutManager.findViewByPosition(pos)
                    ?.findViewById<ImageView>(R.id.image_heart)?.let { it ->
                    // we must reset the drawable to 0, or otherwise the animation does not restart
                    it.setBackgroundResource(0)
                    it.setBackgroundResource(R.drawable.animation_heart)
                    (it.background as AnimationDrawable).start()
                }
            }
        }

        fun progressPlotCreation(miner: Miner, percent: Int) {
            progress.put(miner, percent)

            // if the miner whose plot is being created is among those in this adapter,
            // we require a redraw of its item only
            val pos = snapshot.indexOf(miner)
            if (pos >= 0)
                // by passing a dummy payload, we induce a call to onBindViewHolder with a payload,
                // that does not perform any animation on the updated item; by calling
                // the simpler notifyItemChanged without payload, an ugly flickering effect occurs
                notifyItemChanged(pos, percent)
        }

        fun progressStops(miner: Miner) {
            progress.remove(miner)
        }

        private fun totalNonces(miner: Miner): String {
            return resources.getQuantityString(R.plurals.nonces,
                // the quantity selector must be an Int but we have a Long here...
                if (miner.plotSize > 1000L) 1000 else miner.plotSize.toInt(),
                miner.plotSize)
        }

        private inner class ViewHolder(val binding: MinerCardBinding): RecyclerView.ViewHolder(binding.root) {

            fun bindTo(miner: Miner, status: MinerStatus) {
                if (getController().hasConnectedServiceFor(miner)) {
                    binding.card.backgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.connected_miner)
                    )
                    binding.plotSize.text = getString(R.string.miner_card_plot_size, totalNonces(miner))
                }
                else {
                    binding.card.backgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.disconnected_miner)
                    )

                    if (status.hasPlotReady)
                        binding.plotSize.text = getString(R.string.miner_card_plot_size, totalNonces(miner))
                    else {
                        val percent = progress[miner] ?: 0
                        val noncesProcessed = miner.plotSize * percent / 100
                        binding.plotSize.text = getString(
                            R.string.miner_card_plot_size_in_progress,
                            noncesProcessed,
                            totalNonces(miner))
                    }
                }

                binding.name.text = getString(
                    R.string.miner_card_name,
                    miner.miningSpecification.name,
                    miner.miningSpecification.chainId
                )
                binding.description.text = miner.miningSpecification.description
                binding.uri.text = getString(R.string.miner_card_uri, miner.uri)
                binding.balance.text = getString(R.string.miner_card_balance, status.balance)
                binding.lastUpdated.text = getString(R.string.miner_card_last_updated,
                    lastUpdatedMessage(status))
                binding.publicKey.text = getString(
                    R.string.miner_card_public_key,
                    miner.publicKeyBase58,
                    miner.miningSpecification.signatureForDeadlines.name + ", base58"
                )
                binding.menuButton.setOnClickListener { createMenuForMiner(miner, status) }
            }

            /**
             * Yields a man readable representation of the last updated information in the
             * given miner status information, in the form, for instance, of seconds or minutes ago.
             *
             * @param status the miner status information
             * @return the man readable representation of the last updated information
             */
            fun lastUpdatedMessage(status: MinerStatus): String {
                val lastUpdated = status.lastUpdated

                if (lastUpdated < 0)
                    return context.getString(R.string.never)

                val now = Instant.now().toEpochMilli()
                val diff = now - lastUpdated
                if (diff < 0)
                    return getString(R.string.in_the_future)
                else if (diff < 1_000L)
                    return getString(R.string.now)
                else if (diff < 60_000L) { // one minute
                    val seconds = diff.toInt() / 1_000
                    return resources.getQuantityString(R.plurals.seconds_ago, seconds, seconds)
                }
                else if (diff < 3_600_000L) { // one hour
                    val minutes = diff.toInt() / 60_000
                    return resources.getQuantityString(R.plurals.minutes_ago, minutes, minutes)
                }
                else if (diff < 86_400_000) { // 24 hours
                    val hours = diff.toInt() / 3_600_000
                    return resources.getQuantityString(R.plurals.hours_ago, hours, hours)
                }
                else
                    return getString(R.string.more_than_one_day_ago)
            }

            private fun createMenuForMiner(miner: Miner, status: MinerStatus) {
                val popup = PopupMenu(context, binding.menuButton)
                popup.menuInflater.inflate(R.menu.miner_actions, popup.menu)
                popup.menu[0].isEnabled = status.hasPlotReady && !status.isOn
                popup.menu[1].isEnabled = status.isOn
                popup.setOnMenuItemClickListener{ item -> clickListenerForMiner(item, miner) }
                popup.show()
            }

            private fun clickListenerForMiner(item: MenuItem, miner: Miner): Boolean {
                return when (item.itemId) {
                    R.id.action_turn_off_miner -> {
                        getController().onTurnOffRequested(miner)
                        true
                    }
                    R.id.action_turn_on_miner -> {
                        getController().onTurnOnRequested(miner)
                        true
                    }
                    R.id.action_share_miner -> {
                        getController().onShareRequested(miner, context)
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

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int, payloads: List<Any?>) {
            viewHolder.bindTo(snapshot.getMiner(position), snapshot.getStatus(position))
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            viewHolder.bindTo(snapshot.getMiner(position), snapshot.getStatus(position))
        }

        override fun getItemCount(): Int {
            return snapshot.size()
        }
    }
}