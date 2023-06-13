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
### 8. 在页面销毁时，调用release()释放资源

```
runBlocking {
    whisperContext?.release()
}
```