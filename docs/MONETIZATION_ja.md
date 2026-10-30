# 収益化（広告・アプリ内購入）の下準備

このリポジトリには、広告と課金のための **下準備だけ** が入っています。**既定では無効** で、配布している APK（GitHub Releases）には一切含まれません。

| フレーバー | 用途 | 広告/課金 SDK | INTERNET 権限 | 機能 |
|---|---|---|---|---|
| `standard`（既定） | 配布している版 | なし | **なし** | すべて無料・無制限 |
| `monetized` | 将来 Google Play で出す場合の版 | AdMob + UMP + Play Billing | あり（このフレーバーだけ） | 無料版はマクロ保存 3 件まで。購入で解除 |

どちらのフレーバーでも、**他のアプリの画面内容を集めたり送信したりはしません**。記録するのは指の座標と時間だけで、マクロは端末内にのみ保存されます。

## 仕組み

- 共通コード: `app/src/main/java/jp/ayu/macro/monetization/Monetization.kt`
  - `AdsProvider`（同意確認・バナー表示・プライバシー設定）
  - `EntitlementManager`（購入状態・保存上限・購入フロー）
  - `Products`（商品 ID: `remove_ads`, `unlimited_macros`）、`FreeTier`（無料枠 3 件の判定）
- `standard`: `app/src/standard/.../MonetizationModule.kt` … 何もしない実装（広告なし・上限なし・購入ボタン非表示）
- `monetized`: `app/src/monetized/...`
  - `AdMobAds.kt` … UMP で同意を確認 → `MobileAds.initialize` → **メイン画面の下にだけ** アダプティブバナー。フローティングや他のアプリの上には広告を出しません
  - `PlayBillingEntitlements.kt` … 買い切り（INAPP）商品の購入・確認（acknowledge）・端末内キャッシュ
  - `AndroidManifest.xml` … `INTERNET` / `ACCESS_NETWORK_STATE` / `AD_ID` と AdMob アプリ ID（このフレーバーだけにマージされます）
  - `res/values/strings.xml` … 広告・課金があることを書いた説明文（ユーザー補助の説明・プライバシー表示）
- 無料枠: 記録開始時に `FreeTier.canSaveNew(保存数, 上限)` を確認。上限に達していればトーストで案内して記録を始めません

## ビルドのしかた

```sh
# 配布版（既定）
./gradlew assembleStandardDebug      # app/build/outputs/apk/standard/debug/
# 収益化版（テスト用 ID のまま）
./gradlew assembleMonetizedDebug     # app/build/outputs/apk/monetized/debug/
```

Android Studio では「Build Variants」で `standardDebug` / `monetizedDebug` を切り替えます。
※ どちらも applicationId は `jp.ayu.macro` です（Play の商品はパッケージ名に結び付くため）。同じ端末に両方は入りません。

## 本番 ID の設定（コミットしないこと）

既定では Google 公式の **テスト用 ID** を使います（アプリ ID `ca-app-pub-3940256099942544~3347511713`、バナー `ca-app-pub-3940256099942544/9214589741`）。本番 ID は次のどれかで渡します（上ほど優先）。

1. コマンドライン: `./gradlew assembleMonetizedRelease -PadmobAppId=... -PadmobBannerId=...`
2. `~/.gradle/gradle.properties`（リポジトリの外）に `admobAppId=...` / `admobBannerId=...`
3. リポジトリ直下の `monetization.properties`（`.gitignore` 済み。`monetization.properties.example` をコピー）

**開発中は必ずテスト用 ID を使ってください**。本番 ID の広告を自分でタップすると AdMob アカウントが停止されることがあります。実機で本番 ID を試すときは AdMob 管理画面でテストデバイスを登録します。

## AdMob の準備

1. AdMob でアプリを登録（Google Play で公開後にストアと関連付け）→ アプリ ID を取得
2. 広告ユニット「バナー」を作成 → ユニット ID を取得
3. 上の方法で ID を設定してビルド
4. `app-ads.txt` を開発者サイトに置く（AdMob の案内どおり）

## 同意（UMP / GDPR・各州のプライバシー法）

- AdMob 管理画面の「プライバシーとメッセージ」で GDPR（EEA・英国・スイス）向けのメッセージを作成・公開します。必要なら米国州法向けのメッセージも。
- アプリ側はすでに実装済み: 起動時に `requestConsentInfoUpdate` → `loadAndShowConsentFormIfRequired` → `canRequestAds()` が true のときだけ広告 SDK を初期化
- 同意の変更が必要な地域では、メイン画面「プラン」に「広告のプライバシー設定」ボタンが出ます（`showPrivacyOptionsForm`）
- テスト時は `ConsentDebugSettings`（`setDebugGeography(DEBUG_GEOGRAPHY_EEA)` とテストデバイス ID）で EEA の動作を確認できます

## Google Play Console の商品設定

1. `monetized` の **release** ビルド（署名付き AAB）を内部テストトラックにアップロード（`com.android.vending.BILLING` 権限は Billing ライブラリが自動で追加）
2. 「収益化 → 商品 → アプリ内アイテム」で次の **買い切り（消費しない）** 商品を作成し、有効にする
   - `remove_ads` … 広告を非表示
   - `unlimited_macros` … マクロの保存数を無制限
3. 「設定 → ライセンス テスト」に自分の Google アカウントを追加（テスト購入は課金されません）
4. 内部テストでインストールし、メイン画面の「プラン」から購入を確認
5. 推奨: 購入トークンをサーバーで検証する（Google Play Developer API）。今の実装は端末内で判定する簡易版です。サーバーを使う場合もマクロの内容は送らず、購入トークンだけを扱ってください
6. 返金されたら次回起動時の `queryPurchasesAsync` で権利が外れます

## プライバシーポリシー・データ セーフティ（monetized を公開する場合に必須）

- **プライバシーポリシー**（公開 URL）が必要です。少なくとも次を明記:
  - 記録するのは座標と時間だけで、他のアプリの画面内容は読み取らない。マクロは端末内にのみ保存し送信しない
  - 広告に Google AdMob を使い、広告 ID・IP アドレス・端末情報などが Google によって処理されること（Google のポリシーへのリンク）
  - アプリ内購入は Google Play が処理すること
  - 同意の変更方法（「広告のプライバシー設定」）、問い合わせ先
- **データ セーフティ**（Play Console）: AdMob / Play Billing が収集するデータ（端末 ID・広告 ID、アプリの操作、診断、購入履歴 など）を Google のガイドに従って申告。「データは送信時に暗号化」は「はい」
- **広告 ID の申告**: Play Console の「広告 ID」宣言で「使用する（広告目的）」を選ぶ（`AD_ID` 権限はこのフレーバーだけ）
- **「広告を含む」** を Play Console で「はい」にする
- **ユーザー補助 API の申告**: このアプリは AccessibilityService を使うため、Play の「ユーザー補助 API の使用」申告と、アプリ内での目立つ開示（既存の説明画面）が必要です。ユーザー補助で得た情報を広告に使うことは禁止されています（本アプリは使っていません）
- 対象年齢・ファミリー向けポリシーに注意（子ども向けにする場合は広告設定が大きく変わります）

## 確認済みのこと（v0.4 時点）

- `standardDebug` と `monetizedDebug` の両方がビルドでき、ユニットテストが通る
- `standard` の APK は `aapt2 dump badging` で INTERNET 権限なし、`dexdump` で `com.google.android.gms.ads` / `com.google.android.ump` / `com.android.billingclient` のクラスが 0 件
- 配布（GitHub Releases）するのは `standardDebug` のみ
