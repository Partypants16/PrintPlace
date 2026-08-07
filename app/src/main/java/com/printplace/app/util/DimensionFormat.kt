package com.printplace.app.util

import com.printplace.app.model.ModelDimensions

/** Renders as e.g. "82 x 96 x 121 mm", matching the library card mockup. */
fun ModelDimensions.formatWidthHeightDepth(): String {
    fun fmt(mm: Double) = if (mm == mm.toLong().toDouble()) mm.toLong().toString() else "%.1f".format(mm)
    return "${fmt(widthMm)} x ${fmt(heightMm)} x ${fmt(depthMm)} mm"
}
