package io.mokamint.android.mokaminter.model

import android.os.Parcel
import android.os.Parcelable
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
import io.mokamint.nonce.Prologs
import io.mokamint.nonce.api.Prolog
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlSerializer
import java.net.URI
import java.net.URISyntaxException
import java.security.NoSuchAlgorithmException
import java.security.PublicKey
import java.security.spec.InvalidKeySpecException
import java.util.UUID
import kotlin.math.min

/**
 * The specification of a miner.
 */
class Miner: Comparable<Miner>, Parcelable {

    /**
     * The identifier of the miner.
     */
    val uuid: UUID

    /**
     * The specification of the mining endpoint of the miner.
     */
    val miningSpecification: MiningSpecification

    /**
     * The URI where the mining endpoint can be contacted.
     */
    val uri: URI

    /**
     * The size of the plot of the miner (number of nonces).
     */
    val size: Long

    /**
     * The entropy of the key pair used for signing the deadlines with this miner.
     */
    val entropy: Entropy

    /**
     * The public key of the miner, derived from entropy, password and signature algorithm for deadlines.
     */
    val publicKey: PublicKey

    /**
     * The {@link #publicKey}, in Base58 format.
     */
    val publicKeyBase58: String

    /**
     * True if and only if the plot for this miner has been fully created and is
     * available in the local storage of the app.
     */
    val hasPlotReady: Boolean

    companion object {
        private const val UUID_TAG = "uuid"
        private const val MINING_SPECIFICATION_TAG = "miningSpecification"
        private const val URI_TAG = "uri"
        private const val SIZE_TAG = "size"
        private const val ENTROPY_TAG = "entropy"
        private const val NAME_TAG = "name"
        private const val DESCRIPTION_TAG = "description"
        private const val CHAIN_ID_TAG = "chainId"
        private const val HASHING_FOR_DEADLINES_TAG = "hashingForDeadlines"
        private const val SIGNATURE_FOR_DEADLINES_TAG = "signatureForDeadlines"
        private const val SIGNATURE_FOR_BLOCKS_TAG = "signatureForBlocks"
        private const val PUBLIC_KEY_FOR_SIGNING_BLOCKS_BASE58_TAG = "publicKeyForSigningBlocksBase58"
        private const val PUBLIC_KEY_FOR_SIGNING_DEADLINES_BASE58_TAG = "publicKeyForSigningDeadlinesBase58"
        private const val HAS_PLOT_READY_TAG = "hasPlotReady"

        @Suppress("unused") @JvmField
        val CREATOR = object : Parcelable.Creator<Miner?> {

            override fun createFromParcel(parcel: Parcel): Miner {
                return Miner(parcel)
            }

            override fun newArray(size: Int): Array<Miner?> {
                return arrayOfNulls(size)
            }
        }
    }

    /**
     * Creates a miner.
     *
     * @param uuid the identifier to use for the miner
     * @param miningSpecification the specification of the mining endpoint of the miner
     * @param uri the URI where the mining endpoint can be contacted
     * @param size the size of the plot of the miner (number of nonces, strictly positive)
     * @param entropy the entropy of the key pair used for signing the deadlines with this miner
     * @param password the password of the miner; this is not saved anywhere, but only used
     *                 to create the public key from the entropy
     */
    constructor(uuid: UUID, miningSpecification: MiningSpecification, uri: URI, size: Long, entropy: Entropy, password: String) {
        this.uuid = uuid
        this.miningSpecification = miningSpecification
        this.uri = uri
        this.size = size
        this.entropy = entropy
        this.publicKey = entropy.keys(password, miningSpecification.signatureForDeadlines).public
        this.publicKeyBase58 = Base58.toBase58String(miningSpecification.signatureForDeadlines.encodingOf(publicKey))
        this.hasPlotReady = false

        if (size < 1)
            throw IllegalArgumentException("The plot size must be a strictly positive number")
    }

