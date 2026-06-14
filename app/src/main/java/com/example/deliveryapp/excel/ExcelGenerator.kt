package com.rodgers.routist.excel

import android.content.Context
import com.rodgers.routist.model.ReportPattern
import com.rodgers.routist.model.WorkRecord
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExcelGenerator(private val context: Context) {

    private data class ColDef(
        val header: String,
        val getData: (WorkRecord?) -> String,
        val totalValue: String = ""
    )

    private data class SheetOutput(
        val xml: String,
        val sigStampRow: Int,
        val leftStart: Int, val leftEnd: Int,
        val rightStart: Int, val rightEnd: Int
    )

    fun generate(
        records: List<WorkRecord>,
        yearMonth: String,
        pattern: ReportPattern,
        driverSig: File? = null,
        clientSig: File? = null,
        assignmentName: String = ""
    ): File {
        val ym    = java.time.YearMonth.parse(yearMonth)
        val year  = ym.year
        val month = ym.monthValue
        val safeName = pattern.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val safeAssignment = assignmentName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val prefix = if (safeAssignment.isNotBlank()) "${year}年${month}月_${safeAssignment}_${safeName}" else "${year}年${month}月_${safeName}"
        val dir  = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "$prefix.xlsx")

        val firstDay  = LocalDate.of(year, month, 1)
        val allDays   = (1..firstDay.lengthOfMonth()).map { firstDay.withDayOfMonth(it) }
        val recordMap = records.associateBy { it.date }

        val totalMins   = records.sumOf { it.workingMinutes }
        val totalDeliv  = records.sumOf { it.deliveryCount }
        val totalPkg    = records.sumOf { it.packageCount }
        val totalDist   = records.sumOf { it.distanceKm.toDouble() }
        val totalHours  = "%d時間%02d分".format(totalMins / 60, totalMins % 60)
        val workingDays = records.sumOf { 1 + it.endDateOffset }

        val columns = buildList<ColDef> {
            if (pattern.showTime) {
                add(ColDef("開始時刻", { it?.startTime ?: "" }))
                add(ColDef("終了時刻", {
                    if (it == null) ""
                    else if (it.endDateOffset > 0) "${it.endTime}(+${it.endDateOffset}日)"
                    else it.endTime
                }))
                add(ColDef("稼働時間", { it?.workingHoursText ?: "" }, totalHours))
            }
            if (pattern.showDelivery)
                add(ColDef(pattern.deliveryLabel,
                    { it?.deliveryCount?.toString() ?: "" }, totalDeliv.toString()))
            add(ColDef("開始メーター", { if (it != null && it.startMeter > 0) it.startMeter.toString() else "" }))
            add(ColDef("終了メーター", { if (it != null && it.endMeter > 0) it.endMeter.toString() else "" }))
            if (pattern.showPackage)
                add(ColDef(pattern.packageLabel,
                    { it?.packageCount?.toString() ?: "" }, totalPkg.toString()))
            if (pattern.showDistance)
                add(ColDef("走行(km)",
                    { if (it != null) "%.1f".format(it.distanceKm) else "" },
                    "%.1f".format(totalDist)))
            if (pattern.showArea)
                add(ColDef("エリア",   { it?.area ?: "" }))
            if (pattern.showRemarks)
                add(ColDef("備考",     { it?.remarks ?: "" }))
        }

        val hasSig = driverSig != null || clientSig != null
        val output = sheetXml(allDays, recordMap, columns, year, month, pattern, workingDays, hasSig)

        var relCount = 0
        val driverRelId = driverSig?.let { ++relCount }
        val clientRelId = clientSig?.let { ++relCount }

        ZipOutputStream(FileOutputStream(file)).use { zos ->
            zos.entry("[Content_Types].xml", contentTypesXml(hasSig))
            zos.entry("_rels/.rels",          relsXml())
            zos.entry("xl/workbook.xml",      workbookXml())
            zos.entry("xl/_rels/workbook.xml.rels", workbookRelsXml())
            zos.entry("xl/styles.xml",        stylesXml())
            zos.entry("xl/worksheets/sheet1.xml", output.xml)

            if (hasSig) {
                zos.entry("xl/worksheets/_rels/sheet1.xml.rels", sheetRelsXml())
                zos.entry("xl/drawings/drawing1.xml",
                    drawingXml(driverRelId, clientRelId, output))
                zos.entry("xl/drawings/_rels/drawing1.xml.rels",
                    drawingRelsXml(driverRelId, clientRelId))
                driverSig?.let { zos.entryBytes("xl/media/image${driverRelId}.png", it.readBytes()) }
                clientSig?.let { zos.entryBytes("xl/media/image${clientRelId}.png", it.readBytes()) }
            }
        }
        return file
    }

    private fun ZipOutputStream.entry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun ZipOutputStream.entryBytes(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun String.esc() =
        replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun colLetter(idx: Int): String =
        if (idx < 26) ('A' + idx).toString()
        else ('A' + idx / 26 - 1).toString() + ('A' + idx % 26).toString()

    private fun displayLen(s: String): Int {
        var n = 0
        for (c in s) {
            val code = c.code
            n += if (code in 0x3000..0x9FFF || code in 0xF900..0xFAFF || code >= 0xFF01) 2 else 1
        }
        return n
    }

    private fun calcWidth(header: String, values: List<String>): Double {
        var max = displayLen(header)
        for (v in values) { val l = displayLen(v); if (l > max) max = l }
        return maxOf(5.0, (max + 2).toDouble())
    }

    private fun sc(col: String, row: Int, v: String, s: Int = 0): String {
        val attr = if (s > 0) """ s="$s"""" else ""
        return """<c r="$col$row"$attr t="inlineStr"><is><t>${v.esc()}</t></is></c>"""
    }

    private fun sheetXml(
        allDays: List<LocalDate>,
        recordMap: Map<String, WorkRecord>,
        columns: List<ColDef>,
        year: Int, month: Int,
        pattern: ReportPattern,
        workingDays: Int,
        hasSig: Boolean = false
    ): SheetOutput {
        val sb = StringBuilder()
        val numCols    = columns.size + 1
        val lastLetter = colLetter(numCols - 1)
        val merges     = mutableListOf<String>()
        val dateFmt    = DateTimeFormatter.ofPattern("d日(E)", Locale.JAPANESE)
        val isoFmt     = DateTimeFormatter.ISO_LOCAL_DATE

        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        sb.append("""<sheetPr><pageSetUpPr fitToPage="1"/></sheetPr>""")
        sb.append("""<sheetFormatPr defaultRowHeight="14" customHeight="1"/>""")

        sb.append("<cols>")
        val dateDisplays = allDays.map { day ->
            val r = recordMap[day.format(isoFmt)]
            if (r != null && r.endDateOffset > 0)
                "${day.format(dateFmt)}〜${day.plusDays(r.endDateOffset.toLong()).format(dateFmt)}"
            else
                day.format(dateFmt)
        }
        sb.append("""<col min="1" max="1" width="${calcWidth("日付", dateDisplays)}" bestFit="1" customWidth="1"/>""")
        columns.forEachIndexed { idx, col ->
            val vals = allDays.mapNotNull { day ->
                recordMap[day.format(isoFmt)]?.let { col.getData(it) }
            } + listOf(col.totalValue)
            val n = idx + 2
            sb.append("""<col min="$n" max="$n" width="${calcWidth(col.header, vals)}" bestFit="1" customWidth="1"/>""")
        }
        sb.append("</cols>")

        sb.append("<sheetData>")

        merges.add("A1:${lastLetter}1")
        sb.append("""<row r="1" ht="26" customHeight="1">""")
        sb.append(sc("A", 1, "${year}年${month}月 ${pattern.title}", s = 3))
        sb.append("</row>")

        sb.append("""<row r="2">""")
        sb.append(sc("A", 2, "作業者", s = 8))
        sb.append(sc("B", 2, pattern.driverName))
        if (numCols >= 2) {
            sb.append(sc(colLetter(numCols - 2), 2, "稼働月", s = 8))
            sb.append(sc(lastLetter, 2, "${year}年${month}月"))
        }
        sb.append("</row>")

        sb.append("""<row r="3">""")
        sb.append(sc("A", 3, "取引先", s = 8))
        sb.append(sc("B", 3, pattern.clientName))
        if (numCols >= 2) {
            sb.append(sc(colLetter(numCols - 2), 3, "締め日", s = 8))
            sb.append(sc(lastLetter, 3, "${pattern.closingDay}日"))
        }
        sb.append("</row>")

        sb.append("""<row r="4" ht="5" customHeight="1"/>""")

        sb.append("""<row r="5">""")
        sb.append(sc("A", 5, "日付", s = 1))
        columns.forEachIndexed { idx, col ->
            sb.append(sc(colLetter(idx + 1), 5, col.header, s = 1))
        }
        sb.append("</row>")

        val consumedDays = mutableSetOf<String>()
        for ((dateStr, record) in recordMap) {
            if (record.endDateOffset > 0) {
                val start = LocalDate.parse(dateStr)
                for (i in 1..record.endDateOffset) {
                    consumedDays.add(start.plusDays(i.toLong()).format(isoFmt))
                }
            }
        }

        allDays.forEachIndexed { ri, day ->
            val row    = ri + 6
            val dayStr = day.format(isoFmt)
            val style  = if (ri % 2 == 0) 6 else 7

            if (consumedDays.contains(dayStr)) {
                sb.append("""<row r="$row">""")
                sb.append(sc("A", row, "", s = style))
                columns.forEachIndexed { ci, _ ->
                    sb.append(sc(colLetter(ci + 1), row, "", s = style))
                }
                sb.append("</row>")
                return@forEachIndexed
            }

            val record = recordMap[dayStr]
            val dateDisplay = if (record != null && record.endDateOffset > 0)
                "${day.format(dateFmt)}〜${day.plusDays(record.endDateOffset.toLong()).format(dateFmt)}"
            else
                day.format(dateFmt)
            sb.append("""<row r="$row">""")
            sb.append(sc("A", row, dateDisplay, s = style))
            columns.forEachIndexed { ci, col ->
                sb.append(sc(colLetter(ci + 1), row, col.getData(record), s = style))
            }
            sb.append("</row>")
        }

        val sumRow = allDays.size + 6
        merges.add("A${sumRow}:C${sumRow}")
        sb.append("""<row r="$sumRow" ht="18" customHeight="1">""")
        sb.append(sc("A", sumRow, "合計（${workingDays}日稼働）", s = 2))
        sb.append(sc("B", sumRow, "", s = 2))
        sb.append(sc("C", sumRow, "", s = 2))
        columns.forEachIndexed { ci, col ->
            if (ci >= 2) sb.append(sc(colLetter(ci + 1), sumRow, col.totalValue, s = 2))
        }
        sb.append("</row>")

        sb.append("""<row r="${sumRow + 1}" ht="6" customHeight="1"/>""")

        val sigLabelRow   = sumRow + 2
        val sigStampRow   = sigLabelRow + 1
        // 全列を2等分して左右に割り当て（列Aからlastまで隙間なし）
        val halfCols      = maxOf(1, numCols / 2)
        val leftStartIdx  = 0
        val leftEndIdx    = halfCols - 1
        val rightStartIdx = halfCols
        val rightEndIdx   = numCols - 1

        merges.add("${colLetter(leftStartIdx)}${sigLabelRow}:${colLetter(leftEndIdx)}${sigLabelRow}")
        merges.add("${colLetter(leftStartIdx)}${sigStampRow}:${colLetter(leftEndIdx)}${sigStampRow}")
        merges.add("${colLetter(rightStartIdx)}${sigLabelRow}:${colLetter(rightEndIdx)}${sigLabelRow}")
        merges.add("${colLetter(rightStartIdx)}${sigStampRow}:${colLetter(rightEndIdx)}${sigStampRow}")

        sb.append("""<row r="$sigLabelRow" ht="16" customHeight="1">""")
        for (ci in leftStartIdx..leftEndIdx)
            sb.append(sc(colLetter(ci), sigLabelRow, if (ci == leftStartIdx) "作業者確認印" else "", s = 10))
        for (ci in rightStartIdx..rightEndIdx)
            sb.append(sc(colLetter(ci), sigLabelRow, if (ci == rightStartIdx) "取引先確認印" else "", s = 10))
        sb.append("</row>")

        sb.append("""<row r="$sigStampRow" ht="80" customHeight="1">""")
        for (ci in leftStartIdx..leftEndIdx)
            sb.append(sc(colLetter(ci), sigStampRow, "", s = 5))
        for (ci in rightStartIdx..rightEndIdx)
            sb.append(sc(colLetter(ci), sigStampRow, "", s = 5))
        sb.append("</row>")

        sb.append("</sheetData>")

        if (merges.isNotEmpty()) {
            sb.append("""<mergeCells count="${merges.size}">""")
            merges.forEach { sb.append("""<mergeCell ref="$it"/>""") }
            sb.append("</mergeCells>")
        }

        sb.append("""<pageMargins left="0.5" right="0.5" top="0.6" bottom="0.6" header="0.3" footer="0.3"/>""")
        sb.append("""<pageSetup paperSize="9" orientation="landscape" fitToWidth="1" fitToHeight="0"/>""")

        if (hasSig) {
            sb.append("""<drawing r:id="rId3"/>""")
        }

        sb.append("</worksheet>")
        return SheetOutput(
            xml          = sb.toString(),
            sigStampRow  = sigStampRow,
            leftStart    = leftStartIdx,
            leftEnd      = leftEndIdx,
            rightStart   = rightStartIdx,
            rightEnd     = rightEndIdx
        )
    }

    private fun drawingXml(driverRelId: Int?, clientRelId: Int?, pos: SheetOutput): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        driverRelId?.let {
            sb.append(picAnchor(it,
                pos.leftStart,  pos.leftEnd + 1,
                pos.sigStampRow - 1, pos.sigStampRow))
        }
        clientRelId?.let {
            sb.append(picAnchor(it,
                pos.rightStart, pos.rightEnd + 1,
                pos.sigStampRow - 1, pos.sigStampRow))
        }
        sb.append("</xdr:wsDr>")
        return sb.toString()
    }

    private fun picAnchor(relId: Int, fromCol: Int, toCol: Int, fromRow: Int, toRow: Int) =
        """<xdr:twoCellAnchor editAs="twoCell"><xdr:from><xdr:col>$fromCol</xdr:col><xdr:colOff>76200</xdr:colOff><xdr:row>$fromRow</xdr:row><xdr:rowOff>76200</xdr:rowOff></xdr:from><xdr:to><xdr:col>$toCol</xdr:col><xdr:colOff>-76200</xdr:colOff><xdr:row>$toRow</xdr:row><xdr:rowOff>-76200</xdr:rowOff></xdr:to><xdr:pic><xdr:nvPicPr><xdr:cNvPr id="$relId" name="sig$relId"/><xdr:cNvPicPr><a:picLocks noChangeAspect="0"/></xdr:cNvPicPr></xdr:nvPicPr><xdr:blipFill><a:blip r:embed="rId$relId"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill><xdr:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="1" cy="1"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></xdr:spPr></xdr:pic><xdr:clientData/></xdr:twoCellAnchor>"""

    private fun sheetRelsXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" Target="../drawings/drawing1.xml"/>
