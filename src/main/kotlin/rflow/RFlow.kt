package rflow


import arrow.core.Tuple4
import arrow.core.Tuple5
import arrow.fx.coroutines.parMap
import kotlinx.coroutines.flow.*
import reader.*
import kotlin.experimental.ExperimentalTypeInference

abstract class RFlow<T : Any, U> {
    abstract val rflow: Reader<T, Flow<U>>

    inline fun <R> genMapFlow(crossinline f: (T, Flow<U>) -> Flow<R>): RFlow<T, R> =
        pure(this.rflow.flatMap { fl ->
            Reader { context ->
                f(context, fl)
            }
        })

    inline fun <R> genFlatMap(concurrency: Int,
                              crossinline transform: (T, U) -> RFlow<T, R>): RFlow<T, R> =
        this.genMapFlow { t, fl ->
            fl.flatMapMerge(concurrency) {
                transform(t, it).rflow.runReader(t)
            }
        }

    inline fun <R> genMergeWith(concurrency: Int,
                                crossinline transform: (T) -> Flow<R>): RFlow<T, R> =
        this.genMapFlow { t, fl ->
            fl.flatMapMerge(concurrency) {
                transform(t)
            }
        }

    @OptIn(ExperimentalTypeInference::class)
    inline fun <R> genTransform(
            @BuilderInference crossinline transform: suspend FlowCollector<R>.(T, U) -> Unit
    ): RFlow<T, R> = this.genMapFlow { t, flow -> flow.transform { u -> transform(t, u) } }

    inline fun <R> genMap(crossinline transform: suspend (T, U) -> R): RFlow<T, R> =
        RFlow1.from(this.genMapFlow { t, flu -> flu.map { transform(t, it) } })

    inline fun genOnEach(crossinline action: suspend (T, U) -> Unit): RFlow<T, U> =
        RFlow1.from(this.genMapFlow { t, fl -> fl.onEach { u -> action(t, u) } })

    inline fun <R> genParMap(crossinline transform: suspend (T, U) -> R): RFlow<T, R> =
        RFlow1.from(this.genMapFlow { t, fl -> fl.parMap { u -> transform(t, u) } })

    inline fun genCatch(crossinline action: suspend (T, Throwable) -> Unit): RFlow<T, U> =
        RFlow1.from(this.genMapFlow { t, fl -> fl.catch { u -> action(t, u) } })

    companion object {

        val unit = RFlowU.from(flowOf(Unit))

        @OptIn(ExperimentalTypeInference::class)
        fun <U> init(@BuilderInference block: suspend FlowCollector<U>.() -> Unit): RFlowU<U> =
            RFlowU.from(flow(block))

        fun <T : Any, U> pure(v: Reader<T, Flow<U>>): RFlow1<T, U> =
            RFlow1(v)

        fun <T : Any, U> pure(v: Flow<U>): RFlow1<T, U> =
            RFlow1(Reader.pure(v))
    }
}

fun <T : Any, U> RFlow<T, Flow<U>>.genFlattenFlow(concurrency: Int = DEFAULT_CONCURRENCY): RFlow<T, U> =
    this.genMapFlow { _, fl -> fl.flattenMerge(concurrency) }

class RFlowU<U>(override val rflow: Reader<Unit, Flow<U>>) : RFlow<Unit, U>() {
    inline fun <R : Any> flatMap(concurrency: Int = DEFAULT_CONCURRENCY,
                                 crossinline transform: (Unit, U) -> RFlowU<R>): RFlowU<R> =
        from(this.genFlatMap(concurrency, transform))

    @OptIn(ExperimentalTypeInference::class)
    inline fun <R> transform(@BuilderInference crossinline transform: suspend FlowCollector<R>.(Unit, U) -> Unit) =
        from(this.genTransform(transform))

    inline fun <R> mergeWith(concurrency: Int = DEFAULT_CONCURRENCY,
                             crossinline transform: (Unit) -> Flow<R>): RFlowU<R> =
        from(this.genMergeWith(concurrency, transform))

