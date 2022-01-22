package rflow


import arrow.core.Tuple4
import arrow.fx.coroutines.parMap
import kotlinx.coroutines.flow.*
import reader.*

abstract class RFlow<T : Any, U : Any> {
    abstract val rflow: Reader<T, Flow<U>>

    inline fun <R : Any> genMapFlow(crossinline f: (T, Flow<U>) -> Flow<R>): RFlow<T, R> =
        pure(this.rflow.flatMap { fl ->
            Reader { context ->
                f(context, fl)
            }
        })

    inline fun <R : Any> genFlatMap(concurrency: Int,
                                    crossinline transform: (T, U) -> RFlow<T, R>): RFlow<T, R> =
        this.genMapFlow { t, fl ->
            fl.flatMapMerge(concurrency) {
                transform(t, it).rflow.runReader(t)
            }
        }

    inline fun <R : Any> genMergeWith(concurrency: Int,
                                      crossinline transform: (T) -> Flow<R>): RFlow<T, R> =
        this.genMapFlow { t, fl ->
            fl.flatMapMerge(concurrency) {
                transform(t)
            }
        }

    inline fun <R : Any> genMap(crossinline transform: suspend (T, U) -> R): RFlow<T, R> =
        RFlow1.from(this.genMapFlow { t, flu -> flu.map { transform(t, it) } })

    inline fun genOnEach(crossinline action: suspend (T, U) -> Unit): RFlow<T, U> =
        RFlow1.from(this.genMapFlow { t, fl -> fl.onEach { u -> action(t, u) } })

    inline fun <R : Any> genParMap(crossinline transform: suspend (T, U) -> R): RFlow<T, R> =
        RFlow1.from(this.genMapFlow { t, fl -> fl.parMap { u -> transform(t, u) } })

    inline fun genCatch(crossinline action: suspend (T, Throwable) -> Unit): RFlow<T, U> =
        RFlow1.from(this.genMapFlow { t, fl -> fl.catch { u -> action(t, u) } })

    companion object {

        fun <T : Any, U : Any> pure(v: Reader<T, Flow<U>>): RFlow1<T, U> =
            RFlow1(v)

        fun <T : Any, U : Any> pure(v: Flow<U>): RFlow1<T, U> =
            RFlow1(Reader.pure(v))
    }
}

fun <T : Any, U : Any> RFlow<T, Flow<U>>.genFlattenFlow(concurrency: Int = DEFAULT_CONCURRENCY): RFlow<T, U> =
    this.genMapFlow { _, fl -> fl.flattenMerge(concurrency) }

class RFlow1<T : Any, U : Any>(override val rflow: Reader<T, Flow<U>>) : RFlow<T, U>() {
    inline fun <R : Any> flatMap(concurrency: Int = DEFAULT_CONCURRENCY,
                                 crossinline transform: (T, U) -> RFlow1<T, R>): RFlow1<T, R> =
        from(this.genFlatMap(concurrency, transform))

    inline fun <R : Any> mergeWith(concurrency: Int = DEFAULT_CONCURRENCY,
                                   crossinline transform: (T) -> Flow<R>): RFlow1<T, R> =
        from(this.genMergeWith(concurrency, transform))

    inline fun <R : Any> mapFlow(crossinline transform: (T, Flow<U>) -> Flow<R>): RFlow1<T, R> =
        from(this.genMapFlow(transform))

    inline fun <R : Any> map(crossinline transform: suspend (T, U) -> R): RFlow1<T, R> =
        from(this.genMap(transform))

    inline fun onEach(crossinline action: suspend (T, U) -> Unit): RFlow1<T, U> =
        from(this.genOnEach(action))

    inline fun <R : Any> parMap(crossinline transform: suspend (T, U) -> R): RFlow1<T, R> =
        from(this.genParMap(transform))

    inline fun catch(crossinline action: suspend (T, Throwable) -> Unit): RFlow1<T, U> =
        from(this.genCatch(action))

