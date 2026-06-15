package com.rodgers.routist.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.rodgers.routist.R
import com.rodgers.routist.databinding.FragmentMapBinding
import com.rodgers.routist.model.Delivery
import com.rodgers.routist.util.GeocodingClient
import com.rodgers.routist.util.themeColor
import com.rodgers.routist.util.MarkerIconFactory
import com.rodgers.routist.util.TimeSlotColor
import com.rodgers.routist.viewmodel.DeliveryViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


    internal fun MapFragment.showSosContactDialog() {
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("sos_settings", android.content.Context.MODE_PRIVATE)
        val current = prefs.getString("sos_phone", "") ?: ""
        val input = android.widget.EditText(ctx).apply {
            hint = "090-1234-5678"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setText(current)
            setPadding(48, 24, 48, 8)
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle("📞 SOS連絡先を設定")
            .setMessage("SOS送信時にSMSを送る電話番号を登録してください。")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val phone = input.text.toString().trim()
                prefs.edit().putString("sos_phone", phone).apply()
                Toast.makeText(ctx, "保存しました", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }


    internal fun MapFragment.showSosDialog() {
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("sos_settings", android.content.Context.MODE_PRIVATE)
        val phone = prefs.getString("sos_phone", "") ?: ""
        if (phone.isBlank()) {
            MaterialAlertDialogBuilder(ctx)
                .setTitle("SOS連絡先が未設定")
                .setMessage("メニューの「📞 SOS連絡先を設定」から連絡先を登録してください。")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val types = arrayOf("🚗 交通事故", "🔧 車両トラブル", "❓ その他")
        MaterialAlertDialogBuilder(ctx)
            .setTitle("🆘 SOS送信 - トラブルの内容を選択")
            .setItems(types) { _, which ->
                val typeStr = types[which].substringAfter(" ")
                val loc = lastKnownLocation
                val locStr = if (loc != null)
                    "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
                else "位置情報なし"
                val msg = "【SOS】${typeStr}が発生しました。\n現在地: $locStr"
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).apply {
                    putExtra("sms_body", msg)
                }
                try {
                    startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(ctx, "SMSアプリを開けませんでした", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

