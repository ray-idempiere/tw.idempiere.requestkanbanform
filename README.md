# tw.idempiere.requestkanbanform
### The Request Kanban Form — iDempiere's Missing Middle Child

---

## 🌐 English

> *In the beginning, there was iDempiere. And iDempiere had requests. Many, many requests. And lo, the people cried out: "Where did ticket #1002208 go?" And no one knew.*
>
> *Thus was born the Request Kanban Form — so that no request shall be lost, no status unknown, and no manager shall ever again ask "what are you working on?" without first checking the board.*

---

## 📸 Screenshots

| Kanban View | Gantt View |
|:-----------:|:----------:|
| ![Kanban View](docs/request-kanban.png) | ![Gantt View](docs/request-gannt.png) |

| List View | Update History |
|:---------:|:--------------:|
| ![List View](docs/request-listview.png) | ![Update History](docs/request-update-history.png) |

![Attachment Dialog](docs/request-attachment.png)

---

### ✨ What Is This Thing

`tw.idempiere.requestkanbanform` is an OSGi plugin for **iDempiere 12** that replaces the ancient art of "searching the R_Request window and hoping for the best" with three glorious view modes, real-time updates, and a Gantt chart that even your PM will understand.

No new database tables. No new columns. We are guests in iDempiere's house and we wipe our feet at the door.

---

### 🚀 Features

#### Three Views, One Dashboard, Zero Excuses

**⊞ Kanban View** *(the one with the pretty cards)*
- Requests live as draggable cards, grouped by status column.
- Color-coded by priority: Urgent (pink) / High (orange) / Medium (yellow) / Low (green). If everything is pink, that is a you problem, not a plugin problem.
- Due date chips: 🔴 Overdue / ⏰ Due soon / ⏰ Plenty of time (enjoy it while it lasts).
- Drag-and-drop to change status. Only the SalesRep or Requester can move cards — because democracy has limits.
- **Status column icons**: upload an image (PNG/JPG/GIF) to the `R_Status` record as an attachment, and it becomes the column header icon at 16×16. No image attached? No icon. You had one job.

**📅 Gantt View** *(the one that makes you feel organized)*
- Timeline chart with requests as colored bars. Finally, a reason to hold a meeting that could have been an email.
- Time range pills: This Week / This Month / This Quarter. Custom date range also available for the ambitious.
- Auto-granularity: day columns for short ranges, week columns for medium, month columns for "we are very behind."
- Today's column is highlighted yellow — a gentle reminder that time is passing.
- Bars are color-coded by status (background) and priority (left border). Visual information density: maximum. Excuses: none.
- Requests with no start/end date appear as greyed-out rows labelled "No date set." They are not forgotten. They are merely… pending.
- In Team/All scope, rows are grouped under responsible person headers.
- Click any bar to open the Request Update dialog. It works. We were surprised too.

**≡ List View** *(the one for people who distrust fun)*
- Tabbed list grouped by status. Columns. Rows. Data. Very professional. Very beige.

#### Always-On Features

- **Scope Filter**: Personal / Subordinates / Team / All — applies to all three views.
- **Search**: Filter by summary or document number. Faster than scrolling. We timed it.
- **Real-time Updates**: Powered by OSGi EventAdmin. When someone updates a request, every connected browser refreshes automatically. No F5. No polling. Pure event-driven elegance.
- **Update Dialog**: Three-section layout — Basic Info / Summary / Update History with avatar initials.
- **Permission Control**: Only SalesRep or Requester can edit or move cards. Accountability, enforced.
- **Zoom to Window**: Jump straight to the iDempiere Request window for power users who demand the full experience.
- **Internationalization**: English and Traditional Chinese (zh_TW).
- **Version Display**: Plugin version shown in the top-right corner.

---

### 🛠️ Technical Notes

| Item | Detail |
|------|--------|
| OSGi Bundle ID | `tw.idempiere.requestkanbanform` |
| Entry Point Class | `tw.idempiere.requestkanbanform.RequestKanbanForm` |
| Main ViewModel | `RequestKanbanVM` |
| Main Form | `RequestKanbanForm` |
| Architecture | ZK MVVM (`@id`/`@init`/`@load`/`@command`) |
| New DB Tables | 0 |
| New DB Columns | 0 |
| iDempiere Version | 12 |
| Build System | Maven (Tycho) |
| Plugin Version | 2.0.0 |

