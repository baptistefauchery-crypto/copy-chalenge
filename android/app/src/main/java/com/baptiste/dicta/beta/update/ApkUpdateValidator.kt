package com.baptiste.dicta.beta.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

data class ValidatedUpdateApk(
    val file: File,
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
)

class UpdateValidationException(message: String) : SecurityException(message)

class ApkUpdateValidator(private val context: Context) {
    @Suppress("DEPRECATION")
    fun validate(file: File, expectedVersionName: String): ValidatedUpdateApk {
        if (!file.isFile) throw UpdateValidationException("Update APK is missing")
        val packageManager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        @Suppress("DEPRECATION")
        val archive = packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw UpdateValidationException("Update APK metadata is invalid")
        @Suppress("DEPRECATION")
        val installed = packageManager.getPackageInfo(context.packageName, flags)
        if (archive.packageName != installed.packageName) throw UpdateValidationException("Update package name does not match")
        if (versionCode(archive) <= versionCode(installed)) throw UpdateValidationException("Update version is not newer")
        if (archive.versionName.orEmpty().removePrefix("v") != expectedVersionName.removePrefix("v")) {
            throw UpdateValidationException("Update version does not match the selected release")
        }
        if (signerDigests(archive) != signerDigests(installed) || signerDigests(archive).isEmpty()) {
            throw UpdateValidationException("Update signing certificate does not match")
        }
        return ValidatedUpdateApk(
            file = file,
            packageName = archive.packageName,
            versionCode = versionCode(archive),
            versionName = archive.versionName.orEmpty(),
        )
    }

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners.orEmpty()
        } else {
            info.signatures.orEmpty()
        }
        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }
}
