package com.baptiste.dicta.beta.update

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.FileInputStream

class ApkPackageInstaller(private val context: Context) {
    fun commit(apk: ValidatedUpdateApk, statusReceiver: IntentSender): Int {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(apk.packageName)
            setSize(apk.file.length())
            if (Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
            }
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.file.length()).use { output ->
                    FileInputStream(apk.file).use { input -> input.copyTo(output) }
                    session.fsync(output)
                }
                session.commit(statusReceiver)
            }
        } catch (error: Throwable) {
            runCatching { installer.abandonSession(sessionId) }
            throw error
        }
        return sessionId
    }

    companion object {
        fun status(intent: Intent): Int = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        fun statusMessage(intent: Intent): String? = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        @Suppress("DEPRECATION")
        fun pendingUserAction(intent: Intent): Intent? {
            if (status(intent) != PackageInstaller.STATUS_PENDING_USER_ACTION) return null
            return if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
        }
    }
}