    constructor(parcel: Parcel) {
        this.uuid = parcel.readSerializable() as UUID

        val name = parcel.readString()
        val description = parcel.readString()
        val chainId = parcel.readString()
        var hashingForDeadlines = HashingAlgorithms.of(parcel.readString())
        var signatureForBlocks = SignatureAlgorithms.of(parcel.readString())
        var signatureForDeadlines = SignatureAlgorithms.of(parcel.readString())
        var publicKeyForSigningBlocksBase58 = parcel.readString()

        this.miningSpecification = MiningSpecifications.of(
            name,
            description,
            chainId,
            hashingForDeadlines,
            signatureForBlocks,
            signatureForDeadlines,
            signatureForBlocks.publicKeyFromEncoding(Base58.fromBase58String(publicKeyForSigningBlocksBase58))
        )

        this.uri = parcel.readSerializable() as URI
        this.size = parcel.readLong()

        val entropy = ByteArray(parcel.readInt())
        parcel.readByteArray(entropy)
        this.entropy = Entropies.of(entropy)

        var publicKeyForSigningDeadlinesBytes = ByteArray(parcel.readInt())
        parcel.readByteArray(publicKeyForSigningDeadlinesBytes)
        this.publicKey = miningSpecification.signatureForDeadlines.publicKeyFromEncoding(publicKeyForSigningDeadlinesBytes)
        this.publicKeyBase58 = Base58.toBase58String(publicKeyForSigningDeadlinesBytes)

        this.hasPlotReady = parcel.readByte() != 0.toByte()
    }

    constructor(parser: XmlPullParser) {
        val tag = parser.name
        var uuid: UUID? = null
        var miningSpecification: MiningSpecification? = null
        var uri: URI? = null
        var size: Long? = null
        var entropy: Entropy? = null
        var publicKeyBase58: String? = null
        var hasPlotReady: Boolean? = null

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG)
                continue

