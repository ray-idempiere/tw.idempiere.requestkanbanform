# Image Drag-and-Drop & Kanban Thumbnail Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Trello-style drag-and-drop image upload to both Request dialogs, and display the first image attachment as a banner at the top of each Kanban card (top 20 capped).

**Architecture:** ZK native `upload="true,maxsize=-1"` handles cross-browser drag-and-drop; `onUpload` events are handled in `RequestKanbanForm.java` which saves bytes via `MAttachment`/`addEntry`. Thumbnails are loaded as Base64 data URIs in `RequestKanbanVM.refreshKanbanData()` after the main SQL query, capped at 20, stored in `KanbanRowModel.thumbnailDataUri`, and rendered via a `<image>` component at the top of each card in the ZUL template.

**Tech Stack:** Java 17, ZK Framework (ZUL), iDempiere `MAttachment`/`MAttachmentEntry`, `Base64` (java.util)

---

## File Map

| File | Role |
|------|------|
| `src/.../viewmodel/KanbanRowModel.java` | Add `thumbnailDataUri` field + getter + `isShowThumbnail()` |
| `src/.../viewmodel/RequestKanbanVM.java` | Load top-20 thumbnails after `refreshKanbanData()`, pass to constructor |
| `web/zul/RequestKanbanForm.zul` | Add `<image>` banner at top of card template |
| `web/zul/request-new.zul` | Add `upload` attr, drop zone div, `onUpload` event |
| `web/zul/request-update.zul` | Add `upload` attr, drop zone div, `onUpload` event |
| `src/.../form/RequestKanbanForm.java` | Add `onUpload` handlers + pending-media list for new dialog |

---

## Task 1: Add `thumbnailDataUri` to `KanbanRowModel`

**Files:**
- Modify: `src/tw/idempiere/requestkanbanform/viewmodel/KanbanRowModel.java`

- [ ] **Step 1: Add field and update constructor**

In `KanbanRowModel.java`, add the new field after `avatarsHtml` (line ~46) and update the constructor:

```java
// Field (add after avatarsHtml field):
private final String thumbnailDataUri;

// Constructor signature — add parameter at end:
public KanbanRowModel(int requestId, int statusId, String statusValue,
                      String documentNo, String summary, int priority,
                      String customer, String responsible,
                      LocalDate startDate, LocalDate endDate,
                      boolean hasAttachment, boolean isMyRequest,
                      String avatarsHtml, String thumbnailDataUri) {
    // ... existing assignments ...
    this.thumbnailDataUri = thumbnailDataUri;
}
```

- [ ] **Step 2: Add getter and boolean helper**

Add after `getAvatarsHtml()` getter:

```java
public String getThumbnailDataUri() { return thumbnailDataUri; }
public boolean isShowThumbnail()    { return thumbnailDataUri != null; }
```

- [ ] **Step 3: Fix the single call site that constructs KanbanRowModel**

In `RequestKanbanVM.java` around line 908, the constructor is called inside the `for (RawRowData raw : rawRows)` loop. Add `null` as the last argument temporarily (thumbnail wiring comes in Task 2):

```java
kanbanRows.get(raw.statusVal()).add(new KanbanRowModel(
    raw.requestId(), raw.statusId(), raw.statusVal(),
    raw.documentNo(), raw.summary(), raw.priority(),
    raw.customer(), raw.responsible(),
    raw.startDate(), raw.endDate(),
    raw.hasAtt(), raw.isMyRequest(), avatarsHtml, null  // thumbnailDataUri added in Task 2
));
```

- [ ] **Step 4: Verify it compiles**

```bash
cd /Users/ray/sources/pt12/tw.idempiere.requestkanbanform
mvn compile -q
```

Expected: `BUILD SUCCESS` with no errors.

- [ ] **Step 5: Commit**

```bash
git add src/tw/idempiere/requestkanbanform/viewmodel/KanbanRowModel.java \
        src/tw/idempiere/requestkanbanform/viewmodel/RequestKanbanVM.java
git commit -m "feat: add thumbnailDataUri field to KanbanRowModel"
```

---

## Task 2: Load top-20 thumbnails in `RequestKanbanVM`

