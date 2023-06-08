package com.neunit.tts.http

import android.util.Log
import com.google.gson.Gson
import com.neunit.tts.model.TTSResultData
import com.neunit.tts.model.TTSResultModel
import com.neunit.tts.sign.SignUtils
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.*

interface TTSSynthesisCallBack {
    fun callBack(code: Int, msg: String, data: TTSResultData?)
}

/**
 * @Description TTS合成请求
 * @Author ZhaoXiudong
 * @Date 06-08-2023 周四 16:41
 */
class TTSSynthesisInstance {
    companion object {
        private val tag = TTSActivity::class.java.name

        fun ttsSynthesis(
            params: Map<String, Any?>,
            secretId: String,
            secretKey: String,
            ttsSynthesisCallBack: TTSSynthesisCallBack,
        ) {
            val nonce = UUID.randomUUID().toString()
            val timestamp = Date().time.toString()
            val requestParams = toJSONObject(params).toString()
            val waitSignStr = "${requestParams}_${nonce}_${timestamp}_${secretId}"
            Log.e(tag, "待签名字符串:$waitSignStr")
            val sign = SignUtils.strToHMacSHA256(SignUtils.strToSHA256(waitSignStr), secretKey)
            Log.e(tag, "签名成功:$sign")

            val url = "https://winner-api.neunit.com:18053/cloud/tts/v1/text_to_voice"
            val client = OkHttpClient.Builder().build()

            val headers =
                Headers.Builder().add("Authorization", sign).add("X-NC-SecretId", secretId).add("X-NC-Nonce", nonce)
                    .add("X-NC-Timestamp", timestamp).build()

            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val body: RequestBody = requestParams.toRequestBody(mediaType)
            val request: Request = Request.Builder().url(url).headers(headers).post(body).build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    Log.e(tag, "请求失败:${e.printStackTrace()}")
                    ttsSynthesisCallBack.callBack(-1, e.message ?: "", null)
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = response.body?.string()
                    Log.e(tag, "请求成功:$result")
                    if (result != null) {
                        val ttsResultModel = Gson().fromJson(result, TTSResultModel::class.java)
                        if (ttsResultModel.code == 0) {
                            Log.e(tag, "请求成功:${ttsResultModel.data?.session_id}")
                            ttsSynthesisCallBack.callBack(ttsResultModel.code, ttsResultModel.msg, ttsResultModel.data)
                        } else {
                            Log.e(tag, "请求失败:${ttsResultModel.msg}")
                            ttsSynthesisCallBack.callBack(ttsResultModel.code, ttsResultModel.msg, null)
                        }
                    }

                }
            })
        }

        private fun toJSONObject(params: Map<String, Any?>): JSONObject {
            val param = JSONObject()
            params.forEach {
                param.put(it.key, it.value)
            }
            return param
        }
    }
}