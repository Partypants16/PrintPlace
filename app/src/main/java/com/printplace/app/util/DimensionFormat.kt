package com.printplace.app.util

import com.printplace.app.model.ModelDimensions

/** Renders as e.g. "142 x 76 x 48 mm" (width x depth x height), matching the spec's dimension ordering. */
fun ModelDimensions.formatWidthDepthHeight(): String {
    fun fmt(mm: Double) = if (mm == mm.toLong().toDouble()) mm.toLong().toString() else "%.1f".format(mm)
    return "${fmt(widthMm)} x ${fmt(depthMm)} x ${fmt(heightMm)} mm"
}
