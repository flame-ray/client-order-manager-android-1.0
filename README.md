# 客户订单管理器 Android 1.0

一款无需联网、无需 AI 调用的 Android 客户与订单管理工具。数据仅保存在设备本地，适合记录客户、订单和收款进度。

## 功能

- 客户档案管理：联系方式、来源、状态与备注
- 订单管理：金额、已收款、截止日期和订单状态
- 收款记录：自动同步订单已收金额与待收金额
- 全局搜索：可搜索客户、订单、收款、电话、备注、状态和日期
- 表格导出：导出为 Excel 格式，方便整理与备份
- 备份与导入：可导出和恢复本地数据
- 触控反馈与轮换名人名言
- 离线存储：不上传业务数据，不依赖网络服务

## 下载与安装

从 [Release 页面](https://github.com/flame-ray/client-order-manager-android-1.0/releases/tag/v1.0.1-android) 下载最新版 APK：

[下载 Android APK](https://github.com/flame-ray/client-order-manager-android-1.0/releases/download/v1.0.1-android/client-order-manager-android-1.0.1.apk)

安装新版本前如仍显示旧图标，请先卸载旧版应用后重新安装，以刷新桌面图标缓存。

## 本地构建

环境要求：JDK 17、Android SDK Platform 35、Gradle 8.7。

```bash
gradle -p android assembleDebug
```

生成的 APK 位于：

`android/app/build/outputs/apk/debug/app-debug.apk`

## 数据说明

客户、订单与收款数据存放在本机应用数据中。请定期使用应用内的“备份”或“导出表格”功能保存副本。
