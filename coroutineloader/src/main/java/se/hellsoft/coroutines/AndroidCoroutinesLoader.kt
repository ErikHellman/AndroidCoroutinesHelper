@file:Suppress("unused")

package se.hellsoft.coroutines

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.OnLifecycleEvent
import android.os.Handler
import android.os.Looper
import android.view.View
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.actor
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.channels.map
import java.io.InputStream
import kotlin.coroutines.experimental.CoroutineContext

/**
 * The context to run loading functions on.
 */
internal val loaderContext: CoroutineContext by lazy {
    val threadCount = Runtime.getRuntime().availableProcessors() * 2
    return@lazy newFixedThreadPoolContext(threadCount, "coroutine-loader")
}

/**
 * A lifecycle observer with the task to call `cancel()` on a coroutine `Job` at a certain
 * lifecycle event (default is `ON_STOP`).
 * @param cancelEvent Which event to cancel on (Default is `ON_STOP`).
 * @param job The coroutine job to cancel.
 */
class CoroutineLifecycleListener(private val cancelEvent: Lifecycle.Event = Lifecycle.Event.ON_STOP,
                                 private val job: Job) : LifecycleObserver {
    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun pause() = handleEvent(Lifecycle.Event.ON_PAUSE)

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun stop() = handleEvent(Lifecycle.Event.ON_STOP)

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun destroy() = handleEvent(Lifecycle.Event.ON_DESTROY)

    private fun handleEvent(e: Lifecycle.Event) {
        if (e == cancelEvent && !job.isCancelled) {
            job.cancel()
        }
    }
}

/**
 * This extension function can be called from on a `LifecycleOwner` and creates a coroutine
 * that will call the `loadingFunction()` once started.
 *
 * @param context An optional parameter to specify which `CoroutineContext` for the created coroutine.
 * @param start An optional paramteter to control how to coroutine is started.
 * @param loadingFunction A function that will be called inside the coroutine and return a value of type `T`.
 */
fun <T> LifecycleOwner.load(context: CoroutineContext = loaderContext,
                            start: CoroutineStart = CoroutineStart.DEFAULT,
                            loadingFunction: () -> T): Deferred<T> {
    val deferred = async(context = context, start = start) {
        return@async loadingFunction()
    }
    lifecycle.addObserver(CoroutineLifecycleListener(job = deferred))
    return deferred
}

/**
 * This extension function will create a coroutine that will run on the UI thread. It will call the
 * `await()` on the `Deferred` object it is a called on and pass the value to the `uiFunction`.
 *
 * @param uiFunction A function that takes a single parameter of type `T` once the `await()` call on the `Deferred` returns a value.
 */
infix fun <T> Deferred<T>.then(uiFunction: (T) -> Unit): Job {
    return launch(context = UI, start = CoroutineStart.DEFAULT) {
        uiFunction(this@then.await())
    }
}

class OnClickLoader<out T> internal constructor(private val lifecycle: Lifecycle,
                                                private val view: View,
                                                private val loadingContext: CoroutineContext = loaderContext,
                                                private val disableView: Boolean = true,
                                                private val loadFunction: () -> T,
                                                private val cancelEvent: Lifecycle.Event = Lifecycle.Event.ON_STOP) {
    /**
     * The function will ensure that a clicks on a view will only queue up one background
     * job (@see `LifecycleOwner.whenClicking()`) at a time until `uiFunction` returns.
     *
     * @param uiFunction The function that will be called on the UI thread after the `loadingFunction` has been called.
     */
    infix fun then(uiFunction: (T) -> Unit) {
        val job = Job()
        val actor = actor<Unit>(context = UI, parent = job) {
            channel.map(loadingContext) { loadFunction() }
                    .consumeEach {
                        if (disableView) {
                            view.isEnabled = true
                        }
                        uiFunction(it)
                    }
        }

        lifecycle.addObserver(CoroutineLifecycleListener(cancelEvent, job))

        view.setOnClickListener {
            if (disableView) {
                view.isEnabled = false
            }
            actor.offer(Unit)
        }
    }
}

/**
 * This extension function wraps a `View` and creates a `OnClickLoader` that we then can call `then`
 * on to perform a background work followed by a call to the `uiFunction`.
 *
 * Example:
 * ```kotlin
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     super.onCreate(savedInstanceState)
 *     setContentView(R.layout.activity_main)
 *
 *     whenClicking(button) { loadDataFromNetwork() } then { showData(it) }
 *
 * }
 * ```
 *
 */
fun <T> LifecycleOwner.whenClicking(view: View,
                                    disableView: Boolean = true,
                                    context: CoroutineContext = loaderContext,
                                    cancelEvent: Lifecycle.Event = Lifecycle.Event.ON_STOP,
                                    loadFunction: () -> T): OnClickLoader<T> {
    return OnClickLoader(lifecycle = lifecycle, view = view, disableView = disableView,
            loadingContext = context, cancelEvent = cancelEvent, loadFunction = loadFunction)
}
