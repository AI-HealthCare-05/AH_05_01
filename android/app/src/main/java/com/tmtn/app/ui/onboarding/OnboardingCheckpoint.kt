package com.tmtn.app.ui.onboarding

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

/** UI continuation only. No passwords, verification codes, health values or DB writes. */
object OnboardingCheckpoint {
    private var preferences: SharedPreferences? = null
    fun init(context: Context) {
        preferences = context.applicationContext.getSharedPreferences("onboarding_ui_progress", Context.MODE_PRIVATE)
    }
    fun pendingStep(): OnboardingStep? = preferences?.getString("step", null)?.let {
        runCatching { OnboardingStep.valueOf(it) }.getOrNull()
    }
    fun belongsTo(email: String): Boolean = preferences?.getString("owner", null) == fingerprint(email)
    fun save(step: OnboardingStep, email: String? = null) {
        preferences?.edit()?.apply {
            putString("step", step.name)
            email?.let { putString("owner", fingerprint(it)) }
        }?.commit()
    }
    fun clear() { preferences?.edit()?.clear()?.commit() }
    private fun fingerprint(email: String): String = MessageDigest.getInstance("SHA-256")
        .digest(email.trim().lowercase().toByteArray()).joinToString("") { "%02x".format(it) }
}
