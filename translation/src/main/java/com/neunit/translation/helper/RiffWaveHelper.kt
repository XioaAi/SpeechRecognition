package com.neunit.translation.helper

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

/**
 * @Description 转换成 wav 格式
 * @Author ZhaoXiudong
 * @Date 05-30-2023 周二 14:24
 */
class RiffWaveHelper {
    companion object {

        private val scope: CoroutineScope = CoroutineScope(Executors.newSingleThreadExecutor().asCoroutineDispatcher())

        suspend fun decodeWaveFile(file: File): FloatArray = withContext(scope.coroutineContext) {
            val baos = ByteArrayOutputStream()
            file.inputStream().use { it.copyTo(baos) }
            val buffer = ByteBuffer.wrap(baos.toByteArray())
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            buffer.position(44)
            val shortBuffer = buffer.asShortBuffer()
            val shortArray = ShortArray(shortBuffer.limit())
            shortBuffer.get(shortArray)
            return@withContext FloatArray(shortArray.size) { index ->
                (shortArray[index] / 32767.0f).coerceIn(-1f..1f)
            }
        }

        suspend fun encodeWaveFile(file: File, data: ShortArray) = withContext(scope.coroutineContext) {
            file.outputStream().use {
                it.write(headerBytes(data.size * 2))
                val buffer = ByteBuffer.allocate(data.size * 2)
                buffer.order(ByteOrder.LITTLE_ENDIAN)
                buffer.asShortBuffer().put(data)
                val bytes = ByteArray(buffer.limit())
                buffer.get(bytes)
                it.write(bytes)
            }
        }

        private fun headerBytes(totalLength: Int): ByteArray {
            require(totalLength >= 44)
            ByteBuffer.allocate(44).apply {
                order(ByteOrder.LITTLE_ENDIAN)

                put('R'.code.toByte())
                put('I'.code.toByte())
                put('F'.code.toByte())
                put('F'.code.toByte())

                putInt(totalLength - 8)

                put('W'.code.toByte())
                put('A'.code.toByte())
                put('V'.code.toByte())
                put('E'.code.toByte())

                put('f'.code.toByte())
                put('m'.code.toByte())
                put('t'.code.toByte())
                put(' '.code.toByte())

                putInt(16)
                putShort(1.toShort())
                putShort(1.toShort())
                putInt(16000)
                putInt(32000)
                putShort(2.toShort())
                putShort(16.toShort())

                put('d'.code.toByte())
                put('a'.code.toByte())
                put('t'.code.toByte())
                put('a'.code.toByte())

                putInt(totalLength - 44)
                position(0)
            }.also {
                val bytes = ByteArray(it.limit())
                it.get(bytes)
                return bytes
            }
        }
    }
}