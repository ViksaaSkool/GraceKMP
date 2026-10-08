rootProject.name = "Grace"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                // Google Mobile Ads Next-Gen pulls Cronet from Google's Maven.
                includeGroupAndSubgroups("org.chromium")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                // Google Mobile Ads Next-Gen pulls Cronet from Google's Maven.
                includeGroupAndSubgroups("org.chromium")
            }
        }
        mavenCentral()
    }
}

include(":composeApp")
