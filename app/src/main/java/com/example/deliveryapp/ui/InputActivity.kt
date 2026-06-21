package com.rodgers.routist.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.rodgers.routist.databinding.ActivityInputBinding
import com.rodgers.routist.util.AppSettings
import com.rodgers.routist.util.UrlExtractor
import com.rodgers.routist.util.ZipCodeHelper
import com.rodgers.routist.viewmodel.DeliveryViewModel
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser

class InputActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInputBinding
    private val viewModel: DeliveryViewModel by viewModels()
    private var selectedFileUri: Uri? = null

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        try {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            selectedFileUri = uri

            val mimeType = contentResolver.getType(uri) ?: ""
            val fileName = uri.lastPathSegment?.lowercase() ?: ""
            val isXlsx = mimeType.contains("spreadsheet") ||
                         mimeType.contains("excel") ||
                         fileName.endsWith(".xlsx") ||
                         fileName.endsWith(".xls")

            if (isXlsx) handleXlsxFile(uri) else handleCsvFile(uri)

        } catch (e: Exception) {
            Toast.makeText(this, "ファイルの読み込みに失敗しました", Toast.LENGTH_SHORT).show()
        }
    }

    // ── CSV ──────────────────────────────────────────────────────────

    private fun handleCsvFile(uri: Uri) {
        val text = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return
        val lines = text.lines()
        val isHeader = lines.firstOrNull()?.let {
            it.contains("住所") || it.contains("address", ignoreCase = true)
        } == true
        val addresses = lines
            .drop(if (isHeader) 1 else 0)
            .map { it.split(",").firstOrNull()?.trim()?.trim('"') ?: "" }
            .filter { it.isNotBlank() }
        binding.editTextAddresses.setText(addresses.joinToString("\n"))
        Toast.makeText(this, "ファイルから${addresses.size}件の住所を読み込みました", Toast.LENGTH_SHORT).show()
    }

    // ── Excel (.xlsx) ────────────────────────────────────────────────

    private fun handleXlsxFile(uri: Uri) {
        val rows = readXlsxRows(uri)
        if (rows.isEmpty()) {
            Toast.makeText(this, "データが見つかりませんでした", Toast.LENGTH_SHORT).show()
            return
        }

        val header = rows.first()
        val addressColIdx = header.indexOfFirst {
            it.contains("住所") || it.contains("address", ignoreCase = true)
        }

        if (addressColIdx >= 0) {
            val addresses = rows.drop(1)
                .map { it.getOrElse(addressColIdx) { "" }.trim() }
                .filter { it.isNotBlank() }
            binding.editTextAddresses.setText(addresses.joinToString("\n"))
            Toast.makeText(this, "「${header[addressColIdx]}」列を読み込みました（${addresses.size}件）",
                Toast.LENGTH_SHORT).show()
        } else {
            val hasHeaderRow = header.any { it.isNotBlank() }
            val labels = header.mapIndexed { i, h ->
                val col = xlsxColName(i)
                if (h.isNotBlank()) "$col 列: $h" else "$col 列"
            }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("どの列に住所が入っていますか？")
                .setItems(labels) { _, which ->
                    val addresses = rows.drop(if (hasHeaderRow) 1 else 0)
                        .map { it.getOrElse(which) { "" }.trim() }
                        .filter { it.isNotBlank() }
                    binding.editTextAddresses.setText(addresses.joinToString("\n"))
                    Toast.makeText(this, "${addresses.size}件を読み込みました", Toast.LENGTH_SHORT).show()
                }
                .show()
        }
    }

    private fun readXlsxRows(uri: Uri): List<List<String>> {
        val sharedStrings = ArrayList<String>()
        val fileContents = HashMap<String, ByteArray>()

        val inputStream = contentResolver.openInputStream(uri)
            ?: run { android.widget.Toast.makeText(this, "ファイルを開けませんでした", android.widget.Toast.LENGTH_SHORT).show(); return emptyList() }
        java.util.zip.ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "xl/sharedStrings.xml" ||
                    entry.name == "xl/worksheets/sheet1.xml") {
                    fileContents[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        fileContents["xl/sharedStrings.xml"]?.let { bytes ->
            val parser = android.util.Xml.newPullParser()
            parser.setInput(bytes.inputStream(), null)
            val buf = StringBuilder()
            var inT = false
            loop@ while (true) {
                when (parser.next()) {
                    XmlPullParser.END_DOCUMENT -> break@loop
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "si" -> buf.clear()
                        "t"  -> inT = true
                    }
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "si" -> sharedStrings.add(buf.toString())
                        "t"  -> inT = false
                    }
                    XmlPullParser.TEXT -> if (inT) buf.append(parser.text)
                }
            }
        }

        val rows = ArrayList<List<String>>()
        fileContents["xl/worksheets/sheet1.xml"]?.let { bytes ->
            val parser = android.util.Xml.newPullParser()
            parser.setInput(bytes.inputStream(), null)
            var rowCells = ArrayList<Pair<Int, String>>()
            var colIdx = -1
            var cellType = ""
            val vBuf = StringBuilder()
            val sBuf = StringBuilder()
            var inV = false
            var inInlineT = false
            var inIs = false

            loop@ while (true) {
                when (parser.next()) {
                    XmlPullParser.END_DOCUMENT -> break@loop
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "row" -> rowCells = ArrayList()
                        "c"   -> {
                            val ref = parser.getAttributeValue(null, "r") ?: ""
                            colIdx   = xlsxColIndex(ref)
                            cellType = parser.getAttributeValue(null, "t") ?: ""
                            vBuf.clear(); sBuf.clear()
                            inV = false; inIs = false; inInlineT = false
                        }
                        "v"  -> inV = true
                        "is" -> inIs = true
                        "t"  -> if (inIs) inInlineT = true
                    }
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "row" -> {
                            if (rowCells.isNotEmpty()) {
                                val maxCol = rowCells.maxOf { it.first }
                                val arr = Array(maxCol + 1) { "" }
                                rowCells.forEach { (c, v) -> if (c >= 0) arr[c] = v }
                                rows.add(arr.toList())
                            }
                        }
                        "c"  -> {
                            val value = when (cellType) {
                                "s"          -> sharedStrings.getOrElse(
                                    vBuf.toString().trim().toIntOrNull() ?: -1) { "" }
                                "inlineStr"  -> sBuf.toString().trim()
                                "b"          -> if (vBuf.toString().trim() == "1") "TRUE" else "FALSE"
                                else         -> vBuf.toString().trim()
                            }
                            if (colIdx >= 0) rowCells.add(colIdx to value)
                        }
                        "v"  -> inV = false
                        "is" -> inIs = false
                        "t"  -> inInlineT = false
                    }
                    XmlPullParser.TEXT -> when {
                        inV       -> vBuf.append(parser.text)
                        inInlineT -> sBuf.append(parser.text)
                    }
                }
            }
        }

        return rows
    }

    private fun xlsxColIndex(cellRef: String): Int {
        val letters = cellRef.takeWhile { it.isLetter() }
        return letters.fold(0) { acc, c -> acc * 26 + (c.uppercaseChar() - 'A' + 1) } - 1
    }

    private fun xlsxColName(index: Int): String {
        val alpha = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        return if (index < 26) alpha[index].toString()
        else "${alpha[index / 26 - 1]}${alpha[index % 26]}"
    }

    // ── Activity ─────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInputBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars   = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime    = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            // Toolbar 上部にステータスバー分を追加
            binding.toolbar.updatePadding(top = bars.top)
            binding.toolbar.layoutParams = (binding.toolbar.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)
                .apply { height = resources.getDimensionPixelSize(
                    com.google.android.material.R.dimen.abc_action_bar_default_height_material) + bars.top }
            // ボトムコントロールにナビバー/IME分を追加
            binding.bottomControls.updatePadding(bottom = maxOf(ime, bars.bottom) +
                resources.getDimensionPixelSize(com.google.android.material.R.dimen.abc_action_bar_default_height_material) / 4)
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "配達先を追加"

        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            if (sharedText.startsWith("http")) {
                extractFromUrl(sharedText)
            } else {
                binding.editTextAddresses.setText(sharedText)
            }
        }

        binding.buttonZipSearch.setOnClickListener { searchZipCode() }
        binding.editZipCode.setOnEditorActionListener { _, _, _ -> searchZipCode(); true }

        binding.buttonImport.setOnClickListener {
            val text = binding.editTextAddresses.text.toString().trim()
            if (text.isBlank()) {
                Toast.makeText(this, "配達先の住所または名称を入力してください", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val result = Intent().apply {
                putExtra(EXTRA_ADDRESSES, text)
                selectedFileUri?.let { putExtra(EXTRA_FILE_URI, it.toString()) }
            }
            setResult(Activity.RESULT_OK, result)
            finish()
        }

        binding.buttonPaste.setOnClickListener {
            val clip = (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).primaryClip
            if (clip != null && clip.itemCount > 0) {
                binding.editTextAddresses.setText(clip.getItemAt(0).coerceToText(this).toString())
                selectedFileUri = null
            } else Toast.makeText(this, "クリップボードに内容がありません", Toast.LENGTH_SHORT).show()
        }

        binding.buttonCsv.setOnClickListener {
            filePicker.launch(arrayOf(
                "text/*",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel",
                "application/octet-stream",
                "*/*"
            ))
        }
    }

    private fun searchZipCode() {
        val zip = binding.editZipCode.text.toString().trim()
        if (zip.length != 7) {
            Toast.makeText(this, "郵便番号を7桁で入力してください（ハイフンなし）", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val result = ZipCodeHelper.lookup(zip)
            if (result == null) {
                Toast.makeText(this@InputActivity, "「$zip」の住所が見つかりませんでした", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val existing = binding.editTextAddresses.text.toString().trim()
            val newText = if (existing.isBlank()) result.address else "$existing\n${result.address}"
            binding.editTextAddresses.setText(newText)
            binding.editTextAddresses.setSelection(newText.length)
            binding.editZipCode.setText("")
            Toast.makeText(this@InputActivity, "「${result.address}」を追加しました", Toast.LENGTH_SHORT).show()
        }
    }

    private fun extractFromUrl(url: String) {
        binding.progressUrl.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = UrlExtractor.extract(url)
            binding.progressUrl.visibility = View.GONE

            if (result != null) {
                val existing = binding.editTextAddresses.text.toString().trim()
                binding.editTextAddresses.setText(
                    if (existing.isBlank()) result.query else "$existing\n${result.query}"
                )
                Toast.makeText(this@InputActivity, "「${result.name}」を追加しました", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@InputActivity, "URLから場所を取得できませんでした", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    companion object {
        const val EXTRA_ADDRESSES = "extra_addresses"
        const val EXTRA_FILE_URI  = "extra_file_uri"
    }
}
