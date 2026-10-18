package com.codetrio.spatialflow.data.sourcepool

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.codetrio.spatialflow.BuildConfig
import java.security.MessageDigest
import java.util.Locale

object SourcePoolSecurityHandshake {

    private var cachedFingerprint: String? = null

    fun getCertificateSha256(context: Context): String {
        cachedFingerprint?.let { return it }

        return try {
            val pm = context.packageManager
            val packageName = context.packageName
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = packageInfo.signingInfo
                if (signingInfo?.hasMultipleSigners() == true) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo?.signingCertificateHistory
                }
            } else {
                @Suppress("DEPRECATION")
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            val certBytes = signatures?.firstOrNull()?.toByteArray() ?: return "unknown"
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(certBytes)
            val hex = digest.joinToString("") { "%02x".format(it) }.lowercase(Locale.US)
            cachedFingerprint = hex
            hex
        } catch (_: Exception) {
            "unknown"
        }
    }

    fun buildHandshakeHeaders(context: Context): Map<String, String> {
        val gatewayUrl = BuildConfig.COMMUNITY_GATEWAY_URL
        val salt = BuildConfig.APP_HANDSHAKE_SALT
        if (gatewayUrl.isBlank() || salt.isBlank()) {
            return emptyMap()
        }

        val timestampSeconds = System.currentTimeMillis() / 1000
        val packageName = context.packageName
        val certSha256 = getCertificateSha256(context)

        val rawMessage = "$timestampSeconds:$packageName:$certSha256:$salt"
        val signature = sha256Hex(rawMessage)

        return mapOf(
            "X-SF-TS" to timestampSeconds.toString(),
            "X-SF-Pkg" to packageName,
            "X-SF-Cert" to certSha256,
            "X-SF-Sign" to signature
        )
    }

    private fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.lowercase(Locale.US)
    }
}
