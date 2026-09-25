package com.xzo.agent

import com.xzo.agent.util.Calc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalcTest {

    @Test
    fun `basic arithmetic respects precedence`() {
        assertEquals(14.0, Calc.eval("2 + 3 * 4"), 1e-9)
        assertEquals(20.0, Calc.eval("(2 + 3) * 4"), 1e-9)
        assertEquals(-8.0, Calc.eval("-2^3"), 1e-9)
        assertEquals(2.0, Calc.eval("10 % 4 / 1.5 * 1.5"), 1e-9)
    }

    @Test
    fun `functions and constants`() {
        assertEquals(3.0, Calc.eval("sqrt(9)"), 1e-9)
        assertEquals(2.0, Calc.eval("log(100)"), 1e-9)
        assertEquals(120.0, Calc.eval("fact(5)"), 1e-9)
        assertEquals(Math.PI, Calc.eval("pi"), 1e-9)
        assertEquals(6.0, Calc.eval("gcd(54, 24)"), 1e-9)
        assertEquals(3.0, Calc.eval("median(1,3,9)"), 1e-9)
    }

    @Test
    fun `formatting trims noise`() {
        assertEquals("875", Calc.evalToString("0.175*5000"))
        assertEquals("0.5", Calc.evalToString("1/2"))
    }

    @Test
    fun `invalid input fails loudly`() {
        val e = runCatching { Calc.eval("2 +") }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException)
        assertTrue(runCatching { Calc.eval("1/0") }.isFailure)
        assertTrue(runCatching { Calc.eval("nope(2)") }.isFailure)
    }
}
