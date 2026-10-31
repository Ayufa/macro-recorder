# 引き継ぎ資料（HANDOFF）— 操作マクロ / macro-recorder

この資料は、このプロジェクトを別の AI エージェントへ引き継ぐためのものです（2026-10-31 時点）。
まずこのファイルを最後まで読んでから作業してください。秘密情報（トークン・鍵・パスワード）はここには書いていません。

## 1. プロジェクトの概要

- **目的**: あゆさん（オーナー）専用の Android 用「画面操作マクロ」アプリ。タップ・長押し・スワイプを記録し、あとで自動再生する。root 不要。
  - 記録: ユーザー補助サービス（AccessibilityService）が `TYPE_ACCESSIBILITY_OVERLAY` の透明なビューを全画面に重ね、指の座標と時間だけを受け取り、その操作を `dispatchGesture` で下のアプリへ届ける。
  - 再生: `dispatchGesture` で記録したジェスチャーを再生（繰り返し回数・ループ間隔・速度 0.5〜4倍）。
- **リポジトリ**: <https://github.com/Ayufa/macro-recorder>（公開、MIT ライセンス）。開発機での場所は `/workspace/macro-recorder`。
- **パッケージ名**: `jp.ayu.macro`（applicationId / namespace とも）
- **SDK**: minSdk 26（Android 8.0）/ compileSdk 37 / targetSdk 37（Android 17）。AGP 9.5、Gradle 9.8.2（wrapper）、Kotlin。

### 現在のバージョン

| 項目 | 値 |
| --- | --- |
| versionName | **0.9.1** |
| versionCode | **10** |
| コミット | `b1e49e4`（タグ `v0.9.1`。この資料を足したコミットはその後ろ） |
| 配布 APK | `macro-recorder-v0.9.1-debug.apk`（GitHub Releases の v0.9.1 に添付） |
| APK の sha256 | `6f790a869a5ef7b797266616a0d577389630dadccd6fedc06e2e70667ea6b15b` |
| 署名証明書 | `CN=Android Debug, O=Android, C=US`、SHA-256 `97:77:8F:FF:1C:91:AE:17:7D:D3:6D:C8:A2:B2:50:0E:35:9E:E2:AB:55:3E:D0:0F:FA:22:57:B1:11:EC:55:47` |

## 2. あゆさんの端末

- **Galaxy S26 Ultra / Android 17（One UI）**。不具合報告・動作確認はこの端末。
- 古い Android（minSdk 26 = Android 8.0 まで）もできるだけ動くようにする。API ごとの分岐は `Build.VERSION.SDK_INT` で。
- Galaxy 特有の設定（制限付き設定の許可、自動ブロッカー、電池の「制限なし」、スリープさせないアプリ）は `INSTALL_ja.md` に手順がある。

## 3. 守るべき方針（あゆさんと決めたこと）

1. **root 不要**。root 前提の機能は入れない。
2. **ユーザー補助 API のポリシーを守る**。他のアプリの個人情報（画面の内容・文字など）を集めたり送ったりしない。
   - `canRetrieveWindowContent=false`、アクセシビリティイベントは購読しない（`onAccessibilityEvent` は空）。
   - ユーザー補助の設定を開く前に「何に使うか・何を集めないか」の説明（開示）を出す（v0.9〜）。
   - データはアプリ専用領域（`filesDir`）にだけ保存。`allowBackup=false`。
3. **Google Play には公開しない**（あゆさんの判断）。配布は GitHub Releases の APK だけ。
4. **広告と課金は `monetized` フレーバーだけ**。配布する `standard` には広告 SDK・課金 SDK・`INTERNET` 権限を入れない。
   - monetized は「あとで開発側で有効にできる下準備」で、既定は無効・テスト用 ID。配布しない。詳細 `docs/MONETIZATION_ja.md`。
   - 本番の AdMob ID などは `monetization.properties`（gitignore 済み）で渡す。絶対にコミットしない。
