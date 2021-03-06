package com.danneu.kog.json

import com.danneu.kog.result.Result
import com.eclipsesource.json.Json
import org.junit.Assert.*
import org.junit.Test

// Test many decoders against one json string

private fun <V> String.ok(expected: V, decoder: Decoder<V>) {
    assertEquals(Result.of(expected), decoder(Json.parse(this)))
}

private fun <V> String.ok(message: String, expected: V, decoder: Decoder<V>) {
    assertEquals(message, Result.of(expected), decoder(Json.parse(this)))
}

private fun <V> String.err(decoder: Decoder<V>) {
    assertTrue(decoder(Json.parse(this)) is Result.Failure)
}

private fun <V> String.err(message: String, decoder: Decoder<V>) {
    assertTrue(message, decoder(Json.parse(this)) is Result.Failure)
}

// Test many json strings against one decoder

private fun <V> Decoder<V>.ok(expected: V, json: String) = json.ok(expected, this)
private fun <V> Decoder<V>.ok(message: String, expected: V, json: String) = json.ok(message, expected, this)
private fun <V> Decoder<V>.err(json: String) = json.err(this)
private fun <V> Decoder<V>.err(message: String, json: String) = json.err(message, this)

class DecoderTests {
    @Test
    fun testInt() {
        "42".ok(42, Decoder.int)
        "\"a\"".err(Decoder.int)
    }

    @Test
    fun testString() {
        "\"abc\"".ok("abc", Decoder.string)
        "\"42\"".ok("42", Decoder.string)
        "42".err(Decoder.string)
    }

    @Test
    fun testPair() {
        val decoder = Decoder.pairOf(Decoder.int, Decoder.bool)
        """[42, true]""".ok(42 to true, decoder)
        """[42]""".err(decoder)
        """[42, true, -1]""".err(decoder)
    }

    @Test
    fun testTriple() {
        val decoder = Decoder.triple(Decoder.int, Decoder.bool, Decoder.string)
        """[1, true, "x"]""".ok(Triple(1, true, "x"), decoder)
        """["x", true, 1]""".err(decoder)
        """[true, "x", 1]""".err(decoder)
    }

    @Test
    fun testKeyValuePairs() {
        """{"a": "x", "b": "y"}""".ok(listOf(Pair("a", "x"), Pair("b", "y")), Decoder.keyValuePairs(Decoder.string))
        """{"a": 100, "b": 200}""".ok(listOf(Pair("a", 100), Pair("b", 200)), Decoder.keyValuePairs(Decoder.int))
        """{"a": 100, "b": 200}""".err(Decoder.keyValuePairs(Decoder.string))
    }

    @Test
    fun testKeyValuePairsIteration() {
        // This test exists because I had an impl that used .asSequence() + .firstOrNull() to attempt to short-circuit
        // on first decode failure, but ended up then iterating twice on successful decode, once to check for failure,
        // and then again to return list of pairs.
        var times = 0
        val decoder = Decoder.keyValuePairs(Decoder.int.map { ++times; it })
        decoder(Json.parse("""{"a": 1, "b": 2}"""))
        assertEquals("iterates once", 2, times)
    }

    @Test
    fun testMapOf() {
        """{"a": "x", "b": "y"}""".ok(mapOf("a" to "x", "b" to "y"), Decoder.mapOf(Decoder.string))
        """{"a": 100, "b": 200}""".ok(mapOf("a" to 100, "b" to 200), Decoder.mapOf(Decoder.int))
        """{"a": 100, "b": 200}""".err(Decoder.mapOf(Decoder.string))
    }

    @Test
    fun testMap() {
        """{"a": 1, "b": 2}""".ok(3, Decoder.map(
            Decoder.get("b", Decoder.int),
            { a -> a + 1 }
        ))
    }

    @Test
    fun testMap2() {
        """{"a": 1, "b": 2}""".ok(3, Decoder.map2(
            Decoder.get("a", Decoder.int),
            Decoder.get("b", Decoder.int),
            { a, b -> a + b }
        ))
    }

