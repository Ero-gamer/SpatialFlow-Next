package com.codetrio.spatialflow.data.lyrics.security

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.util.UUID

/**
 * Manages dynamic cryptographic EC P-256 per-device identities,
 * conforming to WebCrypto specifications.
 */
object DeviceKeyIdentityManager {
    private const val TAG = "DeviceKeyIdentity"
    private const val PREFS_NAME = "BetterLyricsIdentity"
    private const val KEY_IDENTITY_JSON = "key_identity_v1"

    data class KeyIdentity(
        val keyId: String,
        val publicKeyJwk: Map<String, Any>,
        val privateKeyJwk: Map<String, Any>,
        val createdAt: Long,
        val displayName: String? = null
    )

    data class SignedPayloadResult(
        val payload: Map<String, Any>,
        val signature: String,
        val publicKey: Map<String, Any>
    )

    @Volatile
    private var cachedIdentity: KeyIdentity? = null

    @Volatile
    private var cachedPrivateKey: PrivateKey? = null

    private val gson = Gson()

    /**
     * Retrieves the persistent device identity, or dynamically generates a fresh one on first run.
     */
    @Synchronized
    fun getOrCreateIdentity(context: Context): KeyIdentity {
        cachedIdentity?.let { return it }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedJson = prefs.getString(KEY_IDENTITY_JSON, null)
        if (!storedJson.isNullOrBlank()) {
            try {
                val identity = parseIdentityJson(storedJson)
                if (identity != null) {
                    cachedIdentity = identity
                    return identity
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse stored identity, generating new one", e)
            }
        }

        val newIdentity = generateNewIdentity()
        saveIdentity(context, newIdentity)
        cachedIdentity = newIdentity
        return newIdentity
    }

    /**
     * Returns the 64-character hex keyId for headers (e.g. "x-key-id: ...").
     */
    fun getKeyId(context: Context): String {
        return getOrCreateIdentity(context).keyId
    }

    /**
     * Generates a deterministic pet name from keyId.
     */
    fun getPetName(keyId: String): String {
        if (keyId.length < 6) return "SonicMelodyFlow"
        return try {
            val adjIndex = keyId.substring(0, 2).toInt(16) % IDENTITY_ADJECTIVES.size
            val nounIndex = keyId.substring(2, 4).toInt(16) % IDENTITY_NOUNS.size
            val actionIndex = keyId.substring(4, 6).toInt(16) % IDENTITY_ACTIONS.size
            "${IDENTITY_ADJECTIVES[adjIndex]}${IDENTITY_NOUNS[nounIndex]}${IDENTITY_ACTIONS[actionIndex]}"
        } catch (_: Exception) {
            "SonicMelodyFlow"
        }
    }

    /**
     * Exports identity in standard JSON format.
     */
    fun exportIdentityJson(context: Context): String {
        val id = getOrCreateIdentity(context)
        val exportMap = linkedMapOf<String, Any>(
            "version" to 1,
            "keyId" to id.keyId,
            "publicKey" to id.publicKeyJwk,
            "privateKey" to id.privateKeyJwk,
            "exportedAt" to System.currentTimeMillis()
        )
        return gson.toJson(exportMap)
    }

    /**
     * Imports an existing identity JSON, verifying hash before committing.
     */
    @Synchronized
    fun importIdentityJson(context: Context, json: String): Result<KeyIdentity> {
        return try {
            val identity = parseIdentityJson(json) ?: throw IllegalArgumentException("Invalid identity JSON format")
            
            // Validate keyId matches canonical hash of publicKey
            val pubX = identity.publicKeyJwk["x"] as? String ?: throw IllegalArgumentException("Missing public key x")
            val pubY = identity.publicKeyJwk["y"] as? String ?: throw IllegalArgumentException("Missing public key y")
            val calculatedId = computeKeyId(pubX, pubY)
            if (calculatedId != identity.keyId) {
                throw IllegalStateException("Key ID mismatch (expected ${identity.keyId}, calculated $calculatedId)")
            }

            saveIdentity(context, identity)
            cachedIdentity = identity
            cachedPrivateKey = null
            Result.success(identity)
        } catch (e: Exception) {
            Log.e(TAG, "Import identity failed", e)
            Result.failure(e)
        }
    }

    /**
     * Signs payload for Unison actions (voting, reporting).
     */
    @Synchronized
    fun signPayload(
        context: Context,
        data: Map<String, Any?>,
        nonce: String = UUID.randomUUID().toString()
    ): SignedPayloadResult {
        val identity = getOrCreateIdentity(context)
        val timestamp = System.currentTimeMillis()

        val fullPayload = sortedMapOf<String, Any>()
        for ((k, v) in data) {
            if (v != null) fullPayload[k] = v
        }
        fullPayload["timestamp"] = timestamp
        fullPayload["nonce"] = nonce
        fullPayload["keyId"] = identity.keyId

        val canonicalPayloadJson = canonicalJson(fullPayload)
        val payloadBytes = canonicalPayloadJson.toByteArray(StandardCharsets.UTF_8)

        val privateKey = getOrLoadPrivateKey(identity)
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(payloadBytes)
        val derSig = signature.sign()

        val joseSig = derToJose(derSig, 64)
        val sigB64 = Base64.encodeToString(joseSig, Base64.NO_WRAP)

        return SignedPayloadResult(
            payload = fullPayload,
            signature = sigB64,
            publicKey = identity.publicKeyJwk
        )
    }

    private fun generateNewIdentity(): KeyIdentity {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val kp = kpg.generateKeyPair()

        val pub = kp.public as ECPublicKey
        val priv = kp.private as ECPrivateKey

        val xB64 = pub.w.affineX.toUnsigned32Bytes().toBase64Url()
        val yB64 = pub.w.affineY.toUnsigned32Bytes().toBase64Url()
        val dB64 = priv.s.toUnsigned32Bytes().toBase64Url()

        val keyId = computeKeyId(xB64, yB64)

        val pubJwk = linkedMapOf<String, Any>(
            "alg" to "ES256",
            "crv" to "P-256",
            "ext" to true,
            "key_ops" to listOf("verify"),
            "kty" to "EC",
            "x" to xB64,
            "y" to yB64
        )

        val privJwk = linkedMapOf<String, Any>(
            "alg" to "ES256",
            "crv" to "P-256",
            "d" to dB64,
            "ext" to true,
            "key_ops" to listOf("sign"),
            "kty" to "EC",
            "x" to xB64,
            "y" to yB64
        )

        cachedPrivateKey = priv

        return KeyIdentity(
            keyId = keyId,
            publicKeyJwk = pubJwk,
            privateKeyJwk = privJwk,
            createdAt = System.currentTimeMillis()
        )
    }

    private fun computeKeyId(x: String, y: String): String {
        val canonical = """{"crv":"P-256","kty":"EC","x":"$x","y":"$y"}"""
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(canonical.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun canonicalJson(map: Map<String, Any>): String {
        val sortedKeys = map.keys.sorted()
        val elements = sortedKeys.map { k ->
            val v = map[k]
            "\"$k\":${gson.toJson(v)}"
        }
        return "{${elements.joinToString(",")}}"
    }

    private fun getOrLoadPrivateKey(identity: KeyIdentity): PrivateKey {
        cachedPrivateKey?.let { return it }

        val dB64 = identity.privateKeyJwk["d"] as? String ?: throw IllegalStateException("Missing d in privateKey")
        val dBytes = Base64.decode(dB64, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val dInt = BigInteger(1, dBytes)

        val kf = KeyFactory.getInstance("EC")
        val ecParamSpec = getSecp256r1Params()
        val privKeySpec = ECPrivateKeySpec(dInt, ecParamSpec)
        val priv = kf.generatePrivate(privKeySpec)
        cachedPrivateKey = priv
        return priv
    }

    private fun saveIdentity(context: Context, identity: KeyIdentity) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(identity)
        prefs.edit().putString(KEY_IDENTITY_JSON, json).apply()
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseIdentityJson(jsonString: String): KeyIdentity? {
        val root = JsonParser.parseString(jsonString).asJsonObject
        val keyId = root.get("keyId")?.asString ?: return null
        val pubElement = root.get("publicKey")?.asJsonObject ?: return null
        val privElement = root.get("privateKey")?.asJsonObject ?: return null
        val createdAt = root.get("createdAt")?.asLong ?: root.get("exportedAt")?.asLong ?: System.currentTimeMillis()
        val displayName = root.get("displayName")?.asString

        val pubMap = gson.fromJson(pubElement, Map::class.java) as Map<String, Any>
        val privMap = gson.fromJson(privElement, Map::class.java) as Map<String, Any>

        return KeyIdentity(
            keyId = keyId,
            publicKeyJwk = pubMap,
            privateKeyJwk = privMap,
            createdAt = createdAt,
            displayName = displayName
        )
    }

    private fun BigInteger.toUnsigned32Bytes(): ByteArray {
        val raw = this.toByteArray()
        if (raw.size == 32) return raw
        if (raw.size > 32) {
            var start = 0
            while (start < raw.size - 32 && raw[start] == 0.toByte()) {
                start++
            }
            return raw.copyOfRange(start, start + 32)
        }
        val padded = ByteArray(32)
        System.arraycopy(raw, 0, padded, 32 - raw.size, raw.size)
        return padded
    }

    private fun ByteArray.toBase64Url(): String {
        return Base64.encodeToString(this, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun derToJose(derSig: ByteArray, joseLen: Int): ByteArray {
        var offset = 2
        val rLen = derSig[offset + 1].toInt()
        offset += 2
        val r = derSig.copyOfRange(offset, offset + rLen)
        offset += rLen
        val sLen = derSig[offset + 1].toInt()
        offset += 2
        val s = derSig.copyOfRange(offset, offset + sLen)

        val jose = ByteArray(joseLen)
        val halfLen = joseLen / 2

        val rTrimmed = if (r.size > halfLen && r[0] == 0.toByte()) r.copyOfRange(1, r.size) else r
        val sTrimmed = if (s.size > halfLen && s[0] == 0.toByte()) s.copyOfRange(1, s.size) else s

        System.arraycopy(rTrimmed, 0, jose, halfLen - rTrimmed.size, rTrimmed.size)
        System.arraycopy(sTrimmed, 0, jose, joseLen - sTrimmed.size, sTrimmed.size)
        return jose
    }

    private fun getSecp256r1Params(): ECParameterSpec {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        return (kpg.generateKeyPair().public as ECPublicKey).params
    }

    // Identity name dictionaries
    private val IDENTITY_ADJECTIVES = listOf(
        "Melodic", "Harmonic", "Acoustic", "Electric", "Mellow", "Groovy", "Funky", "Vibrant",
        "Golden", "Crystal", "Velvet", "Cosmic", "Stellar", "Radiant", "Mystic", "Serene",
        "Dynamic", "Smooth", "Crisp", "Warm", "Bright", "Deep", "Swift", "Bold",
        "Noble", "Grand", "Royal", "Epic", "Vivid", "Lucid", "Prime", "Pure",
        "Sonic", "Hyper", "Ultra", "Mega", "Super", "Astral", "Lunar", "Solar",
        "Neon", "Retro", "Classic", "Modern", "Fusion", "Primal", "Zen", "Nova",
        "Alpha", "Omega", "Delta", "Sigma", "Quantum", "Atomic", "Cyber", "Digital",
        "Analog", "Stereo", "Studio", "Live", "Remix", "Master", "Platinum", "Diamond"
    )

    private val IDENTITY_NOUNS = listOf(
        "Bass", "Guitar", "Piano", "Drum", "Synth", "Chord", "Beat", "Riff",
        "Note", "Tempo", "Rhythm", "Melody", "Verse", "Chorus", "Bridge", "Hook",
        "Track", "Vinyl", "Record", "Album", "Mix", "Tape", "Loop", "Sample",
        "Treble", "Octave", "Scale", "Arpeggio", "Cadence", "Motif", "Theme", "Score",
        "Cymbal", "Snare", "Kick", "Hihat", "Conga", "Bongo", "Shaker", "Gong",
        "Violin", "Cello", "Flute", "Horn", "Trumpet", "Sax", "Harp", "Bell",
        "Staccato", "Legato", "Crescendo", "Fermata", "Vibrato", "Tremolo", "Glissando", "Sforzando",
        "Forte", "Allegro", "Adagio", "Presto", "Andante", "Largo", "Vivace", "Maestro"
    )

    private val IDENTITY_ACTIONS = listOf(
        "Solo", "Remix", "Groove", "Flow", "Vibe", "Echo", "Pulse", "Drift",
        "Wave", "Loop", "Drop", "Rise", "Fade", "Blend", "Sync", "Glide",
        "Swing", "Bounce", "Slide", "Roll", "Spin", "Twist", "Shake", "Break",
        "Jam", "Play", "Rock", "Pop", "Jazz", "Funk", "Soul", "Blues",
        "Surge", "Rush", "Dash", "Zoom", "Flash", "Spark", "Blast", "Burst",
        "Chill", "Cruise", "Coast", "Sway", "Float", "Hover", "Soar", "Leap",
        "Strike", "Stomp", "Clap", "Snap", "Tap", "Slap", "Pluck", "Strum",
        "Hum", "Sing", "Chant", "Call", "Shout", "Whisper", "Croon", "Belt"
    )
}
