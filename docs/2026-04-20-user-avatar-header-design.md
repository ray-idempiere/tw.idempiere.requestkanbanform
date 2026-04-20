# User Avatar in Header Design

**Goal:** Show the logged-in user's avatar in the top bar; clicking it opens the AD_User Attachment dialog so the user can upload or replace their profile picture, which refreshes immediately.

**Architecture:** VM-driven. `RequestKanbanVM` loads the current user's avatar image via `MAttachment` (same pattern as card avatars) and exposes it as an HTML string. The ZUL top bar renders it with `<html content="@load(...)"/>` inside a clickable `<div>`. A VM command opens `WAttachment` with a callback that reloads the avatar field and notifies ZK to re-render.

**Files touched:**
- `src/.../viewmodel/RequestKanbanVM.java`
- `web/zul/RequestKanbanForm.zul`

---

## VM Changes — RequestKanbanVM

### New fields
```java
private int currentUserId;
private String currentUserName;
private String currentUserAvatarHtml;
```

### `init()` — call at end
```java
loadCurrentUserAvatar();
```

### `loadCurrentUserAvatar()` — private
```java
private void loadCurrentUserAvatar() {
    Properties ctx = Env.getCtx();
    currentUserId = Env.getAD_User_ID(ctx);
    MUser u = new MUser(ctx, currentUserId, null);
    currentUserName = u.getName();

    String imgUri = null;
    try {
        MAttachment att = MAttachment.get(ctx, 114, currentUserId);
        if (att != null) {
            for (MAttachmentEntry entry : att.getEntries()) {
                if (entry == null) continue;
                String name = entry.getName() != null ? entry.getName().toLowerCase() : "";
                if (!name.endsWith(".png") && !name.endsWith(".jpg") && !name.endsWith(".jpeg")) continue;
                byte[] data = entry.getData();
                if (data == null || data.length == 0) continue;
                String ct = entry.getContentType();
                if (ct == null || ct.isBlank()) ct = name.endsWith(".png") ? "image/png" : "image/jpeg";
                imgUri = "data:" + ct + ";base64," + Base64.getEncoder().encodeToString(data);
                break;
            }
        }
    } catch (Exception ex) {
        logger.log(Level.WARNING, "loadCurrentUserAvatar failed", ex);
    }

    KanbanRowModel.AvatarModel avatar = new KanbanRowModel.AvatarModel(
        KanbanRowModel.getInitials(currentUserName),
        KanbanRowModel.avatarColor(currentUserId),
        currentUserName,
        imgUri);
    currentUserAvatarHtml = buildSingleAvatarHtml(avatar, 36);
}
```

### `buildSingleAvatarHtml(AvatarModel, int size)` — private static
Renders one avatar circle at the given pixel size:
```java
private static String buildSingleAvatarHtml(KanbanRowModel.AvatarModel a, int size) {
    String style = "width:" + size + "px;height:" + size + "px;border-radius:50%;" +
        "display:inline-flex;align-items:center;justify-content:center;" +
        "font-size:" + (size / 2.5) + "px;font-weight:700;color:#fff;overflow:hidden;" +
        "background:" + a.color() + ";flex-shrink:0;";
    if (a.imageDataUri() != null) {
        return "<div style=\"" + style + "\">" +
            "<img src=\"" + a.imageDataUri() + "\" style=\"width:100%;height:100%;object-fit:cover;\"/>" +
            "</div>";
    }
    return "<div style=\"" + style + "\">" + escHtml(a.initials()) + "</div>";
}

private static String escHtml(String s) {
    if (s == null) return "";
    return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
}
```

(Note: `escHtml` already exists in `KanbanRowModel` — copy the same impl here or make it a shared utility.)

### `@Command openUserAvatar()`
```java
@Command
public void openUserAvatar() {
    WAttachment wa = new WAttachment(0, 0, 114, currentUserId, null, ev -> {
        loadCurrentUserAvatar();
        BindUtils.postNotifyChange(null, null, RequestKanbanVM.this, "currentUserAvatarHtml");
    });
    wa.setWidth("800px");
    wa.setHeight("600px");
    wa.setSclass("attachment-all");
    wa.setShadow(true);
    wa.setBorder("normal");
    wa.setClosable(true);
    wa.doModal();
}
```

### New getters
```java
public String getCurrentUserAvatarHtml() { return currentUserAvatarHtml; }
public String getCurrentUserName()        { return currentUserName; }
```

---

## ZUL Changes — RequestKanbanForm.zul

After `<space hflex="1"/>`, before the version label:

```xml
<!-- Logged-in user avatar -->
<div onClick="@command('openUserAvatar')"
     style="cursor:pointer;display:inline-flex;align-items:center;margin-right:8px;"
     tooltiptext="@load(vm.currentUserName)">
  <html content="@load(vm.currentUserAvatarHtml)"/>
</div>
```

---

## Behaviour

| Situation | Result |
|-----------|--------|
| User has PNG/JPG attachment | Shows photo circle (36 px) |
| No attachment | Shows initials circle with colour derived from user ID |
| Click avatar | Opens WAttachment modal (800×600) |
| Save / upload in WAttachment then close | Avatar reloads immediately, no page refresh needed |
