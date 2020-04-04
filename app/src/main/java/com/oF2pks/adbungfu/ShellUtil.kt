package com.oF2pks.adbungfu

import android.app.ProgressDialog
import android.content.Context
import android.content.pm.PackageManager
import eu.chainfire.libsuperuser.Shell
import java.io.File
import java.io.FileNotFoundException
import java.io.FileWriter
import java.lang.Exception
import java.util.*
import java.util.concurrent.*


/**
 * Created by Pavel Sikun on 16.07.17.
 * 2020 https://stackoverflow.com/questions/38221673/completablefuture-in-the-android-support-library
 */

typealias Callback = (isSuccess: Boolean) -> Unit

private val rootSession by lazy { Shell.Builder()
        .setShell("su")
        .open()
}

fun checkAppOpsPermission(pkg: String, appops: String): CompletableFutureCompat<String> {
    val future = CompletableFutureCompat<String>()

    rootSession.addCommand("appops get $pkg $appops", 1 , Shell.OnCommandResultListener2 { _, _, output: MutableList<String>, _ ->
        val outputString = output.joinToString()
        future.complete(outputString)
    })

    return future
}



fun setAppOpsPermission(pkg: String, appops: String, setEnabled: Boolean, callback: Callback): CompletableFutureCompat<Boolean> {
    val future = CompletableFutureCompat<Boolean>()
    val cmd = if (setEnabled) "allow" else "ignore"
    val suCmd: String
    var isSuccess: Boolean

    suCmd = if (appops == freezer) {
        if (!setEnabled) "pm disable-user $pkg"
        else "pm enable $pkg"
    } else "appops set $pkg $appops $cmd"

    rootSession.addCommand(suCmd, 1, Shell.OnCommandResultListener2 { _, _, output: MutableList<String>, _ ->
        val outputString = output.joinToString()
        isSuccess = if (appops == freezer) outputString.contains("new state")
        else outputString.trim().isEmpty()
        callback(isSuccess)
        future.complete(isSuccess)
    })

    return future

}

fun suBool(cmd: String): CompletableFutureCompat<Boolean> {
    var isSuccess: Boolean
    val future = CompletableFutureCompat<Boolean>()
    rootSession.addCommand(cmd, 1, Shell.OnCommandResultListener2 { _, _, output: MutableList<String>, _ ->
        val outputString = output.joinToString()
        isSuccess = outputString.trim().isEmpty()
        future.complete(isSuccess)
    })
    return future
}

fun suString(cmd: String): CompletableFutureCompat<String> {
    val future = CompletableFutureCompat<String>()
    rootSession.addCommand(cmd, 1, Shell.OnCommandResultListener2 { _, _, output: MutableList<String>, _ ->
        val outputString = output.joinToString()
        future.complete(outputString)
    })
    return future
}

fun suList(cmd: String): ArrayList<String> {
    val stdOUT = ArrayList<String>()
    if (Shell.SU.available()) {
        Shell.Pool.SU.get()
        val stdERR = ArrayList<String>()
        Shell.Pool.SU.run(cmd,stdOUT,stdERR,true)
/*       Shell.Pool.SU.run(cmd, object : Shell.OnSyncCommandLineListener {
            override fun onSTDOUT(line: String) {
                // hey, some output on STDOUT!
            }

            override fun onSTDERR(line: String) {
                // hey, some output on STDERR!
            }
        })*/

    }
    return stdOUT
}