    inline fun <R> mapFlow(crossinline transform: (Unit, Flow<U>) -> Flow<R>): RFlowU<R> =
        from(this.genMapFlow(transform))

    inline fun <R> map(crossinline transform: suspend (Unit, U) -> R): RFlowU<R> =
        from(this.genMap(transform))

    inline fun onEach(crossinline action: suspend (Unit, U) -> Unit): RFlowU<U> =
        from(this.genOnEach(action))

    inline fun <R> parMap(crossinline transform: suspend (Unit, U) -> R): RFlowU<R> =
        from(this.genParMap(transform))

    inline fun catch(crossinline action: suspend (Unit, Throwable) -> Unit): RFlowU<U> =
        from(this.genCatch(action))

    fun <T : Any> requires(r: Has<T>): RFlow1<T, U> =
        RFlow1(this.rflow.local { rt: T -> Unit })

    fun <T : Any, T2 : Any> requires(r1: Has<T>, r2: Has<T2>): RFlow2<T, T2, U> =
        RFlow2(this.rflow.local { rt: Pair<T, T2> -> Unit })

    fun <T : Any, T2 : Any, T3 : Any> requires(r: Has<T>,
                                               r2: Has<T2>,
                                               r3: Has<T3>): RFlow3<T, T2, T3, U> =
        RFlow3(this.rflow.local { rt: Triple<T, T2, T3> -> Unit })

    fun <T : Any, T2 : Any, T3 : Any, T4 : Any> requires(
            r: Has<T>,
            r2: Has<T2>,
            r3: Has<T3>,
            r4: Has<T4>
    ): RFlow4<T, T2, T3, T4, U> =
        RFlow4(this.rflow.local { rt: Tuple4<T, T2, T3, T4> -> Unit })

    fun <T : Any, T2 : Any, T3 : Any, T4 : Any, T5 : Any> requires(
            r: Has<T>,
            r2: Has<T2>,
            r3: Has<T3>,
            r4: Has<T4>,
            r5: Has<T5>
    ): RFlow5<T, T2, T3, T4, T5, U> =
        RFlow5(this.rflow.local { rt: Tuple5<T, T2, T3, T4, T5> -> Unit })

    fun fulfill(): Flow<U> =
        this.rflow.runReader(Unit)

    companion object {
        fun <U> from(rf: RFlow<Unit, U>): RFlowU<U> =
            RFlowU(rf.rflow.local { it })

        fun <U> from(rf: Flow<U>): RFlowU<U> =
            RFlowU(Reader { _ -> rf })
    }
}

fun <U> RFlowU<Flow<U>>.flattenFlow(concurrency: Int = DEFAULT_CONCURRENCY): RFlowU<U> =
    RFlowU.from(this.genFlattenFlow(concurrency))

class RFlow1<T : Any, U>(override val rflow: Reader<T, Flow<U>>) : RFlow<T, U>() {

    @OptIn(ExperimentalTypeInference::class)
    inline fun <R> transform(@BuilderInference crossinline transform: suspend FlowCollector<R>.(T, U) -> Unit) =
        from(this.genTransform(transform))

    inline fun <R : Any> flatMap(concurrency: Int = DEFAULT_CONCURRENCY,
                                 crossinline transform: (T, U) -> RFlow1<T, R>): RFlow1<T, R> =
        from(this.genFlatMap(concurrency, transform))

    inline fun <R> mergeWith(concurrency: Int = DEFAULT_CONCURRENCY,
                             crossinline transform: (T) -> Flow<R>): RFlow1<T, R> =
        from(this.genMergeWith(concurrency, transform))

    inline fun <R> mapFlow(crossinline transform: (T, Flow<U>) -> Flow<R>): RFlow1<T, R> =
        from(this.genMapFlow(transform))

    inline fun <R> map(crossinline transform: suspend (T, U) -> R): RFlow1<T, R> =
        from(this.genMap(transform))

