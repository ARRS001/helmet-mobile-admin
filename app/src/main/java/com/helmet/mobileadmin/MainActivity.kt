package com.helmet.mobileadmin

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private lateinit var container: FrameLayout
    private lateinit var tvUserInfo: TextView
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val views = mutableMapOf<String, View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        container = findViewById(R.id.fragmentContainer)
        tvUserInfo = findViewById(R.id.tvUserInfo)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).apply {
            setNavigationIcon(android.R.drawable.ic_lock_power_off)
            setNavigationOnClickListener { logout() }
            title = "安全帽移动管理中心"
        }
        tvUserInfo.text = "${ApiService.username} · ${levelLabel()}"

        findViewById<BottomNavigationView>(R.id.bottomNav).setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> showDashboard()
                R.id.nav_devices -> showDevices()
                R.id.nav_alarms -> showAlarms()
                R.id.nav_accounts -> showAccounts()
                R.id.nav_monitor -> showMonitor()
            }
            true
        }
        showDashboard()
    }

    private fun levelLabel() = mapOf(0 to "超管", 1 to "管理员", 2 to "用户").getOrDefault(ApiService.level, "?")

    // ── Dashboard ──
    private fun showDashboard() {
        val sv = ScrollView(this).apply { setPadding(16, 16, 16, 16) }
        val ll = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val statsCard = card("")
        val statsGrid = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val totalView = statBox("总设备", "-")
        val onlineView = statBox("在线", "-")
        val alarmView = statBox("7天告警", "-")
        val streamView = statBox("推流中", "-")
        statsGrid.addView(totalView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 8 })
        statsGrid.addView(onlineView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 8 })
        statsGrid.addView(alarmView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 8 })
        statsGrid.addView(streamView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        (statsCard.getChildAt(0) as ViewGroup).addView(statsGrid)

        val quickBtns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 16, 0, 0) }
        quickBtns.addView(actionBtn("设备列表") { findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.nav_devices })
        quickBtns.addView(actionBtn("账号管理") { findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.nav_accounts })
        quickBtns.addView(actionBtn("实时监控") { findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.nav_monitor })

        ll.addView(statsCard)
        ll.addView(quickBtns)
        sv.addView(ll)
        container.removeAllViews(); container.addView(sv)
        views["dashboard"] = sv

        scope.launch {
            try {
                val d = ApiService.dashboard()
                if (d.code == 0) {
                    (totalView.getChildAt(1) as TextView).text = "${(d.data?.get("totalHelmets") as? Double)?.toInt() ?: 0}"
                    (onlineView.getChildAt(1) as TextView).text = "${(d.data?.get("onlineNow") as? Double)?.toInt() ?: 0}"
                    (alarmView.getChildAt(1) as TextView).text = "${(d.data?.get("alarms7d") as? Double)?.toInt() ?: 0}"
                }
                val s = ApiService.activeStreams()
                if (s.code == 0) {
                    val count = (s.data?.get("streams") as? List<*>)?.size ?: 0
                    (streamView.getChildAt(1) as TextView).text = "$count"
                }
            } catch (_: Exception) {}
        }
    }

    // ── Devices ──
    private val deviceList = mutableListOf<Map<String, Any?>>()
    private lateinit var deviceAdapter: DeviceAdapter

    private fun showDevices() {
        val ll = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(16, 12, 16, 8) }
        val searchEt = EditText(this).apply { hint = "搜索设备ID/人员"; setPadding(12, 12, 12, 12); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 8 } }
        val addBtn = MaterialButton(this).apply { text = "添加"; setBackgroundColor(0xFF5b9cf5.toInt()); setTextColor(0xFFFFFFFF.toInt()); textSize = 12f }
        bar.addView(searchEt); bar.addView(addBtn)

        val rv = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@MainActivity) }
        deviceAdapter = DeviceAdapter(deviceList) { device -> showDeviceDetail(device) }
        rv.adapter = deviceAdapter

        ll.addView(bar); ll.addView(rv)
        container.removeAllViews(); container.addView(ll)
        views["devices"] = ll

        addBtn.setOnClickListener { showAddDeviceDialog() }
        loadDevices()
    }

    private fun loadDevices() {
        scope.launch {
            try {
                val r = ApiService.deviceList(mapOf("limit" to "200"))
                if (r.code == 0) {
                    @Suppress("UNCHECKED_CAST")
                    deviceList.clear()
                    deviceList.addAll((r.data?.get("list") as? List<Map<String, Any?>>) ?: emptyList())
                    deviceAdapter.notifyDataSetChanged()
                }
            } catch (_: Exception) {}
        }
    }

    private fun showAddDeviceDialog() {
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16) }
        val nameEt = editField("人员名称", form)
        val phoneEt = editField("手机号", form)
        AlertDialog.Builder(this).setTitle("添加设备")
            .setView(form)
            .setPositiveButton("确认") { _, _ ->
                scope.launch {
                    val r = ApiService.deviceAdd(mapOf(
                        "personnelName" to nameEt.text.toString(),
                        "phone" to phoneEt.text.toString(),
                        "deviceType" to "high"
                    ))
                    if (r.code == 0) {
                        Toast.makeText(this@MainActivity, "已添加: ${r.data?.get("deviceId")} 密码: ${r.data?.get("password")}", Toast.LENGTH_LONG).show()
                        loadDevices()
                    } else Toast.makeText(this@MainActivity, r.msg, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null).show()
    }

    private fun showDeviceDetail(device: Map<String, Any?>) {
        startActivity(Intent(this, DeviceDetailActivity::class.java).apply {
            putExtra("deviceId", device["deviceId"] as? String ?: "")
            putExtra("deviceName", (device["personnelName"] as? String) ?: (device["deviceId"] as? String) ?: "")
        })
    }

    // ── Alarms ──
    private val alarmList = mutableListOf<Map<String, Any?>>()
    private lateinit var alarmAdapter: AlarmAdapter
    private var alarmFilter = ""

    private fun showAlarms() {
        val ll = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(16, 12, 16, 8) }
        val statusSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, listOf("全部", "未处理", "已处理")).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(0)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) { loadAlarms() }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
        }
        bar.addView(statusSpinner, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 8 })

        val rv = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@MainActivity) }
        alarmAdapter = AlarmAdapter(alarmList) { alarm -> handleAlarm(alarm) }
        rv.adapter = alarmAdapter

        ll.addView(bar); ll.addView(rv)
        container.removeAllViews(); container.addView(ll)
        views["alarms"] = ll
        loadAlarms()
    }

    private fun loadAlarms() {
        scope.launch {
            try {
                val params = mutableMapOf("limit" to "200")
                val spinner = (findViewById<ViewGroup>(R.id.fragmentContainer).getChildAt(0) as? ViewGroup)?.let { vg ->
                    for (i in 0 until vg.childCount) { if (vg.getChildAt(i) is Spinner) return@let vg.getChildAt(i) as Spinner }; null
                } ?: return@launch
                val statusIdx = spinner.selectedItemPosition
                if (statusIdx == 1) params["handled"] = "0"
                else if (statusIdx == 2) params["handled"] = "1"
                val r = ApiService.alarmList(params)
                if (r.code == 0) {
                    @Suppress("UNCHECKED_CAST")
                    alarmList.clear()
                    alarmList.addAll((r.data?.get("list") as? List<Map<String, Any?>>) ?: emptyList())
                    alarmAdapter.notifyDataSetChanged()
                }
            } catch (_: Exception) {}
        }
    }

    private fun handleAlarm(alarm: Map<String, Any?>) {
        val handled = (alarm["handled"] as? Number)?.toInt() == 1
        if (handled) return
        val id = alarm["id"] as? String ?: return
        AlertDialog.Builder(this).setTitle("处理告警")
            .setMessage("${alarm["type"]}: ${alarm["message"]}")
            .setPositiveButton("标记已处理") { _, _ ->
                scope.launch {
                    val r = ApiService.alarmHandle(id)
                    if (r.code == 0) { Toast.makeText(this@MainActivity, "已处理", Toast.LENGTH_SHORT).show(); loadAlarms() }
                    else Toast.makeText(this@MainActivity, r.msg ?: "失败", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("取消", null).show()
    }

    // ── Accounts ──
    private val accountList = mutableListOf<Map<String, Any?>>()
    private lateinit var accountAdapter: AccountAdapter

    private fun showAccounts() {
        val ll = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(16, 12, 16, 8) }
        val title = TextView(this).apply { text = "账号列表"; textSize = 16f; setTypeface(null, android.graphics.Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        val addBtn = MaterialButton(this).apply { text = "创建账号"; setBackgroundColor(0xFF5b9cf5.toInt()); setTextColor(0xFFFFFFFF.toInt()); textSize = 12f }
        if (ApiService.level > 1) addBtn.isEnabled = false
        bar.addView(title); bar.addView(addBtn)

        val rv = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@MainActivity) }
        accountAdapter = AccountAdapter(accountList, ApiService.level, ApiService.adminId) { id -> deleteAccount(id) }
        rv.adapter = accountAdapter

        ll.addView(bar); ll.addView(rv)
        container.removeAllViews(); container.addView(ll)
        views["accounts"] = ll

        addBtn.setOnClickListener { showAddAccountDialog() }
        loadAccounts()
    }

    private fun loadAccounts() {
        scope.launch {
            try {
                val r = ApiService.subAdmins()
                if (r.code == 0) {
                    @Suppress("UNCHECKED_CAST")
                    accountList.clear()
                    accountList.addAll((r.data as? List<Map<String, Any?>>) ?: emptyList())
                    accountAdapter.notifyDataSetChanged()
                }
            } catch (_: Exception) {}
        }
    }

    private fun showAddAccountDialog() {
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16) }
        val usernameEt = editField("用户名", form)
        val passwordEt = editField("密码", form)
        val typeGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL
            if (ApiService.level == 0) {
                addView(RadioButton(this@MainActivity).apply { text = "管理员"; id = 1 })
                addView(RadioButton(this@MainActivity).apply { text = "普通用户"; id = 2; isChecked = true })
            } else {
                addView(RadioButton(this@MainActivity).apply { text = "普通用户"; id = 2; isChecked = true })
            }
        }
        form.addView(typeGroup)
        val deviceEt = editField("绑定设备ID (可选)", form)

        AlertDialog.Builder(this).setTitle("创建账号")
            .setView(form)
            .setPositiveButton("确认") { _, _ ->
                val accountType = if (typeGroup.checkedRadioButtonId == 1) "admin" else "user"
                scope.launch {
                    val data = mutableMapOf("username" to usernameEt.text.toString(), "password" to passwordEt.text.toString(), "accountType" to accountType)
                    if (deviceEt.text.toString().isNotEmpty()) data["deviceId"] = deviceEt.text.toString()
                    val r = ApiService.createSubAdmin(data)
                    if (r.code == 0) { Toast.makeText(this@MainActivity, "创建成功", Toast.LENGTH_SHORT).show(); loadAccounts() }
                    else Toast.makeText(this@MainActivity, r.msg, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null).show()
    }

    private fun deleteAccount(id: String) {
        AlertDialog.Builder(this).setTitle("确认删除").setMessage("删除此账号？")
            .setPositiveButton("确认") { _, _ ->
                scope.launch {
                    val r = ApiService.deleteSubAdmin(id)
                    if (r.code == 0) { Toast.makeText(this@MainActivity, "已删除", Toast.LENGTH_SHORT).show(); loadAccounts() }
                    else Toast.makeText(this@MainActivity, r.msg, Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("取消", null).show()
    }

    // ── Monitor ──
    private fun showMonitor() {
        val ll = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(16, 12, 16, 8) }
        val title = TextView(this).apply { text = "实时监控 (点击设备开始)"; textSize = 14f; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        bar.addView(title)
        ll.addView(bar)

        val rv = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@MainActivity) }
        rv.adapter = object : RecyclerView.Adapter<MonitorHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = MonitorHolder(TextView(parent.context).apply { setPadding(16,12,16,12); textSize = 14f })
            override fun onBindViewHolder(h: MonitorHolder, pos: Int) {
                val d = deviceList.getOrNull(pos) ?: return
                val online = (d["online"] as? Number)?.toInt() == 1
                val streaming = (d["streaming"] as? Number)?.toInt() == 1
                h.tv.text = "${if (streaming) "⬤ " else if (online) "● " else "○ "}${d["personnelName"] ?: d["deviceId"]}"
                h.tv.setTextColor(if (streaming) 0xFF00FF88.toInt() else if (online) 0xFF67C23A.toInt() else 0xFF909399.toInt())
                h.tv.setOnClickListener {
                    if (streaming) startActivity(Intent(this@MainActivity, MonitorActivity::class.java).apply { putExtra("deviceId", d["deviceId"] as? String ?: ""); putExtra("name", (d["personnelName"] as? String) ?: (d["deviceId"] as? String) ?: "") })
                }
            }
            override fun getItemCount() = deviceList.size
        }
        ll.addView(rv)
        container.removeAllViews(); container.addView(ll)
        loadDevices()
    }

    // ── Helpers ──
    private fun statBox(title: String, value: String): LinearLayout {
        val ll = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(8, 8, 8, 8) }
        ll.addView(TextView(this).apply { text = title; textSize = 11f; setTextColor(0xFF8896A6.toInt()) })
        ll.addView(TextView(this).apply { text = value; textSize = 20f; setTextColor(0xFF1B2A4A.toInt()); setTypeface(null, android.graphics.Typeface.BOLD) })
        return ll
    }

    private fun actionBtn(label: String, onClick: () -> Unit): MaterialButton {
        return MaterialButton(this).apply {
            text = label; setBackgroundColor(0xFF283545.toInt()); setTextColor(0xFFE4E8EF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 8 }
            setOnClickListener { onClick() }
        }
    }

    private fun card(title: String): MaterialCardView {
        return MaterialCardView(this).apply {
            radius = 8f; setContentPadding(12, 8, 12, 8); cardElevation = 2f; setPadding(4, 4, 4, 4)
            addView(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL })
        }
    }

    private fun editField(hint: String, parent: ViewGroup): EditText {
        val et = EditText(this).apply { this.hint = hint; setPadding(12, 12, 12, 12); setSingleLine(true) }
        parent.addView(TextInputLayout(this).apply { boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE; addView(et); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 8 } })
        return et
    }

    private fun logout() {
        ApiService.authToken = ""; ApiService.adminId = ""
        startActivity(Intent(this, LoginActivity::class.java)); finish()
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}