    fun <T2 : Any> requires(r: Has<T2>): RFlow2<T2, T, U> =
        RFlow2(this.rflow.local { rt: Pair<T2, T> -> rt.second })

    fun <T2 : Any, T3 : Any> requires(r2: Has<T2>, r3: Has<T3>): RFlow3<T2, T3, T, U> =
        RFlow3(this.rflow.local { rt: Triple<T2, T3, T> -> rt.third })

    fun <T2 : Any, T3 : Any, T4 : Any> requires(
            r2: Has<T2>,
            r3: Has<T3>,
            r4: Has<T4>
    ): RFlow4<T2, T3, T4, T, U> =
        RFlow4(this.rflow.local { rt: Tuple4<T2, T3, T4, T> -> rt.fourth })

    fun fulfill(t1: T): Flow<U> =
        this.rflow.runReader(t1)

    companion object {
        fun <T : Any, U : Any> from(rf: RFlow<T, U>): RFlow1<T, U> =
            RFlow1(rf.rflow.local { it })
    }
}

fun <T : Any, U : Any> RFlow1<T, Flow<U>>.flattenFlow(concurrency: Int = DEFAULT_CONCURRENCY): RFlow1<T, U> =
    RFlow1.from(this.genFlattenFlow(concurrency))

class RFlow2<T : Any, T2 : Any, U : Any>(override val rflow: Reader<Pair<T, T2>, Flow<U>>) :
    RFlow<Pair<T, T2>, U>() {

    inline fun <R : Any> mergeWith(concurrency: Int = DEFAULT_CONCURRENCY,
                                   crossinline transform: (Pair<T, T2>) -> Flow<R>): RFlow2<T, T2, R> =
        fromPairs(this.genMergeWith(concurrency, transform))

    inline fun <R : Any> flatMap(concurrency: Int = DEFAULT_CONCURRENCY,
                                 crossinline transform: (Pair<T, T2>, U) -> RFlow2<T, T2, R>): RFlow2<T, T2, R> =
        fromPairs(this.genFlatMap(concurrency, transform))

    inline fun <R : Any> map(
            crossinline transform: suspend (Pair<T, T2>, U) -> R
    ): RFlow2<T, T2, R> =
        fromPairs(this.genMap(transform))

    inline fun <R : Any> mapFlow(crossinline transform: (Pair<T, T2>, Flow<U>) -> Flow<R>): RFlow2<T, T2, R> =
        fromPairs(this.genMapFlow(transform))

    inline fun onEach(
            crossinline action: suspend (Pair<T, T2>, U) -> Unit
    ): RFlow2<T, T2, U> =
        fromPairs(this.genOnEach(action))

    inline fun <R : Any> parMap(
            crossinline transform: suspend (Pair<T, T2>, U) -> R
    ): RFlow2<T, T2, R> =
        fromPairs(this.genParMap(transform))

    inline fun catch(noinline action: suspend (Pair<T, T2>, Throwable) -> Unit): RFlow2<T, T2, U> =
        fromPairs(this.genCatch(action))

    fun <T3 : Any> requires(r: Has<T3>): RFlow3<T3, T, T2, U> =
        RFlow3(this.rflow.local { rt: Triple<T3, T, T2> -> Pair(rt.second, rt.third) })

    fun <T3 : Any, T4 : Any> requires(
            r3: Has<T3>,
            r4: Has<T4>
    ): RFlow4<T3, T4, T, T2, U> =
        RFlow4(this.rflow.local { rt: Tuple4<T3, T4, T, T2> -> Pair(rt.third, rt.fourth) })

    fun fulfill(t1: T, t2: T2): Flow<U> =
        this.rflow.runReader(Pair(t1, t2))

    companion object {
        fun <T : Any, T2 : Any, U : Any> fromPairs(rf: RFlow<Pair<T, T2>, U>): RFlow2<T, T2, U> =
            RFlow2(rf.rflow.local { it })
    }
}

