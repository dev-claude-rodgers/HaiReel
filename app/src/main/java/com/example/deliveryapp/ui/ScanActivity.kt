package com.rodgers.routist.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.rodgers.routist.R
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import java.io.File

class ScanActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SCANNED_TEXT = "scanned_text"
    }

    private val scannedList = mutableListOf<String>()
    private var photoUri: Uri? = null

    private lateinit var tvCount: TextView
    private lateinit var tvStatus: TextView
    private lateinit var etName: EditText
    private lateinit var etAddress: EditText
    private lateinit var btnCapture: Button
    private lateinit var btnAdd: Button
    private lateinit var btnSkip: Button
    private lateinit var btnDone: Button

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoUri?.let { runOcr(it) }
        } else {
            tvStatus.text = "撮影がキャンセルされました"
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera()
        else Toast.makeText(this, "カメラ権限を許可してください", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "伝票スキャン"

        tvCount   = findViewById(R.id.tvCount)
        tvStatus  = findViewById(R.id.tvStatus)
        etName    = findViewById(R.id.etName)
        etAddress = findViewById(R.id.etAddress)
        btnCapture = findViewById(R.id.btnCapture)
        btnAdd    = findViewById(R.id.btnAdd)
        btnSkip   = findViewById(R.id.btnSkip)
        btnDone   = findViewById(R.id.btnDone)

        btnCapture.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
                launchCamera()
            } else {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        btnAdd.setOnClickListener {
            val address = etAddress.text.toString().trim()
            val name    = etName.text.toString().trim()
            if (address.isNotBlank()) {
                val entry = if (name.isNotBlank()) "$name（$address）" else address
                scannedList.add(entry)
                updateCount()
                clearResult()
                Toast.makeText(this, "${scannedList.size}件目を追加しました", Toast.LENGTH_SHORT).show()
            }
        }

        btnSkip.setOnClickListener { clearResult() }

        btnDone.setOnClickListener {
            val resultText = scannedList.joinToString("\n")
            val intent = Intent().apply {
                putExtra(EXTRA_SCANNED_TEXT, resultText)
            }
            setResult(RESULT_OK, intent)
            finish()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    private fun launchCamera() {
        val photo = File(cacheDir, "scan_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photo)
        photoUri = uri
        cameraLauncher.launch(uri)
        tvStatus.text = "処理中..."
        btnAdd.isEnabled = false
        btnSkip.isEnabled = false
        etName.setText("")
        etAddress.setText("")
    }

    private fun runOcr(uri: Uri) {
        val image = try {
            InputImage.fromFilePath(this, uri)
        } catch (e: Exception) {
            tvStatus.text = "画像の読み込みに失敗しました"
            return
        }

        val recognizer = TextRecognition.getClient(
            JapaneseTextRecognizerOptions.Builder().build()
        )
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val address = extractAddress(result.text)
                val name    = extractName(result.text)
                etName.setText(name)
                if (address.isNotBlank()) {
                    etAddress.setText(address)
                    val msg = if (name.isNotBlank()) "氏名・住所を認識しました。" else "住所を認識しました。"
                    tvStatus.text = "${msg}必要なら編集して「追加」してください。"
                    btnAdd.isEnabled = true
                    btnSkip.isEnabled = true
                    btnDone.isEnabled = scannedList.isNotEmpty()
                } else {
                    etAddress.setText(result.text.take(300))
                    tvStatus.text = "住所を特定できませんでした。正しい部分を残して「追加」してください。"
                    btnAdd.isEnabled = true
                    btnSkip.isEnabled = true
                }
            }
            .addOnFailureListener {
                tvStatus.text = "文字認識に失敗しました。もう一度撮影してください。"
            }
    }

    private fun extractName(fullText: String): String {
        val lines = fullText.lines().map { it.trim() }.filter { it.isNotBlank() }
        for (line in lines) {
            if (line.length > 30) continue
            if (line.endsWith("様") || line.endsWith("御中")) return line
            if (line.contains(Regex("株式会社|有限会社|合同会社|一般社団法人|一般財団法人|NPO法人"))) return line
        }
        return ""
    }

    private fun extractAddress(fullText: String): String {
        val lines = fullText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val scored = lines.map { line -> line to scoreAddressLine(line) }
        val best = scored.maxByOrNull { it.second }
        return if ((best?.second ?: 0) >= 2) best!!.first else ""
    }

    private fun scoreAddressLine(text: String): Int {
        var score = 0
        if (text.contains(Regex("[都道府県]"))) score += 3
        if (text.contains(Regex("[市区町村]"))) score += 2
        if (text.contains(Regex("[丁目番地号]"))) score += 2
        if (text.contains(Regex("\\d+[-ー−]\\d+"))) score += 2
        if (text.contains(Regex("\\d+丁目|\\d+番|\\d+号"))) score += 2
        if (text.length in 8..60) score += 1
        // 氏名や会社名っぽいものにはマイナス
        if (text.contains(Regex("様|御中|株式会社|有限会社"))) score -= 2
        return score
    }

    private fun clearResult() {
        etName.setText("")
        etAddress.setText("")
        tvStatus.text = "撮影をタップして次の伝票を読み込む"
        btnAdd.isEnabled = false
        btnSkip.isEnabled = false
        btnDone.isEnabled = scannedList.isNotEmpty()
    }

    private fun updateCount() {
        tvCount.text = "${scannedList.size} 件追加済み"
        btnDone.isEnabled = true
    }
}
