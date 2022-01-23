import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import rflow.*


typealias Duper<A> = (a: A) -> A
typealias Logger = (a: String) -> Unit

fun main() = runBlocking {
    val intDuper: Duper<Int> = { a -> a + a }
    val strDuper: Duper<String> = { a -> a + a }
    val logger: Logger = { println("Logging -- $it") }

    app().fulfill(logger, intDuper, strDuper)
        .collect()
}

fun app() =
    flowOf(Pair(flowOf(1, 2, 3),
                flowOf("hi", "hello", "hai", "aloha")))
        .requires(Has<Duper<Int>>(), Has<Duper<String>>())
        .transform { (intDuper, stringDuper), (iFl, sFl) ->
            emit(process(iFl).fulfill(intDuper))
            emit(process(sFl).fulfill(stringDuper))
        }.flattenFlow(2)
        .requires(Has<Logger>())
        .map { (logger), p -> logger(p.toString()) }


fun <T> process(f: Flow<T>) =
    f.requires(Has<Duper<T>>())
        .map { duper, i -> duper(i) }
        .onEach { _, _ -> delay(50) }
