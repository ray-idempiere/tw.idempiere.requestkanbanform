# Design: Image Drag-and-Drop Attachment & Kanban Card Thumbnail

**Date:** 2026-04-23  
**Status:** Approved  

## Overview

Two features aligned with Trello UX:

1. **Drag & Drop image upload** on the New Request and Update Request dialogs
2. **Image thumbnail banner** displayed at the top of Kanban cards (Trello-style)

## Feature 1: Drag & Drop Image Attachment

### Scope

Both dialogs:
- `web/zul/request-new.zul` (New Request)
- `web/zul/request-update.zul` (Update Request)

### Approach

ZK native upload (`upload="true,maxsize=-1"` on `<window>`). ZK intercepts the browser `drop` event and fires `onUpload` with a `Media[]` payload. No custom JS bridge required.

### ZUL Changes

- Add `upload="true,maxsize=-1"` to the `<window>` element of both dialogs
- Add a drop zone `<div>` at the top of the dialog content area:
  - Dashed border, light background, centered text: "拖曳圖片至此上傳"
  - After upload, display a preview list (filename + small thumbnail) inside the zone
- Wire `onUpload` event to the backend handler

### Backend Changes (`RequestKanbanForm.java`)

**New Request dialog:**
- `onUpload` handler stores received `Media` objects in a `List<Media>` instance variable
- After `req.save()` succeeds in the save handler, batch-write all pending Media to `MAttachment` using `MAttachment.get(ctx, MRequest.Table_ID, req.getR_Request_ID())` then `addEntry()` + `save()`

**Update Request dialog:**
- `onUpload` handler writes Media directly to the existing Request's `MAttachment` (Record already exists, safe to persist immediately)
- Refresh attachment count label after save

**Format validation:**
- Only `image/*` MIME types accepted
- Non-image files: show ZK warning notification and skip silently

## Feature 2: Kanban Card Image Thumbnail (Banner Style)

### Visual Spec

- Position: top of card, above summary text
- Dimensions: full card width, fixed height 100px
- `object-fit: cover` to crop without distortion
- Border radius: `6px 6px 0 0` to match card corners
- Negative margin (`-10px -10px 6px -10px`) to bleed to card edges

### Data Loading (`RequestKanbanVM.java`)

After `refreshKanbanData()` completes:

1. Collect Request IDs where `hasAttachment = true`
2. Take the first 20 IDs (cap)
3. For each ID, call `MAttachment.get(ctx, 417, requestId)` and iterate entries to find the first image entry (`entry.getName()` ends with common image extensions, or check content type)
4. Read image bytes, encode to Base64 data URI (`data:image/jpeg;base64,...`)
5. Store in `Map<Integer, String> thumbnailCache` (requestId → dataUri)
6. Pass `thumbnailDataUri` into `KanbanRowModel` constructor

### `KanbanRowModel` Changes

- New field: `String thumbnailDataUri` (null = no thumbnail)
- New getter: `getThumbnailDataUri()`
- New boolean: `isShowThumbnail()` — returns `thumbnailDataUri != null`

### ZUL Changes (`RequestKanbanForm.zul`)

Add at the very top of the card `<vlayout>`, before the summary label:

```xml
<image visible="@load(row.showThumbnail)"
       src="@load(row.thumbnailDataUri)"
       style="width:100%;height:100px;object-fit:cover;border-radius:6px 6px 0 0;margin:-10px -10px 6px -10px;" />
```

Cards beyond the 20-image cap continue to show only the paperclip icon — no banner.

## Files to Change

| File | Change |
|------|--------|
| `web/zul/request-new.zul` | Add `upload` attr to `<window>`, add drop zone div, wire `onUpload` |
| `web/zul/request-update.zul` | Same as above |
| `web/zul/RequestKanbanForm.zul` | Add `<image>` banner at top of card template |
| `src/.../form/RequestKanbanForm.java` | Add `onUpload` handlers for both dialogs, pending media list, post-save attachment write |
| `src/.../viewmodel/RequestKanbanVM.java` | Add thumbnail loading logic (20-cap), pass to KanbanRowModel |
| `src/.../viewmodel/KanbanRowModel.java` | Add `thumbnailDataUri` field + getter + `isShowThumbnail()` |

## Out of Scope

- Drag & drop for non-image file types
- Lazy/on-demand thumbnail loading (deferred to future if performance requires)
- Thumbnail in List view