**Status Icon Resolution** — On form init, `loadStatusIcons()` iterates all `R_Status` records and calls `MAttachment.get(ctx, 776, statusId)`. If an image entry (`isGraphic()` → `.png/.jpg/.gif`) is found, it is encoded as a Base64 data URI and cached in memory. The ZUL binds `visible` and `src` to `hasStatusIcon()` / `getStatusIconUrl()` on the ViewModel. Size: 16×16 with `object-fit:contain`. See [`docs/technical-guide-status-icon.md`](docs/technical-guide-status-icon.md) for full details.

**Gantt Rendering** — `GanttRenderer.build(ResultSet)` generates a pure server-side HTML string injected into the page via `Clients.evalJavaScript()`. Bar positions are percentage-based (`leftPct`, `widthPct`) relative to the date range. A JS bridge (`window._zkGanttClick`) routes bar clicks back to the ZK server-side event system. Yes, this is a Gantt chart built entirely with HTML table cells and `position:absolute`. Yes, it works. No, we are not sorry.

---

### ⚙️ Installation

1. **i18n**: Run `migration/i18n_setup.sql` in your database, **or** use **Pack In** to import `META-INF/2Pack/RK_Messages.xml`. Do this first. Without it, every label reads `RK_SomethingUntranslated` and you will be sad.
2. **Build**: `mvn package` with the iDempiere parent POM on the Maven path, or use Eclipse PDE.
3. **Deploy**: Drop the OSGi bundle JAR into iDempiere (`dropins/` or via Felix console).
4. **Activate**: Confirm `tw.idempiere.requestkanbanform` is `Active`.
5. **Custom Form**: Create a new **Custom Form** record in iDempiere with classname `tw.idempiere.requestkanbanform.form.RequestKanbanForm` and link it to a menu item. Name it `@RK_RequestKanban@` for auto-translation.

**Optional — Custom Status Icons**: Open the **Request Status** window, find a status record, attach a PNG/JPG/GIF image. On next Kanban page load, that image becomes the column header icon. See [Status Icon Guide](docs/technical-guide-status-icon.md).

---

### 📋 Changelog

#### 2026-04-05 — v2.0.0
- **MVVM Rewrite** — Complete rewrite from Conductor-pattern (`DashboardRunComponent`) to ZK MVVM. `RequestKanbanVM` is a pure POJO ViewModel; `RequestKanbanForm` handles only ZK wiring. Zero component references in the VM.
- **Maven / Tycho Build** — Migrated from Eclipse PDE-only to Maven Tycho build. `mvn package` now works.
- **Status Column Icons** — `R_Status` attachment images now render as 16×16 icons in Kanban column headers. Encoded as Base64 data URIs at form init; no external URL needed. See [technical guide](docs/technical-guide-status-icon.md).
- **Gantt refactor** — Gantt rendering extracted to `GanttRenderer` (pure Java, no ZK imports). HTML is injected via `Clients.evalJavaScript()` rather than ZK `Html` component.
- **KanbanRowModel** — All per-card display values (card style, due badge, priority colour) pre-computed server-side into an immutable model. The ZUL template is now purely declarative.
- **List View** — New pill-tab list view added as a third view mode alongside Kanban and Gantt.
- **New Project dialog** — Create `C_Project` records directly from the Gantt view sidebar.
- **Request Update dialog** — Inline quick-update dialog for priority, SalesRep, and product/time logged.

#### 2026-03-25 — v1.1.0
- **Scrollbar fix** — Kanban columns, List tabpanels, and Gantt panel now scroll vertically when there are too many tasks.
- **Gantt left list clickable** — Clicking the task list row opens the Request Update dialog.
- **Date range crash fix** — `java.sql.Date.toInstant()` is unsupported by the JDK; fixed by calling `.toLocalDate()` directly via `instanceof` check.
- **Gantt bar label** — Bars now show `(Requester) Start~End` instead of DocumentNo.

---

### 🗂️ Repository Layout

```
tw.idempiere.requestkanbanform/
├── src/tw/idempiere/requestkanbanform/
│   ├── form/           RequestKanbanForm.java       (ZK wiring, dialog launchers)
│   ├── viewmodel/      RequestKanbanVM.java          (MVVM ViewModel)
│   ├── viewmodel/      KanbanRowModel.java           (immutable card model)
│   ├── dashboard/      GanttRenderer.java            (Gantt HTML builder)
│   ├── dashboard/      StatusConfig.java             (status filter config)
│   └── factory/        RequestKanbanFormFactory.java (OSGi service registration)
├── web/zul/            RequestKanbanForm.zul         (main MVVM view)
│                       request-new.zul / request-update.zul / project-new.zul
├── META-INF/           MANIFEST.MF, 2Pack/
├── migration/          i18n_setup.sql
├── docs/               technical-guide-status-icon.md
├── pom.xml
└── README.md
```