5. **フローティングは自動で表示しない**。アプリの「フローティングボタン > 表示」を押したときだけ出す（更新・再起動・ユーザー補助の再接続のときも出さない。v0.5〜）。
6. **6 言語に対応**: English（既定）・日本語・简体中文・繁體中文・한국어・Español。文言を足すときは全言語ぶん用意する（§5「文言」）。
7. **デザイン**:
   - [designeer.xyz](https://designeer.xyz) は**雰囲気だけ**参考にする。All Rights Reserved なので、素材・コード・画像はコピーしない。
   - sv-*（sv-animations / sv-table / sv-blocks / sv-efferd / sv-matrix / sv-agentation）のうち **MIT の部分は出典を書いたうえで移植してよい**（例: v0.8 の BlurFade の値、sv-matrix の path-wave）。**sv-particles は参考だけ**（移植しない）。
   - ギャラリーサイト（Mobbin / Refero / Appshots / 60fps / UI Sources / Chamjo / Page Flows / Appllama / ASO Inspo / Collect UI / Spotted in Prod / Pttrns）は**パターンだけ**参考にし、画像は使わない。
   - 出典は `THIRD_PARTY_NOTICES.md` にまとめる（新しく何かを使ったら必ず追記）。
   - **ehmo の Android デザインガイドライン（android-design-guidelines、MIT）に沿う**。v0.9 で全画面を監査済み → `docs/DESIGN_AUDIT_v0.9.md`。ガイドライン本体は `docs/agent-skills/android-design-guidelines/`（`SKILL.md` と `rules/_sections.md`）。コードのコメントにルール番号（R6.5 など）を書いている。
     - 意図的に適用しなかったもの: パネルのボタンの横幅（412dp 未満の画面では 32〜40dp。48dp にするとはみ出す）、トースト 2 か所（「3秒後に再生」と、フローティング上のメッセージ）。理由は監査資料の「適用しなかったもの」。
8. **画面は XML と View で作る。Compose は使わない**。AndroidX も使っていない（フレームワークのビューだけ。`Ui.kt` のヘルパーで組み立てる）。ガイドラインの Compose の例は同じ意図のフレームワーク版に読み替える。
9. フローティングパネルの形: 小さくすると 48dp の丸いバブル。開くときは右へ広がり、閉じるときは左へ縮む（画面右端では逆向き）。左向き矢印で小さくする、設定ボタンでアプリに戻る。

## 4. コード構成

```
app/src/main/java/jp/ayu/macro/       共通のコード
app/src/standard/java/.../monetization/MonetizationModule.kt   standard 用（広告・課金なし）
app/src/monetized/...                 monetized 用（AdMob・UMP・Play Billing、manifest、文言）
app/src/main/res/                     アイコン（vector）・色・テーマ・文言・xml 設定
app/src/main/assets/fonts/            Funnel Sans / Geist Mono（SIL OFL 1.1、ライセンス文同梱）
app/src/test/java/jp/ayu/macro/       単体テスト（JVM / Robolectric）
tools/                                文言の生成スクリプト
docs/                                 監査・収益化・エージェント用スキル
```

### 主要なファイル（`app/src/main/java/jp/ayu/macro/`）

| ファイル | 主なクラス | 役割 |
| --- | --- | --- |
| `MacroAccessibilityService.kt` | `MacroAccessibilityService` | 記録と再生の本体。オーバーレイ（`TYPE_ACCESSIBILITY_OVERLAY`）でフローティングのパネル／バブルを表示（`showPanel` / `hidePanel` / `setCollapsed` と開閉アニメーション）、記録（`startRecording` → `CaptureView` → `onCaptured` → `passThrough` で下のアプリに届ける → `stopRecording` → 名前入力）、再生（`play` / `runStep` / `stopPlayback`）。画面内容は取得しない |
| `MainActivity.kt` | `MainActivity`（`Activity`） | メイン画面。セットアップ手順カード（ユーザー補助・制限付き設定・電池）、マクロ一覧（行＝選択・設定・︙、長押しメニュー、TalkBack のカスタムアクション）、削除の「元に戻す」、言語・テーマの選択、ショートカット処理、edge-to-edge、クラッシュログのカード |
| `CaptureView.kt` | `CaptureView` | 記録中に全画面に重ねる透明ビュー。自分へのタッチ（座標と時間）だけを受け取り、1 ジェスチャーずつ渡す |
| `GestureFactory.kt` | `GestureFactory` | `Step` から `GestureDescription` を組み立てる |
| `Model.kt` | `TouchPoint` / `Stroke` / `Step` / `Macro`、`ReplayMath`、`PanelMath` | データモデルと JSON 変換、再生時の速度・画面サイズ変換、パネル寸法の計算（純粋関数でテスト対象） |
| `MacroStore.kt` | `MacroStore` | マクロの保存（`files/macros/` の JSON）。外部ストレージ・ネットワークは使わない |
| `CrashLog.kt` | `CrashLog`、`MacroApp`（`Application`） | v0.9.1。最後のクラッシュのスタックトレースを `filesDir/last_crash.txt` に 1 件だけ保存（アプリ版・Android 版・機種・スレッド・スタックのみ）。送信しない。次の起動で「ログをコピー」できる。`MacroApp` がプロセス起動直後に記録を開始 |
| `AppLocales.kt` | `AppLocales`、`ThemeMode` | アプリの言語（13+ は `LocaleManager`、12 以下は保存して Context を差し替え）とテーマ（端末の設定 / ライト / ダーク。12+ は `UiModeManager.setApplicationNightMode`）。壊れた保存値は安全な既定値に戻す |
| `Ui.kt` | `Ui`（`Ui.Type` = M3 の型スケール） | AndroidX なしで見た目を組み立てるヘルパー（色・角丸カード・ピル型ボタン・アイコンボタン 48dp・フォント） |
| `Dialogs.kt` | `DialogCard` / `Dialogs` / `BottomSheet` / `MaxHeightLayout` | アプリ内ダイアログ、下から出るシート（つまみで閉じる）、高さ上限つきレイアウト |
| `SettingsSheet.kt` | `SettingsSheet` | マクロごとの再生設定シート（名前・繰り返し 1/5/10/∞/指定・間隔・速度・削除） |
| `Snackbar.kt` | `Snackbar` | 自前のスナックバー（「元に戻す」、表示時間はユーザー補助の推奨タイムアウトに従う） |
| `FloatingViews.kt` | `DragFrame` / `BubbleView` | ドラッグで動かせるパネルのコンテナ、折りたたみ時の丸いバブル（記録中は赤い点とリング、再生中は進捗リング） |
| `DotMatrixView.kt` | `DotMatrixView` | ドットマトリクスのインジケーター（sv-matrix の path-wave を参考、MIT の値を移植） |
| `GridDrawable.kt` | `GridDrawable` | 見出しの背景の、端に向かって消える方眼 |
| `Motion.kt` | `Motion` | 控えめなアニメーション（BlurFade の値 0.4 秒・6px・easeOut、押し込み）。「アニメーションを削除」設定では動かさない |
| `Texts.kt` | `Texts` | 数値・要約の文字列の組み立て（文言そのものは strings.xml） |
| `monetization/Monetization.kt` | `AdsProvider` / `EntitlementManager` / `Products` / `Monetization` / `FreeTier` | 収益化のインターフェース。実体はフレーバーごとの `MonetizationModule.kt`（standard は何もしない実装） |

### リソースなど

- `AndroidManifest.xml`: `INTERNET` 権限なし（意図的）。権限は `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` だけ。`enableOnBackInvokedCallback`（予測型「戻る」）、`localeConfig`、ショートカット。
- `res/xml/accessibility_service_config.xml`: サービスの設定（画面内容を取得しない）。
- `res/xml/shortcuts.xml`: ランチャーのショートカット 2 つ（ボタンを表示 / 選んだマクロを再生）。
- `res/values*/colors.xml`: `values-v31`（ダイナミックカラー）、`values-v34`（M3 ロール）、`values-night*`（ダーク）。
- `res/values*/strings.xml`: **生成物**。手で直さず `tools/strings_data.py` を編集して生成する。

### テスト（`app/src/test/java/jp/ayu/macro/`）

- `ModelTest.kt`（`ModelTest` / `PanelMathTest` / `FreeTierTest`）: モデル・計算の単体テスト。
- `StringsResourcesTest.kt`: 全言語に同じキーがあり、`%1$s` などの差し込みが一致するか。
- `LaunchTest.kt`（Robolectric）: 実際に `MainActivity` を起動。SDK 26 / 30 / 34 / 35 / 36 / 37、ライト / ダーク × 6 言語、全ダイアログ・シート・スナックバー、ショートカット、壊れた保存値、クラッシュログのカード。v0.9 の起動クラッシュを受けて追加。**UI を触ったら必ず通すこと**。

## 5. ビルドとテスト

### 開発機の環境（あゆさんの共有開発機 `/home/box`）

| もの | 場所 |
| --- | --- |
| JDK 17（ビルド用） | `/home/box/opt/jdk-17.0.21+9` |
| JDK 21（テスト用。Robolectric が SDK 37 を動かすには Java 21 が必要） | `/home/box/opt/jdk-21.0.13+9` |
| Android SDK | `/home/box/opt/android-sdk`（platforms/android-37.0、build-tools/37.0.0、emulator） |
| `local.properties` | `sdk.dir=/home/box/opt/android-sdk`（gitignore 済み。別の環境では自分で作る） |
| gh CLI | Ayufa としてログイン済み（トークンはここに書かない） |

別の環境で作業する場合は、JDK 17 と 21、Android SDK Platform 37 / Build-Tools 37 を入れ、`local.properties` に `sdk.dir=...` を書く。

### コマンド

```sh
cd /workspace/macro-recorder

# ビルド（JDK 17）
export JAVA_HOME=/home/box/opt/jdk-17.0.21+9 PATH=/home/box/opt/jdk-17.0.21+9/bin:$PATH
./gradlew assembleStandardDebug
#  → app/build/outputs/apk/standard/debug/app-standard-debug.apk

# テストと lint（JDK 21）
export JAVA_HOME=/home/box/opt/jdk-21.0.13+9 PATH=/home/box/opt/jdk-21.0.13+9/bin:$PATH
./gradlew testStandardDebugUnitTest lintStandardDebug
#  依存がキャッシュ済みでネットが不安定なときは --offline を付けると速い

# 文言を変えたとき
python3 tools/gen_strings.py      # tools/strings_data.py → 各言語の strings.xml

# 収益化版（配布しない。確認用）
./gradlew assembleMonetizedDebug
```

### APK の確認（配布前に必ず）

```sh
BT=/home/box/opt/android-sdk/build-tools/37.0.0
APK=app/build/outputs/apk/standard/debug/app-standard-debug.apk

# バージョン・SDK・権限（INTERNET が無いこと、権限は REQUEST_IGNORE_BATTERY_OPTIMIZATIONS だけ）
$BT/aapt2 dump badging $APK | grep -E "^package|SdkVersion|uses-permission"

# 広告・課金 SDK が入っていないこと（すべて 0 になるはず）
rm -rf /tmp/dexchk && unzip -o -q $APK 'classes*.dex' -d /tmp/dexchk
for d in /tmp/dexchk/classes*.dex; do $BT/dexdump $d | grep -cE "Lcom/google/android/gms|Lcom/android/billingclient"; done

# 署名（上書きインストールできるよう、証明書の SHA-256 が §1 の値と同じこと）
$BT/apksigner verify --print-certs $APK
```

### リリースの手順

1. `app/build.gradle.kts` の `versionCode` を 1 増やし、`versionName` を上げる（例: 0.9.2 / 11）。
2. `README.md` の「更新履歴」の先頭に、そのバージョンの変更を日本語で 1 項目追加。
3. `INSTALL_ja.md` の見出し・APK のファイル名・「〜を入れている場合はアップデートになります」などの版番号を新しい版に更新。
4. テスト（JDK 21）→ ビルド（JDK 17）→ 上の aapt2 / dexdump / apksigner の確認。
5. APK を `dist/macro-recorder-v<版>-debug.apk` という名前でコピー（`dist/` と `*.apk` は gitignore 済み）。
6. コミット（メッセージは `v0.9.2: 〜` の形式、日本語）→ `git push`。
7. GitHub Release を作る:
   ```sh
   gh release create v0.9.2 dist/macro-recorder-v0.9.2-debug.apk -R Ayufa/macro-recorder \
     --title "v0.9.2" --notes "（変更点の日本語の説明。インストールは INSTALL_ja.md）"
   ```
8. あゆさんに APK とリリースのリンク、インストール手順の要点を送る。

### デバッグ用キーストア（署名）について

- APK は Android Gradle Plugin の**既定のデバッグ鍵**で署名している。場所は開発機の `~/.android/debug.keystore`（= `/home/box/.android/debug.keystore`）。**リポジトリには入っていない**（`.gitignore` で `*.keystore` / `*.jks` を除外）。
- **上書きインストール（アップデート）には同じ署名が必要**。違う鍵で署名した APK は「アプリがインストールされていません」となり、入れ直すにはアンインストールが必要。`allowBackup=false` なので**アンインストールするとマクロが消える**。
- なので、同じ開発機（`/home/box`）で作業するなら、そのままビルドすればよい。別の環境で作業する場合は、この `debug.keystore` をあゆさんから別途受け取り、`~/.android/debug.keystore` に置いてからビルドする。ビルド後は `apksigner verify --print-certs` で SHA-256 が §1 の値と一致するか確認する。
- キーストアを公開リポジトリや公開の場所に置かないこと（誰でもアップデートを装った APK を作れてしまうため）。

## 6. 既知の問題

- **エミュレータが起動しない**: 開発機の AVD `macro30`（API 30）は毎回ブートの途中で止まる（`/dev/kvm` の権限を一時的に変えて試したがだめで、元の 660 に戻してある）。AVD は `macro37` も作ってあるが、画面確認に使えた実績はない。そのため **v0.6 以降は実機・エミュレータでの画面確認（スクリーンショット）ができていない**。代わりに Robolectric の `LaunchTest` で起動とダイアログ類を確認している。
- **v0.9.1 が実機で起動するか、あゆさんの確認待ち**。v0.9 は Android 11 以上で起動直後に落ちていた（`setContentView` より前に `window.insetsController` を呼んでいて DecorView が無く NPE）。v0.9.1 で呼ぶ位置を直し、`window.decorView.windowInsetsController` 経由＋ `runCatching` にした。
- 機能の制限（README の「制限」）: テキスト入力の記録は未対応。スワイプは一定速度でなぞる。記録中、操作を下のアプリに届けている短い間のタッチは記録されない。画面の向き・解像度が違うと座標を比例変換するだけ。

## 7. 未対応・次にやること

1. v0.9.1 が Galaxy S26 Ultra で開くか、あゆさんの返事を待つ。
   - **まだ落ちる場合**: アプリをもう一度開くと「前回、アプリが予期せず終了しました」のカードが出るので、**「ログをコピー」で取ったログ**を貼ってもらう。それをもとに直し、`LaunchTest` に再現ケースを足して v0.9.2 としてリリース。
2. v0.7〜v0.9 の UI へのフィードバック待ち（色・文字の大きさ・︙ メニュー・「元に戻す」・セットアップ手順・触覚など）。もらったら反映する。
3. 余裕があれば: 実機かエミュレータで画面確認できる手段の確保。

## 8. バージョン履歴（要約。詳しくは README の更新履歴）

| 版 | 内容 |
| --- | --- |
| v0.1 | 初版。記録・再生・保存・フローティングボタン |
| v0.2 | フローティングの折りたたみ（丸いバブル）、設定ボタン、Material 3 のデザイン、新アイコン、狭い画面ではみ出す問題の修正 |
| v0.3 | フローティングの開閉アニメーション（右へ広がる／左へ縮む）、小さくするボタンを左向き矢印に、「アニメーションを削除」に対応 |
| v0.4 | 収益化の下準備（standard / monetized フレーバー。既定は無効、配布版に広告・課金なし） |
| v0.5 | フローティングを自動表示しない（「表示」を押したときだけ） |
| v0.6 | 6 言語対応とアプリ内の言語切り替え、再生設定をボトムシートに、designeer.xyz を参考にデザイン刷新、Funnel Sans 同梱 |
| v0.7 | 方眼ヘッダー・等幅バッジ・表形式の一覧・ドットマトリクス・ぼかしフェード（sv-* を参考に作り直し）、Geist Mono 同梱 |
| v0.8 | セットアップ手順、空の状態のボタン、長押しメニューと触覚、設定シートのグループ化（BlurFade / sv-matrix の値を MIT で移植）、ギャラリーのパターンを参考 |
| v0.9 | ehmo の android-design-guidelines に沿って 35 件修正（48dp・M3 型スケール・コントラスト・ダイナミックカラー・テーマ切り替え・元に戻せる削除・︙ メニュー・TalkBack アクション・ユーザー補助の説明・edge-to-edge・予測型「戻る」・ショートカット） |
| v0.9.1 | v0.9 の起動直後のクラッシュ（Android 11+）を修正、壊れた保存値でも起動、端末内だけのクラッシュログ、Robolectric の起動テスト。v0.9 に上書きインストール可（マクロは残る） |

## 9. あゆさんとのやりとり

- **日本語**で、**親しみやすく簡潔に**。専門用語は控えめに。
- 新しい版は、**署名済みのデバッグ APK**（`macro-recorder-v<版>-debug.apk`）を、**インストール手順（INSTALL_ja.md の要点、Galaxy 向け）を添えて**渡す。上書きできるか（マクロが残るか）も一言書く。
- 方針（§3）を変えるような提案は、勝手に実装せず先に聞く。

## 10. 関連ファイル

- `README.md` — 概要・ビルド・更新履歴・制限
- `INSTALL_ja.md` — Galaxy 向けインストール手順（v0.9.1）
- `THIRD_PARTY_NOTICES.md` — フォント・アイコン・sv-*・ガイドライン・参考ギャラリーの出典
- `docs/DESIGN_AUDIT_v0.9.md` — v0.9 のデザイン監査（35 件と、適用しなかったもの）
- `docs/MONETIZATION_ja.md` — monetized フレーバーの有効化の手順
- `docs/agent-skills/android-design-guidelines/` — ehmo のガイドライン本体（MIT、出典は `SOURCE.md`）
- `AGENTS.md` — エージェント向けの要点（この資料への入口）
