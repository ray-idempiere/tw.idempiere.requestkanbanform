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
- **Image thumbnail banner**: if a request has an image attachment, the first image is displayed as a full-width 100 px banner at the top of its card — Trello-style. Capped at the top 20 requests with attachments per refresh (sorted by Priority ASC, StartDate DESC). Cards beyond the cap still show the paperclip icon.
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
- **Request Members**: Associate additional users with a request card — they get read-only access to the card and it appears in their Personal scope. See the Members section below.
- **Participant Avatars**: Every request card shows a Trello-style avatar strip for the Requester, SalesRep, and all Members. Photo from `AD_User` attachment if available; otherwise a coloured initials circle. Up to 5 avatars with a `+N` overflow chip.
- **Current User Avatar**: Your own avatar appears in the top-right of the toolbar. Click it to open the Attachment dialog and upload or replace your profile photo — updates instantly without a page reload.

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
| Plugin Version | 3.1.2 |

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

**Optional — Auto-fill Role by Request Type**: In the **Request Type** window, set the **Description** field to a JSON object specifying the responsible role:

```json
{"Role":"ERP Support"}
```

When a user selects that Request Type in the New Request dialog, the **Responsible Role/Team** field is automatically populated with the matching `AD_Role`. The role name must exactly match the `Name` field in the **Role** window. Leave the Description blank (or use any other format) to skip auto-fill.

---

### 👥 Members

Any user with edit permission (SalesRep, Requester, or Supervisor) can add other users as **Members** of a request card.

**Adding a Member**
1. Open a request card (click any card on the board).
2. Scroll to the **👥 Members** section in the dialog.
3. Type a name in the member search field and select the user.
4. The member chip appears immediately. No save button — the association is written to `r_requestupdates` instantly.

**Removing a Member**
Click the **×** on any member chip. Permission required: `canEditAny` (SalesRep, Requester, or Supervisor).

**Member Access**
- The card appears in the member's **Personal** scope on the Kanban board.
- Clicking the card opens it in **read-only** mode (no editing).
- Members do not count as SalesRep or Requester — they cannot move, edit, or close the card.

**Avatars on Cards**
Each card displays a row of circular avatars: Requester first, then SalesRep (if different), then Members. If `AD_User` has a PNG or JPG uploaded as an attachment, the photo is used; otherwise a colour-coded initials circle is shown. More than 5 participants collapses to a `+N` chip.

**Profile Photo**
Click your own avatar in the top-right toolbar to open the Attachment dialog. Upload a PNG or JPG — the avatar in the toolbar updates immediately on close, and will appear on all cards you are a participant of after the next board refresh.

---

### 📋 Changelog

#### 2026-04-27 — v3.1.1
- **Fix attachment count in edit dialog** — The attachment count badge `(N)` in the Request edit dialog now correctly reflects the number of individual files. Previously, the count was queried from `AD_Attachment` (one row per record) and always showed `(1)`; it now queries `AD_AttachmentEntry` for the true file count.

#### 2026-04-23 — v3.1.0
- **Kanban card image thumbnail banner** — If a request has an image attachment (PNG/JPG/GIF), the first image is rendered as a full-width 100 px Trello-style banner at the top of its Kanban card. Banner images are loaded via `MAttachment` / `MAttachmentEntry` and encoded as Base64 data URIs. Capped at the top 20 requests with attachments per refresh (sorted Priority ASC, StartDate DESC); cards beyond the cap show the paperclip icon only. No new DB columns required.

#### 2026-04-23 — v3.0.2
- **Priority field read-only on New Request form** — The Priority field in the New Request dialog is now read-only; it defaults to Medium and cannot be changed at creation time.

#### 2026-04-20 — v3.0.1
- **Read-only Requester field in edit dialog** — The Requester field in the Request Update dialog is now displayed as read-only to prevent accidental reassignment.
- **Remove text names from Kanban cards** — Participant names are no longer shown as plain text on cards; they are represented exclusively by avatar circles, reducing card clutter.

#### 2026-04-20 — v3.0.0
- **Request Members** — Associate additional users with a request via `r_requestupdates`. Members see the card in their Personal scope and can open it in read-only mode. Add/remove chips write to the DB immediately (no save button). Only `canEditAny` users (SalesRep, Requester, Supervisor) can add or remove members.
- **Participant Avatars on Cards** — Every Kanban card shows a Trello-style avatar strip for all participants (Requester, SalesRep, Members). Profile photo loaded from `AD_User` attachment (PNG/JPG packed as ZIP via `MAttachment` API); falls back to a colour-coded initials circle. Up to 5 avatars shown with a `+N` overflow chip.
- **Scope filter extended** — Private/Subordinates/Team scopes now include cards where the current user is a Member (via `EXISTS` subquery on `r_requestupdates`).
- **Current User Avatar in toolbar** — Logged-in user's avatar (36 px circle) displayed in the top-right toolbar. Click to open the `AD_User` Attachment dialog and upload/replace a profile photo; avatar refreshes instantly on close via `BindUtils.postNotifyChange`.

