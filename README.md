# NSE 作業（Android タブレットアプリ）

工場生産管理システム（`C:\dev\nse` の Laravel アプリ）の **作業者画面** をタブレット向け Android ネイティブアプリにしたもの。
作業者が「自分の担当工程を見て、開始 / 完了をつける」ことに特化している。

## 機能（v1）

- **ログイン**（メール / パスワード → Sanctum トークン認証）
- **作業一覧**：担当中の未完了工程を納期日順にグルーピング表示。タップで詳細へ。復帰時に自動更新。
- **作業詳細**：
  - 全工程パイプライン表示
  - ステータスに応じたアクション
    - 待機 → **作業開始**（前工程未完了ならロック表示）
    - 作業中 → **加工完了へ進む** / 中断 / 故障中 / 不良品報告
    - 一時停止 → 再開 / 故障報告
    - 機械停止 → 復旧（待機に戻す）
  - **中断理由**選択ダイアログ（電話対応 / トイレ / 休憩 / その他）
  - **不良品報告**ダイアログ（個数入力）
  - **材料確認**ダイアログ（第一工程開始時、材料到着日かつ未確認の場合）
- **接続先サーバー設定**画面（API ベース URL を端末で変更可能）

## 技術スタック

- Kotlin + Jetpack Compose（Material 3）
- Retrofit + OkHttp + kotlinx.serialization
- DataStore（トークン / 接続先 URL の永続化）
- Navigation Compose
- minSdk 26 / targetSdk 34、縦横両対応（端末の回転に追従 / タブレット想定）

## バックエンド API

`C:\dev\nse` の `routes/api.php`（`app/Http/Controllers/Api/` 配下）をそのまま利用する。主に使用するのは：

| 用途 | メソッド / パス |
| --- | --- |
| ログイン | `POST /api/login` |
| ログアウト | `POST /api/logout` |
| 作業一覧 | `GET /api/tasks` |
| 作業詳細 | `GET /api/tasks/{process}` |
| ステータス更新 | `PATCH /api/orders/{order}/processes/{process}/status` |
| 不良品報告 | `POST /api/orders/{order}/processes/{process}/defect` |
| 材料確認 | `PATCH /api/orders/{order}/material-confirm` |

接続先のデフォルトは `app/build.gradle.kts` の `DEFAULT_API_BASE_URL`
（現在: `https://nagashima0701.sakura.ne.jp/nse/api/`）。アプリ内の設定画面でも上書きできる。

## ビルド / 実行

1. Android Studio で `C:\dev\nse-android` を開く（Gradle 同期）。
2. 実機タブレットまたはエミュレータを接続。
3. Run（▶）でインストール。

CLI からビルドする場合（要 JDK 17+）：

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug
```

生成物: `app/build/outputs/apk/debug/app-debug.apk`

## リリース（署名 / バージョン採番 / 配布）

### 1. 署名キーストアの用意（初回のみ）

```powershell
# プロジェクト直下で実行（JDK 同梱の keytool）
& "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v `
    -keystore nse-worker-release.jks -alias nse-worker `
    -keyalg RSA -keysize 2048 -validity 10000
```

`keystore.properties.example` をコピーして `keystore.properties` を作り、
キーストアのパス・パスワード・エイリアスを記入する。
**`keystore.properties` と `.jks` はコミット禁止（.gitignore 済み）。必ず安全にバックアップする**
（鍵を失うと上書き更新できず、端末で再インストール＝データ消失になる）。

### 2. リリースビルド

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleRelease
```

- `versionCode` は `version.properties` で管理し、**リリースビルド実行時に自動で +1**（ログに `▶ release versionCode = N`）。`versionName` は `version.properties` を手動更新。
- `keystore.properties` があれば署名済み APK、無ければ `app-release-unsigned.apk` を生成。
- 生成物: `app/build/outputs/apk/release/app-release.apk`

### 3. 配布（アプリ内自動アップデート）

1. 署名済み APK を本番サーバーの `storage/app/app-release/nse-worker.apk` に配置。
2. サーバー `.env` を更新し `php artisan config:clear`：
   - `APP_UPDATE_VERSION_CODE`（上記の自動採番された値）
   - `APP_UPDATE_VERSION_NAME`、必要なら `APP_UPDATE_NOTES` / `APP_UPDATE_MANDATORY`
3. 各端末は次回起動時に `GET /api/app/latest` で新版を検知し、更新ダイアログ → DL → インストール。

> 初回のみ、現在の端末には更新チェッカーが入っていないため新 APK を手動インストールする。
> 以降はこの仕組みで配布できる。

## 未実装 / 今後の拡張

- **部分完了（個数単位の開始 / 完了）**：現状の `GET /api/tasks/{process}` が
  `can_partial_complete` やアイテム一覧を返さないため v1 では通常の工程完了フローに統一。
  対応するにはサーバー側 `Api\WorkerController::show` のレスポンス拡張が必要。
- **バーコード / QR スキャン**：作業一覧のショートカット（発注番号でジャンプ）と
  QR ログインはサーバー側 API（`GET /api/tasks/barcode`、`POST /api/login/barcode`）が
  既に存在する。カメラ（CameraX + ML Kit）または業務用ハンディ端末のキーボード入力で
  後付け可能。
- **通知**（`GET /api/notifications/unread`）。
