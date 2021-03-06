package rflow

import reader.*
import arrow.core.Tuple4
import arrow.core.Tuple5
import arrow.core.Tuple6
import arrow.core.Tuple7
import arrow.fx.coroutines.parMap
import kotlinx.coroutines.flow.*
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
                              crossinline transform: suspend (T, U) -> RFlow<T, R>): RFlow<T, R> =
        this.genMapFlow { t, fl ->
            fl.flatMapMerge(concurrency) {
                transform(t, it).rflow.runReader(t)
            }
        }

    inline fun <R> genMergeWith(concurrency: Int,
                                crossinline transform: suspend (T) -> Flow<R>): RFlow<T, R> =
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

        fun unit() = RFlowU.from(flowOf(Unit))

        @OptIn(ExperimentalTypeInference::class)
        fun <U> init(@BuilderInference block: suspend FlowCollector<U>.() -> Unit): RFlowU<U> =
            RFlowU.from(flow(block))

        fun <T : Any, U> pure(v: Reader<T, Flow<U>>): RFlow1<T, U> =
            RFlow1(v)

        fun <T : Any, U> pure(v: Flow<U>): RFlow1<T, U> =
            RFlow1(Reader.pure(v))
    }
}

fun <T : Any, U> RFlow<T, Flow<U>>.genFlattenFlow(concurrency: Int): RFlow<T, U> =
    this.genMapFlow { _, fl -> fl.flattenMerge(concurrency) }

class RFlowU<U>(override val rflow: Reader<Unit, Flow<U>>) : RFlow<Unit, U>() {
    inline fun <R : Any> flatMap(concurrency: Int = DEFAULT_CONCURRENCY,
                                 crossinline transform: suspend (Unit, U) -> RFlowU<R>): RFlowU<R> =
        from(this.genFlatMap(concurrency, transform))

    @OptIn(ExperimentalTypeInference::class)
    inline fun <R> transform(@BuilderInference crossinline transform: suspend FlowCollector<R>.(Unit, U) -> Unit) =
        from(this.genTransform(transform))

    inline fun <R> mergeWith(concurrency: Int = DEFAULT_CONCURRENCY,
                             crossinline transform: suspend (Unit) -> Flow<R>): RFlowU<R> =
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

    fun <T : Any> require(): RFlow1<T, U> =
        RFlow1(this.rflow.local { })

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

fun <U> RFlowU<RFlowU<U>>.flattenMerge(concurrency: Int = DEFAULT_CONCURRENCY): RFlowU<U> =
    RFlowU.from(this.genFlatMap(concurrency) { _, fl -> fl })

class RFlow1<T : Any, U>(override val rflow: Reader<T, Flow<U>>) : RFlow<T, U>() {

    @OptIn(ExperimentalTypeInference::class)
    inline fun <R> transform(@BuilderInference crossinline transform: suspend FlowCollector<R>.(T, U) -> Unit) =
        from(this.genTransform(transform))

    inline fun <R : Any> flatMap(concurrency: Int = DEFAULT_CONCURRENCY,
                                 crossinline transform: suspend T.(U) -> RFlow1<T, R>): RFlow1<T, R> =
        from(this.genFlatMap(concurrency, transform))

    inline fun <R> mergeWith(concurrency: Int = DEFAULT_CONCURRENCY,
                             crossinline transform: suspend T.() -> Flow<R>): RFlow1<T, R> =
        from(this.genMergeWith(concurrency, transform))

    inline fun <R> mapFlow(crossinline transform: T.(Flow<U>) -> Flow<R>): RFlow1<T, R> =
        from(this.genMapFlow(transform))

    inline fun <R> map(crossinline transform: suspend T.(U) -> R): RFlow1<T, R> =
        from(this.genMap(transform))

    inline fun onEach(crossinline action: suspend T.(U) -> Unit): RFlow1<T, U> =
        from(this.genOnEach(action))

    inline fun <R> parMap(crossinline transform: suspend T.(U) -> R): RFlow1<T, R> =
        from(this.genParMap(transform))