fun <T : Any, T2 : Any, U : Any> RFlow2<T, T2, Flow<U>>.flattenFlow(concurrency: Int = DEFAULT_CONCURRENCY): RFlow2<T, T2, U> =
    RFlow2.fromPairs(this.genFlattenFlow(concurrency))

class RFlow3<T : Any, T2 : Any, T3 : Any, U : Any>(override val rflow: Reader<Triple<T, T2, T3>, Flow<U>>) :
    RFlow<Triple<T, T2, T3>, U>() {

    inline fun <R : Any> mergeWith(concurrency: Int = DEFAULT_CONCURRENCY,
                                   crossinline transform: (Triple<T, T2, T3>) -> Flow<R>): RFlow3<T, T2, T3, R> =
        from(this.genMergeWith(concurrency, transform))

    inline fun <R : Any> flatMap(concurrency: Int = DEFAULT_CONCURRENCY,
                                 crossinline transform: (Triple<T, T2, T3>, U) -> RFlow3<T, T2, T3, R>): RFlow3<T, T2, T3, R> =
        from(this.genFlatMap(concurrency, transform))

    inline fun <R : Any> mapFlow(crossinline transform: (Triple<T, T2, T3>, Flow<U>) -> Flow<R>): RFlow3<T, T2, T3, R> =
        from(this.genMapFlow(transform))

    inline fun <R : Any> map(crossinline transform: suspend (Triple<T, T2, T3>, U) -> R): RFlow3<T, T2, T3, R> =
        from(this.genMap(transform))

    inline fun onEach(crossinline action: suspend (Triple<T, T2, T3>, U) -> Unit): RFlow3<T, T2, T3, U> =
        from(this.genOnEach(action))

    inline fun <R : Any> parMap(crossinline transform: suspend (Triple<T, T2, T3>, U) -> R): RFlow3<T, T2, T3, R> =
        from(this.genParMap(transform))

    inline fun catch(crossinline action: suspend (Triple<T, T2, T3>, Throwable) -> Unit): RFlow3<T, T2, T3, U> =
        from(this.genCatch(action))

    fun <T4 : Any> requires(r: Has<T4>): RFlow4<T4, T, T2, T3, U> =
        RFlow4(this.rflow.local { rt: Tuple4<T4, T, T2, T3> ->
            Triple(
                rt.second,
                rt.third,
                rt.fourth
            )
        })

    fun fulfill(t1: T, t2: T2, t3: T3): Flow<U> =
        this.rflow.runReader(Triple(t1, t2, t3))

    companion object {
        fun <T : Any, T2 : Any, T3 : Any, U : Any> fromPairs(rf: RFlow<Pair<T, Pair<T2, T3>>, U>): RFlow3<T, T2, T3, U> =
            RFlow3(rf.rflow.local { t: Triple<T, T2, T3> ->
                Pair(t.first, Pair(t.second, t.third))
            })

        fun <T : Any, T2 : Any, T3 : Any, U : Any> from(rf: RFlow<Triple<T, T2, T3>, U>): RFlow3<T, T2, T3, U> =
            RFlow3(rf.rflow.local { it })
    }
}

fun <T : Any, T2 : Any, T3 : Any, U : Any> RFlow3<T, T2, T3, Flow<U>>.flattenFlow(concurrency: Int = DEFAULT_CONCURRENCY): RFlow3<T, T2, T3, U> =
    RFlow3.from(this.genFlattenFlow(concurrency))

