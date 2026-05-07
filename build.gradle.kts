plugins {
    kotlin("multiplatform") version "2.1.0"
    kotlin("plugin.spring") version "2.1.0"
}

group = "connect4"
version = "1.0.0"

val springBootVersion = "3.3.5"

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

    sourceSets {
        commonMain.dependencies {
            // Pure Kotlin only. No Spring on this classpath.
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
    }
}

// Spring Boot run target. We do not apply the Spring Boot Gradle plugin
// because it does not compose cleanly with the Kotlin Multiplatform plugin.
// JavaExec is sufficient for development: it picks up the JVM classpath
// produced by the multiplatform JVM target.
tasks.register<JavaExec>("bootRun") {
    group = "application"
    description = "Run the Spring Boot SSR application on http://localhost:8080."
    dependsOn("jvmMainClasses", "jvmProcessResources")
    mainClass.set("connect4.server.ApplicationKt")
    classpath = files(
        tasks.named("jvmJar"),
        configurations.named("jvmRuntimeClasspath"),
    )
    standardInput = System.`in`
}
