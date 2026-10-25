package com.customlauncher

import com.customlauncher.search.UnitConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitConverterTest {
    @Test fun kmToMiles() = assertEquals(6.2137, UnitConverter.convert("10 km to mi")!!.converted, 1e-3)
    @Test fun kgInPounds() = assertEquals(11.023, UnitConverter.convert("5kg in lb")!!.converted, 1e-3)
    @Test fun fahrenheitToCelsius() = assertEquals(37.778, UnitConverter.convert("100 f to c")!!.converted, 1e-3)
    @Test fun celsiusToKelvin() = assertEquals(273.15, UnitConverter.convert("0 c to k")!!.converted, 1e-9)
    @Test fun data() = assertEquals(1500.0, UnitConverter.convert("1.5 gb to mb")!!.converted, 1e-9)
    @Test fun speed() = assertEquals(62.137, UnitConverter.convert("100 km/h to mph")!!.converted, 1e-3)
    @Test fun incompatibleDimensions() = assertNull(UnitConverter.convert("10 km to kg"))
    @Test fun notAConversion() = assertNull(UnitConverter.convert("weather tomorrow"))
}
