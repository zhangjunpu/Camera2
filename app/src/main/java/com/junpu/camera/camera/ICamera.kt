package com.junpu.camera.camera

import android.graphics.Bitmap
import android.hardware.camera2.params.Face
import android.view.TextureView

/**
 * Camera
 * @author junpu
 * @date 2021/3/2
 */
interface ICamera {
    fun init(faceFront: Boolean, repeatCapture: Boolean, faceDetect: Boolean)
    fun openCamera(previewView: TextureView, displayRotation: Int)
    fun takePhoto()
    fun closeCamera()
    fun doOnCapture(callback: (Bitmap, Int) -> Unit)
    fun doOnFaceDetect(callback: (Array<Face>?) -> Unit)
    fun switchCamera()
}