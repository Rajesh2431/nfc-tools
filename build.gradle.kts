// Root build file: declares plugins for all subprojects (applied in each module).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
}
