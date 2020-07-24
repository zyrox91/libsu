import com.android.build.gradle.BaseExtension
import java.net.URL

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("maven-publish")
    id("java")
}

buildscript {
    repositories {
        google()
        jcenter()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:4.0.1")
        classpath("com.github.dcendents:android-maven-gradle-plugin:2.1")

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}

val dlPackageList = tasks.register("dlPackageList") {
    outputs.upToDateWhen { false }
    doLast {
        /* Merge framework packages with AndroidX packages into the same list
        * so links to Android classes can work properly in Javadoc */
        rootProject.buildDir.mkdirs()
        File(rootProject.buildDir, "package-list").outputStream().use { out ->
            URL("https://developer.android.com/reference/package-list")
                .openStream().use { src -> src.copyTo(out) }
            URL("https://developer.android.com/reference/androidx/package-list")
                .openStream().use { src -> src.copyTo(out) }
        }
    }
}

val javadoc = tasks.replace("javadoc", Javadoc::class).apply {
    dependsOn(dlPackageList)
    isFailOnError = false
    title = "libsu API"
    exclude("**/internal/**")
    (options as StandardJavadocDocletOptions).apply {
        links = listOf("https://docs.oracle.com/javase/8/docs/api/")
        linksOffline = listOf(JavadocOfflineLink(
            "https://developer.android.com/reference/", rootProject.buildDir.path))
        isNoDeprecated = true
    }
    setDestinationDir(File(rootProject.buildDir, "javadoc"))
}

val javadocJar = tasks.register("javadocJar", Jar::class) {
    dependsOn(javadoc)
    archiveClassifier.set("javadoc")
    from(javadoc.destinationDir)
}

/* Force JitPack to build javadocJar and publish */
tasks.register("install") {
    dependsOn(tasks["publishToMavenLocal"])
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifact(javadocJar.get())
            groupId = "com.github.topjohnwu"
            artifactId = "docs"
        }
    }
}

val Project.android get() = extensions.getByName<BaseExtension>("android")

subprojects {
    buildscript {
        repositories {
            google()
            jcenter()
        }
    }

    repositories {
        google()
        jcenter()
    }

    configurations.create("javadocDeps")

    afterEvaluate {
        android.apply {
            compileSdkVersion(30)
            buildToolsVersion = "30.0.1"

            defaultConfig {
                if (minSdkVersion == null)
                    minSdkVersion(9)
                targetSdkVersion(30)
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_1_8
                targetCompatibility = JavaVersion.VERSION_1_8
            }
        }

        if (plugins.hasPlugin("com.android.library")) {
            android.apply {
                buildFeatures.apply {
                    buildConfig = false
                }
            }

            (rootProject.tasks["javadoc"] as Javadoc).apply {
                source = source.plus(android.sourceSets.getByName("main").java.sourceFiles)
                classpath = classpath.plus(project.files(android.bootClasspath))
                classpath = classpath.plus(configurations.getByName("javadocDeps"))
            }

            val sourcesJar = tasks.register("sourcesJar", Jar::class) {
                archiveClassifier.set("sources")
                from(android.sourceSets.getByName("main").java.sourceFiles)
            }

            artifacts {
                add("archives", sourcesJar)
            }
        }
    }
}