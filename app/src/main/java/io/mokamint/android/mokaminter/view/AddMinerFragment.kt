package io.mokamint.android.mokaminter.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.preference.PreferenceManager
import io.hotmoka.crypto.BIP39Dictionaries
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

    private lateinit var insertWordsViews: Array<AutoCompleteTextView>

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

        insertWordsViews = arrayOf(
            binding.insertWord1, binding.insertWord2, binding.insertWord3, binding.insertWord4,
            binding.insertWord5, binding.insertWord6, binding.insertWord7, binding.insertWord8,
            binding.insertWord9, binding.insertWord10, binding.insertWord11, binding.insertWord12
        )

        val allWords: Array<String> = BIP39Dictionaries.ENGLISH_DICTIONARY.allWords.toArray { i -> arrayOfNulls(i) }
        val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, allWords)
        insertWordsViews.forEach { view ->  view.setAdapter(adapter) }

        binding.addMinerButton.setOnClickListener {
            closeKeyboard()
            addMiner()
        }

        binding.createKey.setOnCheckedChangeListener { button, isChecked ->
            if (isChecked) configureForCreateKey() }

        binding.useExistingKey.setOnCheckedChangeListener { button, isChecked ->
            if (isChecked) configureForUseExistingKey() }

        binding.useExistingKeyPair.setOnCheckedChangeListener { button, isChecked ->
            if (isChecked) configureForUseExistingKeyPair() }

        if (binding.createKey.isChecked)
            configureForCreateKey()
        else if (binding.useExistingKey.isChecked)
            configureForUseExistingKey()
        else
            configureForUseExistingKeyPair()

        return binding.root
    }

    private fun configureForCreateKey() {
        var pos = 0
        BIP39Mnemonics.of(entropy.entropyAsBytes)
            .stream()
            .forEachOrdered { word ->
                wordsViews[pos].text = getString(R.string.add_miner_bip39_word, pos + 1, word)
                pos++
            }

        binding.rowWords1.visibility = View.VISIBLE
        binding.rowWords2.visibility = View.VISIBLE
        binding.rowWords3.visibility = View.VISIBLE
        binding.rowInsertWords1.visibility = View.GONE
        binding.rowInsertWords2.visibility = View.GONE
        binding.rowInsertWords3.visibility = View.GONE
        binding.keyPairPassword.visibility = View.VISIBLE
        binding.hideShowKeyPairPassword.visibility = View.VISIBLE
        binding.mnemonicsInfo.visibility = View.VISIBLE
        binding.mnemonicsInfo.text = getString(R.string.words_warning)
        binding.publicKeyBase58.visibility = View.GONE
    }

    private fun configureForUseExistingKey() {
        binding.rowWords1.visibility = View.GONE
        binding.rowWords2.visibility = View.GONE
        binding.rowWords3.visibility = View.GONE
        binding.rowInsertWords1.visibility = View.GONE
        binding.rowInsertWords2.visibility = View.GONE
        binding.rowInsertWords3.visibility = View.GONE
        binding.keyPairPassword.visibility = View.GONE
        binding.hideShowKeyPairPassword.visibility = View.GONE
        binding.mnemonicsInfo.visibility = View.GONE
        binding.publicKeyBase58.visibility = View.VISIBLE
    }

    private fun configureForUseExistingKeyPair() {
        binding.rowWords1.visibility = View.GONE
        binding.rowWords2.visibility = View.GONE
        binding.rowWords3.visibility = View.GONE
        binding.rowInsertWords1.visibility = View.VISIBLE
        binding.rowInsertWords2.visibility = View.VISIBLE
        binding.rowInsertWords3.visibility = View.VISIBLE
        binding.keyPairPassword.visibility = View.VISIBLE
        binding.hideShowKeyPairPassword.visibility = View.VISIBLE
        binding.mnemonicsInfo.visibility = View.VISIBLE
        binding.mnemonicsInfo.text = getString(R.string.insert_words_info)
        binding.publicKeyBase58.visibility = View.GONE
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

        val maxSize = PreferenceManager.getDefaultSharedPreferences(context).getString("max_plot_size", "1000")!!.toLong()
        if (size > maxSize) {
            notifyUser(getString(R.string.add_miner_message_plot_size_limited, maxSize))
            return
        }

        if (binding.createKey.isChecked) {
            val password = binding.keyPairPassword.text.toString()
            uuid = getController().onMinerCreationRequested(uri, size, entropy, password, null)
        }
        else if (binding.useExistingKey.isChecked) {
            val publicKeyBase58 = binding.publicKeyBase58.text.toString()
            uuid = getController().onMinerCreationRequested(uri, size, null, null, publicKeyBase58)
        }
        else if (binding.useExistingKeyPair.isChecked) {
            val words = insertWordsViews.map { view -> view.text.toString() }.toTypedArray()

            try {
                val entropy = Entropies.of(BIP39Mnemonics.of(words).bytes)
                val password = binding.keyPairPassword.text.toString()
                uuid = getController().onMinerCreationRequested(uri, size, entropy, password, null)
            }
            catch (e: IllegalArgumentException) {
                notifyUser(getString(R.string.wrong_bip39_phrase, e.message))
            }
        }
    }

    @UiThread override fun onReadyToCreatePlotFor(miner: Miner) {
        // we serve only the miner whose creation we started
        if (miner.uuid == uuid)
            CreatePlotConfirmationDialogFragment.show(this, miner)
    }

    override fun onPlotCreationConfirmed(miner: Miner) {
        // we serve only the miner whose creation we started
        if (miner.uuid == uuid)
            popBackStack()
    }
}