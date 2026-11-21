package cn.local.yolovision

import android.content.Context

/** One versioned local policy is shared by the consent gate and the review screen. */
object PrivacyPolicy {
    const val VERSION = "2026-11-21"
    const val CONSENT_KEY = "privacy_consent_version"
    const val PREFERENCES_NAME = "privacy"
    const val ASSET_NAME = "privacy-policy.html"
    const val COMPANY = "壹合原码（北京）科技有限公司"
    const val CONTACT = "zhangxu@yihecode.com"
    const val WEBSITE = "https://www.yihecode.com/"

    fun readBody(context: Context): String = context.assets.open(ASSET_NAME)
        .bufferedReader(Charsets.UTF_8).use { it.readText() }
        .substringAfter("<body>").substringBefore("</body>")
}
