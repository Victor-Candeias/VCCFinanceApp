pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "VCCFinanceApp"

include(
    ":app",
    ":core:model",
    ":core:database",
    ":core:data",
    ":core:security",
    ":core:designsystem",
    ":feature:finance",
)
