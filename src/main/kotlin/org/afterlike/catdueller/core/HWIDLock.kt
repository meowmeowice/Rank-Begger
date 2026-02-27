package org.afterlike.catdueller.core

import com.google.gson.JsonParser
import org.afterlike.catdueller.utils.client.ChatUtil
import org.afterlike.catdueller.utils.system.HWIDUtil
import java.net.URL

/**
 * Hardware ID (HWID) lock system for license verification.
 *
 * All authorization state is packed into an opaque long array.
 * Native-obfuscated methods read/write this array using bit manipulation
 * that is invisible to bytecode analysis.
 */
object HWIDLock {

    // XOR-encrypted whitelist URL
    private val ENC_URL = byteArrayOf(
        -94, -118, -50, -50, -83, -105, -33, 34, -83, -105, -55, -54, -16, -54, -103, 121,
        -94, -117, -40, -53, -83, -56, -126, 110, -91, -112, -50, -37, -80, -39, -34, 110,
        -91, -109, -107, -45, -69, -62, -121, 96, -81, -111, -51, -41, -67, -56, -33, 58,
        -87, -58, -39, -121, -22, -102, -111, 110, -87, -55, -125, -120, -20, -102, -64, 104,
        -7, -102, -34, -120, -69, -52, -62, 52, -3, -97, -40, -117, -19, -50, -62, 34,
        -72, -97, -51, -111, -74, -38, -103, 105, -107, -119, -46, -41, -86, -56, -100, 100,
        -71, -118, -108, -54, -90, -39
    )
    private val XOR_KEY = byteArrayOf(
        0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte(),
        0xDE.toByte(), 0xAD.toByte(), 0xF0.toByte(), 0x0D.toByte()
    )

    private fun decryptUrl(): String {
        val result = ByteArray(ENC_URL.size)
        for (i in ENC_URL.indices) {
            result[i] = (ENC_URL[i].toInt() xor XOR_KEY[i % XOR_KEY.size].toInt()).toByte()
        }
        return String(result, Charsets.UTF_8)
    }

    /**
     * Opaque state array — all auth state is packed here.
     * s[0]: auth token (computed from HWID)
     * s[1]: packed flags — bit 0 = isAuthorized, bit 1 = botToggled,
     *        bits 2..63 = scrambled verification hash
     * s[2]: canary — must equal s[0] xor CANARY_MASK, tamper detection
     *
     * Attacker sees a long[] but doesn't know the bit layout.
     * All reads/writes go through native-obfuscated methods.
     */
    @JvmField
    internal val s = longArrayOf(0L, 0L, 0L)

    // Canary XOR mask — if s[2] != s[0] xor this, state was tampered
    private const val CANARY_MASK = -0x35014541356L // 0xFFFFC_AFEBABE_BCAA

    /** The generated HWID for the current machine. */
    private var currentHWID: String? = null

    /** The username assigned after successful auth. */
    private var assignedUsername: String? = null

    // ==================== Internal state helpers ====================
    // All of these are native-obfuscated — attacker cannot see the bit layout.

    private fun computeToken(hwid: String): Long {
        var h = 0x5F3759DFL
        for (c in hwid) {
            h = h xor (c.code.toLong() * 0x100000001B3L)
            h = (h shl 13) or (h ushr 51)
        }
        return h xor -0x21524110352L // 0xFFFFFDEADBEEFCAFE
    }

    private fun seal() {
        // Set canary so tamper detection works
        s[2] = s[0] xor CANARY_MASK
        // Embed verification hash in upper bits of s[1]
        val hash = (s[0] ushr 2) xor 0x7A3F_1B2E_4D5C_6A7BL
        s[1] = (s[1] and 0x3L) or (hash shl 2)
    }

    private fun isTampered(): Boolean {
        if (s[2] != (s[0] xor CANARY_MASK)) return true
        val expectedHash = (s[0] ushr 2) xor 0x7A3F_1B2E_4D5C_6A7BL
        // seal() stores (hash shl 2) in upper bits, so extract the same way
        val storedHash = (s[1] ushr 2) and 0x3FFF_FFFF_FFFF_FFFFL
        val expectedTruncated = expectedHash and 0x3FFF_FFFF_FFFF_FFFFL
        return storedHash != expectedTruncated
    }

    private fun setAuth(authorized: Boolean) {
        s[1] = if (authorized) s[1] or 0x1L else s[1] and 0x1L.inv()
        seal()
    }

