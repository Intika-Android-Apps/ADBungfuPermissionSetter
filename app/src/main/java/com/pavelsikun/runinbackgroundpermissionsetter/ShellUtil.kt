package com.pavelsikun.runinbackgroundpermissionsetter

import eu.chainfire.libsuperuser.Shell
import java.util.concurrent.*

/**
 * Created by Pavel Sikun on 16.07.17.
 * 2020 https://stackoverflow.com/questions/38221673/completablefuture-in-the-android-support-library
 */

typealias Callback = (isSuccess: Boolean) -> Unit

private val shell by lazy { Shell.Builder()
        .setShell("su")
        .open()
}

fun checkAppOpsPermission(pkg: String, appops: String): CompletableFutureCompat<String> {
    val future = CompletableFutureCompat<String>()
    shell.addCommand("appops get $pkg $appops", 1) { _, _, output: MutableList<String> ->
        val outputString = output.joinToString()
        future.complete(outputString)
    }

    return future
}

fun setAppOpsPermission(pkg: String, appops: String, setEnabled: Boolean, callback: Callback): CompletableFutureCompat<Boolean> {
    val future = CompletableFutureCompat<Boolean>()
    val cmd = if (setEnabled) "allow" else "ignore"
    val su_cmd: String
    var isSuccess: Boolean

    if (appops.equals(freezer)) {
        if (!setEnabled) su_cmd = "pm disable-user $pkg"
                else su_cmd = "pm enable $pkg"
    } else su_cmd = "appops set $pkg $appops $cmd"

    shell.addCommand(su_cmd, 1) { _, _, output: MutableList<String> ->
        val outputString = output.joinToString()
        if (appops.equals(freezer)) isSuccess = outputString.contains("new state")
        else isSuccess = outputString.trim().isEmpty()
        callback(isSuccess)
        future.complete(isSuccess)
    }

    return future

}

fun resetAppOpsPermission(pkg: String): CompletableFutureCompat<String> {
    val future = CompletableFutureCompat<String>()
    shell.addCommand("appops reset $pkg", 1) { _, _, output: MutableList<String> ->
        val outputString = output.joinToString()
        future.complete(outputString)
    }

    return future
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