</Relationships>"""

    private fun drawingRelsXml(driverRelId: Int?, clientRelId: Int?): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        driverRelId?.let {
            sb.append("""<Relationship Id="rId$it" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image$it.png"/>""")
        }
        clientRelId?.let {
            sb.append("""<Relationship Id="rId$it" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image$it.png"/>""")
        }
        sb.append("</Relationships>")
        return sb.toString()
    }

    private fun contentTypesXml(hasSig: Boolean = false): String {
        val extra = if (hasSig) """
  <Default Extension="png" ContentType="image/png"/>
  <Override PartName="/xl/drawings/drawing1.xml" ContentType="application/vnd.openxmlformats-officedocument.drawing+xml"/>""" else ""
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml"  ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml"          ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml"            ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>$extra
</Types>"""
    }

    private fun relsXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbookXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="稼働報告書" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

    private fun workbookRelsXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles"    Target="styles.xml"/>
</Relationships>"""

    private fun stylesXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="5">
    <font><sz val="10"/><name val="Yu Gothic"/><family val="2"/></font>
    <font><b/><sz val="10"/><name val="Yu Gothic"/><family val="2"/></font>
    <font><b/><sz val="13"/><name val="Yu Gothic"/><family val="2"/></font>
    <font><b/><sz val="10"/><name val="Yu Gothic"/><family val="2"/><color rgb="FF444444"/></font>
    <font><b/><sz val="10"/><name val="Yu Gothic"/><family val="2"/><color rgb="FFFFFFFF"/></font>
  </fonts>
  <fills count="8">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFBDD7EE"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFDCE6F1"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFFF2CC"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFEBF3FB"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF2E75B6"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFD6E4F0"/></patternFill></fill>
  </fills>
  <borders count="4">
    <border><left/><right/><top/><bottom/><diagonal/></border>
    <border>
      <left  style="thin"  ><color rgb="FF9E9E9E"/></left>
      <right style="thin"  ><color rgb="FF9E9E9E"/></right>
      <top   style="thin"  ><color rgb="FF9E9E9E"/></top>
      <bottom style="thin" ><color rgb="FF9E9E9E"/></bottom>
    </border>
    <border>
      <left  style="medium"><color rgb="FF616161"/></left>
      <right style="medium"><color rgb="FF616161"/></right>
      <top   style="medium"><color rgb="FF616161"/></top>
      <bottom style="medium"><color rgb="FF616161"/></bottom>
    </border>
    <border>
      <left/><right/><top/>
      <bottom style="thin"><color rgb="FF616161"/></bottom>
      <diagonal/>
    </border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="11">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="4" fillId="6" borderId="1" xfId="0"
        applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center" wrapText="1"/>
    </xf>
    <xf numFmtId="0" fontId="1" fillId="4" borderId="1" xfId="0"
        applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1">
      <alignment vertical="center"/>
    </xf>
    <xf numFmtId="0" fontId="2" fillId="3" borderId="0" xfId="0"
        applyFont="1" applyFill="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center"/>
    </xf>
    <xf numFmtId="0" fontId="4" fillId="6" borderId="2" xfId="0"
        applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center"/>
    </xf>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="2" xfId="0" applyBorder="1"/>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0"
        applyBorder="1" applyAlignment="1">
      <alignment vertical="center"/>
    </xf>
    <xf numFmtId="0" fontId="0" fillId="5" borderId="1" xfId="0"
        applyFill="1" applyBorder="1" applyAlignment="1">
      <alignment vertical="center"/>
    </xf>
    <xf numFmtId="0" fontId="4" fillId="6" borderId="1" xfId="0"
        applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center"/>
    </xf>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="3" xfId="0"
        applyBorder="1" applyAlignment="1">
      <alignment vertical="bottom"/>
    </xf>
    <xf numFmtId="0" fontId="1" fillId="7" borderId="1" xfId="0"
        applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center"/>
    </xf>
  </cellXfs>
</styleSheet>"""
}