fun suADB(suList: ArrayList<String> , output: File): HashMap<String, List<String>> {
    val tableADB: HashMap<String, List<String>> = HashMap()
    if (Shell.SU.available()) {
        val shell: Shell.Threaded = Shell.Pool.SU.get()
        val writer: FileWriter
        var zz = ""
        try {
            zz = output.readText()
            if (zz.isNotEmpty()) tableADB["_ADBungFu errors"] = zz.split("\n")
            else zz = "cmd   gpu #\ncmd   sensorservice #\n"
        } catch (e: FileNotFoundException) {
            zz = "cmd   gpu #\ncmd   sensorservice #\n"
        }
        writer = FileWriter(output)

        suList.forEach {
            val tmp = ArrayList<String>()
            if (zz.contains(it)
                    /*|| it.contains("gpu")
                    || it.contains("sensorservice")*/)
            else {
                try {
                    shell.run(it, object : Shell.OnSyncCommandLineListener {
                        override fun onSTDOUT(line: String) {
                            if (line.contains("cmd: Failure calling service")) {
                                zz += "$it \n"
                                if (line.endsWith("Broken pipe (32)")) {
                                    zz += "#"
                                    writer.write(zz)
                                    writer.close()
                                }
                            }
                            tmp.add(line+"\n")
                        }

                        override fun onSTDERR(line: String) {
                            // hey, some output on STDERR!
                        }
                    })
                } catch (e: Exception) {
                }
                if (tmp.size > 1) tableADB[it+" "+tmp.size] = tmp //else ADB.put("_"+it,tmp)
                else zz += "$it \n"
            }
        }
        writer.write(zz)
        writer.close()
    }

    return tableADB

}

fun suProfman(ctx: Context, ad: ProgressDialog): Int {
    if (Shell.SU.available()) {
        val shell: Shell.Threaded = Shell.Pool.SU.get()
        val appsInfos = ctx.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        for (i in appsInfos.indices) {
            if (i%20 == 0) {
                ad.setMessage(appsInfos.size.toString() + "/"+i)
                ad.show()
            }
            if (appsInfos[i].packageName != "android" && appsInfos[i].packageName != "com.oF2pks.adbungfu") {
                shell.run("cmd package dump-profiles ${appsInfos[i].packageName}")
            }
        }
        shell.run("cp -RF /data/misc/profman ${ctx.getExternalFilesDir(null)}")
        return appsInfos.size
    }
    return 0
}


/**
 * A backport of Java `CompletableFuture` which works with old Androids.
 */
class CompletableFutureCompat<V> : Future<V> {
    private sealed class Result<out V> {
        abstract val value: V
        class Ok<V>(override val value: V) : Result<V>()
        class Error(val e: Throwable) : Result<Nothing>() {
            override val value: Nothing
                get() = throw e
        }
        object Cancel : Result<Nothing>() {
            override val value: Nothing
                get() = throw CancellationException()
        }
    }

    /**
     * Offers the completion result for [result].
     *
     * If this queue is not empty, the future is completed.
     */
    private val completion = LinkedBlockingQueue<Result<V>>(1)
    /**
     * Holds the result of the computation. Takes the item from [completion] upon running and provides it as a result.
     */
    private val result = FutureTask<V> { completion.peek()!!.value }
    /**
     * If not already completed, causes invocations of [get]
     * and related methods to throw the given exception.
     *
     * @param ex the exception
     * @return `true` if this invocation caused this CompletableFuture
     * to transition to a completed state, else `false`
     */
    fun completeExceptionally(ex: Throwable): Boolean {
        val offered = completion.offer(Result.Error(ex))
        if (offered) {
            result.run()
        }
        return offered
    }

    /**
     * If not already completed, completes this CompletableFuture with
     * a [CancellationException].
     *
     * @param mayInterruptIfRunning this value has no effect in this
     * implementation because interrupts are not used to control
     * processing.
     *
     * @return `true` if this task is now cancelled
     */
    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        val offered = completion.offer(Result.Cancel)
        if (offered) {
            result.cancel(mayInterruptIfRunning)
        }
        return offered
    }

    /**
     * If not already completed, sets the value returned by [get] and related methods to the given value.
     *
     * @param value the result value
     * @return `true` if this invocation caused this CompletableFuture
     * to transition to a completed state, else `false`
     */
    fun complete(value: V): Boolean {
        val offered = completion.offer(Result.Ok(value))
        if (offered) {
            result.run()
        }
        return offered
    }

    override fun isDone(): Boolean = completion.isNotEmpty()

    override fun get(): V = result.get()

    override fun get(timeout: Long, unit: TimeUnit): V = result.get(timeout, unit)

    override fun isCancelled(): Boolean = completion.peek() == Result.Cancel
}
