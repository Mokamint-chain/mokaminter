package io.mokamint.android.mokaminter.view

import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CompoundButton
import android.widget.TextView
import io.hotmoka.crypto.BIP39Dictionaries
import io.hotmoka.crypto.BIP39Mnemonics
import io.hotmoka.crypto.Entropies
import io.mokamint.android.mokaminter.R
import io.mokamint.android.mokaminter.databinding.FragmentAddMinerBinding

class AddMinerFragment: AbstractFragment<FragmentAddMinerBinding>() {

    private lateinit var wordsViews: Array<AutoCompleteTextView>;

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

        // we only allow to insert words that come from the English BIP39 dictionary
        val allWords: Array<String> = BIP39Dictionaries.ENGLISH_DICTIONARY.allWords.toArray { i -> arrayOfNulls(i) }
        val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, allWords)

        for (wordsView in wordsViews)
            wordsView.setAdapter(adapter)

        binding.addMinerButton.setOnClickListener {
            closeKeyboard()
            addMiner()
        }

        binding.keypairCreateNew.setOnCheckedChangeListener { button, isChecked ->
            if (isChecked) showNewKeyPair() else eraseKeyPair() }

        if (binding.keypairCreateNew.isChecked)
            showNewKeyPair()

        return binding.root
    }

    private fun showNewKeyPair() {
        val entropy = Entropies.random()

        var pos = 0;
        BIP39Mnemonics.of(entropy.entropyAsBytes)
            .stream()
            .forEachOrdered { word ->
                // we freeze the edit view so that changes are not allowed
                wordsViews[pos].isFocusable = false
                wordsViews[pos++].setText(word)
            }
    }

    private fun eraseKeyPair() {
        wordsViews.forEach { view ->
            view.isFocusable = true
            view.setText("")
        }
    }

    private fun addMiner() {
        notifyUser(getString(R.string.add_miner))
    }
}