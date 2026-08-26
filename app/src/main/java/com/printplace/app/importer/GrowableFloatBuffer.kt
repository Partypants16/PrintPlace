package com.printplace.app.importer

/**
 * Minimal growable primitive-float buffer, doubling capacity as needed.
 *
 * Exists because ASCII STL (and potentially other text-based formats) don't
 * declare their triangle count up front, so the parser can't pre-size a
 * FloatArray. A `MutableList<Float>` would work too but boxes every element,
 * which matters once a model has millions of floats (section 19: parsing
 * must not blow up memory on large print models).
 */
internal class GrowableFloatBuffer(initialCapacity: Int = 1024) {
    private var array = FloatArray(initialCapacity)
    var size: Int = 0
        private set

    fun add(value: Float) {
        if (size == array.size) {
            array = array.copyOf(array.size * 2)
        }
        array[size] = value
        size++
    }

    fun toFloatArray(): FloatArray = array.copyOf(size)
}
