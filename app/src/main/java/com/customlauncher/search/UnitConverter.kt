package com.customlauncher.search

/**
 * Parses queries like "10 km to mi", "5kg in lb", "100 f to c", "1.5 gb to mb"
 * and converts between units of the same dimension. Framework-free and unit tested.
 */
object UnitConverter {

    data class Result(val value: Double, val from: String, val to: String, val converted: Double)

    private enum class Dim { LENGTH, MASS, VOLUME, SPEED, DATA, TIME, TEMPERATURE, AREA }

    /** factor = how many base units one unit is. Base: m, kg, l, m/s, byte, s, m². */
    private data class UnitDef(val dim: Dim, val factor: Double, val label: String)

    private val units: Map<String, UnitDef> = buildMap {
        fun add(dim: Dim, factor: Double, label: String, vararg aliases: String) {
            aliases.forEach { put(it, UnitDef(dim, factor, label)) }
        }
        add(Dim.LENGTH, 1e-3, "mm", "mm", "millimeter", "millimeters", "millimetre", "millimetres")
        add(Dim.LENGTH, 1e-2, "cm", "cm", "centimeter", "centimeters", "centimetre", "centimetres")
        add(Dim.LENGTH, 1.0, "m", "m", "meter", "meters", "metre", "metres")
        add(Dim.LENGTH, 1000.0, "km", "km", "kilometer", "kilometers", "kilometre", "kilometres")
        add(Dim.LENGTH, 0.0254, "in", "in", "inch", "inches")
        add(Dim.LENGTH, 0.3048, "ft", "ft", "foot", "feet")
        add(Dim.LENGTH, 0.9144, "yd", "yd", "yard", "yards")
        add(Dim.LENGTH, 1609.344, "mi", "mi", "mile", "miles")
        add(Dim.LENGTH, 1852.0, "nmi", "nmi", "nauticalmile", "nauticalmiles")

        add(Dim.MASS, 1e-6, "mg", "mg", "milligram", "milligrams")
        add(Dim.MASS, 1e-3, "g", "g", "gram", "grams")
        add(Dim.MASS, 1.0, "kg", "kg", "kilogram", "kilograms", "kilo", "kilos")
        add(Dim.MASS, 1000.0, "t", "t", "tonne", "tonnes", "ton", "tons")
        add(Dim.MASS, 0.028349523125, "oz", "oz", "ounce", "ounces")
        add(Dim.MASS, 0.45359237, "lb", "lb", "lbs", "pound", "pounds")
        add(Dim.MASS, 6.35029318, "st", "st", "stone", "stones")

        add(Dim.VOLUME, 1e-3, "ml", "ml", "milliliter", "milliliters", "millilitre", "millilitres")
        add(Dim.VOLUME, 1.0, "l", "l", "liter", "liters", "litre", "litres")
        add(Dim.VOLUME, 3.785411784, "gal", "gal", "gallon", "gallons")
        add(Dim.VOLUME, 0.946352946, "qt", "qt", "quart", "quarts")
        add(Dim.VOLUME, 0.473176473, "pt", "pt", "pint", "pints")
        add(Dim.VOLUME, 0.2365882365, "cup", "cup", "cups")
        add(Dim.VOLUME, 0.0295735295625, "floz", "floz", "fl.oz")

        add(Dim.SPEED, 1.0, "m/s", "m/s", "mps")
        add(Dim.SPEED, 1 / 3.6, "km/h", "km/h", "kmh", "kph")
        add(Dim.SPEED, 0.44704, "mph", "mph")
        add(Dim.SPEED, 0.514444, "kn", "kn", "knot", "knots")

        add(Dim.DATA, 1.0, "B", "b", "byte", "bytes")
        add(Dim.DATA, 1e3, "KB", "kb", "kilobyte", "kilobytes")
        add(Dim.DATA, 1e6, "MB", "mb", "megabyte", "megabytes")
        add(Dim.DATA, 1e9, "GB", "gb", "gigabyte", "gigabytes")
        add(Dim.DATA, 1e12, "TB", "tb", "terabyte", "terabytes")
        add(Dim.DATA, 1024.0, "KiB", "kib")
        add(Dim.DATA, 1024.0 * 1024, "MiB", "mib")
        add(Dim.DATA, 1024.0 * 1024 * 1024, "GiB", "gib")

        add(Dim.TIME, 1.0, "s", "s", "sec", "secs", "second", "seconds")
        add(Dim.TIME, 60.0, "min", "min", "mins", "minute", "minutes")
        add(Dim.TIME, 3600.0, "h", "h", "hr", "hrs", "hour", "hours")
        add(Dim.TIME, 86400.0, "d", "d", "day", "days")
        add(Dim.TIME, 604800.0, "wk", "wk", "week", "weeks")

        add(Dim.AREA, 1.0, "m²", "m2", "sqm")
        add(Dim.AREA, 1e6, "km²", "km2", "sqkm")
        add(Dim.AREA, 0.09290304, "ft²", "ft2", "sqft")
        add(Dim.AREA, 4046.8564224, "acre", "acre", "acres")
        add(Dim.AREA, 10000.0, "ha", "ha", "hectare", "hectares")

        // Temperature uses offsets; factor is unused.
        add(Dim.TEMPERATURE, 0.0, "°C", "c", "°c", "celsius")
        add(Dim.TEMPERATURE, 0.0, "°F", "f", "°f", "fahrenheit")
        add(Dim.TEMPERATURE, 0.0, "K", "k", "kelvin")
    }

    private val pattern = Regex("""^\s*(-?[\d.,]+)\s*([a-z°/.²0-9]+?)\s+(?:to|in|as|->|=)\s+([a-z°/.²0-9]+)\s*$""", RegexOption.IGNORE_CASE)

    fun convert(query: String): Result? {
        val m = pattern.find(query.trim()) ?: return null
        val value = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val from = units[m.groupValues[2].lowercase()] ?: return null
        val to = units[m.groupValues[3].lowercase()] ?: return null
        if (from.dim != to.dim) return null
        val converted = if (from.dim == Dim.TEMPERATURE) {
            fromKelvin(toKelvin(value, from.label), to.label)
        } else {
            value * from.factor / to.factor
        }
        return Result(value, from.label, to.label, converted)
    }

    private fun toKelvin(v: Double, unit: String) = when (unit) {
        "°C" -> v + 273.15
        "°F" -> (v - 32) * 5 / 9 + 273.15
        else -> v
    }

    private fun fromKelvin(k: Double, unit: String) = when (unit) {
        "°C" -> k - 273.15
        "°F" -> (k - 273.15) * 9 / 5 + 32
        else -> k
    }
}
