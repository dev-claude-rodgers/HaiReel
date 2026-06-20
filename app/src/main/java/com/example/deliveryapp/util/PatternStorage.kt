package com.rodgers.routist.util

import android.content.Context
import com.rodgers.routist.model.ReportPattern

object PatternStorage {
    private const val PREFS = "report_patterns"

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getIds(ctx: Context): List<Int> {
        val raw = p(ctx).getString("ids", "") ?: ""
        return if (raw.isBlank()) emptyList()
        else raw.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    private fun putIds(ctx: Context, ids: List<Int>) =
        p(ctx).edit().putString("ids", ids.joinToString(",")).apply()

    fun get(ctx: Context, id: Int): ReportPattern? {
        if (!getIds(ctx).contains(id)) return null
        val sp = p(ctx)
        return ReportPattern(
            id            = id,
            title         = sp.getString("${id}_title",      "稼働報告書") ?: "稼働報告書",
            clientName    = sp.getString("${id}_client",     "") ?: "",
            driverName    = sp.getString("${id}_driver",     "") ?: "",
            closingDay    = sp.getInt(   "${id}_closing",    25),
            deliveryLabel = sp.getString("${id}_deliv_lbl",  "配達件数") ?: "配達件数",
            packageLabel  = sp.getString("${id}_pkg_lbl",    "個数") ?: "個数",
            showTime      = sp.getBoolean("${id}_col_time",  true),
            showDelivery  = sp.getBoolean("${id}_col_deliv", true),
            showPackage   = sp.getBoolean("${id}_col_pkg",   true),
            showDistance  = sp.getBoolean("${id}_col_dist",   true),
            showFuel      = sp.getBoolean("${id}_col_fuel",   true),
            showMeter     = sp.getBoolean("${id}_col_meter",  false),
            showIncome    = sp.getBoolean("${id}_col_income", false),
            showArea      = sp.getBoolean("${id}_col_area",   true),
            showTotal     = sp.getBoolean("${id}_show_total", true),
            showRemarks   = sp.getBoolean("${id}_col_rem",   true),
            paymentType   = sp.getInt(   "${id}_pay_type",   3),
            unitPrice     = sp.getInt(   "${id}_unit_price", 0)
        )
    }

    fun getAll(ctx: Context): List<ReportPattern> = getIds(ctx).mapNotNull { get(ctx, it) }

    fun save(ctx: Context, pattern: ReportPattern) {
        val ids = getIds(ctx).toMutableList()
        if (!ids.contains(pattern.id)) { ids.add(pattern.id); putIds(ctx, ids) }
        p(ctx).edit().apply {
            putString ("${pattern.id}_title",      pattern.title)
            putString ("${pattern.id}_client",     pattern.clientName)
            putString ("${pattern.id}_driver",     pattern.driverName)
            putInt    ("${pattern.id}_closing",    pattern.closingDay)
            putString ("${pattern.id}_deliv_lbl",  pattern.deliveryLabel)
            putString ("${pattern.id}_pkg_lbl",    pattern.packageLabel)
            putBoolean("${pattern.id}_col_time",   pattern.showTime)
            putBoolean("${pattern.id}_col_deliv",  pattern.showDelivery)
            putBoolean("${pattern.id}_col_pkg",    pattern.showPackage)
            putBoolean("${pattern.id}_col_dist",   pattern.showDistance)
            putBoolean("${pattern.id}_col_fuel",   pattern.showFuel)
            putBoolean("${pattern.id}_col_meter",  pattern.showMeter)
            putBoolean("${pattern.id}_col_income", pattern.showIncome)
            putBoolean("${pattern.id}_col_area",   pattern.showArea)
            putBoolean("${pattern.id}_show_total", pattern.showTotal)
            putBoolean("${pattern.id}_col_rem",    pattern.showRemarks)
            putInt    ("${pattern.id}_pay_type",   pattern.paymentType)
            putInt    ("${pattern.id}_unit_price", pattern.unitPrice)
        }.apply()
    }

    fun delete(ctx: Context, id: Int) {
        val ids = getIds(ctx).toMutableList().also { it.remove(id) }
        putIds(ctx, ids)
        p(ctx).edit().apply {
            listOf("title","client","driver","closing","deliv_lbl","pkg_lbl",
                   "col_time","col_deliv","col_pkg","col_dist","col_fuel",
                   "col_meter","col_income","col_area","show_total","col_rem",
                   "pay_type","unit_price")
                .forEach { remove("${id}_$it") }
        }.apply()
        if (getActiveId(ctx) == id) setActiveId(ctx, ids.firstOrNull() ?: -1)
    }

    fun nextId(ctx: Context): Int {
        val sp = p(ctx); val n = sp.getInt("next_id", 0)
        sp.edit().putInt("next_id", n + 1).apply()
        return n
    }

    fun getActiveId(ctx: Context): Int = p(ctx).getInt("active_id", -1)
    fun setActiveId(ctx: Context, id: Int) = p(ctx).edit().putInt("active_id", id).apply()

    fun setNextId(ctx: Context, id: Int) = p(ctx).edit().putInt("next_id", id).apply()

    fun getActive(ctx: Context): ReportPattern = ensureDefault(ctx)

    fun ensureDefault(ctx: Context): ReportPattern {
        val ids = getIds(ctx)
        if (ids.isEmpty()) {
            val def = ReportPattern.default(nextId(ctx))
            save(ctx, def); setActiveId(ctx, def.id); return def
        }
        val aid = getActiveId(ctx)
        val validId = if (ids.contains(aid)) aid else ids.first()
        if (validId != aid) setActiveId(ctx, validId)
        return get(ctx, validId) ?: ReportPattern.default(validId)
    }
}
