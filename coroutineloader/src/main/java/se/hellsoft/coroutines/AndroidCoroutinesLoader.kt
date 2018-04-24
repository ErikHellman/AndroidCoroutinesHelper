@file:Suppress("unused")

package se.hellsoft.coroutines

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.OnLifecycleEvent
import android.view.View
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.actor
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.channels.map
import kotlin.coroutines.experimental.CoroutineContext

internal val loaderContext: CoroutineContext by lazy {
    val threadCount = Runtime.getRuntime().availableProcessors() * 2
    return@lazy newFixedThreadPoolContext(threadCount, "coroutine-loader")
}

internal class CoroutineLifecycleListener(private val cancelEvent: Lifecycle.Event = Lifecycle.Event.ON_STOP,
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

fun <T> LifecycleOwner.load(context: CoroutineContext = loaderContext,
                            start: CoroutineStart = CoroutineStart.DEFAULT,
                            block: () -> T): Deferred<T> {
    val deferred = async(context = context, start = start) {
        return@async block()
    }
    lifecycle.addObserver(CoroutineLifecycleListener(job = deferred))
    return deferred
}

infix fun <T> Deferred<T>.then(block: (T) -> Unit): Job {
    return launch(context = UI, start = CoroutineStart.DEFAULT) {
        block(this@then.await())
    }
}

class OnClickLoader<out T> internal constructor(private val lifecycle: Lifecycle,
                                                private val view: View,
                                                private val cancelEvent: Lifecycle.Event = Lifecycle.Event.ON_STOP,
                                                private val loadFunction: () -> T) {
    infix fun then(uiFunction: (T) -> Unit) {
        val job = Job()
        val actor = actor<Unit>(context = UI, parent = job) {
            channel.map(loaderContext) { loadFunction() }
                    .consumeEach { uiFunction(it) }
        }

        lifecycle.addObserver(CoroutineLifecycleListener(cancelEvent, job))

        view.setOnClickListener { actor.offer(Unit) }
    }
}

fun <T> LifecycleOwner.whenClicking(view: View,
                                    cancelEvent: Lifecycle.Event = Lifecycle.Event.ON_STOP,
                                    loadFunction: () -> T): OnClickLoader<T> {
    return OnClickLoader(lifecycle = lifecycle, view = view,
            cancelEvent = cancelEvent, loadFunction = loadFunction)
}