**Files:**
- Modify: `src/tw/idempiere/requestkanbanform/viewmodel/RequestKanbanVM.java`

- [ ] **Step 1: Add the thumbnail-loading helper method**

Add this private method to `RequestKanbanVM.java`, after `loadUserAvatarImages()` or near the other `MAttachment` helpers:

```java
/**
 * Loads the first image attachment for each request ID in the given list,
 * up to a maximum of {@code cap} entries. Returns a map from request ID to
 * a Base64 data URI string suitable for use in an HTML src attribute.
 */
private Map<Integer, String> loadRequestThumbnails(List<Integer> requestIds, int cap) {
    Map<Integer, String> result = new HashMap<>();
    Properties ctx = Env.getCtx();
    final int R_REQUEST_TABLE_ID = 417;
    int count = 0;
    for (int requestId : requestIds) {
        if (count >= cap) break;
        try {
            MAttachment att = MAttachment.get(ctx, R_REQUEST_TABLE_ID, requestId);
            if (att == null) continue;
            MAttachmentEntry[] entries = att.getEntries();
            if (entries == null) continue;
            for (MAttachmentEntry entry : entries) {
                if (entry == null || !entry.isGraphic()) continue;
                byte[] data = entry.getData();
                if (data == null || data.length == 0) continue;
                String ct = entry.getContentType();
                if (ct == null || ct.isBlank()) ct = "image/png";
                String uri = "data:" + ct + ";base64," + Base64.getEncoder().encodeToString(data);
                result.put(requestId, uri);
                count++;
                break; // first image only
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "loadRequestThumbnails: failed for R_Request_ID=" + requestId, ex);
        }
    }
    return result;
}
```

- [ ] **Step 2: Call the helper after building `rawRows`, wire into constructor**

In `refreshKanbanData()`, after the `DB.close(rs, pstmt)` block and before the `loadUserNames` line (~line 902), add:

```java
// Collect request IDs that have attachments, in query order (Priority ASC, StartDate DESC)
List<Integer> idsWithAttach = rawRows.stream()
    .filter(RawRowData::hasAtt)
    .map(RawRowData::requestId)
    .collect(Collectors.toList());
Map<Integer, String> thumbnailCache = loadRequestThumbnails(idsWithAttach, 20);
```

Then in the `for (RawRowData raw : rawRows)` loop, replace the `null` placeholder from Task 1:

```java
kanbanRows.get(raw.statusVal()).add(new KanbanRowModel(
    raw.requestId(), raw.statusId(), raw.statusVal(),
    raw.documentNo(), raw.summary(), raw.priority(),
    raw.customer(), raw.responsible(),
    raw.startDate(), raw.endDate(),
    raw.hasAtt(), raw.isMyRequest(), avatarsHtml,
    thumbnailCache.get(raw.requestId())  // null if not in top-20 or no image
));
```

- [ ] **Step 3: Verify it compiles**

```bash
mvn compile -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add src/tw/idempiere/requestkanbanform/viewmodel/RequestKanbanVM.java
git commit -m "feat: load top-20 request image thumbnails in refreshKanbanData"
```

---

## Task 3: Show thumbnail banner in Kanban card ZUL

**Files:**
- Modify: `web/zul/RequestKanbanForm.zul`

- [ ] **Step 1: Add `<image>` banner at the very top of the card vlayout**

In `RequestKanbanForm.zul`, inside the card `<template name="model">`, the card content starts at line ~90:

```xml
<vlayout spacing="5px" style="@load(row.cardStyle)">
```

Insert the image **as the first child** of this `<vlayout>`, before the summary `<label>`:

```xml
<vlayout spacing="5px" style="@load(row.cardStyle)">
 <image visible="@load(row.showThumbnail)"
        src="@load(row.thumbnailDataUri)"
        style="width:100%;height:100px;object-fit:cover;border-radius:6px 6px 0 0;display:block;margin:-10px -10px 6px -10px;max-width:calc(100% + 20px);" />
 <label value="@load(row.summary)" ...
```

- [ ] **Step 2: Verify it compiles**

