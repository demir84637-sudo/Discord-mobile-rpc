package com.example.gamerpc

import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var switchRpc: Switch
    private lateinit var btnGrantAccess: Button
    private lateinit var tvStatus: TextView

    private val prefs by lazy { getSharedPreferences("gamerpc_prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        switchRpc = findViewById(R.id.switchRpc)
        btnGrantAccess = findViewById(R.id.btnGrantAccess)
        tvStatus = findViewById(R.id.tvStatus)

        switchRpc.isChecked = prefs.getBoolean("rpc_enabled", false)
        updateStatusText()

        btnGrantAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        switchRpc.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !hasUsageAccess()) {
                Toast.makeText(this, "Önce Kullanım Erişimi izni ver", Toast.LENGTH_LONG).show()
                switchRpc.isChecked = false
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                return@setOnCheckedChangeListener
            }

            prefs.edit().putBoolean("rpc_enabled", isChecked).apply()

            if (isChecked) {
                requestNotificationPermissionIfNeeded()
                val svcIntent = Intent(this, GameDetectionService::class.java)
                ContextCompat.startForegroundService(this, svcIntent)
            } else {
                stopService(Intent(this, GameDetectionService::class.java))
            }
            updateStatusText()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatusText()
    }

    private fun updateStatusText() {
        val access = if (hasUsageAccess()) "✅ Kullanım erişimi verildi" else "❌ Kullanım erişimi gerekli"
        val rpcState = if (switchRpc.isChecked) "RPC aktif — oyun algılanınca bildirim gelecek" else "RPC kapalı"
        tvStatus.text = "$access\n$rpcState"
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }
}
