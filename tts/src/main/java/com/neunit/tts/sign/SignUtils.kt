package com.neunit.tts.sign

import android.util.Log
import okhttp3.internal.and
import java.lang.String.format
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * @Description 签名
 * @Author ZhaoXiudong
 * @Date 06-07-2023 周三 16:37
 */
class SignUtils {
    companion object {

        private val tag = SignUtils::class.java.name

        /**
         * SHA加密
         */
        fun strToSHA256(strSrc: String): String {
            val bt = strSrc.toByteArray()
            return try {
                val md = MessageDigest.getInstance("SHA-256")
                md.update(bt)
                var result = bytesToHex(md.digest())
                Log.e(tag, "SHA256 结果:$result")
                result
            } catch (e: NoSuchAlgorithmException) {
                Log.e(tag, "SHA256 出现异常:${e.message}")
                ""
            }
        }

        fun strToHMacSHA256(message: String, secret: String): String {
            return try {
                val sha256MAC: Mac = Mac.getInstance("HmacSHA256")
                val secretKey = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
                sha256MAC.init(secretKey)
                val bytes: ByteArray = sha256MAC.doFinal(message.toByteArray())
                val result = bytesToHex(bytes)
                Log.e(tag, "strToHMacSHA256 结果:$result")
                result
            } catch (e: Exception) {
                Log.e(tag, "HMACSHA256 出现异常:${e.message}")
                ""
            }
        }

        /**
         * byte数组转换为16进制字符串
         */
        private fun bytesToHex(b: ByteArray): String {
            val hs = StringBuilder()
            var stmp: String
            var n = 0
            while (n < b.size) {
                stmp = Integer.toHexString(b[n] and 0XFF)
                if (stmp.length == 1) hs.append('0')
                hs.append(stmp)
                n++
            }
            val result = hs.toString().lowercase(Locale.getDefault())
            Log.e(tag, "bytes2Hex 结果:$result")
            return result
        }
    }
}