```bash
mvn compile -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Manual smoke test**

Deploy to iDempiere, open Kanban view. Cards with image attachments should show a 100px-tall banner at the top. Cards without images show no banner (only paperclip icon as before).

- [ ] **Step 4: Commit**

```bash
git add web/zul/RequestKanbanForm.zul
git commit -m "feat: show first-image thumbnail banner on kanban cards (top 20)"
```

---

## Task 4: Drag & Drop upload on New Request dialog

**Files:**
- Modify: `web/zul/request-new.zul`
- Modify: `src/tw/idempiere/requestkanbanform/form/RequestKanbanForm.java`

### 4a — ZUL changes

- [ ] **Step 1: Add `upload` attribute and drop zone to `request-new.zul`**

Replace the `<window>` opening tag to add `upload="true,maxsize=-1"`:

```xml
<window title="Request Application"
        position="center,center" closable="true"
        border="normal" width="560px" height="auto"
        upload="true,maxsize=-1">
```

Add a drop zone `<div>` as the **first child** of the `<vlayout>`, before `<div id="requestDoc"/>`:

```xml
<vlayout spacing="0" style="padding:8px;">
 <div id="dropZoneNew"
      style="border:2px dashed #aab;border-radius:6px;padding:12px;text-align:center;color:#888;font-size:12px;margin-bottom:6px;background:#fafafa;">
  拖曳圖片至此上傳（儲存後生效）
 </div>
 <div id="dropPreviewNew"/>
 <div id="requestDoc"/>
 ...
```

### 4b — Backend: pending media list + onUpload handler

- [ ] **Step 2: Add pending media list field to `RequestKanbanForm.java`**

Add instance field near the other dialog fields (line ~82):

```java
// Pending images dropped on the new-request dialog before save
private final java.util.List<org.zkoss.util.media.Media> pendingNewMedia = new java.util.ArrayList<>();
```

- [ ] **Step 3: Register `onUpload` listener in `openNewRequestDialog()`**

In `openNewRequestDialog()`, after `buildNewRequestDialogEditors(dialog)` and before `dialog.doModal()`:

```java
pendingNewMedia.clear();
Div dropZone    = (Div) dialog.getFellow("dropZoneNew");
Div dropPreview = (Div) dialog.getFellow("dropPreviewNew");
dialog.addEventListener(org.zkoss.zk.ui.event.Events.ON_UPLOAD, ev -> {
    org.zkoss.zk.ui.event.UploadEvent ue = (org.zkoss.zk.ui.event.UploadEvent) ev;
    org.zkoss.util.media.Media[] medias = ue.getMedias();
    if (medias == null) return;
    for (org.zkoss.util.media.Media m : medias) {
        if (m == null || m.getContentType() == null) continue;
        if (!m.getContentType().startsWith("image/")) {
            org.zkoss.zk.ui.util.Clients.showNotification(
                m.getName() + " 不是圖片格式，略過。",
                org.zkoss.zk.ui.util.Clients.NOTIFICATION_TYPE_WARNING, null, null, 3000);
            continue;
        }
        pendingNewMedia.add(m);
        // Show preview label inside dropPreview div
        org.zkoss.zul.Label lbl = new org.zkoss.zul.Label("✓ " + m.getName());
        lbl.setStyle("font-size:11px;color:#2e7d32;display:block;");
        dropPreview.appendChild(lbl);
    }
    if (!pendingNewMedia.isEmpty())
        dropZone.setStyle(dropZone.getStyle().replace("#fafafa", "#e8f5e9"));
});
```

- [ ] **Step 4: Save pending media after `req.save()` in `saveNewRequest()`**

In `RequestKanbanVM.saveNewRequest()`, after the existing `if (!req.save()) { ... return; }` block and before `broadcastRefresh()`, call a new helper on the form. Since `saveNewRequest` lives in the VM and `pendingNewMedia` is in the Form, pass the request ID back and have the Form save — the cleanest approach is to move the save trigger into the Form's button handler.

In `RequestKanbanForm.java`, `bindNewRequestButtons()`, replace the existing `btnSave` listener:

```java
if (btnSave != null) btnSave.addEventListener(Events.ON_CLICK, e -> {
    String summary = txtSummary != null ? txtSummary.getValue().trim() : "";
    int savedId = vm.saveNewRequest(fUser, fDoc, fPriority, fDepart, fSalesRep, fRole, fProject, summary);
    if (savedId > 0 && !pendingNewMedia.isEmpty()) {
        savePendingMediaToRequest(savedId);
    }
    pendingNewMedia.clear();
    dialog.detach();
});
```

- [ ] **Step 5: Change `saveNewRequest` return type to `int` in VM**

In `RequestKanbanVM.java`, change the method signature and the final lines:

```java
// Old: public void saveNewRequest(...)
// New:
public int saveNewRequest(WEditor fUser, WEditor fDoc, WEditor fPriority,
                           WEditor fDepart, WEditor fSalesRep, WEditor fRole,
                           WEditor fProject, String summary) {
    // ... existing body unchanged until the end ...

    if (!req.save()) {
        Clients.showNotification("Failed to save request",
            Clients.NOTIFICATION_TYPE_ERROR, null, null, 3000);
        return 0;  // was: return;
    }
    broadcastRefresh();
    refreshKanbanData();
    BindUtils.postNotifyChange(this, "*");
    return req.getR_Request_ID();  // was: (end of method)
}
```

- [ ] **Step 6: Add `savePendingMediaToRequest` helper to `RequestKanbanForm.java`**

```java
private void savePendingMediaToRequest(int requestId) {
    try {
        org.compiere.model.MAttachment att =
            org.compiere.model.MAttachment.get(Env.getCtx(), MRequest.Table_ID, requestId);
        if (att == null) {
            att = new org.compiere.model.MAttachment(Env.getCtx(), MRequest.Table_ID, requestId, null);
        }
        for (org.zkoss.util.media.Media m : pendingNewMedia) {
            try {
                att.addEntry(m.getName(), m.getByteData());
            } catch (Exception ex) {
                log.warning("savePendingMediaToRequest: could not add " + m.getName() + ": " + ex.getMessage());
            }
        }
        att.save();
    } catch (Exception ex) {
        log.log(Level.WARNING, "savePendingMediaToRequest failed for R_Request_ID=" + requestId, ex);
    }
}
```

- [ ] **Step 7: Verify it compiles**

```bash
mvn compile -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Manual smoke test**

