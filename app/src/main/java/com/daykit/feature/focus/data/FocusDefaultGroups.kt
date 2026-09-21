package com.daykit.feature.focus.data

/** Built-in group suggestions. Members are only added when installed. */
object FocusDefaultGroups {
    const val SOCIAL_ID = "daykit-default-social"
    const val SOCIAL_NAME = "Social"
    const val SOCIAL_COLOR_INDEX = 7

    // Package ids are used instead of app labels so we never accidentally add
    // an unrelated launcher app merely because it happens to include a word
    // such as "chat" in its name.
    private val SOCIAL_MEDIA_PACKAGES = setOf(
        "com.discord",
        "com.facebook.katana",
        "com.instagram.android",
        "com.linkedin.android",
        "com.pinterest",
        "com.reddit.frontpage",
        "com.snapchat.android",
        "com.ss.android.ugc.trill",
        "com.tumblr",
        "com.twitter.android",
        "com.viber.voip",
        "com.zhiliaoapp.musically",
        "org.telegram.messenger",
        "org.thunderdog.challegram",
    )

    fun socialPackages(installedPackageNames: Collection<String>): List<String> =
        installedPackageNames
            .asSequence()
            .filter { it in SOCIAL_MEDIA_PACKAGES }
            .distinct()
            .sorted()
            .toList()
}