#### 2026-04-19 — v2.2.0
- **`MRequestKanban` custom model** — New `MRequest` subclass that implements a *beforeSave / afterSave sandwich* to preserve `EndTime` against erasure by iDempiere's core `RequestEventHandler`. `beforeSave` stashes the value before `PO_BEFORE_CHANGE` fires; `afterSave` restores it via direct SQL UPDATE after the event has completed, avoiding infinite recursion.
- **`RequestKanbanModelFactory`** — New `IModelFactory` OSGi service (ranking 100) registered via `OSGI-INF/requestkanban_model_factory.xml`, ensuring iDempiere instantiates `MRequestKanban` instead of `MRequest` for all `R_Request` PO operations.
- **`build.properties` fix** — `OSGI-INF/requestkanban_model_factory.xml` was missing from `bin.includes`; without this the OSGi component was never packaged and the factory service never registered.
- **`RequestKanbanVM` model usage** — All five `new MRequest(...)` call sites replaced with `new MRequestKanban(...)` so that custom `beforeSave`/`afterSave` hooks are active for every save operation originating from the Kanban form.

#### 2026-04-19 — v2.1.3
- **Auto-fill Role from Request Type** — `R_RequestType.Description` now accepts `{"Role":"<role name>"}` JSON. Selecting a Request Type in the New Request dialog automatically resolves the role name to an `AD_Role_ID` and populates the Responsible Role/Team field.
- **Session role default** — The Responsible Role/Team field in the New Request dialog now defaults to the current user's login role instead of a hardcoded ID.

#### 2026-04-18 — v2.1.2
- **Gantt: drag request onto project** — Drag a request bar and drop it onto a project row to reassign; change is broadcast to all sessions in real time.
- **Gantt: create / update project with push** — Creating or editing a project from the Gantt sidebar fires a broadcast refresh so all connected clients see the updated project list immediately.

#### 2026-04-18 — v2.1.0
- **Cross-session real-time refresh** — Switched from OSGi service registration to `EventManager.postEvent` so any edit in one browser session instantly refreshes all other open Kanban views via server push.
- **Supervisor edit permissions** — Supervisors can now edit Priority, SalesRep, StartTime, EndTime, Result, and Product in the request dialog (previously restricted to the assignee only).
- **`broadcastRefresh()`** — New VM method fires a topic event after any card move or status change, triggering a refresh across all sessions.

#### 2026-04-16 — v2.0.3
- **Double-click to zoom fix** — Fixed a bug where double-clicking a project folder in the Gantt view did not open the correct iDempiere Project window.
- **Request counter badge** — Each project row in the Gantt panel now shows a scoped request count badge on the right side; respects the `showFinalClose` toggle.

#### 2026-04-12 — v2.0.2
- **Project selector in New Request dialog** — Users can now link a new request to a `C_Project` directly from the creation dialog.
- **Gantt date-picker fix** — Resolved a crash when opening the date range picker in the Gantt view.

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
│   ├── form/           RequestKanbanForm.java        (ZK wiring, dialog launchers)
│   ├── viewmodel/      RequestKanbanVM.java           (MVVM ViewModel)
│   ├── viewmodel/      KanbanRowModel.java            (immutable card model)
│   ├── dashboard/      GanttRenderer.java             (Gantt HTML builder)
│   ├── dashboard/      StatusConfig.java              (status filter config)
│   ├── factory/        RequestKanbanFormFactory.java  (OSGi form service)
│   ├── factory/        RequestKanbanModelFactory.java (OSGi IModelFactory — returns MRequestKanban)
│   └── model/          MRequestKanban.java            (MRequest subclass, EndTime sandwich)
├── web/zul/            RequestKanbanForm.zul          (main MVVM view)
│                       request-new.zul / request-update.zul / project-new.zul
├── OSGI-INF/           requestkanban_form_factory.xml / requestkanban_model_factory.xml
├── META-INF/           MANIFEST.MF, 2Pack/
├── migration/          i18n_setup.sql
├── docs/               technical-guide-status-icon.md
│                       2026-04-19-idempiere-endtime-persistence-model-sandwich.md
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
- **圖片縮圖橫幅**：若請求有圖片附件（PNG/JPG/GIF），第一張圖片將以 Trello 風格顯示為卡片頂端全寬 100px 橫幅。每次刷新最多顯示前 20 筆有附件的請求（依優先權升冪、起始日期降冪排序），超過上限的卡片仍顯示迴紋針圖示。
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
- **請求成員（Members）**：可將其他使用者加入請求卡片，成員享有唯讀存取權限，卡片會出現在其「個人」範圍。詳見下方「成員」章節。
- **參與者頭像**：每張卡片以 Trello 風格顯示申請人、SalesRep 及所有成員的頭像圓圈。AD_User 有上傳 PNG/JPG 附件則顯示照片，否則顯示首字彩色圓圈。最多顯示 5 個，超過以 `+N` 顯示。
- **工具列頭像**：右上角顯示登入使用者的頭像（36px 圓形）。點擊可開啟附件對話框上傳大頭照，關閉後立即更新，無需重新整理頁面。