Open New Request dialog. Drag a PNG from Finder onto the drop zone. Confirm the filename appears below with a ✓. Save the request. Open it in update dialog — verify the Attachment button shows `(1)`.

- [ ] **Step 9: Commit**

```bash
git add web/zul/request-new.zul \
        src/tw/idempiere/requestkanbanform/form/RequestKanbanForm.java \
        src/tw/idempiere/requestkanbanform/viewmodel/RequestKanbanVM.java
git commit -m "feat: drag-and-drop image upload on new request dialog"
```

---

## Task 5: Drag & Drop upload on Update Request dialog

**Files:**
- Modify: `web/zul/request-update.zul`
- Modify: `src/tw/idempiere/requestkanbanform/form/RequestKanbanForm.java`

### 5a — ZUL changes

- [ ] **Step 1: Add `upload` attribute and drop zone to `request-update.zul`**

Replace the `<window>` opening tag:

```xml
<window
    position="center,center" closable="true"
    action="show: slideDown;hide: slideUp" border="normal"
    width="460px"
    upload="true,maxsize=-1">
```

Add a drop zone `<div>` as the **first child** of the `<vlayout>`, before `<div id="requestDoc">`:

```xml
<vlayout>
 <div id="dropZoneUpdate"
      style="border:2px dashed #aab;border-radius:6px;padding:12px;text-align:center;color:#888;font-size:12px;margin-bottom:6px;background:#fafafa;">
  拖曳圖片至此上傳（立即儲存）
 </div>
 <div id="dropPreviewUpdate"/>
 <div id="requestDoc"></div>
 ...
```

### 5b — Backend: immediate save on drop

- [ ] **Step 2: Register `onUpload` listener in `setupUpdateDialog()`**

In `RequestKanbanForm.java`, at the **end** of `setupUpdateDialog()` (after the `btnZoom` listener, before the closing `}`), add:

