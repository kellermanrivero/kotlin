import org.jetbrains.kotlin.gradle.dsl.KotlinCompile
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrLink
import java.io.OutputStream

plugins {
    `maven-publish`
    kotlin("multiplatform")
}

description = "Kotlin Standard Library for experimental WebAssembly platform"

val unimplementedNativeBuiltIns =
    (file("$rootDir/core/builtins/native/kotlin/").list().toSortedSet() - file("$rootDir/libraries/stdlib/wasm/builtins/kotlin/").list())
        .map { "core/builtins/native/kotlin/$it" }



val builtInsSources by task<Sync> {
    val sources = listOf(
        "core/builtins/src/kotlin/"
    ) + unimplementedNativeBuiltIns

    val excluded = listOf(
        // JS-specific optimized version of emptyArray() already defined
        "ArrayIntrinsics.kt",
        // Included with K/N collections
        "Collections.kt", "Iterator.kt", "Iterators.kt"
    )

    sources.forEach { path ->
        from("$rootDir/$path") {
            into(path.dropLastWhile { it != '/' })
            excluded.forEach {
                exclude(it)
            }
        }
    }

    into("$buildDir/builtInsSources")
}

val commonMainSources by task<Sync> {
    val sources = listOf(
        "libraries/stdlib/common/src/",
        "libraries/stdlib/src/kotlin/",
        "libraries/stdlib/unsigned/"
    )

    sources.forEach { path ->
        from("$rootDir/$path") {
            into(path.dropLastWhile { it != '/' })
        }
    }

    into("$buildDir/commonMainSources")

    dependsOn(":prepare:build.version:writeStdlibVersion")
}

val commonTestSources by task<Sync> {
    val sources = listOf(
        "libraries/stdlib/test/",
        "libraries/stdlib/common/test/"
    )

    sources.forEach { path ->
        from("$rootDir/$path") {
            into(path.dropLastWhile { it != '/' })
            // exclusions due to KT-51647
            exclude("generated/minmax")
            exclude("collections/MapTest.kt")
        }
    }

    into("$buildDir/commonTestSources")
}

kotlin {
    wasm {
        nodejs {
            testTask {
                useMocha {
                    timeout = "10s"
                }
            }
        }
    }

    sourceSets {
        val wasmMain by getting {
            kotlin.srcDirs("builtins", "internal", "runtime", "src", "stubs")
            kotlin.srcDirs("$rootDir/libraries/stdlib/native-wasm/src")
            kotlin.srcDirs(files(builtInsSources.map { it.destinationDir }))
        }

        val commonMain by getting {
            kotlin.srcDirs(files(commonMainSources.map { it.destinationDir }))
        }

        val commonTest by getting {
            dependencies {
                api(project(":kotlin-test:kotlin-test-wasm"))
            }
            kotlin.srcDir(files(commonTestSources.map { it.destinationDir }))
        }

        val wasmTest by getting {
            dependencies {
                api(project(":kotlin-test:kotlin-test-wasm"))
            }
            kotlin.srcDir("$rootDir/libraries/stdlib/wasm/test/")
            kotlin.srcDir("$rootDir/libraries/stdlib/native-wasm/test/")
        }
    }
}

tasks.withType<KotlinCompile<*>>().configureEach {
    // TODO: fix all warnings, enable explicit API mode and -Werror
    kotlinOptions.suppressWarnings = true

    kotlinOptions.freeCompilerArgs += listOf(
        "-Xallow-kotlin-package",
        "-opt-in=kotlin.ExperimentalMultiplatform",
        "-opt-in=kotlin.contracts.ExperimentalContracts",
        "-opt-in=kotlin.RequiresOptIn",
        "-opt-in=kotlin.ExperimentalUnsignedTypes",
        "-opt-in=kotlin.ExperimentalStdlibApi"
    )
}

tasks.named("compileKotlinWasm") {
    (this as KotlinCompile<*>).kotlinOptions.freeCompilerArgs += "-Xir-module-name=kotlin"
    dependsOn(commonMainSources)
    dependsOn(builtInsSources)
}

val compileTestKotlinWasm by tasks.existing(KotlinCompile::class) {
    val sources: FileCollection = kotlin.sourceSets["commonTest"].kotlin
    doFirst {
        // Note: common test sources are copied to the actual source directory by commonMainSources task,
        // so can't do this at configuration time:
        kotlinOptions.freeCompilerArgs += listOf("-Xcommon-sources=${sources.joinToString(",")}")
    }
}

val compileTestDevelopmentExecutableKotlinWasm = tasks.named<KotlinJsIrLink>("compileTestDevelopmentExecutableKotlinWasm") {
    (this as KotlinCompile<*>).kotlinOptions.freeCompilerArgs += listOf("-Xwasm-enable-array-range-checks", "-Xwasm-launcher=d8")
}

val runWasmStdLibTestsWithD8 by tasks.registering(Exec::class) {
    dependsOn(":js:js.tests:unzipV8")
    dependsOn(compileTestDevelopmentExecutableKotlinWasm)

    val unzipV8Task = tasks.getByPath(":js:js.tests:unzipV8")
    val d8Path = File(unzipV8Task.outputs.files.single(), "d8")
    executable = d8Path.toString()

    val compiledFile = compileTestDevelopmentExecutableKotlinWasm
        .get()
        .kotlinOptions
        .outputFile
        ?.let { File(it) }
    check(compiledFile != null)

    if (System.getenv("TEAMCITY_VERSION") == null) {
        standardOutput = object : OutputStream() {
            override fun write(b: Int) = Unit
        }
        errorOutput = standardOutput
    }

    workingDir = compiledFile.parentFile
    args = listOf("--experimental-wasm-gc", "--experimental-wasm-eh", "--module", compiledFile.name)
}

val runtimeElements by configurations.creating {}
val apiElements by configurations.creating {}

publish {
    pom.packaging = "klib"
    artifact(tasks.named("wasmJar")) {
        extension = "klib"
    }
}

afterEvaluate {
    // cleanup default publications
    // TODO: remove after mpp plugin allows avoiding their creation at all, KT-29273
    publishing {
        publications.removeAll { it.name != "Main" }
    }

    tasks.withType<AbstractPublishToMaven> {
        if (publication.name != "Main") this.enabled = false
    }

    tasks.named("publish") {
        doFirst {
            publishing.publications {
                if (singleOrNull()?.name != "Main") {
                    throw GradleException("kotlin-stdlib-wasm should have only one publication, found $size: ${joinToString { it.name }}")
                }
            }
        }
    }
}

if (!isConfigurationCacheDisabled) {
    tasks.matching {
        it is org.jetbrains.kotlin.gradle.tooling.BuildKotlinToolingMetadataTask
                || it is org.jetbrains.kotlin.gradle.plugin.mpp.GenerateProjectStructureMetadata
                || it is org.jetbrains.kotlin.gradle.plugin.mpp.TransformKotlinGranularMetadata
    }.configureEach {
        onlyIf {
            logger.warn("Task '$name' is disabled due to incompatibility with configuration cache. KT-49933")
            false
        }
    }
}