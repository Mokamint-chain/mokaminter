package io.mokamint.android.mokaminter.model

import org.xmlpull.v1.XmlPullParser
import java.net.URI
import java.security.KeyPair

/**
 * The specification of a miner. Miners are identified by name.
 */
class Miner: Comparable<Miner> {

    private val name: String

    private val uri: URI;

    private val keyPair: KeyPair;

    /**
     * Creates the specification of a miner.
     *
     * @param name the name used to refer to the miner
     * @param uri the URI where the mining endpoint can be contacted
     * @param keyPair the key pair identifying the miner
     */
    public constructor(name: String, uri: URI, keyPair: KeyPair) {
        this.name = name;
        this.uri = uri;
        this.keyPair = keyPair;
    }

    constructor(parser: XmlPullParser) {
        parser.require(XmlPullParser.START_TAG, null, "miner")
    }

    override fun compareTo(other: Miner): Int {
        return name.compareTo(other.name);
    }

    override fun equals(other: Any?): Boolean {
        return other is Miner && name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    override fun toString(): String {
        return "$name ($uri)"
    }
}