package com.quarx.leaks

import android.app.Dialog
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import java.text.DecimalFormat

class AppDetailsDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_TRAFFIC_DATA = "traffic_data"

        fun newInstance(trafficData: TrafficData): AppDetailsDialogFragment {
            val args = Bundle().apply {
                putSerializable(ARG_TRAFFIC_DATA, trafficData)
            }
            return AppDetailsDialogFragment().apply {
                arguments = args
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val trafficData = arguments?.getSerializable(ARG_TRAFFIC_DATA) as? TrafficData
            ?: return AlertDialog.Builder(requireContext()).create()

        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_app_details, null)

        // Заполняем данные
        view.findViewById<TextView>(R.id.dialogAppName).text = trafficData.appName
        view.findViewById<TextView>(R.id.dialogPackageName).text = trafficData.packageName
        view.findViewById<TextView>(R.id.dialogDataSent).text = "📤 Отправлено: ${formatBytes(trafficData.dataSent)}"
        view.findViewById<TextView>(R.id.dialogDataReceived).text = "📥 Получено: ${formatBytes(trafficData.dataReceived)}"
        view.findViewById<TextView>(R.id.dialogRiskLevel).apply {
            text = "⚡ Уровень риска: ${trafficData.riskLevel.description}"
            setTextColor(trafficData.riskLevel.color)
        }

        // Трекеры
        val trackersText = if (trafficData.trackers.isNotEmpty()) {
            trafficData.trackers.joinToString("\n") { "• ${it.name}: ${it.description}" }
        } else {
            "✅ Без известных трекеров"
        }
        view.findViewById<TextView>(R.id.dialogTrackersList).text = trackersText

        // Разрешения
        val permissionsText = if (trafficData.permissions.isNotEmpty()) {
            trafficData.permissions.take(15).joinToString("\n") { "• $it" }
        } else {
            "ℹ️ Разрешения не указаны"
        }
        view.findViewById<TextView>(R.id.dialogPermissionsList).text = permissionsText

        return AlertDialog.Builder(requireContext())
            .setView(view)
            .setPositiveButton("Закрыть") { dialog, _ -> dialog.dismiss() }
            .create()
    }

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