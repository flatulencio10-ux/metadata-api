package com.customlauncher

import com.customlauncher.search.Calculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorTest {
    private fun eval(s: String) = Calculator.evaluate(s)

    @Test fun precedence() = assertEquals(8.0, eval("2+2*3")!!, 1e-9)
    @Test fun parentheses() = assertEquals(12.0, eval("(2+2)*3")!!, 1e-9)
    @Test fun powerIsRightAssociative() = assertEquals(512.0, eval("2^3^2")!!, 1e-9)
    @Test fun unaryMinus() = assertEquals(-6.0, eval("-2*3")!!, 1e-9)
    @Test fun functions() = assertEquals(4.0, eval("sqrt(16)")!!, 1e-9)
    @Test fun decimalsAndSpaces() = assertEquals(0.75, eval(" 1.5 / 2 ")!!, 1e-9)
    @Test fun plainNumberIsNotACalculation() = assertNull(eval("42"))
    @Test fun wordsAreNotACalculation() = assertNull(eval("spotify"))
    @Test fun incompleteExpression() = assertNull(eval("2+"))
    @Test fun formatting() {
        assertEquals("8", Calculator.format(8.0))
        assertEquals("0.3333333333", Calculator.format(1.0 / 3))
    }
}