    @Test
    fun testGet() {
        """{ "a": 42 }""".apply {
            ok(42, Decoder.get("a", Decoder.int))
            ok(42, Decoder.object1({ x -> x }, Decoder.get("a", Decoder.int)))
            err(Decoder.get("a", Decoder.string))
            err(Decoder.get("notfound", Decoder.int))
        }
    }

    @Test
    fun testObject() {
        data class Creds(val uname: String, val password: String)

        val decoder = Decoder.object2(
            ::Creds,
            Decoder.getIn(listOf("user", "uname"), Decoder.string),
            Decoder.get("password", Decoder.string)
        )

        """{ "user": { "uname": "chuck" }, "password": "secret" }""".ok(Creds("chuck", "secret"), decoder)
        """{ "user": { "uname": 42 }, "password": "secret" }""".err(decoder)
    }

    @Test
    fun testGetIn() {
        // Empty path

        "42".ok(42, Decoder.getIn(listOf(), Decoder.int))

        // Deep path

        """{"a":{"b":{"c":42}}}""".apply {
            ok(42, Decoder.getIn(listOf("a", "b", "c"), Decoder.int))
            err("too few keys will not match", Decoder.getIn(listOf("a", "b"), Decoder.int))
            err("too many keys will not match", Decoder.getIn(listOf("a", "b", "c", "d"), Decoder.int))
        }
    }

    @Test
    fun testFlatMap() {
        // the "version" tells us how to decode the "test".
        val decoder = Decoder.get("version", Decoder.int)
            .flatMap { version: Int ->
                when (version) {
                    3 -> Decoder.get("test", Decoder.string.map(String::reversed))
                    4 -> Decoder.get("test", Decoder.string)
                    else -> Decoder.fail("version $version is not supported")
                }
            }

        decoder.apply {
            ok("oof", """{"version": 3, "test": "foo"}""")
            ok("foo", """{"version": 4, "test": "foo"}""")
            err("""{"version": 5, "test": "foo"}""")
        }
    }

    @Test
    fun testOneOf() {
        Decoder.oneOf(
            Decoder.string.map { s -> s + "x" },
            Decoder.int.map { n -> n + 1 }
        ).apply {
            ok("foox", json = "\"foo\"")
            ok(3, json = "2")
            err(json = "true")
        }
    }

    @Test
    fun testNull() {
        "null".ok(42, Decoder.`null`(42))
        "42".err(Decoder.`null`(42))
    }

    @Test
    fun testNullable() {
        "42".ok(42, Decoder.nullable(Decoder.int))
        "null".ok(null, Decoder.nullable(Decoder.int))
    }

    // LISTS & ARRAYS

    @Test
    fun testListOfInt() {
        val decoder = Decoder.listOf(Decoder.int)
        """[1, 2, 3, 4]""".ok(listOf(1, 2, 3, 4), decoder)
        """[1, "b", 3, 4]""".err(decoder)
    }

    @Test
    fun testArrayOfInt() {
        """[1, 2, 3]""".ok(listOf(1, 2, 3), Decoder.arrayOf(Decoder.int).map { it.toList() })
        """[1, "b", 3]""".err(Decoder.arrayOf(Decoder.int))
    }

    @Test
    fun testListOfString() {
        val decoder = Decoder.listOf(Decoder.string)
        """["a", "b", "c"]""".ok(listOf("a", "b", "c"), decoder)
        """[1, "b", "c"]""".err(decoder)
    }

    @Test
    fun testIndex() {
        """["a", "b", "c"]""".apply {
            err(Decoder.index(-1, Decoder.string))
            ok("a", Decoder.index(0, Decoder.string))
            ok("b", Decoder.index(1, Decoder.string))
            ok("c", Decoder.index(2, Decoder.string))
            err(Decoder.index(3, Decoder.string))
        }
    }
}


