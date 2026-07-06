package com.rodgers.haireel.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface KnownAddressDao {

    /** 住所・名前の前方一致で検索（どちらかにヒットすれば返す） */
    @Query("""
        SELECT * FROM known_addresses
        WHERE address LIKE :prefix || '%'
           OR (name IS NOT NULL AND name LIKE :prefix || '%')
        ORDER BY deliveryCount DESC, lastDeliveredAt DESC
        LIMIT 15
    """)
    suspend fun searchByPrefix(prefix: String): List<KnownAddressEntity>

    /** 住所・名前の部分一致で検索（2文字以上で使用） */
    @Query("""
        SELECT * FROM known_addresses
        WHERE address LIKE '%' || :keyword || '%'
           OR (name IS NOT NULL AND name LIKE '%' || :keyword || '%')
        ORDER BY deliveryCount DESC, lastDeliveredAt DESC
        LIMIT 15
    """)
    suspend fun searchByKeyword(keyword: String): List<KnownAddressEntity>

    @Query("SELECT * FROM known_addresses WHERE address = :address LIMIT 1")
    suspend fun findByAddress(address: String): KnownAddressEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNew(entity: KnownAddressEntity)

    @Query("""
        UPDATE known_addresses
        SET deliveryCount = deliveryCount + 1,
            lastDeliveredAt = :ts,
            name = CASE WHEN :name IS NOT NULL THEN :name ELSE name END
        WHERE address = :address
    """)
    suspend fun incrementCount(address: String, name: String?, ts: Long)

    @Delete
    suspend fun delete(entity: KnownAddressEntity)

    @Query("SELECT * FROM known_addresses ORDER BY deliveryCount DESC, lastDeliveredAt DESC")
    fun allFlow(): Flow<List<KnownAddressEntity>>

    @Query("SELECT COUNT(*) FROM known_addresses")
    suspend fun count(): Int

    /** 最近の配達先を最大15件（ダイアログ初期表示用） */
    @Query("SELECT * FROM known_addresses ORDER BY deliveryCount DESC, lastDeliveredAt DESC LIMIT 15")
    suspend fun getRecent(): List<KnownAddressEntity>

    /** 全件数 */
    @Query("SELECT COUNT(*) FROM known_addresses")
    suspend fun countAll(): Int

    /**
     * 期間超え かつ 配達回数が少ないものを削除
     * protectMinCount 以上の回数があるものは保護
     */
    @Query("""
        DELETE FROM known_addresses
        WHERE lastDeliveredAt < :cutoffMs
          AND deliveryCount < :protectMinCount
    """)
    suspend fun deleteOldLowCount(cutoffMs: Long, protectMinCount: Int)

    /**
     * 上限件数を超えた分を削除（配達回数少ない・古い順）
     * SQLite は DELETE + ORDER BY + LIMIT が使えないため
     * 削除対象IDをサブクエリで取得する方式
     */
    @Query("""
        DELETE FROM known_addresses
        WHERE address IN (
            SELECT address FROM known_addresses
            ORDER BY deliveryCount ASC, lastDeliveredAt ASC
            LIMIT MAX(0, (SELECT COUNT(*) FROM known_addresses) - :maxCount)
        )
    """)
    suspend fun deleteOverLimit(maxCount: Int)
}
