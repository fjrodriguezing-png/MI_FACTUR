package com.francisco.mifactur
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
class RoundingTest {
 @Test fun halfCent(){ assertEquals(BigDecimal("0.01"), BigDecimal("0.005").setScale(2,RoundingMode.HALF_UP)) }
 @Test fun invoice(){ val b=BigDecimal("100.00"); val iva=b.multiply(BigDecimal("21")).divide(BigDecimal("100"),2,RoundingMode.HALF_UP); val irpf=b.multiply(BigDecimal("15")).divide(BigDecimal("100"),2,RoundingMode.HALF_UP); assertEquals(BigDecimal("106.00"),b.add(iva).subtract(irpf)) }
}
