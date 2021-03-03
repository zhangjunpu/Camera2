package com.junpu.camera.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.media.Image
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import kotlin.math.max
import kotlin.math.min

/**
 *
 * @author junpu
 * @date 2021/3/2
 */

// Camera2API保证的最大尺寸
private const val MAX_PREVIEW_WIDTH = 1920
private const val MAX_PREVIEW_HEIGHT = 1200

private val ORIENTATIONS = SparseIntArray(4).apply {
    append(Surface.ROTATION_0, 90)
    append(Surface.ROTATION_90, 0)
    append(Surface.ROTATION_180, 270)
    append(Surface.ROTATION_270, 180)
}

/**
 * 获取拍照尺寸
 */
fun getCaptureSize(sizes: Array<Size>): Size? {
    val list = sizes.filter { it.width == it.height * 4 / 3 }
    return list.maxByOrNull { it.height * it.width }
}

/**
 * 获取预览尺寸；
 * 必须判断最大尺寸，否则部分机型拍照方向会出问题
 */
fun getPreviewSize(
    sizes: Array<Size>,
    captureSize: Size,
    width: Int,
    height: Int,
    displayRotation: Int,
    sensorOrientation: Int,
): Size {
    // 90，270
    val swap = (displayRotation + sensorOrientation) / 90 and 1 == 1
    val w = if (swap) height else width
    val h = if (swap) width else height
    val mw = min(w, MAX_PREVIEW_WIDTH)
    val mh = min(h, MAX_PREVIEW_HEIGHT)
    val cw = captureSize.width
    val ch = captureSize.height

    val bigList = ArrayList<Size>()
    val smallList = ArrayList<Size>()

    for (size in sizes) {
        val sw = size.width
        val sh = size.height
        // 选出比例一样的尺寸
        if (sw <= mw && sh <= mh && sh == sw * ch / cw) {
            // 对尺寸列表分类：大于目标尺寸的、小于目标尺寸的
            if (sw >= w && sh >= h) {
                bigList.add(size)
            } else {
                smallList.add(size)
            }
        }
    }

    // 优先选择大堆里最小的尺寸，其次是小堆里最大的尺寸
    return bigList.minByOrNull { it.width * it.height }
        ?: smallList.maxByOrNull { it.width * it.height } ?: sizes[0]
}

/**
 * 配置Matrix到TextureView；
 * 这个方法应该在setUpCameraOutputs中确定相机预览大小后调用，同时“textureView”的大小也固定了。
 */
fun TextureView.configureTransform(
    viewWidth: Int,
    viewHeight: Int,
    previewSize: Size,
    displayRotation: Int
) {
    val matrix = Matrix()
    val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
    val bufferRect = RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
    val centerX = viewRect.centerX()
    val centerY = viewRect.centerY()

    if (Surface.ROTATION_90 == displayRotation || Surface.ROTATION_270 == displayRotation) {
        bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
        val scale = max(
            viewHeight.toFloat() / previewSize.height,
            viewWidth.toFloat() / previewSize.width
        )
        with(matrix) {
            setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            postScale(scale, scale, centerX, centerY)
            postRotate((90 * (displayRotation - 2)).toFloat(), centerX, centerY)
        }
    } else if (Surface.ROTATION_180 == displayRotation) {
        matrix.postRotate(180f, centerX, centerY)
    }
    setTransform(matrix)
}

/**
 * 根据View显示方向和摄像头方向，获取JPEG图片方向
 */
fun getOrientation(displayRotation: Int, sensorOrientation: Int): Int {
    return (ORIENTATIONS[displayRotation] + sensorOrientation + 270) % 360
}

/**
 * Image to Bitmap
 */
fun Image.toBitmap(): Bitmap {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    close()
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

val curThreadName: String
    get() = Thread.currentThread().name