// ── Adapters ──
class DeviceAdapter(private val list: List<Map<String, Any?>>, private val onClick: (Map<String, Any?>) -> Unit) : RecyclerView.Adapter<DeviceAdapter.VH>() {
    class VH(val card: MaterialCardView) : RecyclerView.ViewHolder(card)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(MaterialCardView(parent.context).apply {
        radius = 8f; setContentPadding(12, 10, 12, 10); cardElevation = 1f
        layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(16, 4, 16, 4) }
    })
    override fun onBindViewHolder(vh: VH, pos: Int) {
        val d = list[pos]; val ctx = vh.card.context
        val online = (d["online"] as? Number)?.toInt() == 1
        val streaming = (d["streaming"] as? Number)?.toInt() == 1
        val ll = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val row1 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(TextView(ctx).apply { text = "${d["deviceId"]}"; textSize = 14f; setTypeface(null, android.graphics.Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        row1.addView(TextView(ctx).apply { text = if (streaming) "推流中" else if (online) "在线" else "离线"; textSize = 11f; setTextColor(if (streaming) 0xFF4CAF50.toInt() else if (online) 0xFF67C23A.toInt() else 0xFF909399.toInt()) })
        ll.addView(row1)
        ll.addView(TextView(ctx).apply { text = "${d["personnelName"] ?: "-"} · ${d["deviceType"] ?: "high"}"; textSize = 12f; setTextColor(0xFF8896A6.toInt()) })
        if (vh.card.childCount > 0) vh.card.removeAllViews()
        vh.card.addView(ll)
        vh.card.setOnClickListener { onClick(d) }
    }
    override fun getItemCount() = list.size
}

class AccountAdapter(private val list: List<Map<String, Any?>>, private val myLevel: Int, private val myId: String, private val onDelete: (String) -> Unit) : RecyclerView.Adapter<AccountAdapter.VH>() {
    class VH(val card: MaterialCardView) : RecyclerView.ViewHolder(card)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(MaterialCardView(parent.context).apply {
        radius = 8f; setContentPadding(12, 10, 12, 10); cardElevation = 1f
        layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(16, 4, 16, 4) }
    })
    override fun onBindViewHolder(vh: VH, pos: Int) {
        val a = list[pos]; val ctx = vh.card.context
        val level = (a["level"] as? Number)?.toInt() ?: 2
        val levelLabel = mapOf(0 to "超管", 1 to "管理员", 2 to "用户").getOrDefault(level, "?")
        val ll = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val row1 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(TextView(ctx).apply { text = "${a["username"]}"; textSize = 14f; setTypeface(null, android.graphics.Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        row1.addView(TextView(ctx).apply { text = levelLabel; textSize = 11f; setTextColor(0xFF5b9cf5.toInt()) })
        ll.addView(row1)
        val sub = "${a["deviceId"] ?: "未绑设备"} · ${(a["createdAt"] as? String)?.take(10) ?: ""}"
        ll.addView(TextView(ctx).apply { text = sub; textSize = 11f; setTextColor(0xFF8896A6.toInt()) })
        if (vh.card.childCount > 0) vh.card.removeAllViews()
        vh.card.addView(ll)
        // 超管可删任何非超管，管理员只删自己的
        val canDel = (myLevel == 0 && level > 0) || (a["parentId"] as? String == myId)
        if (canDel) vh.card.setOnLongClickListener { onDelete(a["id"] as? String ?: ""); true }
        else vh.card.setOnLongClickListener(null)
    }
    override fun getItemCount() = list.size
}

class AlarmAdapter(private val list: List<Map<String, Any?>>, private val onHandle: (Map<String, Any?>) -> Unit) : RecyclerView.Adapter<AlarmAdapter.VH>() {
    class VH(val card: MaterialCardView) : RecyclerView.ViewHolder(card)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(MaterialCardView(parent.context).apply {
        radius = 8f; setContentPadding(12, 10, 12, 10); cardElevation = 1f
        layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(16, 4, 16, 4) }
    })
    override fun onBindViewHolder(vh: VH, pos: Int) {
        val a = list[pos]; val ctx = vh.card.context
        val handled = (a["handled"] as? Number)?.toInt() == 1
        val ts = (a["timestamp"] as? String)?.take(16)?.replace("T", " ") ?: ""
        val ll = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val row1 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(TextView(ctx).apply {
            text = "${a["type"] ?: "告警"} ${if (handled) "✓" else "⚠"}"
            textSize = 14f; setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(if (handled) 0xFF8896A6.toInt() else 0xFFC62828.toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row1.addView(TextView(ctx).apply { text = ts; textSize = 10f; setTextColor(0xFF8896A6.toInt()) })
        ll.addView(row1)
        ll.addView(TextView(ctx).apply { text = "${a["message"] ?: ""}"; textSize = 12f; setTextColor(0xFF8896A6.toInt()) })
        ll.addView(TextView(ctx).apply { text = "设备: ${a["deviceId"] ?: "-"}"; textSize = 11f; setTextColor(0xFF5f6b7a.toInt()) })
        if (vh.card.childCount > 0) vh.card.removeAllViews()
        vh.card.addView(ll)
        if (!handled) vh.card.setOnClickListener { onHandle(a) }
    }
    override fun getItemCount() = list.size
}

class MonitorHolder(val tv: TextView) : RecyclerView.ViewHolder(tv)