class RFlow4<T : Any, T2 : Any, T3 : Any, T4 : Any, U : Any>(override val rflow: Reader<Tuple4<T, T2, T3, T4>, Flow<U>>) :
    RFlow<Tuple4<T, T2, T3, T4>, U>() {

    inline fun <R : Any> mergeWith(concurrency: Int = DEFAULT_CONCURRENCY,
                                   crossinline transform: (Tuple4<T, T2, T3, T4>) -> Flow<R>): RFlow4<T, T2, T3, T4, R> =
        from(this.genMergeWith(concurrency, transform))

    inline fun <R : Any> flatMap(concurrency: Int = DEFAULT_CONCURRENCY,
                                 crossinline transform: (Tuple4<T, T2, T3, T4>, U) -> RFlow4<T, T2, T3, T4, R>): RFlow4<T, T2, T3, T4, R> =
        from(this.genFlatMap(concurrency, transform))

    inline fun <R : Any> mapFlow(crossinline transform: (Tuple4<T, T2, T3, T4>, Flow<U>) -> Flow<R>): RFlow4<T, T2, T3, T4, R> =
        from(this.genMapFlow(transform))

    inline fun <R : Any> map(crossinline transform: suspend (Tuple4<T, T2, T3, T4>, U) -> R): RFlow4<T, T2, T3, T4, R> =
        from(this.genMap(transform))

    inline fun onEach(crossinline action: suspend (Tuple4<T, T2, T3, T4>, U) -> Unit): RFlow4<T, T2, T3, T4, U> =
        from(this.genOnEach(action))

    inline fun <R : Any> parMap(crossinline transform: suspend (Tuple4<T, T2, T3, T4>, U) -> R): RFlow4<T, T2, T3, T4, R> =
        from(this.genParMap(transform))

    inline fun catch(crossinline action: suspend (Tuple4<T, T2, T3, T4>, Throwable) -> Unit): RFlow4<T, T2, T3, T4, U> =
        from(this.genCatch(action))

    fun fulfill(t1: T, t2: T2, t3: T3, t4: T4): Flow<U> =
        this.rflow.runReader(Tuple4(t1, t2, t3, t4))

    companion object {
        fun <T : Any, T2 : Any, T3 : Any, T4 : Any, U : Any> fromPairs(
                rf: RFlow<Pair<T, Pair<T2, Pair<T3, T4>>>, U>
        ): RFlow4<T, T2, T3, T4, U> =
            RFlow4(rf.rflow.local { t: Tuple4<T, T2, T3, T4> ->
                Pair(t.first, Pair(t.second, Pair(t.third, t.fourth)))
            })

        fun <T : Any, T2 : Any, T3 : Any, T4 : Any, U : Any> from(rf: RFlow<Tuple4<T, T2, T3, T4>, U>): RFlow4<T, T2, T3, T4, U> =
            RFlow4(rf.rflow.local { it })
    }
}

fun <T : Any, T2 : Any, T3 : Any, T4 : Any, U : Any> RFlow4<T, T2, T3, T4, Flow<U>>.flattenFlow(
        concurrency: Int = DEFAULT_CONCURRENCY): RFlow4<T, T2, T3, T4, U> =
    RFlow4.from(this.genFlattenFlow(concurrency))

fun <T : Any, U : Any> Flow<U>.requires(t: Has<T>): RFlow1<T, U> =
    RFlow.pure(this)

fun <T : Any, T2 : Any, U : Any> Flow<U>.requires(t: Has<T>, t2: Has<T2>): RFlow2<T, T2, U> =
    RFlow2.fromPairs(RFlow.pure(Reader { this }))

fun <T : Any, T2 : Any, T3 : Any, U : Any> Flow<U>.requires(
        t: Has<T>,
        t2: Has<T2>,
        t3: Has<T3>
): RFlow3<T, T2, T3, U> =
    RFlow3.fromPairs(RFlow.pure(Reader { this }))

fun <T : Any, T2 : Any, T3 : Any, T4 : Any, U : Any> Flow<U>.requires(
        t: Has<T>,
        t2: Has<T2>,
        t3: Has<T3>,
        t4: Has<T4>
): RFlow4<T, T2, T3, T4, U> =
    RFlow4.fromPairs(RFlow.pure(Reader { this }))


class Has<A> {
    companion object
}