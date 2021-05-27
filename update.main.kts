#!/usr/bin/env kotlin

@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.0")

import kotlinx.coroutines.*
import java.io.File

val targetDirectory: File = File("public")
val tempDir: File = File("temp")

fun String.execute(): String {
    val command = this.split(" ")
    val output = File("output.txt") 
    output.createNewFile()

    ProcessBuilder()
        .command(command)
        .redirectErrorStream(true)
        .redirectOutput(output)
        .start()
        .waitFor()

    val o = output.readText()
    println(o)
    output.deleteOnExit()

    return o
}

suspend fun scheduleRepeatedly(delayTimeMillis: Long = 30 * 60_000, action: suspend CoroutineScope.() -> Unit) =
    coroutineScope {
        while (true) {
            launch { action() }
            delay(delayTimeMillis)
        }
    }

fun projectNotCloned(): Boolean {
    return !File("temp").exists()
}

fun cloneProject() {
    tempDir.mkdir()
    "git clone --recurse-submodules https://github.com/remylavergne/remylavergne.dev.git temp".execute()
}

fun fetchRepo(): Boolean {
    val output: String = "git -C temp fetch".execute()

    val dataAvailable = output.isNotEmpty()
       
    return dataAvailable
}

fun pullLatestVersion() {
    "git -C temp pull".execute()
    "git -C temp submodule update --recursive --remote".execute()
}

fun buildLatestVersion() {
    "hugo -D -s temp".execute()
}

fun deployLatestVersion() {
    "cp -R temp/public/. public/".execute()
}

runBlocking {

    targetDirectory.mkdir()

    scheduleRepeatedly {

        if (projectNotCloned()) {
            cloneProject()
            buildLatestVersion()
            deployLatestVersion()
        }

        val updateAvailable: Boolean = fetchRepo()

        if (updateAvailable) {
            pullLatestVersion()
            buildLatestVersion()
            deployLatestVersion()
        } else {
            println("No recent data found. Sleep...")
        }
    }
}
