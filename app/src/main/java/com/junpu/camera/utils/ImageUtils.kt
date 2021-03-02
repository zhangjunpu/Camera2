@file:JvmName("ImageUtils")

package com.junpu.camera.utils

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import java.lang.IllegalArgumentException
import kotlin.math.min

fun adjustIntoFrame(bitmap: Bitmap, orientation: Int, aspectRatio: Size, frameWidth: Int, frameHeight: Int): Bitmap {
    if (orientation % 90 != 0 || frameWidth <= 0 || frameHeight <= 0 || bitmap.isRecycled) throw IllegalArgumentException()

    val width0 = bitmap.width
    val height0 = bitmap.height

    val swap = orientation / 90 and 1 == 1

    val ratio = if (swap) Size(aspectRatio.height, aspectRatio.width) else aspectRatio

    val width1 = min(width0, height0 * ratio.width / ratio.height)
    val height1 = min(height0, width0 * ratio.height / ratio.width)

    val width2 = if (swap) height1 else width1
    val height2 = if (swap) width1 else height1

    val s = min(frameWidth / width2.toFloat(), frameHeight / height2.toFloat())

    val m = Matrix()
    m.postRotate(orientation.toFloat())
    m.postScale(s, s)

    return Bitmap.createBitmap(bitmap, (width0 - width1) / 2, (height0 - height1) / 2, width1, height1, m, true)
}
