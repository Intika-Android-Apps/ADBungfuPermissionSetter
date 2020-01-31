package com.pavelsikun.runinbackgroundpermissionsetter

import eu.chainfire.libsuperuser.Shell
import java.util.concurrent.CompletableFuture

/**
 * Created by Pavel Sikun on 16.07.17.
 */

typealias Callback = (isSuccess: Boolean) -> Unit

private val shell by lazy { Shell.Builder()
        .setShell("su")
        .open()
}

fun checkRunInBackgroundPermission(pkg: String, appops: String): CompletableFuture<String> {
    val future = CompletableFuture<String>()
    shell.addCommand("cmd appops get $pkg $appops", 1) { _, _, output: MutableList<String> ->
        val outputString = output.joinToString()
        future.complete(outputString)
    }

    return future
}

fun setRunInBackgroundPermission(pkg: String, appops: String, setEnabled: Boolean, callback: Callback): CompletableFuture<Boolean> {
    val future = CompletableFuture<Boolean>()
    val cmd = if (setEnabled) "allow" else "ignore"

    shell.addCommand("cmd appops set $pkg $appops $cmd", 1) { _, _, output: MutableList<String> ->
        val outputString = output.joinToString()
        val isSuccess = outputString.trim().isEmpty()
        callback(isSuccess)
        future.complete(isSuccess)
    }

    return future
}
