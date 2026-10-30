# -*- coding: utf-8 -*-
"""v0.6 の文言（6言語）。tools/gen_strings.py が res/values*/strings.xml を作る。
キー -> {言語: 文字列}。plurals は {言語: {quantity: 文字列}}。"""

LANGS = ["en", "ja", "zh-rCN", "zh-rTW", "ko", "es"]

# 翻訳しない（全言語共通）
FIXED = {
    "version_label": "v%1$s",
    "value_speed": "%1$s×",
    "lang_en": "English",
    "lang_ja": "日本語",
    "lang_zh_cn": "简体中文",
    "lang_zh_tw": "繁體中文",
    "lang_ko": "한국어",
    "lang_es": "Español",
}

S = {
"app_name": dict(en="Macro Recorder", ja="操作マクロ", **{"zh-rCN": "操作宏", "zh-rTW": "操作巨集"}, ko="조작 매크로", es="Grabador de macros"),
"service_label": dict(en="Macro Recorder (record & play)", ja="操作マクロ（記録・再生）", **{"zh-rCN": "操作宏（录制与播放）", "zh-rTW": "操作巨集（錄製與播放）"}, ko="조작 매크로 (녹화·재생)", es="Grabador de macros (grabar y reproducir)"),
"service_summary": dict(
    en="Records screen actions (taps, long presses, swipes) and plays them back automatically later.",
    ja="画面操作（タップ・長押し・スワイプ）を記録し、あとで自動で再生します。",
    **{"zh-rCN": "录制屏幕操作（点按、长按、滑动），之后自动重放。",
       "zh-rTW": "錄製螢幕操作（輕觸、長按、滑動），之後自動重播。"},
    ko="화면 조작(탭, 길게 누르기, 스와이프)을 녹화하고 나중에 자동으로 재생합니다.",
    es="Graba acciones en pantalla (toques, pulsaciones largas y deslizamientos) y las reproduce automáticamente más tarde."),
"service_description": dict(
    en="This service records the coordinates and timing of taps, long presses and swipes on the screen only while you have pressed Record, and performs the same actions again when you press Play.\n\n[Features used]\n• Performing gestures (to replay recorded actions and to pass your touches through to the app underneath while recording)\n• Buttons shown on top of the screen (Record, Play, Stop)\n\n[How your data is handled]\n• Only finger positions (coordinates) and timing are recorded. The content, text, passwords and personal information of other apps are never read.\n• Recorded macros are stored only inside this app on this device.\n• The app has no internet permission, so no data is ever sent off the device.\n• You can delete macros at any time from the list in the app.",
    ja="このサービスは、あなたが「記録」を押している間だけ、画面上のタップ・長押し・スワイプの座標と時間を記録し、「再生」を押したときにその操作を同じように実行するために使います。\n\n【使う機能】\n・ジェスチャーの実行（記録した操作の再生と、記録中の操作を下のアプリに届けるため）\n・画面の上に重ねて表示するボタン（記録・再生・停止）\n\n【データの取り扱い】\n・記録するのは、指の位置（座標）と時間だけです。他のアプリの画面内容・文字・パスワード・個人情報は読み取りません。\n・記録したマクロはこの端末のアプリ内にのみ保存されます。\n・インターネット権限を持たないため、データを外部に送信することはありません。\n・マクロはアプリ内の一覧からいつでも削除できます。",
    **{"zh-rCN": "此服务仅在你按下“录制”期间，记录屏幕上点按、长按和滑动的坐标与时间，并在你按下“播放”时以相同方式执行这些操作。\n\n【使用的功能】\n・执行手势（用于重放录制的操作，以及在录制期间把你的操作传递给下方的应用）\n・显示在屏幕上层的按钮（录制、播放、停止）\n\n【数据处理】\n・只记录手指的位置（坐标）和时间。不会读取其他应用的屏幕内容、文字、密码或个人信息。\n・录制的宏只保存在此设备上的本应用内。\n・本应用没有网络权限，因此不会向外部发送任何数据。\n・你可以随时在应用内的列表中删除宏。",
       "zh-rTW": "此服務僅在你按下「錄製」期間，記錄螢幕上輕觸、長按和滑動的座標與時間，並在你按下「播放」時以相同方式執行這些操作。\n\n【使用的功能】\n・執行手勢（用於重播錄製的操作，以及在錄製期間將你的操作傳遞給下方的應用程式）\n・顯示在螢幕上層的按鈕（錄製、播放、停止）\n\n【資料處理】\n・只記錄手指的位置（座標）和時間。不會讀取其他應用程式的畫面內容、文字、密碼或個人資料。\n・錄製的巨集只會儲存在此裝置上的本應用程式內。\n・本應用程式沒有網路權限，因此不會向外部傳送任何資料。\n・你可以隨時在應用程式內的清單中刪除巨集。"},
    ko="이 서비스는 사용자가 '녹화'를 누른 동안에만 화면의 탭, 길게 누르기, 스와이프의 좌표와 시간을 기록하고, '재생'을 누르면 같은 조작을 그대로 실행하는 데 사용됩니다.\n\n[사용하는 기능]\n• 제스처 실행(녹화한 조작을 재생하고, 녹화 중의 조작을 아래 앱에 전달하기 위해)\n• 화면 위에 겹쳐 표시하는 버튼(녹화, 재생, 정지)\n\n[데이터 처리]\n• 기록하는 것은 손가락의 위치(좌표)와 시간뿐입니다. 다른 앱의 화면 내용, 텍스트, 비밀번호, 개인정보는 읽지 않습니다.\n• 녹화한 매크로는 이 기기의 앱 안에만 저장됩니다.\n• 인터넷 권한이 없으므로 데이터를 외부로 전송하지 않습니다.\n• 매크로는 앱의 목록에서 언제든지 삭제할 수 있습니다.",
    es="Este servicio registra las coordenadas y la duración de los toques, pulsaciones largas y deslizamientos en la pantalla solo mientras tienes pulsado Grabar, y repite esas mismas acciones cuando pulsas Reproducir.\n\n[Funciones que usa]\n• Realizar gestos (para reproducir las acciones grabadas y para pasar tus toques a la app que hay debajo mientras grabas)\n• Botones superpuestos en la pantalla (Grabar, Reproducir, Detener)\n\n[Tratamiento de los datos]\n• Solo se registran la posición de los dedos (coordenadas) y los tiempos. Nunca se lee el contenido, el texto, las contraseñas ni los datos personales de otras apps.\n• Las macros grabadas se guardan únicamente dentro de esta app en este dispositivo.\n• La app no tiene permiso de acceso a internet, así que nunca envía datos fuera del dispositivo.\n• Puedes eliminar las macros en cualquier momento desde la lista de la app."),
"privacy_note": dict(
    en="Only finger positions and timing are recorded; the screen content of other apps is never read. Data stays on this device only, and the app has no internet permission.",
    ja="記録するのは指の位置と時間だけで、他のアプリの画面内容は読み取りません。データはこの端末内にのみ保存され、インターネット権限はありません。",
    **{"zh-rCN": "只记录手指的位置和时间，不会读取其他应用的屏幕内容。数据只保存在此设备上，本应用也没有网络权限。",
       "zh-rTW": "只記錄手指的位置和時間，不會讀取其他應用程式的畫面內容。資料只儲存在此裝置上，本應用程式也沒有網路權限。"},
    ko="손가락의 위치와 시간만 기록하며, 다른 앱의 화면 내용은 읽지 않습니다. 데이터는 이 기기에만 저장되며, 인터넷 권한이 없습니다.",
    es="Solo se registran la posición de los dedos y los tiempos; nunca se lee el contenido de la pantalla de otras apps. Los datos se quedan en este dispositivo y la app no tiene permiso de acceso a internet."),

"tagline": dict(en="Record taps and swipes, then play them back automatically", ja="タップ・スワイプを記録して、自動で再生", **{"zh-rCN": "录制点按和滑动，然后自动重放", "zh-rTW": "錄製輕觸和滑動，然後自動重播"}, ko="탭과 스와이프를 녹화해 자동으로 재생", es="Graba toques y deslizamientos y reprodúcelos automáticamente"),
"section_how_to": dict(en="How to use", ja="使い方", **{"zh-rCN": "使用方法", "zh-rTW": "使用方式"}, ko="사용 방법", es="Cómo se usa"),
"section_macros": dict(en="Saved macros", ja="保存したマクロ", **{"zh-rCN": "已保存的宏", "zh-rTW": "已儲存的巨集"}, ko="저장한 매크로", es="Macros guardadas"),
"section_plan": dict(en="Plan", ja="プラン", **{"zh-rCN": "方案", "zh-rTW": "方案"}, ko="플랜", es="Plan"),
"section_app_settings": dict(en="App settings", ja="アプリの設定", **{"zh-rCN": "应用设置", "zh-rTW": "應用程式設定"}, ko="앱 설정", es="Ajustes de la app"),
"howto_1": dict(en="Tap the floating Record button to start, then use your phone as usual", ja="フローティングの記録ボタンで記録開始 → ふつうに操作", **{"zh-rCN": "点按悬浮的录制按钮开始录制 → 照常操作", "zh-rTW": "輕觸浮動的錄製按鈕開始錄製 → 照常操作"}, ko="플로팅 녹화 버튼을 눌러 녹화 시작 → 평소처럼 조작", es="Pulsa el botón flotante de grabar para empezar y usa el móvil con normalidad"),
"howto_2": dict(en="Tap it again to stop recording, then name and save the macro", ja="もう一度押すと記録終了。名前を付けて保存", **{"zh-rCN": "再按一次即可结束录制，命名后保存", "zh-rTW": "再按一次即可結束錄製，命名後儲存"}, ko="한 번 더 누르면 녹화 종료. 이름을 붙여 저장", es="Vuelve a pulsarlo para terminar y guarda la macro con un nombre"),
"howto_3": dict(en="Play runs the ★ selected macro; Stop ends playback", ja="再生ボタンで ★ 選択中のマクロを再生、停止で止まります", **{"zh-rCN": "播放按钮会播放 ★ 选中的宏，按停止即可停下", "zh-rTW": "播放按鈕會播放 ★ 選取的巨集，按停止即可停下"}, ko="재생 버튼으로 ★ 선택한 매크로를 재생하고, 정지로 멈춥니다", es="Reproducir ejecuta la macro ★ seleccionada; Detener la para"),
"howto_4": dict(en="Minimize shrinks the buttons to a round icon; tap it to bring them back", ja="小さくするボタンで丸いアイコンに。タップで元に戻ります", **{"zh-rCN": "最小化按钮会把按钮收成圆形图标，点按即可恢复", "zh-rTW": "最小化按鈕會把按鈕收成圓形圖示，輕觸即可還原"}, ko="최소화 버튼으로 동그란 아이콘이 되며, 탭하면 원래대로 돌아옵니다", es="Minimizar reduce los botones a un icono redondo; tócalo para volver a abrirlos"),
"howto_5": dict(en="The settings button brings you back to this screen", ja="設定ボタンでこの画面に戻れます", **{"zh-rCN": "设置按钮可回到此画面", "zh-rTW": "設定按鈕可回到此畫面"}, ko="설정 버튼으로 이 화면에 돌아올 수 있습니다", es="El botón de ajustes te trae de vuelta a esta pantalla"),

"language": dict(en="Language", ja="言語", **{"zh-rCN": "语言", "zh-rTW": "語言"}, ko="언어", es="Idioma"),
"lang_system": dict(en="System default", ja="端末の言語に従う", **{"zh-rCN": "跟随系统", "zh-rTW": "跟隨系統"}, ko="시스템 기본값", es="Idioma del sistema"),

"plan_unlimited": dict(en="Saved macros: unlimited", ja="マクロの保存数: 無制限", **{"zh-rCN": "可保存的宏：无限制", "zh-rTW": "可儲存的巨集：無限制"}, ko="매크로 저장 개수: 무제한", es="Macros guardadas: ilimitadas"),
"plan_owned": dict(en="%1$s (purchased)", ja="%1$s（購入済み）", **{"zh-rCN": "%1$s（已购买）", "zh-rTW": "%1$s（已購買）"}, ko="%1$s (구매함)", es="%1$s (comprado)"),
"plan_remove_ads": dict(en="Remove ads", ja="広告を非表示にする", **{"zh-rCN": "移除广告", "zh-rTW": "移除廣告"}, ko="광고 제거", es="Quitar anuncios"),
"plan_unlimited_macros": dict(en="Save unlimited macros", ja="マクロを無制限に保存", **{"zh-rCN": "无限制保存宏", "zh-rTW": "無限制儲存巨集"}, ko="매크로 무제한 저장", es="Guardar macros sin límite"),
"plan_ad_privacy": dict(en="Ad privacy settings", ja="広告のプライバシー設定", **{"zh-rCN": "广告隐私设置", "zh-rTW": "廣告隱私設定"}, ko="광고 개인정보 설정", es="Privacidad de los anuncios"),

"toast_battery_already": dict(en="Already excluded", ja="すでに除外されています", **{"zh-rCN": "已排除", "zh-rTW": "已排除"}, ko="이미 제외되어 있습니다", es="Ya está excluida"),
"status_service": dict(en="Accessibility service", ja="ユーザー補助サービス", **{"zh-rCN": "无障碍服务", "zh-rTW": "無障礙服務"}, ko="접근성 서비스", es="Servicio de accesibilidad"),
"status_service_running": dict(en="Running", ja="動作中", **{"zh-rCN": "运行中", "zh-rTW": "執行中"}, ko="실행 중", es="En funcionamiento"),
"status_service_stuck": dict(en="On but not responding (turn it off and on again)", ja="オンですが応答がありません（一度オフ→オンに）", **{"zh-rCN": "已开启但没有响应（请关闭后重新开启）", "zh-rTW": "已開啟但沒有回應（請關閉後重新開啟）"}, ko="켜져 있지만 응답이 없습니다(껐다가 다시 켜 주세요)", es="Activado pero no responde (desactívalo y vuelve a activarlo)"),
"status_service_off": dict(en="Off: turn on \"%1$s\"", ja="オフ：「%1$s」をオンにしてください", **{"zh-rCN": "已关闭：请开启“%1$s”", "zh-rTW": "已關閉：請開啟「%1$s」"}, ko="꺼짐: '%1$s'을(를) 켜 주세요", es="Desactivado: activa «%1$s»"),
"action_open": dict(en="Open", ja="開く", **{"zh-rCN": "打开", "zh-rTW": "開啟"}, ko="열기", es="Abrir"),
"status_battery": dict(en="Battery optimization", ja="電池の最適化", **{"zh-rCN": "电池优化", "zh-rTW": "電池最佳化"}, ko="배터리 최적화", es="Optimización de batería"),
"status_battery_ok": dict(en="Excluded", ja="除外済み", **{"zh-rCN": "已排除", "zh-rTW": "已排除"}, ko="제외됨", es="Excluida"),
"status_battery_on": dict(en="Optimized (the app may be stopped)", ja="最適化中（停止されることがあります）", **{"zh-rCN": "已优化（应用可能会被停止）", "zh-rTW": "已最佳化（應用程式可能會被停止）"}, ko="최적화 중(앱이 중지될 수 있음)", es="Optimizada (la app puede detenerse)"),
"action_exclude": dict(en="Exclude", ja="除外", **{"zh-rCN": "排除", "zh-rTW": "排除"}, ko="제외", es="Excluir"),
"status_panel": dict(en="Floating buttons", ja="フローティングボタン", **{"zh-rCN": "悬浮按钮", "zh-rTW": "浮動按鈕"}, ko="플로팅 버튼", es="Botones flotantes"),
"status_panel_shown": dict(en="Showing", ja="表示中", **{"zh-rCN": "显示中", "zh-rTW": "顯示中"}, ko="표시 중", es="Visibles"),
"status_panel_hidden": dict(en="Hidden", ja="非表示", **{"zh-rCN": "已隐藏", "zh-rTW": "已隱藏"}, ko="숨김", es="Ocultos"),
"action_show": dict(en="Show", ja="表示", **{"zh-rCN": "显示", "zh-rTW": "顯示"}, ko="표시", es="Mostrar"),
"action_app_info": dict(en="Open app info (e.g. allow restricted settings)", ja="アプリ情報を開く（制限付き設定の許可など）", **{"zh-rCN": "打开应用信息（如允许受限设置）", "zh-rTW": "開啟應用程式資訊（如允許受限制的設定）"}, ko="앱 정보 열기(제한된 설정 허용 등)", es="Abrir información de la app (p. ej., permitir ajustes restringidos)"),

"empty_title": dict(en="No macros yet", ja="まだマクロがありません", **{"zh-rCN": "还没有宏", "zh-rTW": "還沒有巨集"}, ko="아직 매크로가 없습니다", es="Aún no hay macros"),
"empty_body": dict(en="Create one with the floating Record button", ja="フローティングの記録ボタンから作成できます", **{"zh-rCN": "可通过悬浮的录制按钮创建", "zh-rTW": "可透過浮動的錄製按鈕建立"}, ko="플로팅 녹화 버튼으로 만들 수 있습니다", es="Crea una con el botón flotante de grabar"),
"selected_badge": dict(en="Selected", ja="選択中", **{"zh-rCN": "已选择", "zh-rTW": "已選取"}, ko="선택됨", es="Seleccionada"),
"macro_summary": dict(en="%1$s (%2$s)", ja="%1$s（%2$s）", **{"zh-rCN": "%1$s（%2$s）", "zh-rTW": "%1$s（%2$s）"}, ko="%1$s (%2$s)", es="%1$s (%2$s)"),
"chip_repeat": dict(en="Repeat %1$s", ja="繰り返し %1$s", **{"zh-rCN": "重复 %1$s", "zh-rTW": "重複 %1$s"}, ko="반복 %1$s", es="Repetir %1$s"),
"chip_interval": dict(en="Interval %1$s", ja="間隔 %1$s", **{"zh-rCN": "间隔 %1$s", "zh-rTW": "間隔 %1$s"}, ko="간격 %1$s", es="Intervalo %1$s"),
"chip_speed": dict(en="%1$s× speed", ja="%1$s倍速", **{"zh-rCN": "%1$s 倍速", "zh-rTW": "%1$s 倍速"}, ko="%1$s배속", es="Velocidad %1$s×"),
"action_run": dict(en="Run", ja="実行", **{"zh-rCN": "运行", "zh-rTW": "執行"}, ko="실행", es="Ejecutar"),
"desc_select": dict(en="Select (used by the floating Play button)", ja="選択（フローティングの再生で使う）", **{"zh-rCN": "选择（供悬浮的播放按钮使用）", "zh-rTW": "選取（供浮動的播放按鈕使用）"}, ko="선택(플로팅 재생 버튼에서 사용)", es="Seleccionar (la usa el botón flotante de reproducir)"),
"toast_selected": dict(en="The floating Play button will play this macro", ja="フローティングの再生ボタンで再生されます", **{"zh-rCN": "悬浮的播放按钮将播放此宏", "zh-rTW": "浮動的播放按鈕將播放此巨集"}, ko="플로팅 재생 버튼으로 이 매크로가 재생됩니다", es="El botón flotante de reproducir usará esta macro"),
"desc_settings": dict(en="Playback settings", ja="再生設定", **{"zh-rCN": "播放设置", "zh-rTW": "播放設定"}, ko="재생 설정", es="Ajustes de reproducción"),
"desc_rename": dict(en="Rename", ja="名前を変更", **{"zh-rCN": "重命名", "zh-rTW": "重新命名"}, ko="이름 변경", es="Cambiar nombre"),
"delete": dict(en="Delete", ja="削除", **{"zh-rCN": "删除", "zh-rTW": "刪除"}, ko="삭제", es="Eliminar"),
"toast_enable_service_first": dict(en="Turn on \"%1$s\" in Accessibility first", ja="先にユーザー補助で「%1$s」をオンにしてください", **{"zh-rCN": "请先在无障碍中开启“%1$s”", "zh-rTW": "請先在無障礙中開啟「%1$s」"}, ko="먼저 접근성에서 '%1$s'을(를) 켜 주세요", es="Primero activa «%1$s» en Accesibilidad"),
"toast_play_in_3s": dict(en="Playing in 3 seconds. Open the target app.", ja="3秒後に再生します。対象のアプリを開いてください", **{"zh-rCN": "3 秒后开始播放，请打开目标应用", "zh-rTW": "3 秒後開始播放，請開啟目標應用程式"}, ko="3초 후 재생합니다. 대상 앱을 열어 주세요", es="Se reproducirá en 3 segundos. Abre la app de destino."),

"name_hint": dict(en="Macro name", ja="マクロの名前", **{"zh-rCN": "宏名称", "zh-rTW": "巨集名稱"}, ko="매크로 이름", es="Nombre de la macro"),
"rename_title": dict(en="Rename", ja="名前を変更", **{"zh-rCN": "重命名", "zh-rTW": "重新命名"}, ko="이름 변경", es="Cambiar nombre"),
"save": dict(en="Save", ja="保存", **{"zh-rCN": "保存", "zh-rTW": "儲存"}, ko="저장", es="Guardar"),
"cancel": dict(en="Cancel", ja="キャンセル", **{"zh-rCN": "取消", "zh-rTW": "取消"}, ko="취소", es="Cancelar"),
"close": dict(en="Close", ja="閉じる", **{"zh-rCN": "关闭", "zh-rTW": "關閉"}, ko="닫기", es="Cerrar"),
"delete_title": dict(en="Delete this macro?", ja="削除しますか？", **{"zh-rCN": "要删除吗？", "zh-rTW": "要刪除嗎？"}, ko="삭제할까요?", es="¿Eliminar esta macro?"),
"delete_message": dict(en="\"%1$s\" will be deleted. This can't be undone.", ja="「%1$s」を削除します。元に戻せません。", **{"zh-rCN": "将删除“%1$s”，此操作无法撤销。", "zh-rTW": "將刪除「%1$s」，此操作無法復原。"}, ko="'%1$s'을(를) 삭제합니다. 되돌릴 수 없습니다.", es="Se eliminará «%1$s». No se puede deshacer."),

"settings_title": dict(en="Playback settings", ja="再生設定", **{"zh-rCN": "播放设置", "zh-rTW": "播放設定"}, ko="재생 설정", es="Ajustes de reproducción"),
"settings_subtitle": dict(en="%1$s · %2$s", ja="%1$s · %2$s", **{"zh-rCN": "%1$s · %2$s", "zh-rTW": "%1$s · %2$s"}, ko="%1$s · %2$s", es="%1$s · %2$s"),
"settings_name": dict(en="Name", ja="名前", **{"zh-rCN": "名称", "zh-rTW": "名稱"}, ko="이름", es="Nombre"),
"settings_repeat": dict(en="Repeat", ja="繰り返し", **{"zh-rCN": "重复", "zh-rTW": "重複"}, ko="반복", es="Repetir"),
"repeat_custom": dict(en="Custom", ja="指定", **{"zh-rCN": "自定义", "zh-rTW": "自訂"}, ko="직접", es="Otro"),
"repeat_infinite": dict(en="∞ Forever", ja="∞ 無限", **{"zh-rCN": "∞ 无限", "zh-rTW": "∞ 無限"}, ko="∞ 무한", es="∞ Sin fin"),
"unit_times": dict(en="times", ja="回", **{"zh-rCN": "次", "zh-rTW": "次"}, ko="회", es="veces"),
"unit_seconds": dict(en="sec", ja="秒", **{"zh-rCN": "秒", "zh-rTW": "秒"}, ko="초", es="s"),
"decrease": dict(en="Decrease", ja="減らす", **{"zh-rCN": "减少", "zh-rTW": "減少"}, ko="줄이기", es="Disminuir"),
"increase": dict(en="Increase", ja="増やす", **{"zh-rCN": "增加", "zh-rTW": "增加"}, ko="늘리기", es="Aumentar"),
"settings_interval": dict(en="Interval between loops", ja="ループの間隔", **{"zh-rCN": "循环间隔", "zh-rTW": "循環間隔"}, ko="반복 간격", es="Pausa entre repeticiones"),
"interval_hint": dict(en="How long to wait after each loop before the next one starts.", ja="1回の再生が終わってから、次を始めるまでの待ち時間です。", **{"zh-rCN": "每轮播放结束后，等待多久再开始下一轮。", "zh-rTW": "每輪播放結束後，等待多久再開始下一輪。"}, ko="한 번 재생이 끝난 뒤 다음 재생을 시작하기까지 기다리는 시간입니다.", es="Tiempo de espera al terminar cada repetición antes de empezar la siguiente."),
"settings_speed": dict(en="Playback speed", ja="再生速度", **{"zh-rCN": "播放速度", "zh-rTW": "播放速度"}, ko="재생 속도", es="Velocidad de reproducción"),
"speed_hint": dict(en="Higher is faster. Some apps may not keep up with very fast gestures.", ja="大きいほど速く再生します。速すぎると反応しないアプリもあります。", **{"zh-rCN": "数值越大播放越快。速度过快时，部分应用可能来不及响应。", "zh-rTW": "數值越大播放越快。速度過快時，部分應用程式可能來不及回應。"}, ko="값이 클수록 빠르게 재생합니다. 너무 빠르면 반응하지 못하는 앱도 있습니다.", es="Cuanto más alto, más rápido. Algunas apps no responden bien a gestos muy rápidos."),
"delete_macro": dict(en="Delete this macro", ja="このマクロを削除", **{"zh-rCN": "删除此宏", "zh-rTW": "刪除此巨集"}, ko="이 매크로 삭제", es="Eliminar esta macro"),
"repeat_hint_infinite": dict(en="Repeats until you tap Stop on the floating buttons.", ja="フローティングの停止ボタンを押すまで繰り返します。", **{"zh-rCN": "将一直重复，直到点按悬浮按钮中的“停止”。", "zh-rTW": "會一直重複，直到輕觸浮動按鈕中的「停止」。"}, ko="플로팅 버튼의 정지를 누를 때까지 반복합니다.", es="Se repite hasta que pulses Detener en los botones flotantes."),
"value_seconds": dict(en="%1$s s", ja="%1$s秒", **{"zh-rCN": "%1$s 秒", "zh-rTW": "%1$s 秒"}, ko="%1$s초", es="%1$s s"),
"list_separator": dict(en=", ", ja="・", **{"zh-rCN": "、", "zh-rTW": "、"}, ko=", ", es=", "),
"repeat_infinite_short": None,

# --- サービス（フローティング）---
"toast_panel_failed": dict(en="Couldn't show the floating buttons: %1$s", ja="フローティングボタンを表示できませんでした: %1$s", **{"zh-rCN": "无法显示悬浮按钮：%1$s", "zh-rTW": "無法顯示浮動按鈕：%1$s"}, ko="플로팅 버튼을 표시하지 못했습니다: %1$s", es="No se pudieron mostrar los botones flotantes: %1$s"),
"desc_record": dict(en="Record", ja="記録", **{"zh-rCN": "录制", "zh-rTW": "錄製"}, ko="녹화", es="Grabar"),
"desc_play": dict(en="Play", ja="再生", **{"zh-rCN": "播放", "zh-rTW": "播放"}, ko="재생", es="Reproducir"),
"desc_stop": dict(en="Stop", ja="停止", **{"zh-rCN": "停止", "zh-rTW": "停止"}, ko="정지", es="Detener"),
"desc_open_app": dict(en="Settings (open the app)", ja="設定（アプリを開く）", **{"zh-rCN": "设置（打开应用）", "zh-rTW": "設定（開啟應用程式）"}, ko="설정(앱 열기)", es="Ajustes (abrir la app)"),
"desc_collapse": dict(en="Minimize", ja="小さくする", **{"zh-rCN": "最小化", "zh-rTW": "最小化"}, ko="최소화", es="Minimizar"),
"desc_hide_panel": dict(en="Hide the floating buttons", ja="フローティングボタンを隠す", **{"zh-rCN": "隐藏悬浮按钮", "zh-rTW": "隱藏浮動按鈕"}, ko="플로팅 버튼 숨기기", es="Ocultar los botones flotantes"),
"desc_stop_recording": dict(en="Stop recording and save", ja="記録を終了して保存", **{"zh-rCN": "结束录制并保存", "zh-rTW": "結束錄製並儲存"}, ko="녹화를 끝내고 저장", es="Terminar de grabar y guardar"),
"desc_bubble_idle": dict(en="Macro Recorder (tap to open)", ja="操作マクロ（タップで開く）", **{"zh-rCN": "操作宏（点按即可展开）", "zh-rTW": "操作巨集（輕觸即可展開）"}, ko="조작 매크로(탭하여 열기)", es="Grabador de macros (toca para abrir)"),
"desc_bubble_recording": dict(en="Recording (tap to open)", ja="記録中（タップで開く）", **{"zh-rCN": "录制中（点按即可展开）", "zh-rTW": "錄製中（輕觸即可展開）"}, ko="녹화 중(탭하여 열기)", es="Grabando (toca para abrir)"),
"desc_bubble_playing": dict(en="Playing (tap to open)", ja="再生中（タップで開く）", **{"zh-rCN": "播放中（点按即可展开）", "zh-rTW": "播放中（輕觸即可展開）"}, ko="재생 중(탭하여 열기)", es="Reproduciendo (toca para abrir)"),
"toast_play_target": dict(en="Macro to play: %1$s", ja="再生するマクロ: %1$s", **{"zh-rCN": "要播放的宏：%1$s", "zh-rTW": "要播放的巨集：%1$s"}, ko="재생할 매크로: %1$s", es="Macro que se reproducirá: %1$s"),
"toast_no_macros_short": dict(en="No macros", ja="マクロがありません", **{"zh-rCN": "没有宏", "zh-rTW": "沒有巨集"}, ko="매크로가 없습니다", es="No hay macros"),
"toast_open_failed": dict(en="Couldn't open the app", ja="アプリを開けませんでした", **{"zh-rCN": "无法打开应用", "zh-rTW": "無法開啟應用程式"}, ko="앱을 열지 못했습니다", es="No se pudo abrir la app"),
"toast_panel_hidden": dict(en="You can show them again from the app", ja="アプリの画面から再表示できます", **{"zh-rCN": "可在应用画面中重新显示", "zh-rTW": "可在應用程式畫面中重新顯示"}, ko="앱 화면에서 다시 표시할 수 있습니다", es="Puedes volver a mostrarlos desde la app"),
"toast_rec_failed": dict(en="Couldn't start recording: %1$s", ja="記録を開始できませんでした: %1$s", **{"zh-rCN": "无法开始录制：%1$s", "zh-rTW": "無法開始錄製：%1$s"}, ko="녹화를 시작하지 못했습니다: %1$s", es="No se pudo empezar a grabar: %1$s"),
"toast_rec_started": dict(en="Recording started. Use your phone as usual (one action at a time, a little slowly).", ja="記録を開始しました。ふつうに操作してください（1操作ずつ、ゆっくりめに）", **{"zh-rCN": "已开始录制。请照常操作（一次一个动作，稍微慢一点）", "zh-rTW": "已開始錄製。請照常操作（一次一個動作，稍微慢一點）"}, ko="녹화를 시작했습니다. 평소처럼 조작해 주세요(한 번에 하나씩, 조금 천천히).", es="Grabación iniciada. Usa el móvil con normalidad (una acción cada vez y sin prisa)."),
"toast_nothing_recorded": dict(en="Nothing was recorded", ja="操作が記録されていません", **{"zh-rCN": "没有录制到任何操作", "zh-rTW": "沒有錄製到任何操作"}, ko="녹화된 조작이 없습니다", es="No se grabó ninguna acción"),
"default_macro_name": dict(en="Macro %1$s", ja="マクロ %1$s", **{"zh-rCN": "宏 %1$s", "zh-rTW": "巨集 %1$s"}, ko="매크로 %1$s", es="Macro %1$s"),
"toast_no_macros": dict(en="No macros yet. Record one first.", ja="マクロがありません。まず記録してください", **{"zh-rCN": "还没有宏，请先录制", "zh-rTW": "還沒有巨集，請先錄製"}, ko="매크로가 없습니다. 먼저 녹화해 주세요", es="No hay macros. Graba una primero."),
"toast_empty_macro": dict(en="This macro has no actions", ja="このマクロには操作がありません", **{"zh-rCN": "此宏中没有操作", "zh-rTW": "此巨集中沒有操作"}, ko="이 매크로에는 조작이 없습니다", es="Esta macro no tiene acciones"),
"toast_dispatch_failed": dict(en="Couldn't perform the action. Check the accessibility settings.", ja="操作を実行できませんでした。ユーザー補助の設定を確認してください", **{"zh-rCN": "无法执行操作，请检查无障碍设置", "zh-rTW": "無法執行操作，請檢查無障礙設定"}, ko="조작을 실행하지 못했습니다. 접근성 설정을 확인해 주세요", es="No se pudo realizar la acción. Revisa los ajustes de accesibilidad."),
"toast_finished": dict(en="Playback finished", ja="再生が終わりました", **{"zh-rCN": "播放已结束", "zh-rTW": "播放已結束"}, ko="재생이 끝났습니다", es="Reproducción terminada"),
"saved_message": dict(en="Give it a name", ja="名前を付けてください", **{"zh-rCN": "请为它命名", "zh-rTW": "請為它命名"}, ko="이름을 붙여 주세요", es="Ponle un nombre"),
"keep_name": dict(en="Keep as is", ja="このまま", **{"zh-rCN": "保持原样", "zh-rTW": "保持原樣"}, ko="그대로 두기", es="Dejar así"),
}
del S["repeat_infinite_short"]

