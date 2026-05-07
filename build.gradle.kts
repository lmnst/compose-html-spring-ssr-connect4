plugins {
    kotlin("multiplatform") version "2.1.0"
}

group = "connect4"
version = "1.0.0"

kotlin {
    jvm {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Pure Kotlin only. No platform deps.
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
