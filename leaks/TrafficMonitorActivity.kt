package com.quarx.leaks

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.DecimalFormat

class TrafficMonitorActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TrafficAdapter
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var emptyStateText: TextView
    private lateinit var startMonitoringButton: TextView
    private lateinit var stopMonitoringButton: TextView

    private var trafficDataList: List<TrafficData> = emptyList()

    private val trafficReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "TRAFFIC_DATA_UPDATED") {
                @Suppress("UNCHECKED_CAST")
                val data = intent.getSerializableExtra("traffic_data") as? ArrayList<TrafficData>
                data?.let {
                    trafficDataList = it
                    updateUI()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_traffic_monitor)

        setupViews()
        setupRecyclerView()

        registerTrafficReceiver()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerTrafficReceiver() {
        val filter = IntentFilter("TRAFFIC_DATA_UPDATED")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(trafficReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(trafficReceiver, filter)
        }
    }

    private fun setupViews() {
        // Используем правильные ID из макета
        recyclerView = findViewById(R.id.trafficRecyclerView)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        emptyStateText = findViewById(R.id.emptyStateText)
        startMonitoringButton = findViewById(R.id.startMonitoringButton)
        stopMonitoringButton = findViewById(R.id.stopMonitoringButton)

        startMonitoringButton.setOnClickListener {
            startMonitoring()
        }

        stopMonitoringButton.setOnClickListener {
            stopMonitoring()
        }
    }

    private fun setupRecyclerView() {
        adapter = TrafficAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun startMonitoring() {
        loadingIndicator.visibility = View.VISIBLE
        emptyStateText.visibility = View.GONE

        val intent = Intent(this, AppTrafficService::class.java).apply {
            action = AppTrafficService.ACTION_START_MONITORING
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            @Suppress("DEPRECATION")
            startService(intent)
        }
    }

    private fun stopMonitoring() {
        val intent = Intent(this, AppTrafficService::class.java).apply {
            action = AppTrafficService.ACTION_STOP_MONITORING
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            @Suppress("DEPRECATION")
            startService(intent)
        }

        loadingIndicator.visibility = View.GONE
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateUI() {
        loadingIndicator.visibility = View.GONE

        if (trafficDataList.isEmpty()) {
            emptyStateText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyStateText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE

            // Сортируем по общему объему трафика
            val sortedData = trafficDataList.sortedByDescending {
                it.dataSent + it.dataReceived
            }

            adapter.updateData(sortedData)
        }
    }

    private fun showAppDetails(trafficData: TrafficData) {
        val dialog = AppDetailsDialogFragment.newInstance(trafficData)
        dialog.show(supportFragmentManager, "AppDetailsDialog")
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(trafficReceiver)
        } catch (e: IllegalArgumentException) {
        }
    }

    inner class TrafficAdapter : RecyclerView.Adapter<TrafficAdapter.ViewHolder>() {

        private var data: List<TrafficData> = emptyList()

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
            val appName: TextView = itemView.findViewById(R.id.appName)
            val dataUsage: TextView = itemView.findViewById(R.id.dataUsage)
            val riskLevel: TextView = itemView.findViewById(R.id.riskLevel)
            val trackerCount: TextView = itemView.findViewById(R.id.trackerCount)
            val permissionCount: TextView = itemView.findViewById(R.id.permissionCount)
            val trackerList: TextView = itemView.findViewById(R.id.trackerList)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_traffic_data, parent, false)
            return ViewHolder(view)
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = data[position]

            holder.appIcon.setImageDrawable(item.appIcon)

            holder.appName.text = item.appName

            val totalBytes = item.dataSent + item.dataReceived
            val formattedData = formatBytes(totalBytes)
            holder.dataUsage.text = "📊 $formattedData"

            holder.riskLevel.text = "⚡ ${item.riskLevel.description}"
            holder.riskLevel.setTextColor(item.riskLevel.color)

            holder.trackerCount.text = " Трекеров: ${item.trackers.size}"

            holder.permissionCount.text = "Разрешений: ${item.permissions.size}"

            val trackerNames = item.trackers.joinToString(", ") { it.name }
            holder.trackerList.text = if (trackerNames.isNotEmpty()) {
                "🔍 $trackerNames"
            } else {
                "✅ Без известных трекеров"
            }

            holder.itemView.setOnClickListener {
                showAppDetails(item)
            }
        }

        override fun getItemCount(): Int = data.size

        @SuppressLint("NotifyDataSetChanged")
        fun updateData(newData: List<TrafficData>) {
            data = newData
            notifyDataSetChanged()
        }
    }

    companion object {
        private fun formatBytes(bytes: Long): String {
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var size = bytes.toDouble()
            var unitIndex = 0

            while (size >= 1024 && unitIndex < units.size - 1) {
                size /= 1024
                unitIndex++
            }

            return DecimalFormat("#,##0.#").format(size) + " " + units[unitIndex]
        }
    }
}