package com.printplace.app.model

/**
 * The unit a model's geometry is interpreted in. STL files carry no unit
 * information at all, so an importer has to assume one (millimetres, per the
 * spec) -- this is recorded here rather than baked silently into the
 * millimetre dimensions, so a future "override units" feature can re-derive
 * [ModelDimensions] from the raw geometry without re-parsing the file.
 */
enum class DimensionUnit {
    MILLIMETER,
    CENTIMETER,
    METER,
    INCH,
    UNKNOWN,
}
