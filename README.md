# 鋒兄導演（FengBro Director Mobile）

Android 版剪輯軟體，移植自桌面 [鋒兄導演](https://github.com/huang1988pioneer/FengBroDirector)。

桌面原作為 Avalonia + LibVLC + ffmpeg；本專案改為 **Kotlin + Jetpack Compose + Media3**，時間軸語意與字幕解析與桌面版同一套。

## 能做什麼

- 匯入影片、照片、音樂、字幕（SRT / VTT / ASS）與 LRC 歌詞
- 時間軸：畫面、字幕、聲音；同一軌道媒體不重疊
- 預覽：播放頭、字幕、歌詞卡拉 OK 高亮
- 分割、刪除、加字幕、復原／重做
- 可選浮水印：鋒兄 / Papaya Feng / パパイヤ フェン
- 匯出 H.264 MP4（橫向、直向、方形），完成後寫入相簿
- 專案檔 `.fbdproj`（自動暫存，可另存）

## 介面

對齊桌面工作台，依手機／平板切換：

| 裝置 | 編排 |
|------|------|
| **手機直向** | 上預覽、下時間軸；媒體庫與詳細資料由底部滑出 |
| **手機橫向** | 左預覽、右時間軸 |
| **平板** | 左媒體庫 260–300、中預覽器、右詳細 236–268、底三軌（與桌面同一資訊架構） |

| 區域 | 內容 |
|------|------|
| 開始畫面 | 專案名稱、橫向／直向／方形／4K、最近專案 |
| 頂列 | 鋒兄導演、復原／重做、匯出 |
| 預覽 | 目前畫面；點一下播放／暫停 |
| 控制列 | 倒退、播放、快轉、時間碼 |
| 工具列 | 匯入、分割、刪除、加字幕、縮放（手機另有媒體庫／詳細） |
| 時間軸 | 畫面／字幕／聲音三軌；點片段選取，點尺規跳轉 |

用語用一般中文：匯入、時間軸、字幕、匯出。

## 與桌面版的差異

| 項目 | 桌面（Avalonia） | Android（本專案） |
|------|------------------|-------------------|
| UI | 三欄加底軸 | 上預覽、下時間軸、工作表 |
| 預覽 | LibVLC | Media3 CompositionPlayer |
| 匯出 | 本機 ffmpeg | Media3 Transformer（與預覽共用 Composition） |
| 檔案 | 原生路徑 + 拖放 | Storage Access Framework，複製進 App 目錄 |
| 進階濾鏡 | ffmpeg filtergraph | 預覽／匯出先做翻轉、旋轉、字幕與浮水印 |

## 下載

正式 APK 見 [Releases](https://github.com/huang1988pioneer/FengBroDirectorMobile/releases)。打 `v*` 標籤會由 GitHub Actions 自動建置並上傳。

## 建置

需求：

- JDK 17+
- Android SDK（compileSdk 36、targetSdk 35）
- Android Studio 或命令列 Gradle

```bash
# Windows
.\gradlew.bat :core:test
.\gradlew.bat :app:assembleRelease
```

產出 APK：`app/build/outputs/apk/release/app-release.apk`

第一次建置會下載 Gradle 與 Android 依賴，需網路。

## 來源

移植參考：<https://github.com/huang1988pioneer/FengBroDirector>
