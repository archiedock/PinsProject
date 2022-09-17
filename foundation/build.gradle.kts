import java.io.IOException
import java.nio.file.*
import java.util.*
import java.io.*
import java.nio.file.attribute.BasicFileAttributes
import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    compileSdk = 32

    defaultConfig {
        minSdk = 21
        targetSdk = 32

        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    sourceSets {
        val dirs = listOf("main", "p_entity", "p_interfaces", "p_usecase")

        sourceSets[SourceSet.MAIN_SOURCE_SET_NAME].manifest {
            srcFile("main/src/main/AndroidManifest.xml")
        }

        dirs.forEach {
            sourceSets[SourceSet.MAIN_SOURCE_SET_NAME].java {
                srcDir("$it/src/main/java")
            }
            sourceSets[SourceSet.TEST_SOURCE_SET_NAME].java {
                srcDir("$it/src/test/java")
            }
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

task("code_check") {
    doLast {
        project.logger.error("start code check -------------------------------- ")
        val allSubs = projectDir.listFiles().filter { it.name.startsWith("p_") }
        val classMap: MutableMap<String, List<String>> = mutableMapOf()
        allSubs.forEach { subPath ->
            project.logger.error(subPath.name)
            val javaFile = File(subPath.absolutePath, "src/main/java")
            Files.walkFileTree(javaFile.toPath(), object : java.nio.file.FileVisitor<java.nio.file.Path> {
                private val classFiles: MutableList<String> = mutableListOf()
                override fun preVisitDirectory(
                    p0: java.nio.file.Path?,
                    p1: BasicFileAttributes?
                ): FileVisitResult {
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(
                    path: java.nio.file.Path?,
                    attr: BasicFileAttributes?
                ): FileVisitResult {
                    project.logger.error("walk current path:" + path.toString())
                    classFiles.add(path?.toFile()?.name?.replace(".kt", "") ?: "")
                    path?.let {
                        path.toFile().readLines().forEach { line ->
                            //project.logger.error("current line:" + line.toString())
                            if (line.contains("com.example.pins.interfaces")) {
                                //throw GradleException("code check error: check file dependency: $path")
                            }
                        }
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(
                    p0: java.nio.file.Path?,
                    p1: IOException?
                ): FileVisitResult {
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(
                    p0: java.nio.file.Path?,
                    p1: IOException?
                ): FileVisitResult {
                    classMap[subPath.name] = classFiles
                    project.logger.error("post walk path: $classMap")
                    return FileVisitResult.CONTINUE
                }

            })
        }

        allSubs.forEach { subPath ->
            val javaFile = File(subPath.absolutePath, "src/main/java")
            val propertyFile = File(subPath.absolutePath, "project.properties")
            var includeList: List<String> = emptyList()
            if (propertyFile.exists()) {
                val properties = Properties()
                if (propertyFile.isFile) {
                    InputStreamReader(
                        FileInputStream(propertyFile),
                        com.google.common.base.Charsets.UTF_8
                    ).use { reader ->
                        properties.load(reader)
                    }
                }
                val includeProperty = properties.getProperty("include")
                includeList = includeProperty.split(",").toList()
                project.logger.error("project.properties include $includeProperty")
            } else {
                project.logger.error("project.properties file not found!")
            }
            val otherClassMap = classMap.filter { it.key != subPath.name }.filter { !includeList.contains(it.key) }
            project.logger.error("code check walk ${subPath.name}: $otherClassMap, includeList: $includeList")
            Files.walkFileTree(javaFile.toPath(), object : java.nio.file.FileVisitor<java.nio.file.Path> {
                override fun preVisitDirectory(
                    p0: java.nio.file.Path?,
                    p1: BasicFileAttributes?
                ): FileVisitResult {
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(
                    path: java.nio.file.Path?,
                    attr: BasicFileAttributes?
                ): FileVisitResult {

                    path?.let {
                        var error = ""
                        path.toFile().readLines().forEachIndexed { index, line ->
                            otherClassMap.forEach { it.value.forEach { className ->
                                if (line.contains(className)) {
                                    error += "dependency not inject, class name: $className, file: $path, line number: ${index + 1} \n"
                                }
                            } }
                        }
                        if (error.isNotEmpty()) {
                            throw GradleException(error)
                        }
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(
                    p0: java.nio.file.Path?,
                    p1: IOException?
                ): FileVisitResult {
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(
                    p0: java.nio.file.Path?,
                    p1: IOException?
                ): FileVisitResult {
                    return FileVisitResult.CONTINUE
                }

            })
        }
    }
}

afterEvaluate {
    val codecheck = tasks.getByName("code_check")
    val buildTask = tasks.getByName("build")
    tasks.filter { it.name.startsWith("assemble") }.forEach {
        it.dependsOn(codecheck)
    }
    val app = rootProject.subprojects.first { it.name == "app" }
    app.tasks.filter { it.name.startsWith("assemble") }.forEach {
        it.dependsOn(codecheck)
    }
    app.tasks.getByName("build").dependsOn(codecheck)
    buildTask.dependsOn(codecheck)
}