    inline fun onEach(crossinline action: suspend (T, U) -> Unit): RFlow1<T, U> =
        from(this.genOnEach(action))

    inline fun <R> parMap(crossinline transform: suspend (T, U) -> R): RFlow1<T, R> =
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

    fun <T2 : Any, T3 : Any, T4 : Any, T5 : Any> requires(
            r2: Has<T2>,
            r3: Has<T3>,
            r4: Has<T4>,
            r5: Has<T5>
    ): RFlow5<T2, T3, T4, T5, T, U> =
        RFlow5(this.rflow.local { rt: Tuple5<T2, T3, T4, T5, T> -> rt.fifth })

    fun fulfill(t1: T): Flow<U> =
        this.rflow.runReader(t1)

    companion object {
        fun <T : Any, U> from(rf: RFlow<T, U>): RFlow1<T, U> =
            RFlow1(rf.rflow.local { it })
    }
}

fun <T : Any, U> RFlow1<T, Flow<U>>.flattenFlow(concurrency: Int = DEFAULT_CONCURRENCY): RFlow1<T, U> =
    RFlow1.from(this.genFlattenFlow(concurrency))

class RFlow2<T : Any, T2 : Any, U>(override val rflow: Reader<Pair<T, T2>, Flow<U>>) :
    RFlow<Pair<T, T2>, U>() {


    @OptIn(ExperimentalTypeInference::class)
    inline fun <R> transform(
            @BuilderInference crossinline transform: suspend FlowCollector<R>.(Pair<T, T2>, U) -> Unit
    ): RFlow2<T, T2, R> = fromPairs(this.genTransform(transform))


    inline fun <R> mergeWith(concurrency: Int = DEFAULT_CONCURRENCY,
                             crossinline transform: (Pair<T, T2>) -> Flow<R>): RFlow2<T, T2, R> =
        fromPairs(this.genMergeWith(concurrency, transform))

    inline fun <R> flatMap(concurrency: Int = DEFAULT_CONCURRENCY,
                           crossinline transform: (Pair<T, T2>, U) -> RFlow2<T, T2, R>): RFlow2<T, T2, R> =
        fromPairs(this.genFlatMap(concurrency, transform))

    inline fun <R> map(
            crossinline transform: suspend (Pair<T, T2>, U) -> R
    ): RFlow2<T, T2, R> =
        fromPairs(this.genMap(transform))

    inline fun <R> mapFlow(crossinline transform: (Pair<T, T2>, Flow<U>) -> Flow<R>): RFlow2<T, T2, R> =
        fromPairs(this.genMapFlow(transform))

    inline fun onEach(
            crossinline action: suspend (Pair<T, T2>, U) -> Unit
    ): RFlow2<T, T2, U> =
        fromPairs(this.genOnEach(action))

    inline fun <R> parMap(
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

    fun <T3 : Any, T4 : Any, T5 : Any> requires(
            r3: Has<T3>,
            r4: Has<T4>,
            r5: Has<T5>
    ): RFlow5<T3, T4, T5, T, T2, U> =
        RFlow5(this.rflow.local { rt: Tuple5<T3, T4, T5, T, T2> -> Pair(rt.fourth, rt.fifth) })

    fun fulfill(t1: T, t2: T2): Flow<U> =
        this.rflow.runReader(Pair(t1, t2))

    companion object {
        fun <T : Any, T2 : Any, U> fromPairs(rf: RFlow<Pair<T, T2>, U>): RFlow2<T, T2, U> =
            RFlow2(rf.rflow.local { it })
    }
}

fun <T : Any, T2 : Any, U> RFlow2<T, T2, Flow<U>>.flattenFlow(concurrency: Int = DEFAULT_CONCURRENCY): RFlow2<T, T2, U> =
    RFlow2.fromPairs(this.genFlattenFlow(concurrency))

class RFlow3<T : Any, T2 : Any, T3 : Any, U>(override val rflow: Reader<Triple<T, T2, T3>, Flow<U>>) :
    RFlow<Triple<T, T2, T3>, U>() {

    @OptIn(ExperimentalTypeInference::class)
    inline fun <R> transform(
            @BuilderInference crossinline transform: suspend FlowCollector<R>.(Triple<T, T2, T3>, U) -> Unit
    ): RFlow3<T, T2, T3, R> = from(this.genTransform(transform))

    inline fun <R> mergeWith(concurrency: Int = DEFAULT_CONCURRENCY,
                             crossinline transform: (Triple<T, T2, T3>) -> Flow<R>): RFlow3<T, T2, T3, R> =
        from(this.genMergeWith(concurrency, transform))

    inline fun <R> flatMap(concurrency: Int = DEFAULT_CONCURRENCY,
                           crossinline transform: (Triple<T, T2, T3>, U) -> RFlow3<T, T2, T3, R>): RFlow3<T, T2, T3, R> =
        from(this.genFlatMap(concurrency, transform))

    inline fun <R> mapFlow(crossinline transform: (Triple<T, T2, T3>, Flow<U>) -> Flow<R>): RFlow3<T, T2, T3, R> =
        from(this.genMapFlow(transform))

    inline fun <R> map(crossinline transform: suspend (Triple<T, T2, T3>, U) -> R): RFlow3<T, T2, T3, R> =
        from(this.genMap(transform))

    inline fun onEach(crossinline action: suspend (Triple<T, T2, T3>, U) -> Unit): RFlow3<T, T2, T3, U> =
        from(this.genOnEach(action))

    inline fun <R> parMap(crossinline transform: suspend (Triple<T, T2, T3>, U) -> R): RFlow3<T, T2, T3, R> =
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

    fun <T4 : Any, T5 : Any> requires(r: Has<T4>, r2: Has<T5>): RFlow5<T4, T5, T, T2, T3, U> =
        RFlow5(this.rflow.local { rt: Tuple5<T4, T5, T, T2, T3> ->
            Triple(
                rt.third,
                rt.fourth,
                rt.fifth
            )
        })

    fun fulfill(t1: T, t2: T2, t3: T3): Flow<U> =
        this.rflow.runReader(Triple(t1, t2, t3))

    companion object {
        fun <T : Any, T2 : Any, T3 : Any, U> fromPairs(rf: RFlow<Pair<T, Pair<T2, T3>>, U>): RFlow3<T, T2, T3, U> =
            RFlow3(rf.rflow.local { t: Triple<T, T2, T3> ->
                Pair(t.first, Pair(t.second, t.third))
            })

        fun <T : Any, T2 : Any, T3 : Any, U> from(rf: RFlow<Triple<T, T2, T3>, U>): RFlow3<T, T2, T3, U> =
            RFlow3(rf.rflow.local { it })
    }
}

fun <T : Any, T2 : Any, T3 : Any, U> RFlow3<T, T2, T3, Flow<U>>.flattenFlow(concurrency: Int = DEFAULT_CONCURRENCY): RFlow3<T, T2, T3, U> =
    RFlow3.from(this.genFlattenFlow(concurrency))

class RFlow4<T : Any, T2 : Any, T3 : Any, T4 : Any, U>(override val rflow: Reader<Tuple4<T, T2, T3, T4>, Flow<U>>) :
    RFlow<Tuple4<T, T2, T3, T4>, U>() {

    @OptIn(ExperimentalTypeInference::class)
    inline fun <R> transform(
            @BuilderInference crossinline transform: suspend FlowCollector<R>.(Tuple4<T, T2, T3, T4>, U) -> Unit
    ): RFlow4<T, T2, T3, T4, R> = from(this.genTransform(transform))

    inline fun <R> mergeWith(concurrency: Int = DEFAULT_CONCURRENCY,
                             crossinline transform: (Tuple4<T, T2, T3, T4>) -> Flow<R>): RFlow4<T, T2, T3, T4, R> =
        from(this.genMergeWith(concurrency, transform))

    inline fun <R> flatMap(concurrency: Int = DEFAULT_CONCURRENCY,
                           crossinline transform: (Tuple4<T, T2, T3, T4>, U) -> RFlow4<T, T2, T3, T4, R>): RFlow4<T, T2, T3, T4, R> =
        from(this.genFlatMap(concurrency, transform))

    inline fun <R> mapFlow(crossinline transform: (Tuple4<T, T2, T3, T4>, Flow<U>) -> Flow<R>): RFlow4<T, T2, T3, T4, R> =
        from(this.genMapFlow(transform))

    inline fun <R> map(crossinline transform: suspend (Tuple4<T, T2, T3, T4>, U) -> R): RFlow4<T, T2, T3, T4, R> =
        from(this.genMap(transform))

    inline fun onEach(crossinline action: suspend (Tuple4<T, T2, T3, T4>, U) -> Unit): RFlow4<T, T2, T3, T4, U> =
        from(this.genOnEach(action))

    inline fun <R> parMap(crossinline transform: suspend (Tuple4<T, T2, T3, T4>, U) -> R): RFlow4<T, T2, T3, T4, R> =
        from(this.genParMap(transform))

    inline fun catch(crossinline action: suspend (Tuple4<T, T2, T3, T4>, Throwable) -> Unit): RFlow4<T, T2, T3, T4, U> =
        from(this.genCatch(action))

    fun fulfill(t1: T, t2: T2, t3: T3, t4: T4): Flow<U> =
        this.rflow.runReader(Tuple4(t1, t2, t3, t4))

    fun <T5 : Any> requires(r: Has<T5>): RFlow5<T5, T, T2, T3, T4, U> =
        RFlow5(this.rflow.local { rt: Tuple5<T5, T, T2, T3, T4> ->
            Tuple4(
                rt.second,
                rt.third,
                rt.fourth,
                rt.fifth
            )
        })

    companion object {
        fun <T : Any, T2 : Any, T3 : Any, T4 : Any, U> fromPairs(
                rf: RFlow<Pair<T, Pair<T2, Pair<T3, T4>>>, U>
        ): RFlow4<T, T2, T3, T4, U> =
            RFlow4(rf.rflow.local { t: Tuple4<T, T2, T3, T4> ->
                Pair(t.first, Pair(t.second, Pair(t.third, t.fourth)))
            })

        fun <T : Any, T2 : Any, T3 : Any, T4 : Any, U> from(rf: RFlow<Tuple4<T, T2, T3, T4>, U>): RFlow4<T, T2, T3, T4, U> =
            RFlow4(rf.rflow.local { it })
    }
}

fun <T : Any, T2 : Any, T3 : Any, T4 : Any, U> RFlow4<T, T2, T3, T4, Flow<U>>.flattenFlow(
        concurrency: Int = DEFAULT_CONCURRENCY): RFlow4<T, T2, T3, T4, U> =
    RFlow4.from(this.genFlattenFlow(concurrency))

class RFlow5<T : Any, T2 : Any, T3 : Any, T4 : Any, T5 : Any, U>(override val rflow: Reader<Tuple5<T, T2, T3, T4, T5>, Flow<U>>) :
    RFlow<Tuple5<T, T2, T3, T4, T5>, U>() {

    @OptIn(ExperimentalTypeInference::class)
    inline fun <R> transform(
            @BuilderInference crossinline transform: suspend FlowCollector<R>.(Tuple5<T, T2, T3, T4, T5>, U) -> Unit
    ): RFlow5<T, T2, T3, T4, T5, R> = from(this.genTransform(transform))

    inline fun <R> mergeWith(concurrency: Int = DEFAULT_CONCURRENCY,
                             crossinline transform: (Tuple5<T, T2, T3, T4, T5>) -> Flow<R>): RFlow5<T, T2, T3, T4, T5, R> =
        from(this.genMergeWith(concurrency, transform))

    inline fun <R> flatMap(concurrency: Int = DEFAULT_CONCURRENCY,
                           crossinline transform: (Tuple5<T, T2, T3, T4, T5>, U) -> RFlow5<T, T2, T3, T4, T5, R>): RFlow5<T, T2, T3, T4, T5, R> =
        from(this.genFlatMap(concurrency, transform))

    inline fun <R> mapFlow(crossinline transform: (Tuple5<T, T2, T3, T4, T5>, Flow<U>) -> Flow<R>): RFlow5<T, T2, T3, T4, T5, R> =
        from(this.genMapFlow(transform))

    inline fun <R> map(crossinline transform: suspend (Tuple5<T, T2, T3, T4, T5>, U) -> R): RFlow5<T, T2, T3, T4, T5, R> =
        from(this.genMap(transform))

    inline fun onEach(crossinline action: suspend (Tuple5<T, T2, T3, T4, T5>, U) -> Unit): RFlow5<T, T2, T3, T4, T5, U> =
        from(this.genOnEach(action))

    inline fun <R> parMap(crossinline transform: suspend (Tuple5<T, T2, T3, T4, T5>, U) -> R): RFlow5<T, T2, T3, T4, T5, R> =
        from(this.genParMap(transform))

    inline fun catch(crossinline action: suspend (Tuple5<T, T2, T3, T4, T5>, Throwable) -> Unit): RFlow5<T, T2, T3, T4, T5, U> =
        from(this.genCatch(action))

    fun fulfill(t1: T, t2: T2, t3: T3, t4: T4, t5: T5): Flow<U> =
        this.rflow.runReader(Tuple5(t1, t2, t3, t4, t5))

    companion object {
        fun <T : Any, T2 : Any, T3 : Any, T4 : Any, T5 : Any, U> fromPairs(
                rf: RFlow<Pair<T, Pair<T2, Pair<T3, Pair<T4, T5>>>>, U>
        ): RFlow5<T, T2, T3, T4, T5, U> =
            RFlow5(rf.rflow.local { t: Tuple5<T, T2, T3, T4, T5> ->
                Pair(t.first, Pair(t.second, Pair(t.third, Pair(t.fourth, t.fifth))))
            })

        fun <T : Any, T2 : Any, T3 : Any, T4 : Any, T5 : Any, U> from(rf: RFlow<Tuple5<T, T2, T3, T4, T5>, U>): RFlow5<T, T2, T3, T4, T5, U> =
            RFlow5(rf.rflow.local { it })
    }
}

fun <T : Any, T2 : Any, T3 : Any, T4 : Any, T5 : Any, U> RFlow5<T, T2, T3, T4, T5, Flow<U>>.flattenFlow(
        concurrency: Int = DEFAULT_CONCURRENCY): RFlow5<T, T2, T3, T4, T5, U> =
    RFlow5.from(this.genFlattenFlow(concurrency))

fun <U> Flow<U>.asR(): RFlowU<U> =
    RFlowU.from(this)

fun <T : Any, U> Flow<U>.requires(t: Has<T>): RFlow1<T, U> =
    RFlow.pure(this)

fun <T : Any, T2 : Any, U> Flow<U>.requires(t: Has<T>, t2: Has<T2>): RFlow2<T, T2, U> =
    RFlow2.fromPairs(RFlow.pure(Reader { this }))

fun <T : Any, T2 : Any, T3 : Any, U> Flow<U>.requires(
        t: Has<T>,
        t2: Has<T2>,
        t3: Has<T3>
): RFlow3<T, T2, T3, U> =
    RFlow3.fromPairs(RFlow.pure(Reader { this }))

fun <T : Any, T2 : Any, T3 : Any, T4 : Any, U> Flow<U>.requires(
        t: Has<T>,
        t2: Has<T2>,
        t3: Has<T3>,
        t4: Has<T4>
): RFlow4<T, T2, T3, T4, U> =
    RFlow4.fromPairs(RFlow.pure(Reader { this }))


class Has<A>() {
    companion object
}