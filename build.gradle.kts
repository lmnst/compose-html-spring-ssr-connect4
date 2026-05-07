import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    kotlin("multiplatform") version "2.1.0"
    kotlin("plugin.spring") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
}

group = "connect4"
version = "1.0.0"

val springBootVersion = "3.3.5"

// The Compose compiler must only run on the JS target. The pure-Kotlin game
// engine, the SSR engine, and the view layer in commonMain have no Compose
// runtime on their classpath by design, so they stay portable to the JVM.
composeCompiler {
    targetKotlinPlatforms.set(listOf(KotlinPlatformType.js))
}

kotlin {
    jvm {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                }
            }
        }
        // Spring Boot starter-test uses JUnit Jupiter. Routing the kotlin-test
        // assertions through JUnit 5 keeps both pure-engine tests and Spring
        // integration tests under a single test task.
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    js(IR) {
        moduleName = "connect4"
        browser {
            commonWebpackConfig {
                outputFileName = "connect4.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            // Pure Kotlin only. No Compose, no browser, no Spring on this classpath.
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmMain.dependencies {
            implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
            implementation("org.springframework.boot:spring-boot-starter-web")
            implementation("org.jetbrains.kotlin:kotlin-reflect:2.1.0")
        }
        jvmTest.dependencies {
            implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
            implementation(kotlin("test-junit5"))
            implementation("org.springframework.boot:spring-boot-starter-test")
            runtimeOnly("org.junit.platform:junit-platform-launcher")
        }
        jsMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.html.core)
        }
    }
}

// Package the production JS bundle into the JVM resources under /static so
// the Spring controller can serve it from the same JVM jar.
val jsDistDir = layout.buildDirectory.dir("dist/js/productionExecutable")
val staticStagingDir = layout.buildDirectory.dir("generated/spring-static")

val syncJsBundleToStatic by tasks.registering(Sync::class) {
    dependsOn("jsBrowserDistribution")
    from(jsDistDir)
    into(staticStagingDir.map { it.dir("static") })
}

tasks.named<ProcessResources>("jvmProcessResources") {
    dependsOn(syncJsBundleToStatic)
    from(staticStagingDir)
}

// Spring Boot run target. We do not apply the Spring Boot Gradle plugin
// because it does not compose cleanly with the Kotlin Multiplatform plugin.
// JavaExec is sufficient for development: it picks up the JVM classpath
// produced by the multiplatform JVM target.
tasks.register<JavaExec>("bootRun") {
    group = "application"
    description = "Run the Spring Boot SSR application on http://localhost:8080."
    dependsOn("jvmMainClasses", syncJsBundleToStatic, "jvmProcessResources")
    mainClass.set("connect4.server.ApplicationKt")
    classpath = files(
        tasks.named("jvmJar"),
        configurations.named("jvmRuntimeClasspath"),
    )
    standardInput = System.`in`
}
