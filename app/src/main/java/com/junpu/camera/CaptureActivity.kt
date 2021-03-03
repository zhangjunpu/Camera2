/*
 * Copyright 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.junpu.camera

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.junpu.camera.camera.Camera2Manager
import com.junpu.camera.camera.ICamera
import com.junpu.camera.utils.curThreadName
import com.junpu.log.L
import com.junpu.utils.setVisibility
import kotlinx.android.synthetic.main.activity_capture.*

class CaptureActivity : AppCompatActivity() {

    private var camera: ICamera? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_capture)

        camera = Camera2Manager(this).apply {
            init(true, repeatCapture = true, faceDetect = true)
            doOnCapture { bitmap, orientation ->
                L.vv("capture: ${bitmap.width}/${bitmap.height}, $orientation")
                imageCapture.setVisibility(true)
                imageCapture.setImageBitmap(bitmap)
            }
            doOnFaceDetect {
                if (!it.isNullOrEmpty()) L.vv("检测到张人脸数量：${it.size}, $curThreadName")
            }
        }

        btnRecapture.setOnClickListener {
            if (imageCapture.isVisible) imageCapture.setVisibility(false)
        }
        btnCapture.setOnClickListener {
            camera?.takePhoto()
        }
        btnSwitch.setOnClickListener {
            camera?.switchCamera()
        }
    }

    override fun onResume() {
        super.onResume()
        val rotation = windowManager.defaultDisplay.rotation
        camera?.openCamera(textureView, rotation)
    }

    override fun onPause() {
        super.onPause()
        camera?.closeCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        camera = null
    }

}