def P(en1, en, ja, cn, tw, ko, es1, es):
    return {"en": {"one": en1, "other": en}, "ja": {"other": ja}, "zh-rCN": {"other": cn}, "zh-rTW": {"other": tw},
            "ko": {"other": ko}, "es": {"one": es1, "many": es, "other": es}}

PL = {
"steps_count": P("%d action", "%d actions", "%d操作", "%d 个操作", "%d 個操作", "%d개 조작", "%d acción", "%d acciones"),
"repeat_times": P("%d time", "%d times", "%d回", "%d 次", "%d 次", "%d회", "%d vez", "%d veces"),
"repeat_hint": P("Plays %d time.", "Plays %d times.", "%d回再生します。", "将播放 %d 次。", "將播放 %d 次。", "%d회 재생합니다.", "Se reproduce %d vez.", "Se reproduce %d veces."),
"kind_tap": P("%d tap", "%d taps", "タップ%d", "点按 %d", "輕觸 %d", "탭 %d", "%d toque", "%d toques"),
"kind_long_press": P("%d long press", "%d long presses", "長押し%d", "长按 %d", "長按 %d", "길게 누르기 %d", "%d pulsación larga", "%d pulsaciones largas"),
"kind_swipe": P("%d swipe", "%d swipes", "スワイプ%d", "滑动 %d", "滑動 %d", "스와이프 %d", "%d deslizamiento", "%d deslizamientos"),
# %1$d = 指の本数, %2$d = 件数（quantity は件数）
"kind_multi": P("%2$d multi-finger (%1$d)", "%2$d multi-finger (%1$d)", "複数指 (%1$d本)%2$d", "多指（%1$d 指）%2$d", "多指（%1$d 指）%2$d", "멀티 터치(%1$d손가락) %2$d", "%2$d multitáctil (%1$d dedos)", "%2$d multitáctiles (%1$d dedos)"),
"kind_empty": P("%d empty", "%d empty", "空%d", "空 %d", "空 %d", "빈 조작 %d", "%d vacía", "%d vacías"),
"plan_free_limit": P("Free version: you can save up to %d macro", "Free version: you can save up to %d macros", "無料版: マクロは %d 件まで保存できます", "免费版：最多可保存 %d 个宏", "免費版：最多可儲存 %d 個巨集", "무료 버전: 매크로를 최대 %d개까지 저장할 수 있습니다", "Versión gratuita: puedes guardar hasta %d macro", "Versión gratuita: puedes guardar hasta %d macros"),
"toast_free_limit": P(
    "The free version can save up to %d macro. Delete macros you don't need, or go unlimited from \"Plan\" in the app.",
    "The free version can save up to %d macros. Delete macros you don't need, or go unlimited from \"Plan\" in the app.",
    "無料版で保存できるマクロは %d 件までです。不要なマクロを削除するか、アプリの「プラン」から無制限にできます",
    "免费版最多可保存 %d 个宏。请删除不需要的宏，或在应用的“方案”中升级为无限制。",
    "免費版最多可儲存 %d 個巨集。請刪除不需要的巨集，或在應用程式的「方案」中升級為無限制。",
    "무료 버전은 매크로를 최대 %d개까지 저장할 수 있습니다. 필요 없는 매크로를 삭제하거나 앱의 '플랜'에서 무제한으로 바꿀 수 있습니다.",
    "La versión gratuita puede guardar hasta %d macro. Elimina las que no necesites o pasa a ilimitado desde «Plan» en la app.",
    "La versión gratuita puede guardar hasta %d macros. Elimina las que no necesites o pasa a ilimitado desde «Plan» en la app."),
"toast_finished_cancelled": P("Playback finished (%d action was interrupted)", "Playback finished (%d actions were interrupted)", "再生が終わりました（中断された操作: %d）", "播放已结束（被中断的操作：%d）", "播放已結束（被中斷的操作：%d）", "재생이 끝났습니다(중단된 조작: %d)", "Reproducción terminada (%d acción interrumpida)", "Reproducción terminada (%d acciones interrumpidas)"),
"saved_title": P("Saved (%d action)", "Saved (%d actions)", "保存しました（%d操作）", "已保存（%d 个操作）", "已儲存（%d 個操作）", "저장했습니다(%d개 조작)", "Guardada (%d acción)", "Guardada (%d acciones)"),
}