    inline fun catch(crossinline action: suspend T.(Throwable) -> Unit): RFlow1<T, U> =
        from(this.genCatch(action))

    fun <T2 : Any> require(): RFlow2<T, T2, U> =
        RFlow2(this.rflow.local { rt: Pair<T, T2> -> rt.first })

    fun fulfill(t: T): Flow<U> =
        this.rflow.runReader(t)

    companion object {
        fun <T : Any, U> from(rf: RFlow<T, U>): RFlow1<T, U> =
            RFlow1(rf.rflow.local { it })
    }
}

fun <T : Any, U> RFlow1<T, Flow<U>>.flattenFlow(concurrency: Int = DEFAULT_CONCURRENCY): RFlow1<T, U> =
    RFlow1.from(this.genFlattenFlow(concurrency))

fun <T : Any, U> RFlow1<T, RFlow1<T, U>>.flattenMerge(concurrency: Int = DEFAULT_CONCURRENCY): RFlow1<T, U> =
    RFlow1.from(this.genFlatMap(concurrency) { _, fl -> fl })

class RFlow2<T : Any, T2 : Any, U>(override val rflow: Reader<Pair<T, T2>, Flow<U>>) :
    RFlow<Pair<T, T2>, U>() {

    @OptIn(ExperimentalTypeInference::class)
    inline fun <R> transform(
            @BuilderInference crossinline transform: suspend FlowCollector<R>.(Pair<T, T2>, U) -> Unit
    ): RFlow2<T, T2, R> = fromPairs(this.genTransform(transform))


    inline fun <R> mergeWith(concurrency: Int = DEFAULT_CONCURRENCY,
                             crossinline transform: suspend T.(T2) -> Flow<R>): RFlow2<T, T2, R> =
        fromPairs(this.genMergeWith(concurrency) { (t, t2) -> t.transform(t2) })

    inline fun <R> flatMap(concurrency: Int = DEFAULT_CONCURRENCY,
                           crossinline transform: suspend T.(T2, U) -> RFlow2<T, T2, R>): RFlow2<T, T2, R> =
        fromPairs(this.genFlatMap(concurrency) { (t, t2), u -> t.transform(t2, u) })

    inline fun <R> map(
            crossinline transform: suspend T.(T2, U) -> R
    ): RFlow2<T, T2, R> =
        fromPairs(this.genMap { (t, t2), u -> t.transform(t2, u) })

    inline fun <R> mapFlow(crossinline transform: T.(T2, Flow<U>) -> Flow<R>): RFlow2<T, T2, R> =
        fromPairs(this.genMapFlow { (t, t2), u -> t.transform(t2, u) })

    inline fun onEach(
            crossinline action: suspend T.(T2, U) -> Unit
    ): RFlow2<T, T2, U> =
        fromPairs(this.genOnEach { (t, t2), u -> t.action(t2, u) })

    inline fun <R> parMap(
            crossinline transform: suspend T.(T2, U) -> R
    ): RFlow2<T, T2, R> =
        fromPairs(this.genParMap { (t, t2), u -> t.transform(t2, u) })

    inline fun catch(noinline action: suspend T.(T2, Throwable) -> Unit): RFlow2<T, T2, U> =
        fromPairs(this.genCatch { (t, t2), e -> t.action(t2, e) })

    fun <T3 : Any> require(): RFlow3<T, T2, T3, U> =
        RFlow3(this.rflow.local
        { rt: Triple<T, T2, T3> -> Pair(rt.first, rt.second) })

    fun fulfill(t2: T2): RFlow1<T, U> =
        RFlow1(this.rflow.local { rt: T ->
            Pair(rt, t2)
        })


    companion object {
        fun <T : Any, T2 : Any, U> fromPairs(rf: RFlow<Pair<T, T2>, U>): RFlow2<T, T2, U> =
            RFlow2(rf.rflow.local { it })
    }

}

fun <T : Any, T2 : Any, U> RFlow2<T, T2, Flow<U>>.flattenFlow(concurrency: Int = DEFAULT_CONCURRENCY): RFlow2<T, T2, U> =
    RFlow2.fromPairs(this.genFlattenFlow(concurrency))

fun <T : Any, T2 : Any, U> RFlow2<T, T2, RFlow2<T, T2, U>>.flattenMerge(concurrency: Int = DEFAULT_CONCURRENCY): RFlow2<T, T2, U> =
    RFlow2.fromPairs(this.genFlatMap(concurrency) { _, fl -> fl })

class RFlow3<T : Any, T2 : Any, T3 : Any, U>(override val rflow: Reader<Triple<T, T2, T3>, Flow<U>>) :
    RFlow<Triple<T, T2, T3>, U>() {

    @OptIn(ExperimentalTypeInference::class)
    inline fun <R> transform(
            @BuilderInference crossinline transform: suspend FlowCollector<R>.(Triple<T, T2, T3>, U) -> Unit
    ): RFlow3<T, T2, T3, R> = from(this.genTransform(transform))

    inline fun <R> mergeWith(concurrency: Int = DEFAULT_CONCURRENCY,
                             crossinline transform: suspend T.(Pair<T2, T3>) -> Flow<R>): RFlow3<T, T2, T3, R> =
        from(this.genMergeWith(concurrency) { (t, t2, t3) -> t.transform(Pair(t2, t3)) })

    inline fun <R> flatMap(concurrency: Int = DEFAULT_CONCURRENCY,
                           crossinline transform: suspend T.(Pair<T2, T3>, U) -> RFlow3<T, T2, T3, R>): RFlow3<T, T2, T3, R> =
        from(this.genFlatMap(concurrency) { (t, t2, t3), u -> t.transform(Pair(t2, t3), u) })

    inline fun <R> mapFlow(crossinline transform: T.(Pair<T2, T3>, Flow<U>) -> Flow<R>): RFlow3<T, T2, T3, R> =
        from(this.genMapFlow { (t, t2, t3), u -> t.transform(Pair(t2, t3), u) })

    inline fun <R> map(crossinline transform: suspend T.(Pair<T2, T3>, U) -> R): RFlow3<T, T2, T3, R> =
        from(this.genMap { (t, t2, t3), u -> t.transform(Pair(t2, t3), u) })

    inline fun onEach(crossinline action: suspend T.(Pair<T2, T3>, U) -> Unit): RFlow3<T, T2, T3, U> =
        from(this.genOnEach { (t, t2, t3), u -> t.action(Pair(t2, t3), u) })

    inline fun <R> parMap(crossinline transform: suspend T.(Pair<T2, T3>, U) -> R): RFlow3<T, T2, T3, R> =
        from(this.genParMap { (t, t2, t3), u -> t.transform(Pair(t2, t3), u) })

    inline fun catch(crossinline action: suspend T.(Pair<T2, T3>, Throwable) -> Unit): RFlow3<T, T2, T3, U> =
        from(this.genCatch { (t, t2, t3), e -> t.action(Pair(t2, t3), e) })

    fun fulfill(t3: T3): RFlow2<T, T2, U> =
        RFlow2(this.rflow.local { (t, t2): Pair<T, T2> ->
            Triple(t, t2, t3)
        })


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

fun <T : Any, T2 : Any, T3 : Any, U> RFlow3<T, T2, T3, RFlow3<T, T2, T3, U>>.flattenMerge(
        concurrency: Int = DEFAULT_CONCURRENCY): RFlow3<T, T2, T3, U> =
    RFlow3.from(this.genFlatMap(concurrency) { _, fl -> fl })

class Requirement<A>()

fun <A> require() = Requirement<A>()

fun <U> Flow<U>.asRFlow(): RFlowU<U> =
    RFlowU.from(RFlow.pure(this))

fun <U> rFlowOf(vararg elements: U): RFlowU<U> =
    RFlowU.from(RFlow.pure(elements.asFlow()))

fun <U> rFlowOf(elements: Flow<U>): RFlowU<U> =
    RFlowU.from(RFlow.pure(elements))
