package com.devbehindyou.refract.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FileResultTest {

    private val success: FileResult<Int> = FileResult.Success(42)
    private val failure: FileResult<Int> = FileResult.Failure(FileError.FileNotFound("thing.txt"))

    @Test
    fun `fold calls onSuccess for a Success`() {
        val result = success.fold(onSuccess = { it * 2 }, onFailure = { -1 })

        assertEquals(84, result)
    }

    @Test
    fun `fold calls onFailure for a Failure`() {
        val result = failure.fold(onSuccess = { it * 2 }, onFailure = { -1 })

        assertEquals(-1, result)
    }

    @Test
    fun `map transforms a Success value and leaves a Failure untouched`() {
        val mappedSuccess = success.map { it.toString() }
        val mappedFailure = failure.map { it.toString() }

        assertEquals(FileResult.Success("42"), mappedSuccess)
        assertEquals(failure, mappedFailure)
    }

    @Test
    fun `getOrNull returns the value or null`() {
        assertEquals(42, success.getOrNull())
        assertNull(failure.getOrNull())
    }

    @Test
    fun `getOrElse returns the value or the fallback`() {
        assertEquals(42, success.getOrElse { -1 })
        assertEquals(-1, failure.getOrElse { -1 })
    }

    @Test
    fun `getOrReturn's non-local return actually exits the enclosing function on Failure`() {
        fun tryIt(input: FileResult<Int>): Int {
            val value = input.getOrReturn { return -99 }
            return value * 10
        }

        assertEquals(420, tryIt(success))
        assertEquals(-99, tryIt(failure))
    }
}
