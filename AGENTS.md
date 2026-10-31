# AGENTS.md

このリポジトリで作業する AI エージェントへ。**最初に [HANDOFF.md](HANDOFF.md) を読んでください**（経緯・方針・ビルド手順・未対応のことがすべて書いてあります）。

## 要点

- あゆさん専用の Android 操作マクロアプリ（`jp.ayu.macro`、Kotlin、minSdk 26 / target 37）。端末は Galaxy S26 Ultra / Android 17。
- 記録はユーザー補助サービスのオーバーレイ、再生は `dispatchGesture`。**root 不要**。
- **他のアプリの画面内容・個人情報を集めない／送らない**（`canRetrieveWindowContent=false`、イベント購読なし）。ユーザー補助 API のポリシーを守る。
- Google Play には公開しない。配布する `standard` フレーバーには**広告・課金・`INTERNET` 権限を入れない**（それらは `monetized` だけ）。
- フローティングは**自動で表示しない**（アプリの「表示」を押したときだけ）。
- 6 言語（en / ja / zh-CN / zh-TW / ko / es）。文言は `tools/strings_data.py` を直して `python3 tools/gen_strings.py`。
- UI は **XML / フレームワークの View だけ**（Compose・AndroidX なし）。[ehmo の android-design-guidelines](docs/agent-skills/android-design-guidelines/SKILL.md) に沿う（監査: `docs/DESIGN_AUDIT_v0.9.md`）。
- デザインの参考元の素材はコピーしない（designeer.xyz は雰囲気だけ、sv-* は MIT の部分を出典つきで、sv-particles とギャラリーは参考だけ）。出典は `THIRD_PARTY_NOTICES.md`。

## ビルド・テスト

```sh
JAVA_HOME=/home/box/opt/jdk-17.0.21+9 ./gradlew assembleStandardDebug            # ビルドは JDK 17
JAVA_HOME=/home/box/opt/jdk-21.0.13+9 ./gradlew testStandardDebugUnitTest lintStandardDebug   # テストは JDK 21（Robolectric / SDK 37）
```

- UI を変えたら `LaunchTest`（Robolectric の起動テスト）を必ず通す。
- 配布前に aapt2 / dexdump / apksigner で確認（手順は HANDOFF.md §5）。
- 上書きインストールには同じデバッグ鍵（`~/.android/debug.keystore`、リポジトリ外）が必要。鍵・トークン・本番 ID はコミットしない。

## あゆさんへの返事

日本語で、親しみやすく簡潔に。新しい版は署名済みデバッグ APK とインストール手順（INSTALL_ja.md）を添えて渡す。
