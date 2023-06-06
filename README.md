## 语音命令（commands） 集成步骤

### 1. 把模型(ptl格式)文件导入到 assets 目录内

```
demo 模型下载地址
https://qpt-dev-voices-1251626569.cos.ap-beijing.myqcloud.com/ican/speechcommand-demo.ptl
```

### 2. 添加 pytorch_android_lite 和 android-vad 依赖,并在 manifest 文件中添加 录音 权限

```
implementation 'org.pytorch:pytorch_android_lite:1.12.2'
implementation 'com.github.gkonovalov:android-vad:1.0.1'
```

```
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

### 3. 申请录音权限，开启录音，监听语音流信息

```
startRecordAndTimer()
```

### 3. 将每一帧的流信息先暂存在一个临时的 ShortArray 中， 通过 Vad 检测每一帧流信息是否是静音，如果是静音，静音累计次数增加1，如果不是静音，则重置静音累计次数，当有连续的 25 帧为静音时且之前包含非静音帧，即认为当前由非静音转换成静音状态，然后在通过临时的 ShortArray 中流信息长度检测语音最短是否超过 300ms，如果未超过则认为是 无效语音，否则 将处理后(截掉25帧的ShortArray)的流信息交给 转译线程池 去处理

```
1s 16K采样率 16位 单声道的音频所采集数据大小为  1s -> 16000 * 16 * 1 = 256000bit = 32000 byte  = 16000 short
正常的音频传输为 20ms 一帧，即 20ms -> 320 short
```

```
saveToTempArray(shortArrayData)
//检测是否静音
val isSpeech = vad?.isSpeech(shortArrayData) ?: false
if (isSpeech) {
    isContainSpeechBuffer = true
    muteTempCount = 0
} else {
    muteTempCount += 1
    //持续0.5s没有声音，认为当前是 静音 状态
    if (muteTempCount >= 25) {
        if (isContainSpeechBuffer) {
            tempArray?.let {
                //语音最少时长为0.3s认为是有效时长
                if (it.size - 320 * 25 >= (16000 * 0.3)) {
                    val newBuffer = it.copyOfRange(0, it.size - 320 * 25)
                    isContainSpeechBuffer = false
                    audioNoMuteToMuteMuteCallBack.callBack(newBuffer)
                }
            }
        }
        muteTempCount = 0
        tempArray = null
    }
}
```

### 4. 转译工作线程中，会先加载模型加载模型获取到 Module

```
val modelPath = assetFilePath(applicationContext, "speechcommand-demo.ptl.ptl")
module = LiteModuleLoader.load(modelPath)
```

### 5. 将VadUtils中回调的流信息转换格式，并获取到 Tensor 对象

```
val recordingLength = shortArrayData.size
val floatInputBuffer = FloatArray(recordingLength)
for (i in 0 until recordingLength) {
    floatInputBuffer[i] = shortArrayData[i] / Short.MAX_VALUE.toFloat()
}

val inTensor: Tensor = Tensor.fromBlob(floatInputBuffer, longArrayOf(1, floatInputBuffer.size.toLong()))
```

### 6. 将 Tensor转换成IValue并调用 Module 的 forward() 获取转译结果 Tensor

```
val tensor = module?.forward(IValue.from(inTensor))?.toTensor()
```

### 7. 解析 Tensor，获取Tensor中的 dataAsLongArray 遍历，并通过ConvertCommandUtils类中covertCommand()从关键词集合中转译成具体命令

```
tensor.dataAsLongArray.forEach {
    result = it
}

ConvertCommandUtils.covertCommand(result)
```

## 语音转译（translation） 集成步骤

### 1. 把模型(bin格式)文件导入到 assets 目录内

### 2. 添加 armeabi-v7a 架构的 so包

把 so 包 复制到 libs目录中，并在 build.gradle 中配置，并在 manifest 文件中添加 录音 权限

```
ndk {
    abiFilters 'armeabi-v7a'
}
```

```
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

### 3. 创建 com.neunit.winner.whisper 目录，把 LibWhisper.kt 文件复制到该目录

### 4. 录音或选择文件（仅支持 wav 格式）

### 5. 通过 RiffWaveHelper 中的 decodeWaveFile() 方法 获取到文件的流信息

```
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
```

### 6. 调用 LibWhisper.kt 中的 WhisperContext.createContextFromAsset() 方法加载模型文件，获取到 WhisperContext 对象

```
whisperContext = WhisperContext.createContextFromAsset(application.assets, "models/${models[0]}")
```

### 7. 调用 WhisperContext 的 transcribeData() 方法，参数为  (5) 中获取到的流信息，返回值即为转译结果

```
val result = whisperContext?.transcribeData(floatArrayData)
```
### 7. 在页面销毁时，调用release()释放资源

```
runBlocking {
    whisperContext?.release()
}
```




