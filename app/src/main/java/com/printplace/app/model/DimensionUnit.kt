package com.printplace.app.model

/**
 * The unit a model's geometry is interpreted in. STL files carry no unit
 * information at all, so an importer has to assume one (millimetres, per the
 * spec) -- this is recorded here rather than baked silently into the
 * millimetre dimensions, so a future "override units" feature can re-derive
 * [ModelDimensions] from the raw geometry without re-parsing the file.
 *
 * MICRON/FOOT exist because 3MF's `unit` attribute legally allows them
 * (alongside millimeter/centimeter/meter/inch) -- unlike STL, 3MF *declares*
 * its unit rather than leaving it assumed, and disclosing that declared unit
 * accurately means representing all six legal values, not just the ones STL
 * happens to need.
 */
enum class DimensionUnit {
    MICRON,
    MILLIMETER,
    CENTIMETER,
    METER,
    INCH,
    FOOT,
    UNKNOWN,
}

/** Multiply a value in this unit by this factor to get millimetres. */
val DimensionUnit.millimetersPerUnit: Double
    get() = when (this) {
        DimensionUnit.MICRON -> 0.001
        DimensionUnit.MILLIMETER -> 1.0
        DimensionUnit.CENTIMETER -> 10.0
        DimensionUnit.METER -> 1000.0
        DimensionUnit.INCH -> 25.4
        DimensionUnit.FOOT -> 304.8
        // Nothing currently produces UNKNOWN, but a future manual unit-override UI could
        // start in this state before the user picks one -- treat as already-millimetres
        // rather than throwing.
        DimensionUnit.UNKNOWN -> 1.0
    }