---

### 🤝 Contributing

Found a bug? Open an issue.
Have an idea? Open a PR.
Want to say thank you? A ⭐ on GitHub is the garlic bread of open source — not required, but deeply appreciated.

### 📜 License
GPL-2.0-only. Share and share alike.

---
---

## 🌐 中文版

> **太史公曰：** 自古請求如恆河之沙，數之不盡，管之不易。或藏於資料庫深處，或迷失於 Window 介面之汪洋，負責人曰「我不知道」，主管曰「怎麼又卡住了」，申請人曰「我上週就填了啊」。三者相望於江湖，皆不得要領。
>
> 於是乎，看板降世。從此，請求有所歸，狀態有所顯，每週站會終於可以在十分鐘內結束。此誠 ERP 管理之一大德政也。

---

### ✨ 這是什麼

`tw.idempiere.requestkanbanform` 是一個 **iDempiere 12** 的 OSGi 外掛，將「打開 R_Request 視窗然後祈禱」這門傳統藝術，升華為三種視圖、即時更新，以及一張連 PM 都看得懂的甘特圖。

無新增資料表。無新增欄位。吾等乃 iDempiere 之座上賓，入門必擦鞋底。

---

### 🚀 功能特色

#### 三種視圖，一個表單，零藉口

**⊞ 看板視圖** *（有漂亮卡片的那個）*
- 請求以可拖曳卡片形式呈現，依狀態分欄。
- 優先權顏色編碼：緊急（粉紅）/ 高（橘）/ 中（黃）/ 低（綠）。
- 到期日標籤：🔴 逾期 / ⏰ 快到了 / ⏰ 還早。
- 拖放換狀態，僅限 SalesRep 或申請人可操作。
- **狀態欄 Icon**：在 `R_Status` 記錄上傳 PNG/JPG/GIF 附件，即自動成為欄標題 16×16 圖示。詳見 [Status Icon 技術文件](docs/technical-guide-status-icon.md)。

**📅 甘特視圖** *（讓你覺得自己很有規劃的那個）*
- 時間軸，請求以彩色長條呈現。
- 時間範圍 Pill：本週 / 本月 / 本季 / 自訂區間。
- 自動粒度：短區間每日、中區間每週、長區間每月。
- 今日欄以黃色標示，溫柔提醒時光流逝。
- 長條底色 = 狀態，左邊框色 = 優先權。
- 無起訖時間的請求顯示為灰色列。
- 部屬/團隊/全部範圍下，依負責人自動分組。
- 點擊長條即開啟請求更新對話框。

**≡ 列表視圖** *（給不相信趣味的人）*
- 依狀態分頁的表格。欄、列、資料、非常專業。

#### 常駐功能
- **範圍篩選**：個人 / 部屬 / 團隊 / 全部，三種視圖通用。
- **搜尋**：依摘要或單號篩選。
- **即時更新**：OSGi EventAdmin 驅動，自動推播，無需 F5。
- **更新對話框**：基本資訊 / 摘要 / 更新歷程。
- **權限控制**：僅 SalesRep 或申請人可編輯與移動卡片。
- **縮放視窗**：一鍵跳至 iDempiere 標準請求視窗。
- **多國語系**：英文與繁體中文（zh_TW）。
- **版本顯示**：右上角顯示外掛版本。

---

### 🛠️ 技術細節

| 項目 | 說明 |
|------|------|
| OSGi Bundle ID | `tw.idempiere.requestkanbanform` |
| 入口 classname | `tw.idempiere.requestkanbanform.RequestKanbanForm` |
| ViewModel | `RequestKanbanVM`（純 POJO，無 ZK Component 參考） |
| Form | `RequestKanbanForm`（ZK 配線、對話框開啟） |
| 架構模式 | ZK MVVM |
| 新增資料表 | 0 |
| 新增欄位 | 0 |
| iDempiere 版本 | 12 |
| 建置工具 | Maven Tycho |
| 外掛版本 | 2.0.0 |

