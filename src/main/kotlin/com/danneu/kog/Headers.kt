package com.danneu.kog

typealias Header = Pair<String, String>

// FIXME: Not sure how to create an interface with default implementations that always returning the implementing self.
// For now I use these unchecked `this as T` returns.
interface HasHeaders <out T> {
    val headers: MutableList<Header>

    fun getHeader(key: String): String? {
        return headers.find { it.first.toLowerCase() == key.toLowerCase() }?.second
    }

    // if value is null, then the header does not get set
    fun setHeader(key: String, value: String?): T {
        if (value == null) return this as T
        removeHeader(key)
        appendHeader(key, value)
        return this as T
    }

    // if value is null, then the header does not get set
    fun appendHeader(key: String, value: String?): T {
        if (value == null) return this as T
        headers.add(Pair(key, value))
        return this as T
    }

    fun removeHeader(key: String): T {
        headers.removeIf { it.first.toLowerCase() == key.toLowerCase() }
        return this as T
    }
}