```java
// Wire drag-drop upload — save immediately since the request already exists
Div dropZone    = (Div) dialog.getFellow("dropZoneUpdate");
Div dropPreview = (Div) dialog.getFellow("dropPreviewUpdate");
if (dropZone != null) {
    dialog.addEventListener(org.zkoss.zk.ui.event.Events.ON_UPLOAD, ev -> {
        org.zkoss.zk.ui.event.UploadEvent ue = (org.zkoss.zk.ui.event.UploadEvent) ev;
        org.zkoss.util.media.Media[] medias = ue.getMedias();
        if (medias == null) return;
        boolean anyAdded = false;
        try {
            org.compiere.model.MAttachment att =
                org.compiere.model.MAttachment.get(Env.getCtx(), MRequest.Table_ID, request.getR_Request_ID());
            if (att == null)
                att = new org.compiere.model.MAttachment(Env.getCtx(),
                    MRequest.Table_ID, request.getR_Request_ID(), null);
            for (org.zkoss.util.media.Media m : medias) {
                if (m == null || m.getContentType() == null) continue;
                if (!m.getContentType().startsWith("image/")) {
                    org.zkoss.zk.ui.util.Clients.showNotification(
                        m.getName() + " 不是圖片格式，略過。",
                        org.zkoss.zk.ui.util.Clients.NOTIFICATION_TYPE_WARNING, null, null, 3000);
                    continue;
                }
                att.addEntry(m.getName(), m.getByteData());
                anyAdded = true;
                org.zkoss.zul.Label lbl = new org.zkoss.zul.Label("✓ " + m.getName());
                lbl.setStyle("font-size:11px;color:#2e7d32;display:block;");
                if (dropPreview != null) dropPreview.appendChild(lbl);
            }
            if (anyAdded) {
                att.save();
                dropZone.setStyle(dropZone.getStyle().replace("#fafafa", "#e8f5e9"));
                vm.broadcastRefresh();
            }
        } catch (Exception ex) {
            log.log(Level.WARNING, "Update dialog onUpload failed", ex);
            org.zkoss.zk.ui.util.Clients.showNotification(
                "上傳失敗：" + ex.getMessage(),
                org.zkoss.zk.ui.util.Clients.NOTIFICATION_TYPE_ERROR, null, null, 4000);
        }
    });
}
```

- [ ] **Step 3: Verify it compiles**

```bash
mvn compile -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Manual smoke test**

Open an existing Request. Drag a JPEG from Finder onto the drop zone. Confirm ✓ filename appears. Close and reopen the dialog — verify Attachment button shows `(1)` or incremented count. Open Attachment panel — verify image is listed.

- [ ] **Step 5: Verify Kanban card thumbnail appears**

After dropping an image on a Request's update dialog, go back to Kanban view and refresh. Confirm the card now shows the image as a 100px banner at the top (if it is within the first 20 with attachments).

- [ ] **Step 6: Commit**

```bash
git add web/zul/request-update.zul \
        src/tw/idempiere/requestkanbanform/form/RequestKanbanForm.java
git commit -m "feat: drag-and-drop image upload on update request dialog"
```

---

## Self-Review Notes

**Spec coverage:**
- ✅ Drag & drop on New Request dialog
- ✅ Drag & drop on Update Request dialog
- ✅ Image-only validation (skip non-images with warning)
- ✅ Kanban card thumbnail banner (Trello style, top of card)
- ✅ Cap at 20 thumbnails per refresh
- ✅ New dialog: pending media saved after `req.save()`
- ✅ Update dialog: media saved immediately on drop

**Type consistency:**
- `saveNewRequest` returns `int` (requestId, or 0 on failure) — used in Task 4 Step 4 and defined in Task 4 Step 5
- `savePendingMediaToRequest(int requestId)` defined in Task 4 Step 6, called in Task 4 Step 4
- `KanbanRowModel` 14-arg constructor defined in Task 1 Step 1, called in Task 2 Step 2
- `isShowThumbnail()` defined in Task 1 Step 2, used in Task 3 Step 1
- `getThumbnailDataUri()` defined in Task 1 Step 2, used in Task 3 Step 1

**No placeholders:** All steps contain exact code.
