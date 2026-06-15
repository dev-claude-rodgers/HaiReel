plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android.plugin) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics.plugin) apply false
    alias(libs.plugins.detekt)
}

detekt {
    config.setFrom(files("detekt.yml"))
    source.setFrom(files("app/src/main/java"))
    parallel = true
}