# monetized フレーバーだけで上書きする文言
MON = {
"service_description": dict(
    en=S["service_description"]["en"].replace("• The app has no internet permission, so no data is ever sent off the device.", "• Recorded macros (coordinates and timing) are never sent off the device.\n• This build shows ads (Google AdMob) on the app's main screen and uses in-app purchases (Google Play), so it has the internet permission. Information needed for ads and purchases is processed by Google (see the privacy policy for details)."),
    ja="このサービスは、あなたが「記録」を押している間だけ、画面上のタップ・長押し・スワイプの座標と時間を記録し、「再生」を押したときにその操作を同じように実行するために使います。\n\n【使う機能】\n・ジェスチャーの実行（記録した操作の再生と、記録中の操作を下のアプリに届けるため）\n・画面の上に重ねて表示するボタン（記録・再生・停止）\n\n【データの取り扱い】\n・記録するのは、指の位置（座標）と時間だけです。他のアプリの画面内容・文字・パスワード・個人情報は読み取りません。\n・記録したマクロはこの端末のアプリ内にのみ保存されます。\n・記録したマクロ（座標・時間）を外部に送信することはありません。\n・このビルドはアプリのメイン画面に広告（Google AdMob）を表示し、アプリ内購入（Google Play）を使うため、インターネット権限を持ちます。広告や購入に必要な情報は Google によって処理されます（詳しくはプライバシーポリシー）。\n・マクロはアプリ内の一覧からいつでも削除できます。",
    **{"zh-rCN": S["service_description"]["zh-rCN"].replace("・本应用没有网络权限，因此不会向外部发送任何数据。", "・录制的宏（坐标和时间）不会发送到外部。\n・此版本会在应用主画面显示广告（Google AdMob）并使用应用内购买（Google Play），因此具有网络权限。广告和购买所需的信息由 Google 处理（详见隐私政策）。"),
       "zh-rTW": S["service_description"]["zh-rTW"].replace("・本應用程式沒有網路權限，因此不會向外部傳送任何資料。", "・錄製的巨集（座標和時間）不會傳送到外部。\n・此版本會在應用程式主畫面顯示廣告（Google AdMob）並使用應用程式內購買（Google Play），因此具有網路權限。廣告和購買所需的資訊由 Google 處理（詳見隱私權政策）。")},
    ko=S["service_description"]["ko"].replace("• 인터넷 권한이 없으므로 데이터를 외부로 전송하지 않습니다.", "• 녹화한 매크로(좌표, 시간)를 외부로 전송하지 않습니다.\n• 이 빌드는 앱의 메인 화면에 광고(Google AdMob)를 표시하고 인앱 구매(Google Play)를 사용하므로 인터넷 권한을 가집니다. 광고와 구매에 필요한 정보는 Google이 처리합니다(자세한 내용은 개인정보처리방침 참고)."),
    es=S["service_description"]["es"].replace("• La app no tiene permiso de acceso a internet, así que nunca envía datos fuera del dispositivo.", "• Las macros grabadas (coordenadas y tiempos) nunca se envían fuera del dispositivo.\n• Esta versión muestra anuncios (Google AdMob) en la pantalla principal de la app y usa compras integradas (Google Play), por lo que tiene permiso de acceso a internet. Google trata la información necesaria para los anuncios y las compras (consulta la política de privacidad).")),
"privacy_note": dict(
    en="Only finger positions and timing are recorded; the screen content of other apps is never read. Macros are stored only on this device and are never sent anywhere. This screen shows ads (Google AdMob), and the internet is used for ads and in-app purchases.",
    ja="記録するのは指の位置と時間だけで、他のアプリの画面内容は読み取りません。マクロはこの端末内にのみ保存され、外部には送信しません。この画面には広告（Google AdMob）が表示され、広告とアプリ内購入のためにインターネットを使います。",
    **{"zh-rCN": "只记录手指的位置和时间，不会读取其他应用的屏幕内容。宏只保存在此设备上，不会发送到外部。此画面会显示广告（Google AdMob），并为广告和应用内购买使用网络。",
       "zh-rTW": "只記錄手指的位置和時間，不會讀取其他應用程式的畫面內容。巨集只儲存在此裝置上，不會傳送到外部。此畫面會顯示廣告（Google AdMob），並為廣告和應用程式內購買使用網路。"},
    ko="손가락의 위치와 시간만 기록하며, 다른 앱의 화면 내용은 읽지 않습니다. 매크로는 이 기기에만 저장되며 외부로 전송하지 않습니다. 이 화면에는 광고(Google AdMob)가 표시되며, 광고와 인앱 구매를 위해 인터넷을 사용합니다.",
    es="Solo se registran la posición de los dedos y los tiempos; nunca se lee el contenido de la pantalla de otras apps. Las macros se guardan solo en este dispositivo y nunca se envían a ningún sitio. Esta pantalla muestra anuncios (Google AdMob) y se usa internet para los anuncios y las compras integradas."),
}
