package com.helmet.mobileadmin

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.*

class DeviceDetailActivity : AppCompatActivity() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var deviceId: String
    private lateinit var container: FrameLayout
    private var detailData: Map<String, Any?>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deviceId = intent.getStringExtra("deviceId") ?: ""
        val name = intent.getStringExtra("deviceName") ?: deviceId
        title = name

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val tabs = TabLayout(this).apply {
            addTab(newTab().setText("信息"))
            addTab(newTab().setText("回放"))
            addTab(newTab().setText("截图"))
            addTab(newTab().setText("通话"))
            setBackgroundColor(0xFF1B2A4A.toInt())
            setTabTextColors(0xFF8896A6.toInt(), 0xFFFFFFFF.toInt())
            setSelectedTabIndicatorColor(0xFF5b9cf5.toInt())
        }
        container = FrameLayout(this).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f) }
        root.addView(tabs)
        root.addView(container)
        setContentView(root)

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { showTab(tab.position) }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        scope.launch {
            val r = ApiService.deviceDetail(deviceId)
            if (r.code == 0) {
                detailData = r.data
                showTab(0)
            } else { finish() }
        }
    }

    private fun showTab(pos: Int) {
        container.removeAllViews()
        when (pos) {
            0 -> showInfo()
            1 -> showRecordings()
            2 -> showScreenshots()
            3 -> showCallRecords()
        }
    }

    private fun showInfo() {
        val d = detailData ?: return
        val sv = ScrollView(this)
        val ll = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16) }

        ll.addView(section("基本信息"))
        ll.addView(row("设备ID", d["deviceId"]))
        ll.addView(row("人员", d["personnelName"]))
        ll.addView(row("手机", d["phone"]))
        ll.addView(row("类型", d["deviceType"]))
        ll.addView(row("分组", d["groupName"] ?: "未分组"))
        val online = (d["online"] as? Number)?.toInt() == 1
        val streaming = (d["streaming"] as? Number)?.toInt() == 1
        ll.addView(row("状态", if (streaming) "推流中" else if (online) "在线" else "离线"))

        // 生命体征
        @Suppress("UNCHECKED_CAST")
        val vital = d["vitalSigns"] as? Map<String, Any?>
        if (vital != null) {
            ll.addView(section("生命体征"))
            ll.addView(row("心率", "${(vital["heartRate"] as? Number)?.toInt() ?: "-"} bpm"))
            ll.addView(row("体温", "${vital["bodyTemp"] ?: "-"}°C"))
            ll.addView(row("血氧", "${(vital["bloodOxygen"] as? Number)?.toInt() ?: "-"}%"))
        }

        // 气体数据
        @Suppress("UNCHECKED_CAST")
        val gas = d["gasData"] as? Map<String, Any?>
        if (gas != null) {
            ll.addView(section("气体数据"))
            ll.addView(row("CO", "${gas["co"] ?: "-"} ppm"))
            ll.addView(row("H2S", "${gas["h2s"] ?: "-"} ppm"))
            ll.addView(row("O2", "${gas["o2"] ?: "-"}%"))
        }

        // 账户绑定
        ll.addView(section("账户绑定"))
        @Suppress("UNCHECKED_CAST")
        val user = d["assignedUser"] as? Map<String, Any?>
        if (user != null) {
            ll.addView(row("用户", user["username"]))
            val level = (user["level"] as? Number)?.toInt() ?: 2
            ll.addView(row("角色", mapOf(0 to "超管", 1 to "管理员", 2 to "用户").getOrDefault(level, "?")))
            val unbind = Button(this).apply {
                text = "解绑"; setBackgroundColor(0xFFC62828.toInt()); setTextColor(0xFFFFFFFF.toInt())
                setOnClickListener {
                    AlertDialog.Builder(this@DeviceDetailActivity).setTitle("确认解绑")
                        .setPositiveButton("确认") { _, _ ->
                            scope.launch {
                                val r = ApiService.deviceUnassign(deviceId)
                                if (r.code == 0) { detailData = (ApiService.deviceDetail(deviceId)).data; showInfo() }
                            }
                        }.setNegativeButton("取消", null).show()
                }
            }
            ll.addView(unbind)
        } else {
            val bind = Button(this).apply {
                text = "分配给用户"; setBackgroundColor(0xFF5b9cf5.toInt()); setTextColor(0xFFFFFFFF.toInt())
                setOnClickListener { showAssignDialog() }
            }
            ll.addView(bind)
        }

        sv.addView(ll)
        container.addView(sv)
    }

    private fun showRecordings() {
        val sv = ScrollView(this)
        val ll = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16) }
        ll.addView(section("录制文件"))
        ll.addView(TextView(this).apply { text = "加载中..."; textSize = 12f; setTextColor(0xFF8896A6.toInt()); tag = "loading" })
        sv.addView(ll)
        container.addView(sv)

        scope.launch {
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val r = ApiService.recordings(deviceId, today)
            @Suppress("UNCHECKED_CAST")
            val files = if (r.code == 0) (r.data?.get("files") as? List<String>) ?: emptyList() else emptyList()
            ll.removeAllViews()
            ll.addView(section("录制文件 (${files.size})"))
            if (files.isEmpty()) {
                ll.addView(TextView(this@DeviceDetailActivity).apply { text = "今日暂无录制"; textSize = 12f; setTextColor(0xFF8896A6.toInt()) })
            } else {
                for (f in files.take(30)) {
                    val btn = TextView(this@DeviceDetailActivity).apply {
                        text = f; textSize = 12f; setPadding(8, 6, 8, 6)
                        setTextColor(0xFF5b9cf5.toInt())
                        setOnClickListener {
                            scope.launch {
                                val pr = ApiService.playbackUrl(deviceId, today, f)
                                if (pr.code == 0) {
                                    val url = pr.data?.get("url") as? String ?: ""
                                    if (url.isNotEmpty()) {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("${ApiService.serverBase}$url"))
                                        startActivity(intent)
                                    }
                                }
                            }
                        }
                    }
                    ll.addView(btn)
                }
            }
        }
    }

    private fun showScreenshots() {
        val sv = ScrollView(this)
        val ll = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16) }
        ll.addView(section("截图"))
        ll.addView(TextView(this).apply { text = "加载中..."; textSize = 12f; setTextColor(0xFF8896A6.toInt()) })
        sv.addView(ll)
        container.addView(sv)

        scope.launch {
            val r = ApiService.snapshots(deviceId)
            @Suppress("UNCHECKED_CAST")
            val snaps = if (r.code == 0) (r.data as? List<Map<String, Any?>>) ?: emptyList() else emptyList()
            ll.removeAllViews()
            ll.addView(section("截图 (${snaps.size})"))
            if (snaps.isEmpty()) {
                ll.addView(TextView(this@DeviceDetailActivity).apply { text = "暂无截图"; textSize = 12f; setTextColor(0xFF8896A6.toInt()) })
            } else {
                for (s in snaps.take(50)) {
                    val row = LinearLayout(this@DeviceDetailActivity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(4, 4, 4, 4) }
                    row.addView(TextView(this@DeviceDetailActivity).apply {
                        text = (s["createdAt"] as? String)?.take(16)?.replace("T", " ") ?: s["filename"] as? String ?: "?"
                        textSize = 11f; setTextColor(0xFF8896A6.toInt())
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    row.addView(TextView(this@DeviceDetailActivity).apply {
                        text = "查看"; textSize = 11f; setTextColor(0xFF5b9cf5.toInt()); setPadding(8, 0, 0, 0)
                        setOnClickListener {
                            val filename = s["filename"] as? String ?: return@setOnClickListener
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("${ApiService.serverBase}/api/screenshots/$deviceId/$filename"))
                            startActivity(intent)
                        }
                    })
                    ll.addView(row)
                }
            }
        }
    }

    private fun showCallRecords() {
        val sv = ScrollView(this)
        val ll = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16) }
        ll.addView(section("通话记录"))
        ll.addView(TextView(this).apply { text = "加载中..."; textSize = 12f; setTextColor(0xFF8896A6.toInt()) })
        sv.addView(ll)
        container.addView(sv)

        scope.launch {
            val r = ApiService.callRecords(deviceId)
            @Suppress("UNCHECKED_CAST")
            val calls = if (r.code == 0) (r.data as? List<Map<String, Any?>>) ?: emptyList() else emptyList()
            ll.removeAllViews()
            ll.addView(section("通话记录 (${calls.size})"))
            if (calls.isEmpty()) {
                ll.addView(TextView(this@DeviceDetailActivity).apply { text = "无通话记录"; textSize = 12f; setTextColor(0xFF8896A6.toInt()) })
            } else {
                for (c in calls) {
                    val status = c["status"] as? String ?: ""
                    val statusLabel = mapOf("ended" to "已挂", "missed" to "未接", "answered" to "已接", "ringing" to "振铃")
                    val type = if ((c["callType"] as? String) == "video") "📹" else "📞"
                    val start = (c["startTime"] as? String)?.take(16)?.replace("T", " ") ?: ""
                    val caller = c["callerName"] ?: "?"
                    ll.addView(TextView(this@DeviceDetailActivity).apply {
                        text = "$type $caller · ${statusLabel[status] ?: status}"
                        textSize = 12f; setTextColor(0xFF8896A6.toInt()); setPadding(4, 4, 4, 4)
                    })
                    ll.addView(TextView(this@DeviceDetailActivity).apply {
                        text = "  $start"; textSize = 10f; setTextColor(0xFF5f6b7a.toInt()); setPadding(4, 0, 4, 8)
                    })
                }
            }
        }
    }

    private fun showAssignDialog() {
        scope.launch {
            val r = ApiService.assignableUsers()
            if (r.code != 0) return@launch
            @Suppress("UNCHECKED_CAST")
            val users = r.data as? List<Map<String, Any?>> ?: return@launch
            val names = users.map { "${it["username"]} (${mapOf(0 to "超管", 1 to "管理员", 2 to "用户").getOrDefault((it["level"] as? Number)?.toInt(), "?")})" }.toTypedArray()
            AlertDialog.Builder(this@DeviceDetailActivity).setTitle("选择用户")
                .setItems(names) { _, i ->
                    scope.launch {
                        val rr = ApiService.deviceAssign(deviceId, users[i]["id"] as? String ?: "")
                        if (rr.code == 0) {
                            detailData = (ApiService.deviceDetail(deviceId)).data
                            showInfo()
                        } else Toast.makeText(this@DeviceDetailActivity, rr.msg, Toast.LENGTH_SHORT).show()
                    }
                }.show()
        }
    }

    private fun section(title: String): TextView =
        TextView(this).apply { text = title; textSize = 15f; setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 16, 0, 8); setTextColor(0xFF1B2A4A.toInt()) }

    private fun row(label: String, value: Any?): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(0, 3, 0, 3)
            addView(TextView(this@DeviceDetailActivity).apply { text = "$label:  "; textSize = 13f; setTextColor(0xFF8896A6.toInt()) })
            addView(TextView(this@DeviceDetailActivity).apply { text = value?.toString() ?: "-"; textSize = 13f; setTextColor(0xFF1B2A4A.toInt()) })
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
