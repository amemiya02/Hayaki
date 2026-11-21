<p align="center"><img src="AnkiDroid/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" height="96" alt="Hayaki 微笑折角卡片图标"></p>

<h1 align="center">Hayaki 安卓版</h1>

<p align="center"><b>Hayaki = Hayaku + Anki</b></p>

<p align="center">用自己的 Anki 牌组，离线学习和复习。</p>
<p align="center">
  <a href="https://github.com/amemiya02/Hayaki/releases/latest"><img src="https://img.shields.io/github/v/release/amemiya02/Hayaki?label=Hayaki&amp;color=67539a" alt="最新正式版"></a>
  <a href="https://github.com/amemiya02/Hayaki/actions/workflows/tests_unit.yml?query=branch%3Amain+event%3Apush"><img src="https://github.com/amemiya02/Hayaki/actions/workflows/tests_unit.yml/badge.svg?branch=main&amp;event=push" alt="单元测试"></a>
  <a href="https://github.com/amemiya02/Hayaki/actions/workflows/tests_emulator.yml?query=branch%3Amain+event%3Apush"><img src="https://github.com/amemiya02/Hayaki/actions/workflows/tests_emulator.yml/badge.svg?branch=main&amp;event=push" alt="模拟器测试"></a>
  <a href="https://github.com/amemiya02/Hayaki/actions/workflows/lint.yml?query=branch%3Amain+event%3Apush"><img src="https://github.com/amemiya02/Hayaki/actions/workflows/lint.yml/badge.svg?branch=main&amp;event=push" alt="代码质量检查"></a>
  <a href="#安装与构建"><img src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&amp;logoColor=white" alt="支持 Android 7.0 及以上版本"></a>
  <a href="gradle/libs.versions.toml"><img src="https://img.shields.io/badge/target%20SDK-36-3DDC84?logo=android&amp;logoColor=white" alt="目标 SDK 36"></a>
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&amp;logoColor=white" alt="使用 Kotlin 编写"></a>
  <a href="https://developer.android.com/compose"><img src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?logo=jetpackcompose&amp;logoColor=white" alt="界面使用 Jetpack Compose 构建"></a>
  <a href="COPYING"><img src="https://img.shields.io/github/license/amemiya02/Hayaki?color=blue" alt="许可证：GPL-3.0"></a>
</p>

<p align="center"><a href="README.md">English</a> · <a href="https://github.com/amemiya02/Hayaki-iOS">iPhone 与 iPad 版</a> · <a href="docs/releases/0.2.0.md">0.2.0 版本说明</a></p>

打开 Hayaki，就能看到今天每个牌组有多少张新卡、多少张待复习。可以选一副牌，也可以点「全部学习」，依次学完多个牌组。先回忆，再翻开答案，按记忆程度评分。Anki 调度器会据此安排下次复习。

<p align="center"><img src="docs/images/zh-showcase.webp" width="820" alt="Hayaki 安卓版界面：牌组首页显示今天要学 72 张卡片，带插画的英语六级单词答案及四个评分，以及深色模式下的全年复习统计"></p>

<p align="center"><sub>截图来自真实应用，使用示例集合和模拟的学习记录。</sub></p>

## 用自己的牌组学习

- **导入或制卡。** 导入 Anki 牌组包（`.apkg`），就能把卡片加入现有集合，也可以新建牌组，写下第一条笔记。恢复完整集合包（`.colpkg`）会替换本机集合。
- **查卡和改笔记。** 在浏览页搜索卡片，按牌组、卡片状态、旗标或标签筛选，点进卡片即可编辑笔记。统计页可以查看复习历史和未来的学习量。[查看卡片浏览页截图](docs/images/zh-card-browser.png)
- **调整阅读体验。** 十套配色可与跟随系统、浅色、深色或纯黑外观自由搭配。卡片保留制作者设置的排版、图片、音频和数学公式。手机和大屏都支持放大字号，也可以用 TalkBack 读屏完成翻面和评分。
- **跨设备接着学。** 平时可以全程离线。需要同步时，登录 AnkiWeb，或连接自托管的 Anki 同步服务器。

## 安装与构建

Hayaki 支持 **Android 7.0 及以上版本**，可与 AnkiDroid 同时安装。首次安装可直接下载通用 APK。若之前安装的是特定架构的 APK，请选择相同架构的包覆盖升级。[下载通用 APK](https://github.com/amemiya02/Hayaki/releases/download/v0.2.0/Hayaki-0.2.0-universal.apk) · [其他架构、签名说明和校验值](https://github.com/amemiya02/Hayaki/releases/tag/v0.2.0)

要从源码构建 0.2.0，请安装 JDK 21 和 Android SDK Platform 37，用 `ANDROID_HOME` 指定 SDK 目录，或在 `local.properties` 中设置 `sdk.dir`。连接已开启 USB 调试的设备后运行

```sh
git clone --branch v0.2.0 --depth 1 https://github.com/amemiya02/Hayaki.git
cd Hayaki
./gradlew :AnkiDroid:installPlayDebug
```

只需生成调试版 APK 时，运行 `./gradlew :AnkiDroid:assemblePlayDebug`，产物位于 `AnkiDroid/build/outputs/apk/play/debug/`。

## 项目与许可

Hayaki 独立维护。安卓版基于 AnkiDroid 和 Anki 引擎，界面使用 Kotlin、Jetpack Compose 和 Material 3。Android 和 iOS 共用图标、主题和对外版本号。[AnkiDroid](https://github.com/ankidroid/Anki-Android) · [Anki](https://github.com/ankitects/anki) · [双端维护约定](docs/adr/0007-shared-hayaki-product.md)

安卓应用采用 GPL-3.0-or-later 许可。引擎组件、公共 API 和内置资源各自遵循相应的许可证。[许可证](COPYING) · [许可声明](LICENSES/)