---

### 🛠️ 技術細節

| 項目 | 說明 |
|------|------|
| OSGi Bundle ID | `tw.idempiere.requestkanbanform` |
| 入口 class name | `tw.idempiere.requestkanbanform.RequestKanbanForm` |
| ViewModel | `RequestKanbanVM`（純 POJO，無 ZK Component 參考） |
| Form | `RequestKanbanForm`（ZK 配線、對話框開啟） |
| 架構模式 | ZK MVVM |
| 新增資料表 | 0 |
| 新增欄位 | 0 |
| iDempiere 版本 | 12 |
| 建置工具 | Maven Tycho |
| 外掛版本 | 3.1.0 |

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

**選用：依請求類型自動帶入負責角色** — 在 **Request Type** 視窗中，將 **Description** 欄位填入指定負責角色的 JSON：

```json
{"Role":"ERP Support"}
```

使用者在新建請求對話框選擇該請求類型後，**負責角色/團隊** 欄位會自動帶入對應的 `AD_Role`。角色名稱須與 **Role** 視窗的 `Name` 欄位完全一致。Description 留空（或填入其他格式）則不觸發自動帶入。

---

---

### 👥 成員（Members）

具有編輯權限的使用者（SalesRep、申請人或主管）可將其他人加入請求卡片成為**成員**。

**新增成員**
1. 點開任意請求卡片。
2. 捲動至對話框中的 **👥 Members** 區塊。
3. 在搜尋欄輸入姓名並選取使用者。
4. 成員 Chip 立即出現，資料即寫入 `r_requestupdates`，無需另按存檔。

**移除成員**
點擊成員 Chip 上的 **×**。需要 `canEditAny` 權限（SalesRep、申請人或主管）。

**成員的存取權限**
- 卡片會出現在成員的**個人**範圍看板中。
- 點開卡片為**唯讀**模式，無法編輯。
- 成員無法移動、編輯或關閉卡片。

**卡片頭像列**
每張卡片顯示一排圓形頭像：依序為申請人、SalesRep（如不同）、成員。若 `AD_User` 有上傳 PNG 或 JPG 附件，則顯示照片；否則顯示依 User ID 決定顏色的首字圓圈。超過 5 位參與者時折疊為 `+N`。

**個人大頭照**
點擊工具列右上角自己的頭像，開啟附件對話框，上傳 PNG 或 JPG。關閉對話框後頭像立即更新，下次看板刷新後所有卡片上也會同步顯示。

---

### 📋 更新記錄

#### 2026-04-23 — v3.1.0
- **看板卡片圖片縮圖橫幅** — 若請求有圖片附件（PNG/JPG/GIF），第一張圖片以全寬 100px Trello 風格橫幅顯示於卡片頂端。圖片透過 `MAttachment` / `MAttachmentEntry` 讀取並編碼為 Base64 data URI。每次刷新最多處理前 20 筆有附件的請求（依優先權升冪、起始日期降冪），超過上限的卡片僅顯示迴紋針圖示。無需新增資料表或欄位。

#### 2026-04-23 — v3.0.2
- **新建請求表單優先權欄位唯讀** — 新建請求對話框中的優先權欄位改為唯讀，建立時固定為「中」，無法變更。

#### 2026-04-20 — v3.0.1
- **更新對話框申請人欄位唯讀** — 請求更新對話框中的申請人欄位改為唯讀顯示，防止誤操作重新指派。
- **移除卡片文字姓名** — 卡片上不再顯示純文字的參與者姓名，改以頭像圓圈呈現，減少卡片雜訊。

#### 2026-04-20 — v3.0.0
- **請求成員（Members）** — 透過 `r_requestupdates` 將其他使用者關聯至請求。成員在個人範圍可見該卡片，點開後為唯讀模式。新增/移除 Chip 即時寫入資料庫，無需另按存檔。僅 `canEditAny` 使用者（SalesRep、申請人、主管）可操作。
- **參與者頭像** — 每張看板卡片顯示 Trello 風格的頭像列（申請人、SalesRep、成員）。透過 `MAttachment` API 讀取 `AD_User` 的 PNG/JPG 附件作為大頭照；無照片則顯示首字彩色圓圈。超過 5 位以 `+N` 顯示。
- **範圍篩選擴展** — 個人/部屬/團隊範圍現在包含目前使用者為成員的卡片（`r_requestupdates` EXISTS 子查詢）。
- **工具列頭像** — 右上角顯示登入使用者的 36px 頭像圓圈。點擊開啟 AD_User 附件對話框，上傳/更換大頭照後透過 `BindUtils.postNotifyChange` 即時刷新。

