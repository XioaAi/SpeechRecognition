# 使用Wav2Vec2在Android上进行语音识别

## 集成步骤

### 1. 把模型文件导入到 assets 目录内

### 2. 添加 pytorch_android_lite 依赖

```
implementation 'org.pytorch:pytorch_android_lite:1.12.2'
```

### 3. 申请录音权限，开启录音，监听语音流信息

```
startRecord()
```

### 4. 将流信息封装到 Tensor 中

```
val inTensorBuffer: FloatBuffer = Tensor.allocateFloatBuffer(recordingLength)
```

### 5. 加载 module

```
module = LiteModuleLoader.load(assetFilePath(applicationContext, "wav2vec2.ptl"))
```

### 5. 调用 module 的 forward() 获取转译结果

```
module?.forward(IValue.from(inTensor))?.toStr()
```


