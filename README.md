# ByMusic

本地安卓音乐播放器。基于 [Chocola](https://github.com/sosauce/Chocola)（sosauce）源码的独立下游发行版，**不是**官方 Chocola，也不是 GitHub fork。

- 应用名：`ByMusic`
- 包名：`c2cw.bymusic`
- 当前版本：`0.1`
- 技术栈：Kotlin、Jetpack Compose、Media3（沿用上游）
- 启动图标：外观设置中可切换 12 款自定义图标，默认 `04_Geometric_Light`；桌面刷新可能有延迟
- 下载：以本仓库 [Releases](https://github.com/C2CWL/ByMusic/releases) 为准

## 说明

- 保留上游版权声明与 GPL-3.0 许可。
- 本版本主要为改名、独立包名与自定义图标；**未改播放内核与动画**，流畅度尚未实机验证。
- 不声称相对上游有性能提升。

## 构建

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

正式签名包由韩国侧 `release.sh bymusic` 基于 CI 的未签名 Release 产物签发。

## 许可与致谢

本项目基于 Chocola，遵循 [GNU GPL v3](LICENSE)。原作者与贡献者的版权归其所有。
上游仓库：https://github.com/sosauce/Chocola
