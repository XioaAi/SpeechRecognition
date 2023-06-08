package com.neunit.tts.model

/**
 * @Description 语音合成返回数据
 * @Author ZhaoXiudong
 * @Date 06-08-2023 周四 15:21
 */
class TTSResultModel(var rid: String, var code: Int, var msg: String, var data: TTSResultData?)

class TTSResultData(var audio: String, var session_id: String)