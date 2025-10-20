package io.mokamint.android.mokaminter.model

import android.os.Parcel
import android.os.Parcelable
import io.hotmoka.crypto.Base58
import io.hotmoka.crypto.HashingAlgorithms
import io.hotmoka.crypto.SignatureAlgorithms
import io.mokamint.miner.MiningSpecifications
import io.mokamint.miner.api.MiningSpecification
import io.mokamint.nonce.Prologs
import io.mokamint.nonce.api.Prolog
import java.net.URI
import java.security.PublicKey
import java.util.UUID

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
     * The public key of the miner, derived from entropy, password and signature algorithm for deadlines.
     */
    val publicKey: PublicKey

    /**
     * The {@link #publicKey}, in Base58 format.
     */
    val publicKeyBase58: String

    companion object {
        const val UUID_TAG = "uuid"
        const val MINING_SPECIFICATION_TAG = "miningSpecification"
        const val URI_TAG = "uri"
        const val SIZE_TAG = "size"
        const val NAME_TAG = "name"
        const val DESCRIPTION_TAG = "description"
        const val CHAIN_ID_TAG = "chainId"
        const val HASHING_FOR_DEADLINES_TAG = "hashingForDeadlines"
        const val SIGNATURE_FOR_DEADLINES_TAG = "signatureForDeadlines"
        const val SIGNATURE_FOR_BLOCKS_TAG = "signatureForBlocks"
        const val PUBLIC_KEY_FOR_SIGNING_BLOCKS_BASE58_TAG = "publicKeyForSigningBlocksBase58"
        const val PUBLIC_KEY_FOR_SIGNING_DEADLINES_BASE58_TAG = "publicKeyForSigningDeadlinesBase58"

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
     * @param publicKeyBase58 the Base58-encoded public key of the miner; this ust be a valid key
     *                        for the signature algorithm for deadlines of the mining specification
     */
    constructor(uuid: UUID, miningSpecification: MiningSpecification, uri: URI, size: Long, publicKeyBase58: String) {
        this.uuid = uuid
        this.miningSpecification = miningSpecification
        this.uri = uri
        this.size = size
        this.publicKey = miningSpecification.signatureForDeadlines.publicKeyFromEncoding(Base58.fromBase58String(publicKeyBase58))
        this.publicKeyBase58 = publicKeyBase58

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

        var publicKeyForSigningDeadlinesBytes = ByteArray(parcel.readInt())
        parcel.readByteArray(publicKeyForSigningDeadlinesBytes)
        this.publicKey = miningSpecification.signatureForDeadlines.publicKeyFromEncoding(publicKeyForSigningDeadlinesBytes)
        this.publicKeyBase58 = Base58.toBase58String(publicKeyForSigningDeadlinesBytes)
    }

    private constructor(uuid: UUID, miningSpecification: MiningSpecification, uri: URI, size: Long, publicKey: PublicKey, publicKeyBase58: String) {
        this.uuid = uuid
        this.miningSpecification = miningSpecification
        this.uri = uri
        this.size = size
        this.publicKey = publicKey
        this.publicKeyBase58 = publicKeyBase58
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
        val publicKeyBytes = miningSpecification.signatureForDeadlines.encodingOf(publicKey)
        out.writeInt(publicKeyBytes.size)
        out.writeByteArray(publicKeyBytes)
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
        return "${miningSpecification.name} ($uuid)"
    }
}