#### 2026-04-19 — v2.2.0
- **`MRequestKanban` 自訂模型** — 繼承 `MRequest` 的子類別，實作 *beforeSave / afterSave 夾擊模式*，對抗 iDempiere 核心 `RequestEventHandler` 在 `PO_BEFORE_CHANGE` 事件中清除 `EndTime` 的行為。`beforeSave` 在事件觸發前暫存欄位值；`afterSave` 透過直接 SQL UPDATE 還原，避免無限迴圈。
- **`RequestKanbanModelFactory`** — 新增 `IModelFactory` OSGi 服務（ranking 100），透過 `OSGI-INF/requestkanban_model_factory.xml` 註冊，確保所有 `R_Request` PO 操作均實例化 `MRequestKanban` 而非 `MRequest`。
- **`build.properties` 修正** — `OSGI-INF/requestkanban_model_factory.xml` 未包含於 `bin.includes`；缺少此設定時 OSGi component 不會被打包，工廠服務永遠無法註冊。
- **`RequestKanbanVM` 模型替換** — 將 VM 中五處 `new MRequest(...)` 全部改為 `new MRequestKanban(...)`，確保 Kanban 表單所有存檔操作均觸發自訂的 `beforeSave`/`afterSave` 邏輯。

#### 2026-04-19 — v2.1.3
- **依請求類型自動帶入負責角色** — `R_RequestType.Description` 支援 `{"Role":"<角色名稱>"}` JSON 格式。在新建請求對話框選擇請求類型時，自動將角色名稱解析為 `AD_Role_ID` 並填入負責角色/團隊欄位。
- **登入角色預設值** — 新建請求對話框的負責角色/團隊欄位，現在改為預設帶入當前使用者的登入角色，取代原本的硬編碼 ID。

#### 2026-04-18 — v2.1.2
- **甘特圖：拖曳請求至專案** — 將請求長條拖放至專案列即可重新指派，異動即時廣播至所有連線工作階段。
- **甘特圖：建立/更新專案含推播** — 從甘特圖側邊欄新建或編輯專案後，自動廣播刷新，所有連線用戶端立即看到最新專案列表。

#### 2026-04-18 — v2.1.0
- **跨工作階段即時刷新** — 改用 `EventManager.postEvent` 模式，任一瀏覽器工作階段的異動，即時透過 Server Push 刷新所有其他已開啟的看板視圖。
- **主管編輯權限** — 主管現可在請求對話框編輯優先權、SalesRep、起訖時間、結果與產品（原僅限被指派人）。
- **`broadcastRefresh()`** — 新 VM 方法，在卡片移動或狀態變更後觸發跨工作階段廣播刷新。

#### 2026-04-16 — v2.0.3
- **雙擊縮放修正** — 修正在甘特視圖雙擊專案資料夾無法正確開啟 iDempiere 專案視窗的問題。
- **請求計數徽章** — 甘特圖每個專案列右側顯示範圍內的請求計數徽章，並遵循 `showFinalClose` 開關設定。

#### 2026-04-12 — v2.0.2
- **新建請求對話框加入專案選擇器** — 使用者可在新建請求時直接關聯 `C_Project`。
- **甘特日期選擇器修正** — 修正甘特視圖開啟日期區間選擇器時發生崩潰的問題。

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
│   ├── factory/        RequestKanbanFormFactory.java  (OSGi Form 服務)
│   ├── factory/        RequestKanbanModelFactory.java (OSGi IModelFactory — 回傳 MRequestKanban)
│   └── model/          MRequestKanban.java            (MRequest 子類別，EndTime 夾擊邏輯)
├── web/zul/            RequestKanbanForm.zul          (主 MVVM 視圖)
│                       request-new.zul / request-update.zul / project-new.zul
├── OSGI-INF/           requestkanban_form_factory.xml / requestkanban_model_factory.xml
├── META-INF/           MANIFEST.MF, 2Pack/
├── migration/          i18n_setup.sql
├── docs/               technical-guide-status-icon.md
│                       2026-04-19-idempiere-endtime-persistence-model-sandwich.md
├── pom.xml
└── README.md
```

---

### 🤝 貢獻方式

發現 Bug？開 Issue。有好點子？送 PR。想說謝謝？給顆 ⭐。

### 📜 授權
GPL-2.0-only。分享即美德。
