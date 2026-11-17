package com.francisco.mifactur
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
class RoundingTest { @Test fun halfCent(){ assertEquals(BigDecimal("0.01"),BigDecimal("0.005").setScale(2,RoundingMode.HALF_UP)) } }
