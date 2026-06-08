---
AIGC:
  ContentProducer: 001191110102MAD55U9H0F10002
  ContentPropagator: 001191110102MAD55U9H0F10002
  Label: '1'
  ProduceID: 9d20e439-7ea0-4400-922d-80d434985ebf
  PropagateID: 9d20e439-7ea0-4400-922d-80d434985ebf
  ReservedCode1: 2dda1d90-1965-407a-aa54-22a141ed86c9
  ReservedCode2: 2dda1d90-1965-407a-aa54-22a141ed86c9
---

# 模型配置说明

## 支持的大语言模型

### 1. Gemma 3 4B
- **名称**: Gemma 3 4B
- **描述**: Google发布的轻量级大语言模型，平衡性能与资源消耗
- **文件名**: gemma3-4b-it-q4_k_m.gguf
- **下载URL**: https://example.com/models/gemma3-4b-it-q4_k_m.gguf
- **大小**: 4.8GB (5153960755字节)
- **最低内存要求**: 6GB (6442450944字节)
- **推荐内存**: 8GB (8589934592字节)
- **推荐ASR模型**: sherpa-onnx-zipformer-en-2024-03-28

### 2. Qwen 2.5 7B
- **名称**: Qwen 2.5 7B
- **描述**: 阿里巴巴发布的高性能大语言模型，擅长中文理解
- **文件名**: qwen2.5-7b-instruct-q4_k_m.gguf
- **下载URL**: https://example.com/models/qwen2.5-7b-instruct-q4_k_m.gguf
- **大小**: 8.2GB (8812015616字节)
- **最低内存要求**: 10GB (10737418240字节)
- **推荐内存**: 12GB (12884901888字节)
- **推荐ASR模型**: sherpa-onnx-zipformer-zh-2024-03-28

### 3. Phi-3 4B
- **名称**: Phi-3 4B
- **描述**: Microsoft发布的小型大语言模型，专为Edge设备优化
- **文件名**: phi-3-mini-4k-instruct-q4_k_m.gguf
- **下载URL**: https://example.com/models/phi-3-mini-4k-instruct-q4_k_m.gguf
- **大小**: 4.5GB (4831838208字节)
- **最低内存要求**: 6GB (6442450944字节)
- **推荐内存**: 8GB (8589934592字节)
- **推荐ASR模型**: sherpa-onnx-zipformer-en-2024-03-28

### 4. Llama 3.2 3B
- **名称**: Llama 3.2 3B
- **描述**: Meta发布的最新轻量级模型，高效且准确
- **文件名**: llama-3.2-3b-instruct-q4_k_m.gguf
- **下载URL**: https://example.com/models/llama-3.2-3b-instruct-q4_k_m.gguf
- **大小**: 3.8GB (4089448448字节)
- **最低内存要求**: 5GB (5368709120字节)
- **推荐内存**: 7GB (7516192768字节)
- **推荐ASR模型**: sherpa-onnx-zipformer-multi-2024-03-28

---

## 支持的ASR模型

### 1. 中文语音识别模型
- **名称**: sherpa-onnx-zipformer-zh-2024-03-28
- **描述**: 针对中文优化的高精度语音识别模型
- **文件名**: sherpa-onnx-zipformer-zh-2024-03-28.zip
- **下载URL**: https://example.com/models/sherpa-onnx-zipformer-zh-2024-03-28.zip
- **大小**: 150MB (157286400字节)
- **最低内存要求**: 512MB (536870912字节)
- **推荐内存**: 1GB (1073741824字节)

### 2. 英文语音识别模型
- **名称**: sherpa-onnx-zipformer-en-2024-03-28
- **描述**: 针对英文优化的高精度语音识别模型
- **文件名**: sherpa-onnx-zipformer-en-2024-03-28.zip
- **下载URL**: https://example.com/models/sherpa-onnx-zipformer-en-2024-03-28.zip
- **大小**: 140MB (146800640字节)
- **最低内存要求**: 512MB (536870912字节)
- **推荐内存**: 1GB (1073741824字节)

### 3. 多语言语音识别模型
- **名称**: sherpa-onnx-zipformer-multi-2024-03-28
- **描述**: 支持多种语言的通用语音识别模型
- **文件名**: sherpa-onnx-zipformer-multi-2024-03-28.zip
- **下载URL**: https://example.com/models/sherpa-onnx-zipformer-multi-2024-03-28.zip
- **大小**: 200MB (209715200字节)
- **最低内存要求**: 768MB (805306368字节)
- **推荐内存**: 1.5GB (1610612736字节)

---

## 模型选择建议

### 根据设备性能选择

| 设备RAM | 推荐模型 |
|---------|---------|
| 5GB | Llama 3.2 3B |
| 6GB-7GB | Llama 3.2 3B, Gemma 3 4B, Phi-3 4B |
| 8GB-9GB | Gemma 3 4B, Phi-3 4B |
| 10GB+ | Qwen 2.5 7B |

### 根据使用场景选择

- **中文用户**: 优先选择Qwen 2.5 7B（高性能）或Llama 3.2 3B（轻量）
- **英文用户**: 优先选择Gemma 3 4B或Phi-3 4B
- **多语言需求**: 选择Llama 3.2 3B配合多语言ASR模型
- **低功耗设备**: 选择Llama 3.2 3B或Phi-3 4B
- **追求速度**: 选择较小的模型（Llama 3.2 3B）
- **追求准确性**: 选择较大的模型（Qwen 2.5 7B）

---

## 模型文件格式说明

### LLM模型
- 格式: GGUF (通用Gemma/GPT格式)
- 量化: Q4_K_M (平衡性能和质量的4位量化)
- 兼容性: 与LiteRT-LM完全兼容

### ASR模型
- 格式: ZIP压缩包
- 包含: 模型文件、配置文件、词汇表
- 使用: 需要解压后才能使用


AI生成·[星辰超级智能体](https://agent.teleai.com.cn/super-agent) 生成时间: 2026-06-08 16:56:31