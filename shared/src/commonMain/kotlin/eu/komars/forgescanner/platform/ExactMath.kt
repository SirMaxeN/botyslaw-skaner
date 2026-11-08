package eu.komars.forgescanner.platform

/** Same contract as java.lang.Math.addExact(long, long): throws ArithmeticException on overflow. */
fun addExact(x: Long, y: Long): Long {
    val r = x + y
    if (((x xor r) and (y xor r)) < 0) throw ArithmeticException("long overflow")
    return r
}

/** Same contract (and algorithm) as java.lang.Math.multiplyExact(long, long). */
fun multiplyExact(x: Long, y: Long): Long {
    val r = x * y
    val ax = kotlin.math.abs(x)
    val ay = kotlin.math.abs(y)
    if ((ax or ay) ushr 31 != 0L) {
        if ((y != 0L && r / y != x) || (x == Long.MIN_VALUE && y == -1L)) throw ArithmeticException("long overflow")
    }
    return r
}