            when (parser.name) {
                UUID_TAG -> uuid = readUUID(parser)
                MINING_SPECIFICATION_TAG -> miningSpecification = readMiningSpecification(parser)
                URI_TAG -> uri = readURI(parser)
                SIZE_TAG -> size = readSize(parser)
                ENTROPY_TAG -> entropy = readEntropy(parser)
                PUBLIC_KEY_FOR_SIGNING_DEADLINES_BASE58_TAG -> publicKeyBase58 = readText(parser)
                HAS_PLOT_READY_TAG -> hasPlotReady = readBoolean(parser)
                else -> skip(parser)
            }
        }

        parser.require(XmlPullParser.END_TAG, null, tag)

        this.uuid = uuid ?: throw XmlPullParserException("Missing UUID in miner")
        this.miningSpecification = miningSpecification ?: throw XmlPullParserException("Missing mining specification in miner")
        this.uri = uri ?: throw XmlPullParserException("Missing URI in miner")
        this.size = size ?: throw XmlPullParserException("The plot size must be provided and be positive")
        this.entropy = entropy ?: throw XmlPullParserException("Missing entropy in miner")
        this.publicKeyBase58 = Base58.requireBase58(publicKeyBase58, ::XmlPullParserException)

        try {
            this.publicKey = miningSpecification.signatureForDeadlines.publicKeyFromEncoding(
                Base58.fromBase58String(publicKeyBase58)
            )
        }
        catch (e: InvalidKeySpecException) {
            throw XmlPullParserException(e.message)
        }

        this.hasPlotReady = hasPlotReady ?: throw XmlPullParserException("Missing hasPlotReady field in miner")
    }

    private constructor(uuid: UUID, miningSpecification: MiningSpecification, uri: URI, size: Long, entropy: Entropy, publicKey: PublicKey, publicKeyBase58: String, hasPlotReady: Boolean) {
        this.uuid = uuid
        this.miningSpecification = miningSpecification
        this.uri = uri
        this.size = size
        this.entropy = entropy
        this.publicKey = publicKey
        this.publicKeyBase58 = publicKeyBase58
        this.hasPlotReady = hasPlotReady
    }

    fun writeWith(serializer: XmlSerializer, tag: String) {
        serializer.startTag(null, tag)

        serializer.startTag(null, UUID_TAG)
        serializer.text(uuid.toString())
        serializer.endTag(null, UUID_TAG)
        serializer.startTag(null, MINING_SPECIFICATION_TAG)
        serializer.startTag(null, NAME_TAG)
        serializer.text(miningSpecification.name)
        serializer.endTag(null, NAME_TAG)
        serializer.startTag(null, DESCRIPTION_TAG)
        serializer.text(miningSpecification.description)
        serializer.endTag(null, DESCRIPTION_TAG)
        serializer.startTag(null, CHAIN_ID_TAG)
        serializer.text(miningSpecification.chainId)
        serializer.endTag(null, CHAIN_ID_TAG)
        serializer.startTag(null, HASHING_FOR_DEADLINES_TAG)
        serializer.text(miningSpecification.hashingForDeadlines.name)
        serializer.endTag(null, HASHING_FOR_DEADLINES_TAG)
        serializer.startTag(null, SIGNATURE_FOR_BLOCKS_TAG)
        serializer.text(miningSpecification.signatureForBlocks.name)
        serializer.endTag(null, SIGNATURE_FOR_BLOCKS_TAG)
        serializer.startTag(null, SIGNATURE_FOR_DEADLINES_TAG)
        serializer.text(miningSpecification.signatureForDeadlines.name)
        serializer.endTag(null, SIGNATURE_FOR_DEADLINES_TAG)
        serializer.startTag(null, PUBLIC_KEY_FOR_SIGNING_BLOCKS_BASE58_TAG)
        serializer.text(miningSpecification.publicKeyForSigningBlocksBase58)
        serializer.endTag(null, PUBLIC_KEY_FOR_SIGNING_BLOCKS_BASE58_TAG)
        serializer.endTag(null, MINING_SPECIFICATION_TAG)

        serializer.startTag(null, URI_TAG)
        serializer.text(uri.toString())
        serializer.endTag(null, URI_TAG)

        serializer.startTag(null, SIZE_TAG)
        serializer.text(size.toString())
        serializer.endTag(null, SIZE_TAG)

        serializer.startTag(null, ENTROPY_TAG)
        serializer.text(entropy.toString())
        serializer.endTag(null, ENTROPY_TAG)

        serializer.startTag(null, PUBLIC_KEY_FOR_SIGNING_DEADLINES_BASE58_TAG)
        serializer.text(publicKeyBase58)
        serializer.endTag(null, PUBLIC_KEY_FOR_SIGNING_DEADLINES_BASE58_TAG)

        serializer.startTag(null, HAS_PLOT_READY_TAG)
        serializer.text(hasPlotReady.toString())
        serializer.endTag(null, HAS_PLOT_READY_TAG)

        serializer.endTag(null, tag)
    }

    fun withPlotReady(): Miner {
        return Miner(uuid, miningSpecification, uri, size, entropy, publicKey, publicKeyBase58, true)
    }

    /**
     * Yields the prolog to use for this miner.
     */
    fun getProlog(): Prolog {
        return Prologs.of(
            miningSpecification.chainId,
            miningSpecification.signatureForBlocks,
            miningSpecification.publicKeyForSigningBlocks,
            miningSpecification.signatureForDeadlines,
            publicKey,
            ByteArray(0) // TODO: allow the specification of extra in the future
        )
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
                NAME_TAG -> name = readText(parser)
                DESCRIPTION_TAG -> description = readText(parser)
                CHAIN_ID_TAG -> chainId = readText(parser)
                HASHING_FOR_DEADLINES_TAG -> hashingForDeadlines = readHashingAlgorithm(parser)
                SIGNATURE_FOR_BLOCKS_TAG -> signatureForBlocks = readSignatureAlgorithm(parser)
                SIGNATURE_FOR_DEADLINES_TAG -> signatureForDeadlines = readSignatureAlgorithm(parser)
                PUBLIC_KEY_FOR_SIGNING_BLOCKS_BASE58_TAG -> publicKeyForSigningBlocksBase58 = readText(parser)
                else -> skip(parser)
            }
        }

        if (signatureForBlocks == null)
            throw XmlPullParserException("Missing signatureForBlocks tag in miner")

        if (publicKeyForSigningBlocksBase58 == null)
            throw XmlPullParserException("Missing publicKeyForSigningBlocksBase58 tag in miner")

        parser.require(XmlPullParser.END_TAG, null, tag)

        try {
            return MiningSpecifications.of(
                name ?: throw XmlPullParserException("Missing name tag in miner"),
                description ?: throw XmlPullParserException("Missing description tag in miner"),
                chainId ?: throw XmlPullParserException("Missing chainId tag in miner"),
                hashingForDeadlines
                    ?: throw XmlPullParserException("Missing hashingForDeadlines tag in miner"),
                signatureForBlocks,
                signatureForDeadlines
                    ?: throw XmlPullParserException("Missing signatureForDeadlines tag in miner"),
                signatureForBlocks.publicKeyFromEncoding(
                    Base58.fromBase58String(
                        publicKeyForSigningBlocksBase58,
                        ::XmlPullParserException
                    )
                )
            )
        }
        catch (e: InvalidKeySpecException) {
            throw XmlPullParserException(e.message)
        }
    }

    private fun readURI(parser: XmlPullParser): URI {
        val entropy = readText(parser)

        try {
            return URI(entropy)
        }
        catch (e: URISyntaxException) {
            throw XmlPullParserException(e.message)
        }
    }

    private fun readSize(parser: XmlPullParser): Long {
        val size = readText(parser)

        try {
            val result = size.toLong()
            if (result > 0)
                return result
            else
                throw XmlPullParserException("The plot size must be strictly positive")
        }
        catch (_: NumberFormatException) {
            throw XmlPullParserException("The plot size must be a strictly positive number")
        }
    }

    private fun readUUID(parser: XmlPullParser): UUID {
        val uuid = readText(parser)

        try {
            return UUID.fromString(uuid)
        }
        catch (e: IllegalArgumentException) {
            throw XmlPullParserException(e.message)
        }
    }

    private fun readBoolean(parser: XmlPullParser): Boolean {
        val value = readText(parser)

        return when (value) {
            "true" -> true
            "false" -> false
            else -> throw XmlPullParserException("Expected Boolean")
        }
    }

    private fun readHashingAlgorithm(parser: XmlPullParser): HashingAlgorithm {
        val signature = readText(parser)

        try {
            return HashingAlgorithms.of(signature)
        }
        catch (e: NoSuchAlgorithmException) {
            throw XmlPullParserException(e.message)
        }
    }

    private fun readSignatureAlgorithm(parser: XmlPullParser): SignatureAlgorithm {
        val signature = readText(parser)

        try {
            return SignatureAlgorithms.of(signature)
        }
        catch (e: NoSuchAlgorithmException) {
            throw XmlPullParserException(e.message)
        }
    }

    private fun readEntropy(parser: XmlPullParser): Entropy {
        val entropy = readText(parser)

        try {
            return Entropies.of(Hex.fromHexString(entropy, ::XmlPullParserException))
        }
        catch (e: IllegalArgumentException) {
            throw XmlPullParserException(e.message)
        }
    }

    private fun readText(parser: XmlPullParser): String {
        val tag = parser.name

        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }

        parser.require(XmlPullParser.END_TAG, null, tag)

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

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(out: Parcel, flags: Int) {
        out.writeSerializable(uuid)
        out.writeString(miningSpecification.name)
        out.writeString(miningSpecification.description)
        out.writeString(miningSpecification.chainId)
        out.writeString(miningSpecification.hashingForDeadlines.name)
        out.writeString(miningSpecification.signatureForBlocks.name)
        out.writeString(miningSpecification.signatureForDeadlines.name)
        out.writeString(miningSpecification.publicKeyForSigningBlocksBase58)
        out.writeSerializable(uri)
        out.writeLong(size)
        out.writeInt(entropy.length())
        out.writeByteArray(entropy.entropyAsBytes)
        val publicKeyBytes = miningSpecification.signatureForDeadlines.encodingOf(publicKey)
        out.writeInt(publicKeyBytes.size)
        out.writeByteArray(publicKeyBytes)
        out.writeByte(if (hasPlotReady) 1.toByte() else 0.toByte())
    }

    override fun compareTo(other: Miner): Int {
        val diff = miningSpecification.name.compareTo(other.miningSpecification.name)
        return if (diff != 0)
            diff
        else
            uuid.compareTo(other.uuid)
    }

    override fun equals(other: Any?): Boolean {
        return other is Miner && uuid == other.uuid
    }

    override fun hashCode(): Int {
        return uuid.hashCode()
    }

    override fun toString(): String {
        return "${miningSpecification.name} ($uri)"
    }
}