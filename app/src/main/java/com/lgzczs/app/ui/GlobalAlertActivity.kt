package com.lgzczs.app.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import com.lgzczs.app.MainActivity
import com.lgzczs.app.util.PermissionHelper

class GlobalAlertActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val platform = intent.getStringExtra("platform") ?: "未知平台"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先开启悬浮窗权限", Toast.LENGTH_LONG).show()
            PermissionHelper.openOverlaySettings(this)
            finish()
            return
        }

        val builder = AlertDialog.Builder(this)
        builder.setTitle("订单提醒")
        builder.setMessage("$platform 有新的待处理订单")
        builder.setPositiveButton("查看") { _, _ ->
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
            finish()
        }
        builder.setNegativeButton("关闭") { _, _ ->
            finish()
        }
        builder.setCancelable(false)

        val dialog = builder.create()
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
    }
}
