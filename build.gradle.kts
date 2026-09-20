plugins {
    // Declared here (apply false) so each plugin is resolved once and reused by
    // subprojects instead of being loaded into every subproject classloader.
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}
