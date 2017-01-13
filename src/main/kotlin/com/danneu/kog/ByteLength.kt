package com.danneu.kog


/** Represents a Long quantity of bytes.
 *
 * Improves readability and avoids errors involved with manually converting between orders of magnitude.
 */
class ByteLength(val byteLength: Long) {
    companion object {
        fun ofBytes(n: Long) = ByteLength(n)
        fun ofKilobytes(n: Long) = ByteLength(n * 1000)
        fun ofKilobytes(n: Double) = ByteLength((n * 1000).toLong())
        fun ofMegabytes(n: Long) = ByteLength(n * 1000 * 1000)
        fun ofMegabytes(n: Double) = ByteLength((n * 1000 * 1000).toLong())
        fun ofGigabytes(n: Long) = ByteLength(n * 1000 * 1000 * 1000)
        fun ofGigabytes(n: Double) = ByteLength((n * 1000 * 1000 * 1000).toLong())
    }
}


