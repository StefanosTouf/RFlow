@file:Suppress("OVERRIDE_BY_INLINE")

package rflow


import arrow.core.Tuple4
import arrow.fx.coroutines.parMap
import kotlinx.coroutines.flow.*
import reader.*

interface RFlow<T : Any, U : Any> {
    val rflow: Reader<T, Flow<U>>
    fun <R : Any> mapFlow(transform: (Flow<U>) -> Flow<R>): RFlow<T, R>

    fun <R : Any> map(transform: suspend (T, U) -> R): RFlow<T, R>

    fun onEach(action: suspend (T, U) -> Unit): RFlow<T, U>

    fun <R : Any> parMap(transform: suspend (T, U) -> R): RFlow<T, R>

    fun catch(action: suspend (T, Throwable) -> Unit): RFlow<T, U>


    fun <R : Any> genMap(f: (T, Flow<U>) -> Flow<R>): RFlow<T, R> =
        pure(this.rflow.flatMap { fl ->
            Reader { context ->
                f(context, fl)
            }
        })

    companion object {
        fun <T : Any, U : Any> pure(v: Reader<T, Flow<U>>): RFlow1<T, U> =
            RFlow1(v)

        fun <T : Any, U : Any> pure(v: Flow<U>): RFlow1<T, U> =
            RFlow1(Reader.pure(v))
    }
}

class RFlow1<T : Any, U : Any>(override val rflow: Reader<T, Flow<U>>) : RFlow<T, U> {
    override inline fun <R : Any> mapFlow(crossinline transform: (Flow<U>) -> Flow<R>): RFlow<T, R> =
        this.genMap { _, fl -> transform(fl) }

    override inline fun <R : Any> map(crossinline transform: suspend (T, U) -> R): RFlow1<T, R> =
        from(this.genMap { t, flu -> flu.map { transform(t, it) } })

    override inline fun onEach(crossinline action: suspend (T, U) -> Unit): RFlow1<T, U> =
        from(this.genMap { t, fl -> fl.onEach { u -> action(t, u) } })

    override inline fun <R : Any> parMap(crossinline transform: suspend (T, U) -> R): RFlow1<T, R> =
        from(this.genMap { t, fl -> fl.parMap { u -> transform(t, u) } })

    override inline fun catch(noinline action: suspend (T, Throwable) -> Unit): RFlow1<T, U> =
        from(this.genMap { t, fl -> fl.catch { u -> action(t, u) } })

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

class RFlow2<T : Any, T2 : Any, U : Any>(override val rflow: Reader<Pair<T, T2>, Flow<U>>) :
    RFlow<Pair<T, T2>, U> {

    override inline fun <R : Any> map(
            crossinline transform: suspend (Pair<T, T2>, U) -> R
    ): RFlow2<T, T2, R> =
        fromPairs(this.genMap { t, flu -> flu.map { transform(t, it) } })

    override inline fun <R : Any> mapFlow(crossinline transform: (Flow<U>) -> Flow<R>): RFlow2<T, T2, R> =
        fromPairs(this.genMap { _, fl -> transform(fl) })

    override inline fun onEach(
            crossinline action: suspend (Pair<T, T2>, U) -> Unit
    ): RFlow2<T, T2, U> =
        fromPairs(this.genMap { t, fl -> fl.onEach { u -> action(t, u) } })

    override inline fun <R : Any> parMap(
            crossinline transform: suspend (Pair<T, T2>, U) -> R
    ): RFlow2<T, T2, R> =
        fromPairs(this.genMap { t, fl -> fl.parMap { u -> transform(t, u) } })

    override inline fun catch(noinline action: suspend (Pair<T, T2>, Throwable) -> Unit): RFlow2<T, T2, U> =
        fromPairs(this.genMap { t, fl -> fl.catch { u -> action(t, u) } })

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

class RFlow3<T : Any, T2 : Any, T3 : Any, U : Any>(override val rflow: Reader<Triple<T, T2, T3>, Flow<U>>) :
    RFlow<Triple<T, T2, T3>, U> {

    override inline fun <R : Any> mapFlow(crossinline transform: (Flow<U>) -> Flow<R>): RFlow3<T, T2, T3, R> =
        from(this.genMap { _, fl -> transform(fl) })

    override inline fun <R : Any> map(crossinline transform: suspend (Triple<T, T2, T3>, U) -> R): RFlow3<T, T2, T3, R> =
        from(this.genMap { t, flu -> flu.map { transform(t, it) } })

    override inline fun onEach(crossinline action: suspend (Triple<T, T2, T3>, U) -> Unit): RFlow3<T, T2, T3, U> =
        from(this.genMap { t, fl -> fl.onEach { u -> action(t, u) } })

    override inline fun <R : Any> parMap(crossinline transform: suspend (Triple<T, T2, T3>, U) -> R): RFlow3<T, T2, T3, R> =
        from(this.genMap { t, fl -> fl.parMap { u -> transform(t, u) } })

    override inline fun catch(noinline action: suspend (Triple<T, T2, T3>, Throwable) -> Unit): RFlow3<T, T2, T3, U> =
        from(this.genMap { t, fl -> fl.catch { u -> action(t, u) } })

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

class RFlow4<T : Any, T2 : Any, T3 : Any, T4 : Any, U : Any>(override val rflow: Reader<Tuple4<T, T2, T3, T4>, Flow<U>>) :
    RFlow<Tuple4<T, T2, T3, T4>, U> {

    override inline fun <R : Any> mapFlow(crossinline transform: (Flow<U>) -> Flow<R>): RFlow4<T, T2, T3, T4, R> =
        from(this.genMap { _, fl -> transform(fl) })

    override inline fun <R : Any> map(crossinline transform: suspend (Tuple4<T, T2, T3, T4>, U) -> R): RFlow4<T, T2, T3, T4, R> =
        from(this.genMap { t, flu -> flu.map { transform(t, it) } })

    override inline fun onEach(crossinline action: suspend (Tuple4<T, T2, T3, T4>, U) -> Unit): RFlow4<T, T2, T3, T4, U> =
        from(this.genMap { t, fl -> fl.onEach { u -> action(t, u) } })

    override inline fun <R : Any> parMap(crossinline transform: suspend (Tuple4<T, T2, T3, T4>, U) -> R): RFlow4<T, T2, T3, T4, R> =
        from(this.genMap { t, fl -> fl.parMap { u -> transform(t, u) } })

    override inline fun catch(noinline action: suspend (Tuple4<T, T2, T3, T4>, Throwable) -> Unit): RFlow4<T, T2, T3, T4, U> =
        from(this.genMap { t, fl -> fl.catch { u -> action(t, u) } })

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