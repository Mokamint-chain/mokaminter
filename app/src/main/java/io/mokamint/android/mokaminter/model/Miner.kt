package io.mokamint.android.mokaminter.model

import io.hotmoka.crypto.Base58
import io.hotmoka.crypto.Entropies
import io.hotmoka.crypto.HashingAlgorithms
import io.hotmoka.crypto.Hex
import io.hotmoka.crypto.SignatureAlgorithms
import io.hotmoka.crypto.api.Entropy
import io.hotmoka.crypto.api.HashingAlgorithm
import io.hotmoka.crypto.api.SignatureAlgorithm
import io.mokamint.miner.MiningSpecifications
import io.mokamint.miner.api.MiningSpecification
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlSerializer
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.security.NoSuchAlgorithmException

/**
 * The specification of a miner.
 */
class Miner: Comparable<Miner> {

    /**
     * The specification of the mining endpoint of the miner.
     */
    val miningSpecification: MiningSpecification

    /**
     * The URI where the mining endpoint can be contacted.
     */
    val uri: URI;

    /**
     * The entropy of the key pair used for signing the deadlines with this miner.
     */
    val entropy: Entropy;

    /**
     * Creates the specification of a miner.
     *
     * @param miningSpecification the specification of the mining endpoint of the miner
     * @param uri the URI where the mining endpoint can be contacted
     * @param entropy the entropy of the key pair used for signing the deadlines with this miner
     */
    constructor(miningSpecification: MiningSpecification, uri: URI, entropy: Entropy) {
        this.miningSpecification = miningSpecification;
        this.uri = uri;
        this.entropy = entropy;
    }

    constructor(parser: XmlPullParser) {
        parser.require(XmlPullParser.START_TAG, null, "miner")

        var miningSpecification: MiningSpecification? = null
        var uri: URI? = null
        var entropy: Entropy? = null

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG)
                continue

            when (parser.name) {
                "miningSpecification" -> miningSpecification = readMiningSpecification(parser)
                "uri" -> uri = readURI(parser)
                "entropy" -> entropy = readEntropy(parser)
                else -> skip(parser)
            }
        }

        parser.require(XmlPullParser.END_TAG, null, "miner")

        this.miningSpecification = miningSpecification ?: throw IOException("Missing mining specification in miner")
        this.uri = uri ?: throw IOException("Missing URI in miner")
        this.entropy = entropy ?: throw IOException("Missing entropy in miner")
    }

    private fun readMiningSpecification(parser: XmlPullParser): MiningSpecification {
        val tag = parser.name

        var name: String? = null
        var description: String? = null
        var chainId: String? = null
        var hashingForDeadlines: HashingAlgorithm? = null
        var signatureForBlocks: SignatureAlgorithm? = null
        var signatureForDeadlines: SignatureAlgorithm? = null
        var publicKeyForSigningBlocksBase58: String? = null

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG)
                continue

            when (parser.name) {
                "name" -> name = readText(parser)
                "description" -> description = readText(parser)
                "chainId" -> chainId = readText(parser)
                "hashingForDeadlines" -> hashingForDeadlines = readHashingAlgorithm(parser)
                "signatureForBlocks" -> signatureForBlocks = readSignatureAlgorithm(parser)
                "signatureForDeadlines" -> signatureForDeadlines = readSignatureAlgorithm(parser)
                "publicKeyForSigningBlocksBase58" -> publicKeyForSigningBlocksBase58 = readText(parser)
                else -> skip(parser)
            }
        }

        if (signatureForBlocks == null)
            throw IOException("Missing signatureForBlocks tag in miner")

        if (publicKeyForSigningBlocksBase58 == null)
            throw IOException("Missing publicKeyForSigningBlocksBase58 tag in miner")

        parser.require(XmlPullParser.END_TAG, null, tag)

        return MiningSpecifications.of(
            name ?: throw IOException("Missing name tag in miner"),
            description ?: throw IOException("Missing description tag in miner"),
            chainId ?: throw IOException("Missing chainId tag in miner"),
            hashingForDeadlines ?: throw IOException("Missing hashingForDeadlines tag in miner"),
            signatureForBlocks,
            signatureForDeadlines ?: throw IOException("Missing signatureForDeadlines tag in miner"),
            signatureForBlocks.publicKeyFromEncoding(Base58.fromBase58String(publicKeyForSigningBlocksBase58, ::IOException))
        )
    }

    private fun readURI(parser: XmlPullParser): URI {
        val tag = parser.name
        val entropy = readText(parser)
        parser.require(XmlPullParser.END_TAG, null, tag)

        try {
            return URI(entropy)
        }
        catch (e: URISyntaxException) {
            throw XmlPullParserException(e.message)
        }
    }

    private fun readHashingAlgorithm(parser: XmlPullParser): HashingAlgorithm {
        val tag = parser.name
        val signature = readText(parser)
        parser.require(XmlPullParser.END_TAG, null, tag)

        try {
            return HashingAlgorithms.of(signature)
        }
        catch (e: NoSuchAlgorithmException) {
            throw XmlPullParserException(e.message)
        }
    }

    private fun readSignatureAlgorithm(parser: XmlPullParser): SignatureAlgorithm {
        val tag = parser.name
        val signature = readText(parser)
        parser.require(XmlPullParser.END_TAG, null, tag)

        try {
            return SignatureAlgorithms.of(signature)
        }
        catch (e: NoSuchAlgorithmException) {
            throw XmlPullParserException(e.message)
        }
    }

    private fun readEntropy(parser: XmlPullParser): Entropy {
        val tag = parser.name
        val entropy = readText(parser)
        parser.require(XmlPullParser.END_TAG, null, tag)

        try {
            return Entropies.of(Hex.fromHexString(entropy, ::XmlPullParserException))
        }
        catch (e: IllegalArgumentException) {
            throw XmlPullParserException(e.message)
        }
    }

    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result
    }

    private fun skip(parser: XmlPullParser) {
        parser.require(XmlPullParser.START_TAG, null, null)

        var depth = 1
        do {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
        while (depth != 0)
    }

    fun writeWith(serializer: XmlSerializer) { // TODO

    }

    override fun compareTo(other: Miner): Int {
        var diff = miningSpecification.name.compareTo(other.miningSpecification.name);
        if (diff != 0)
            return diff

        diff = uri.compareTo(other.uri)
        if (diff != 0)
            return diff

        return entropy.compareTo(other.entropy)
    }

    override fun equals(other: Any?): Boolean {
        return other is Miner && miningSpecification.name == other.miningSpecification.name
                && uri == other.uri && entropy == other.entropy
    }

    override fun hashCode(): Int {
        return entropy.hashCode()
    }

    override fun toString(): String {
        return "${miningSpecification.name} ($uri)"
    }
}