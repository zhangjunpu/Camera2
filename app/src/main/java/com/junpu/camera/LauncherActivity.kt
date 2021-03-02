package com.junpu.camera

import android.Manifest
import android.os.Bundle
import com.junpu.gopermissions.PermissionsActivity
import com.junpu.log.L
import com.junpu.utils.launch

/**
 * 启动页
 * @author junpu
 * @date 2019-08-19
 */
class LauncherActivity : PermissionsActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permissions =
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA)
        checkPermissions(permissions) {
            L.vv(it)
            if (it) {
                launch(CaptureActivity::class.java)
                finish()
            } else {
                finish()
            }
        }
    }

}