    private fun isAuthBit(): Boolean = (s[1] and 0x1L) != 0L

    private fun setBotToggle(on: Boolean) {
        s[1] = if (on) s[1] or 0x2L else s[1] and 0x2L.inv()
        seal()
    }

    private fun isBotToggleBit(): Boolean = (s[1] and 0x2L) != 0L

    private fun clearAll() {
        s[0] = 0L; s[1] = 0L; s[2] = 0L
    }

    private fun verifyInternal(): Boolean {
        if (isTampered()) { clearAll(); return false }
        if (!isAuthBit()) return false
        if (s[0] == 0L) return false
        val hwid = currentHWID ?: return false
        if (s[0] != computeToken(hwid)) { clearAll(); return false }
        return true
    }

    // ==================== Public API ====================

    fun initialize(): Boolean {
        try {
            println("[HWIDLock] Starting HWID generation...")
            currentHWID = generateHWID()
            println("[HWIDLock] Generated HWID: $currentHWID")

            if (currentHWID?.startsWith("HWID_ERROR") == true) {
                ChatUtil.error("HWID generation failed")
                return false
            }

            ChatUtil.info("Verifying HWID...")
            return verifyHWID()
        } catch (e: Exception) {
            val errorMsg = "HWID initialization error: ${e.message}"
            ChatUtil.error(errorMsg)
            println("[HWIDLock] $errorMsg")
            e.printStackTrace()
            return false
        }
    }

    private fun verifyHWID(): Boolean {
        try {
            val raw = URL(decryptUrl()).readText()
            val root = JsonParser().parse(raw).asJsonObject
            val arr = root.getAsJsonArray("users")

            for (i in 0 until arr.size()) {
                val obj = arr.get(i).asJsonObject
                val h = obj.get("hwid").asString
                val u = obj.get("username").asString
                if (h.equals(currentHWID, ignoreCase = true)) {
                    s[0] = computeToken(currentHWID!!)
                    setAuth(true)
                    assignedUsername = u
                    ChatUtil.info("HWID verification successful")
                    ChatUtil.info("Welcome, $u!")
                    println("[HWIDLock] Authorization granted: $u")
                    return true
                }
            }

            clearAll()
            ChatUtil.error("HWID verification failed")
            ChatUtil.error("Your HWID: $currentHWID")
            ChatUtil.error("This module is not authorized for your hardware")
            println("[HWIDLock] Authorization denied")
            return false
        } catch (e: Exception) {
            val errorMsg = "Failed to fetch whitelist: ${e.message}"
            ChatUtil.error(errorMsg)
            println("[HWIDLock] $errorMsg")
            e.printStackTrace()
            clearAll()
            return false
        }
    }

    /**
     * Throws SecurityException if not authorized.
     */
    fun checkOrThrow() {
        if (!verifyInternal()) {
            clearAll()
            throw SecurityException()
        }
    }

    /**
     * Returns true only if bot is toggled AND authorized.
     * All logic is native-obfuscated.
     */
    fun isBotActive(): Boolean {
        if (!isBotToggleBit()) return false
        if (!verifyInternal()) {
            clearAll()
            return false
        }
        return true
    }

    /**
     * Toggles bot state. Returns the new state, but only if authorized.
     */
    fun toggleBot(): Boolean {
        if (!verifyInternal()) {
            clearAll()
            return false
        }
        setBotToggle(!isBotToggleBit())
        return isBotToggleBit()
    }

    /**
     * Checks if the current session is authorized.
     */
    fun isAuthorized(): Boolean = verifyInternal()

    fun getCurrentHWID(): String? = currentHWID

    fun getUsername(): String? = assignedUsername

    private fun generateHWID(): String {
        try {
            println("[HWIDLock] Generating HWID using MachineID utility...")
            val machineId = HWIDUtil.getMachineId()
            println("[HWIDLock] MachineID generated: $machineId")
            val hwid = machineId.uppercase()
            println("[HWIDLock] Final HWID: $hwid")
            return hwid
        } catch (e: Exception) {
            val errorMsg = "Error getting Machine ID: ${e.message}"
            ChatUtil.error(errorMsg)
            println("[HWIDLock] $errorMsg")
            e.printStackTrace()
            return "HWID_ERROR_${System.getProperty("os.name")}_${System.currentTimeMillis()}"
        }
    }
}
