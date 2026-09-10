# 展厅机器人助手 (RobotGuide)

专为展厅讲解机器人设计的Android应用，支持固定问答库 + 豆包AI智能辅助、图片展示、视频播放。

## 项目特性

- 📚 **固定问答库** - SQLite本地存储，支持关键词匹配、模糊匹配、N-gram相似度
- 🤖 **豆包AI集成** - 优先匹配固定库，匹配不到自动调用豆包API辅助回答
- 🖼️ **图片展示** - 支持从本地或网络加载图片
- 🎬 **视频播放** - 使用ExoPlayer播放视频
- 🎤 **语音输入** - 集成Android语音识别
- 📺 **大屏幕适配** - 针对机器人10+寸屏幕优化

## 开发环境

- Android Studio Hedgehog (2023.1) 或更高
- JDK 17+
- Gradle 8.0
- minSdk 23 (Android 6.0.1)

## 快速开始

### 1. 导入项目
```bash
# 使用Android Studio打开RobotGuide目录
# File → Open → 选择 RobotGuide 文件夹
```

### 2. 配置豆包API

打开「系统设置」→「AI配置」:
1. 开启「启用AI辅助」
2. 粘贴豆包API Key (在 [火山引擎控制台](https://www.volcengine.com) 获取)
3. 选择模型ID (如 `doubao-pro-32k`)
4. 点击保存（会自动测试连接）

### 3. 添加固定问答

打开「系统设置」→「问答库」→ 点击右上角「+」添加:
- **问题**: 用户可能的提问，如「展厅有什么展品」
- **关键词**: 提高匹配率的词，如「展厅,展品,介绍」
- **回答**: 机器人的标准回答
- **关联媒体**: 可选，设置后AI回复时可触发媒体展示

### 4. 导入到机器人

1. 在Android Studio中 Build → Build APK
2. 将生成的 APK 文件拷贝到机器人（可通过USB或网络）
3. 在机器人上允许安装未知来源应用
4. 安装APK并打开

## 项目结构

```
app/src/main/java/com/robot/guide/
├── App.kt                    # Application初始化
├── data/
│   └── Models.kt            # 数据模型（QAItem, ChatMessage, MediaFile）
├── db/
│   └── DatabaseHelper.kt    # SQLite数据库操作
├── matcher/
│   └── QAMatcher.kt         # 智能问答匹配引擎（模糊匹配+相似度）
├── api/
│   ├── DoubaoClient.kt      # 豆包API客户端
│   └── RobotAIService.kt    # AI服务统一入口
├── ui/
│   ├── MainActivity.kt      # 主界面
│   ├── ChatActivity.kt      # AI对话界面
│   ├── MediaActivity.kt     # 媒体展示界面
│   ├── SettingsActivity.kt  # 系统设置
│   ├── QALibraryActivity.kt # 问答库管理
│   ├── QAEditActivity.kt    # 问答编辑
│   ├── ChatAdapter.kt       # 对话消息适配器
│   ├── QALibraryAdapter.kt  # 问答列表适配器
│   └── QuickActionAdapter.kt# 快捷功能适配器
└── util/
    └── AppSettings.kt       # SharedPreferences设置管理
```

## 问答匹配算法

1. **精确匹配** - 完全相同得100分
2. **关键词匹配** - 优先检查关键词，给予较高基础分(70+)
3. **包含匹配** - 用户问题包含在库中或反之
4. **N-gram相似度** - 基于双字组合的Jaccard相似度 + 最长公共子串

匹配阈值可在设置中调整（默认60）。

## 媒体文件放置

将图片/视频放到以下目录（机器人内部存储或SD卡）:
```
/sdcard/RobotGuide/media/
├── images/      # 图片
└── videos/      # 视频
```

关联媒体时填写文件名即可。

## API配置

豆包AI使用火山引擎 Ark 平台:
- 控制台: https://console.volcengine.com/ark
- 文档: https://www.volcengine.com/docs/82379/1099478

API端点: `https://ark.cn-beijing.volces.com/api/v3/chat/completions`

## 注意事项

1. Android 6.0.1需要运行时权限（存储、麦克风），首次使用会请求
2. 建议将系统休眠时间设为「从不」，保持屏幕常亮
3. 如果AI服务不可用，会自动降级到兜底回复
4. 建议定期备份问答库（设置中有导出功能）

## License

自用项目，如有问题欢迎反馈。
