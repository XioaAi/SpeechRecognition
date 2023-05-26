## 集成步骤

### 1. 把模型(ptl格式)文件导入到 assets 目录内

### 2. 添加 pytorch_android_lite 依赖

```
implementation 'org.pytorch:pytorch_android_lite:1.12.2'
```

### 3. 申请录音权限，开启录音，监听语音流信息

```
startRecord()
```

### 4. 将流信息转换格式并传递到 Tensor 中

```
val inTensor: Tensor = Tensor.fromBlob(floatInputBuffer, longArrayOf(1, recordingLength.toLong()))
```

### 5. 加载模型获取到 Module

```
val modelPath = assetFilePath(applicationContext, "speechcommand-demo.ptl.ptl")
module = LiteModuleLoader.load(modelPath)
```

### 5. 调用 Module 的 forward() 获取转译结果 Tensor

```
val tensor = module?.forward(IValue.from(inTensor))?.toTensor()
```

### 6. 解析 Tensor，获取Tensor中的 dataAsLongArray 遍历，并从关键词集合中转译

```
tensor.dataAsLongArray.forEach {
    result = it
}
keyword[result] ?: keyword[11L]!!
```


