# Request Card Members — Design Spec
Date: 2026-04-20

## Overview

Allow additional users to be associated with a Request card beyond `AD_User_ID` (requester) and `SalesRep_ID` (responsible). Members are stored in the existing iDempiere table `r_requestupdates`. Members can view the card (readonly). Each card displays avatars for all participants (requester + SalesRep + members), using a profile image if available, otherwise showing the first character of the user's name.

---

## 1. Data Layer

**Storage table:** `r_requestupdates` (existing, PK: `ad_user_id` + `r_request_id`)

| Operation | SQL |
|-----------|-----|
| Read members | `SELECT ad_user_id FROM r_requestupdates WHERE r_request_id = ? AND isactive = 'Y'` |
| Add member | `INSERT INTO r_requestupdates (ad_user_id, r_request_id, ad_client_id, ad_org_id, createdby, updatedby) VALUES (...)` — ignore PK conflict |
| Remove member | `UPDATE r_requestupdates SET isactive = 'N' WHERE r_request_id = ? AND ad_user_id = ?` |

**`canEditRequest()` unchanged.** Members get readonly access only; edit rights remain `canEdit || isSupervisor`.

**New helper:**
```java
boolean isMember = DB.getSQLValue(null,
    "SELECT count(*) FROM r_requestupdates WHERE r_request_id=? AND ad_user_id=? AND isactive='Y'",
    requestId, currentUserId) > 0;
```

---

## 2. Scope Filter Extension

Private and Team scopes in `loadKanbanData()` each gain an OR branch so members see cards they've been added to.

**Private:**
```sql
AND (AD_User_ID = ? OR SalesRep_ID = ?
     OR EXISTS (SELECT 1 FROM r_requestupdates
                WHERE r_request_id = R_Request.r_request_id
                AND ad_user_id = ? AND isactive = 'Y'))
```

**Team:** same EXISTS clause appended to existing OR chain.

Gantt and list view SQL get the same treatment.

---

## 3. Avatar Rendering

### AvatarModel

```java
record AvatarModel(String initials, String color, String name, String imageDataUri) {}
```

- `imageDataUri`: base64-encoded `data:image/png;base64,...` string if user has a PNG/JPG attached to their AD_User record; otherwise `null`.
- `color`: deterministic from `ad_user_id % colorPalette.length`.
- `initials`: existing `getInitials(name)` logic.

### Image lookup (batch, one SQL per card refresh)

```sql
SELECT record_id, binarydata, title
FROM ad_attachment
WHERE ad_table_id = 114
  AND record_id IN (...)
  AND (lower(title) LIKE '%.png'
    OR lower(title) LIKE '%.jpg'
    OR lower(title) LIKE '%.jpeg')
  AND binarydata IS NOT NULL
```

Result cached as `Map<Integer, String>` (userId → base64 URI) for the duration of `loadKanbanData()`.

### avatarsHtml

`KanbanRowModel` gains:
```java
private final String avatarsHtml; // pre-rendered HTML, empty string if no participants
```

Each avatar rendered as:
- **With image:** `<img src="data:image/png;base64,..." style="border-radius:50%;width:28px;height:28px;object-fit:cover;flex-shrink:0;" title="NAME"/>`
- **Without image:** `<span style="border-radius:50%;background:COLOR;color:#fff;width:28px;height:28px;display:flex;align-items:center;justify-content:center;font-size:11px;font-weight:700;flex-shrink:0;" title="NAME">INITIALS</span>`

Wrapped in a flex container div. Max display: 5 avatars, then `+N` chip if more.

### ZUL card template addition

```xml
<html content="@load(row.avatarsHtml)"
      style="display:flex;gap:4px;margin-top:4px;flex-wrap:wrap;"/>
```

Placed at the bottom of the card `vlayout`, after the due badge row.

---

## 4. Edit Dialog — Member Editor

**Position:** Basic Info section, between Attachment and Summary.

**Layout:**
```
Members:  [王育傑 ×] [Ray Lee ×]  [+]
```

- Each chip: user name + × button (only visible when `canEditAny`)
- `[+]` button opens `WSearchEditor` for `AD_User_ID` (column 5434)
- Add/remove writes to DB immediately (same pattern as Attachment) — no need to wait for Save
- Requester (AD_User_ID) and SalesRep are excluded from the chip list (they have access inherently)

**Readonly scenario** (`isMember && !canEditAny`): chips displayed without × and without `[+]`.

---

## 5. Permission Summary

| User type | See card on board | Open dialog | Edit fields | Manage members |
|-----------|:-----------------:|:-----------:|:-----------:|:--------------:|
| Requester (AD_User_ID) | ✓ | ✓ | ✓ | ✓ |
| SalesRep | ✓ | ✓ | ✓ | ✓ |
| Supervisor | ✓ | ✓ | ✓ | ✓ |
| Member (r_requestupdates) | ✓ | ✓ (readonly) | ✗ | ✗ |

---

## 6. Files to Change

| File | Change |
|------|--------|
| `KanbanRowModel.java` | Add `AvatarModel` record, `avatarsHtml` field, constructor param |
| `RequestKanbanVM.java` | `loadKanbanData()` scope filter, avatar batch SQL, `isMember()` helper |
| `RequestKanbanForm.java` | `setupUpdateDialog()` — member chip UI, add/remove handlers, `isMember` guard |
| `RequestKanbanForm.zul` | Card template — add `<html content="@load(row.avatarsHtml)"/>` |
| `i18n_setup.sql` | Add `RK_Members`, `RK_AddMember` messages |
| `RK_Messages.xml` | Same messages for 2Pack |