**狀態 Icon 解析邏輯** — 表單 init 時，`loadStatusIcons()` 為每個 `R_Status_ID` 呼叫 `MAttachment.get(ctx, 776, statusId)`，找到第一個圖檔（`isGraphic()`）後轉為 Base64 data URI 快取。ZUL 透過 `hasStatusIcon()` / `getStatusIconUrl()` 綁定顯示，16×16，`object-fit:contain`。詳見 [技術文件](docs/technical-guide-status-icon.md)。

**甘特圖渲染** — `GanttRenderer.build(ResultSet)` 產生純伺服器端 HTML 字串，透過 `Clients.evalJavaScript()` 注入。長條位置以百分比計算。JS Bridge（`window._zkGanttClick`）將點擊事件路由至 ZK 伺服器端。是的，這張甘特圖是用 HTML table 格子與 `position:absolute` 打造的。是的，它會動。不，我們不後悔。

---

### ⚙️ 安裝步驟

1. **多國語系**：先執行 `migration/i18n_setup.sql`，或用 **Pack In** 匯入 `META-INF/2Pack/RK_Messages.xml`。
2. **編譯**：`mvn package`（需 iDempiere parent POM 在 Maven 路徑上），或使用 Eclipse PDE。
3. **部署**：將 OSGi Bundle JAR 部署至 iDempiere。
4. **啟動**：確認 `tw.idempiere.requestkanbanform` Bundle 為 Active。
5. **Custom Form 設定**：在 iDempiere 新增一筆 **Custom Form** 記錄，class 填 `tw.idempiere.requestkanbanform.form.RequestKanbanForm`，連結至選單，名稱填 `@RK_RequestKanban@`。

**選用：自訂狀態 Icon** — 開啟 Request Status 視窗，找目標狀態記錄，附加 PNG/JPG/GIF 圖檔，重新載入看板即生效。詳見 [Status Icon 指南](docs/technical-guide-status-icon.md)。

---

### 📋 更新記錄

#### 2026-04-05 — v2.0.0
- **MVVM 全面改寫** — 從 Conductor 模式徹底重構為 ZK MVVM，`RequestKanbanVM` 為純 POJO ViewModel。
- **Maven Tycho 建置** — 從純 Eclipse PDE 遷移至 Maven Tycho，`mvn package` 正式可用。
- **狀態欄 Icon** — `R_Status` 附件圖片自動轉為 Base64 data URI，顯示為 16×16 欄標題圖示。
- **Gantt 重構** — 渲染邏輯提取至 `GanttRenderer`（純 Java，無 ZK import），透過 `Clients.evalJavaScript()` 注入。
- **KanbanRowModel** — 卡片所有顯示值於伺服器端預算，ZUL 純宣告式。
- **列表視圖** — 新增第三種視圖，以狀態為 Pill Tab 的列表。
- **新建專案對話框** — 從甘特圖側邊欄直接建立 `C_Project`。
- **請求更新對話框** — 快速更新優先權、SalesRep、工時。

#### 2026-03-25 — v1.1.0
- **捲軸修正** — 看板欄、列表分頁與甘特面板任務過多時正確顯示垂直捲軸。
- **甘特左側清單可點擊** — 點擊任務列開啟更新對話框。
- **日期區間崩潰修正** — `java.sql.Date.toInstant()` 不支援問題，改用 `.toLocalDate()`。
- **甘特長條標籤** — 改為顯示「（申請人）起始~結束」格式。

---

### 🗂️ 專案結構

```
tw.idempiere.requestkanbanform/
├── src/tw/idempiere/requestkanbanform/
│   ├── form/           RequestKanbanForm.java        (ZK 配線、對話框開啟)
│   ├── viewmodel/      RequestKanbanVM.java           (MVVM ViewModel)
│   ├── viewmodel/      KanbanRowModel.java            (卡片不可變顯示模型)
│   ├── dashboard/      GanttRenderer.java             (甘特 HTML 產生器)
│   ├── dashboard/      StatusConfig.java              (狀態過濾設定)
│   └── factory/        RequestKanbanFormFactory.java  (OSGi 服務註冊)
├── web/zul/            RequestKanbanForm.zul          (主 MVVM 視圖)
│                       request-new.zul / request-update.zul / project-new.zul
├── META-INF/           MANIFEST.MF, 2Pack/
├── migration/          i18n_setup.sql
├── docs/               technical-guide-status-icon.md
├── pom.xml
└── README.md
```

---

### 🤝 貢獻方式

發現 Bug？開 Issue。有好點子？送 PR。想說謝謝？給顆 ⭐。

### 📜 授權
GPL-2.0-only。分享即美德。
