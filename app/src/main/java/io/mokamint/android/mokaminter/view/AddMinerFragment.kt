package io.mokamint.android.mokaminter.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.UiThread
import io.hotmoka.crypto.BIP39Mnemonics
import io.hotmoka.crypto.Entropies
import io.mokamint.android.mokaminter.R
import io.mokamint.android.mokaminter.databinding.FragmentAddMinerBinding
import io.mokamint.android.mokaminter.model.Miner
import java.net.URI
import java.net.URISyntaxException
import java.util.UUID

class AddMinerFragment: AbstractFragment<FragmentAddMinerBinding>() {

    /**
     * The entropy from which a new key pair can be generated, if this is what the user wants.
     */
    private val entropy = Entropies.random()

    private lateinit var wordsViews: Array<TextView>

    /**
     * The identifier of the miner whose creation has been already scheduled by this fragment, if any.
     */
    private var uuid: UUID? = null

    companion object {
        private val TAG = AddMinerFragment::class.simpleName
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        setBinding(FragmentAddMinerBinding.inflate(inflater, container, false))

        wordsViews = arrayOf(
            binding.word1, binding.word2, binding.word3, binding.word4,
            binding.word5, binding.word6, binding.word7, binding.word8,
            binding.word9, binding.word10, binding.word11, binding.word12
        )

        binding.addMinerButton.setOnClickListener {
            closeKeyboard()
            addMiner()
        }

        binding.createNewKey.setOnCheckedChangeListener { button, isChecked ->
            if (isChecked) configureForInsertionOfNewKeyPair() else configureForInsertionOfExistingPublicKey() }

        if (binding.createNewKey.isChecked)
            configureForInsertionOfNewKeyPair()

        return binding.root
    }

    private fun configureForInsertionOfNewKeyPair() {
        var pos = 0
        BIP39Mnemonics.of(entropy.entropyAsBytes)
            .stream()
            .forEachOrdered { word ->
                wordsViews[pos].text = getString(R.string.add_miner_bip39_word, pos + 1, word)
                wordsViews[pos++].visibility = View.VISIBLE
            }

        binding.keypairPassword.visibility = View.VISIBLE
        binding.hideShowKeyPairPassword.visibility = View.VISIBLE
        binding.wordsWarning.visibility = View.VISIBLE
        binding.publicKeyBase58.visibility = View.GONE
    }

    private fun configureForInsertionOfExistingPublicKey() {
        wordsViews.forEach { view ->
            view.visibility = View.GONE
        }

        binding.keypairPassword.visibility = View.GONE
        binding.hideShowKeyPairPassword.visibility = View.GONE
        binding.wordsWarning.visibility = View.GONE
        binding.publicKeyBase58.visibility = View.VISIBLE
    }

    private fun addMiner() {
        val uri: URI

        try {
            uri = URI(binding.uri.text.toString())
        }
        catch (e: URISyntaxException) {
            notifyUser(getString(R.string.add_miner_message_illegal_uri, e.message))
            return
        }

        val size: Long
        try {
            size = binding.size.text.toString().toLong()
        }
        catch (_: NumberFormatException) {
            notifyUser(getString(R.string.add_miner_message_specify_positive_plot_size))
            return
        }

        if (size < 1) {
            notifyUser(getString(R.string.add_miner_message_plot_size_must_be_positive))
            return
        }

        if (binding.createNewKey.isChecked) {
            val password = binding.keypairPassword.text.toString()
            uuid = getController().requestCreationOfMiner(uri, size, entropy, password, null)
        }
        else {
            val publicKeyBase58 = binding.publicKeyBase58.text.toString()
            uuid = getController().requestCreationOfMiner(uri, size, null, null, publicKeyBase58)
        }
    }

    @UiThread override fun onReadyToCreatePlotFor(miner: Miner) {
        // we serve only the miner whose creation we started
        if (miner.uuid == uuid)
            CreatePlotConfirmationDialogFragment.show(this, miner)
    }

    override fun onPlotCreationStarted(miner: Miner) {
        // we serve only the miner whose creation we started
        if (miner.uuid == uuid)
            popBackStack()
    }
}