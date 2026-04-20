# Request Card Members Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Associate additional users ("members") with a Request card via `r_requestupdates`, display all participants as Trello-style avatars on each card, and let members open the card in readonly mode.

**Architecture:** Extend `KanbanRowModel` with a pre-rendered `avatarsHtml` string. `refreshKanbanData()` does a two-pass load: first collect raw row data + user IDs, then batch-query names and avatar images, then build `KanbanRowModel` instances. Member chip UI in the update dialog writes to `r_requestupdates` immediately (same pattern as Attachment). Scope filters (Private/Team) gain an OR branch so members see their cards.

**Tech Stack:** Java 17, ZK 9, iDempiere 12, PostgreSQL — `DB.prepareStatement`, `Env.getCtx()`, `MUser`, `WSearchEditor`

---

## File Structure

| File | Change |
|------|--------|
| `src/.../viewmodel/KanbanRowModel.java` | Add `AvatarModel` record, color palette, `getInitials`, `escHtml`, `buildAvatarsHtml`, `avatarsHtml` field |
| `src/.../viewmodel/RequestKanbanVM.java` | Two-pass `refreshKanbanData`, scope filter extensions, `isMember` helper, `loadUserData`, `buildAvatarList` |
| `src/.../form/RequestKanbanForm.java` | Member chip section in `setupUpdateDialog`, `buildMemberChip` helper |
| `web/zul/RequestKanbanForm.zul` | Add `<html content="@load(row.avatarsHtml)"/>` to card template |
| `migration/i18n_setup.sql` | Add `RK_Members`, `RK_AddMember` |
| `META-INF/2Pack/RK_Messages.xml` | Same two messages |
| `src/test/java/.../viewmodel/KanbanRowModelTest.java` | Unit tests for avatar HTML generation |

---

## Task 1: i18n Messages

**Files:**
- Modify: `migration/i18n_setup.sql`
- Modify: `META-INF/2Pack/RK_Messages.xml`

- [ ] **Step 1: Add messages to i18n_setup.sql**

In the INSERT VALUES block, add after `('RK_RequestNotFound', 'Request not found', 'E')`:

```sql
    ('RK_Members',   'Members',    'I'),
    ('RK_AddMember', '+ Member',   'I')
```

In the zh_TW CASE block, add before the final `END`:

```sql
        WHEN 'RK_Members'   THEN '成員'
        WHEN 'RK_AddMember' THEN '+ 成員'
```

- [ ] **Step 2: Add messages to RK_Messages.xml**

After the `RK_RequestNotFound` row:

```xml
      <row>
        <field name="Value" type="String">RK_Members</field>
        <field name="MsgText" type="String">Members</field>
        <field name="MsgType" type="String">I</field>
        <field name="EntityType" type="String">U</field>
        <translation language="zh_TW"><field name="MsgText" type="String">成員</field></translation>
      </row>
      <row>
        <field name="Value" type="String">RK_AddMember</field>
        <field name="MsgText" type="String">+ Member</field>
        <field name="MsgType" type="String">I</field>
        <field name="EntityType" type="String">U</field>
        <translation language="zh_TW"><field name="MsgText" type="String">+ 成員</field></translation>
      </row>
```

- [ ] **Step 3: Commit**

```bash
git add migration/i18n_setup.sql META-INF/2Pack/RK_Messages.xml
git commit -m "feat: add RK_Members and RK_AddMember i18n messages"
```

---

## Task 2: AvatarModel + Static Utilities in KanbanRowModel

**Files:**
- Modify: `src/tw/idempiere/requestkanbanform/viewmodel/KanbanRowModel.java`

- [ ] **Step 1: Add AvatarModel record and color palette**

After the class opening brace (before field declarations), add:

```java
    public record AvatarModel(String initials, String color, String name, String imageDataUri) {}

    private static final String[] AVATAR_COLORS = {
        "#1976d2", "#e65100", "#2e7d32", "#6a1b9a",
        "#00838f", "#c62828", "#4527a0", "#00695c"
    };

    public static String avatarColor(int userId) {
        return AVATAR_COLORS[Math.abs(userId) % AVATAR_COLORS.length];
    }
```

- [ ] **Step 2: Add getInitials, escHtml, buildAvatarsHtml static methods**

Add at the bottom of the class (before the closing `}`):

```java
    public static String getInitials(String name) {
        if (name == null || name.isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2 && !parts[0].isEmpty() && !parts[parts.length - 1].isEmpty())
            return (String.valueOf(parts[0].charAt(0))
                + String.valueOf(parts[parts.length - 1].charAt(0))).toUpperCase();
        return name.length() >= 2 ? name.substring(0, 2).toUpperCase()
                                  : name.substring(0, 1).toUpperCase();
    }

    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    public static String buildAvatarsHtml(java.util.List<AvatarModel> avatars) {
        if (avatars == null || avatars.isEmpty()) return "";
        int max = Math.min(avatars.size(), 5);
        StringBuilder sb = new StringBuilder(
            "<div style=\"display:flex;gap:4px;margin-top:4px;flex-wrap:wrap;align-items:center;\">");
        for (int i = 0; i < max; i++) {
            AvatarModel a = avatars.get(i);
            if (a.imageDataUri() != null) {
                sb.append("<img src=\"").append(escHtml(a.imageDataUri()))
                  .append("\" style=\"border-radius:50%;width:28px;height:28px;")
                  .append("object-fit:cover;flex-shrink:0;\" title=\"")
                  .append(escHtml(a.name())).append("\"/>");
            } else {
                sb.append("<span style=\"border-radius:50%;background:")
                  .append(a.color())
                  .append(";color:#fff;width:28px;height:28px;display:inline-flex;")
                  .append("align-items:center;justify-content:center;")
                  .append("font-size:11px;font-weight:700;flex-shrink:0;\" title=\"")
                  .append(escHtml(a.name())).append("\">")
                  .append(escHtml(a.initials())).append("</span>");
            }
        }
        if (avatars.size() > 5) {
            sb.append("<span style=\"border-radius:50%;background:#888;color:#fff;width:28px;height:28px;")
              .append("display:inline-flex;align-items:center;justify-content:center;")
              .append("font-size:10px;font-weight:700;flex-shrink:0;\">+")
              .append(avatars.size() - 5).append("</span>");
        }
        sb.append("</div>");
        return sb.toString();
    }
```

- [ ] **Step 3: Commit**

```bash
git add src/tw/idempiere/requestkanbanform/viewmodel/KanbanRowModel.java
git commit -m "feat: add AvatarModel, color palette, and buildAvatarsHtml to KanbanRowModel"
```

---

## Task 3: Unit Tests for Avatar Logic

**Files:**
- Create: `src/test/java/tw/idempiere/requestkanbanform/viewmodel/KanbanRowModelTest.java`

- [ ] **Step 1: Create test directory**

```bash
mkdir -p src/test/java/tw/idempiere/requestkanbanform/viewmodel
```

- [ ] **Step 2: Write failing tests**

```java
package tw.idempiere.requestkanbanform.viewmodel;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class KanbanRowModelTest {

    @Test
    void getInitials_singleWord_returnsFirstTwoChars() {
        assertEquals("WA", KanbanRowModel.getInitials("Wang"));
    }

    @Test
    void getInitials_twoWords_returnsFirstCharOfEach() {
        assertEquals("RL", KanbanRowModel.getInitials("Ray Lee"));
    }

    @Test
    void getInitials_null_returnsQuestionMark() {
        assertEquals("?", KanbanRowModel.getInitials(null));
    }

    @Test
    void avatarColor_sameUserId_returnsSameColor() {
        assertEquals(KanbanRowModel.avatarColor(42), KanbanRowModel.avatarColor(42));
    }

    @Test
    void avatarColor_differentUserIds_returnsValidHexColor() {
        String color = KanbanRowModel.avatarColor(1);
        assertTrue(color.startsWith("#"), "Expected hex color, got: " + color);
    }

    @Test
    void buildAvatarsHtml_emptyList_returnsEmptyString() {
        assertEquals("", KanbanRowModel.buildAvatarsHtml(List.of()));
    }

    @Test
    void buildAvatarsHtml_withInitials_rendersSpan() {
        var avatar = new KanbanRowModel.AvatarModel("RL", "#1976d2", "Ray Lee", null);
        String html = KanbanRowModel.buildAvatarsHtml(List.of(avatar));
        assertTrue(html.contains("RL"));
        assertTrue(html.contains("#1976d2"));
        assertTrue(html.contains("Ray Lee"));
    }

    @Test
    void buildAvatarsHtml_withImage_rendersImg() {
        var avatar = new KanbanRowModel.AvatarModel("RL", "#1976d2", "Ray Lee", "data:image/png;base64,abc");
        String html = KanbanRowModel.buildAvatarsHtml(List.of(avatar));
        assertTrue(html.contains("<img"));
        assertTrue(html.contains("data:image/png;base64,abc"));
    }

    @Test
    void buildAvatarsHtml_moreThanFive_showsPlusChip() {
        List<KanbanRowModel.AvatarModel> avatars = List.of(
            new KanbanRowModel.AvatarModel("A1", "#111111", "User1", null),
            new KanbanRowModel.AvatarModel("A2", "#222222", "User2", null),
            new KanbanRowModel.AvatarModel("A3", "#333333", "User3", null),
            new KanbanRowModel.AvatarModel("A4", "#444444", "User4", null),
            new KanbanRowModel.AvatarModel("A5", "#555555", "User5", null),
            new KanbanRowModel.AvatarModel("A6", "#666666", "User6", null)
        );
        String html = KanbanRowModel.buildAvatarsHtml(avatars);
        assertTrue(html.contains("+1"), "Expected +1 overflow chip");
    }

    @Test
    void buildAvatarsHtml_xssInName_isEscaped() {
        var avatar = new KanbanRowModel.AvatarModel("XS", "#1976d2", "<script>alert(1)</script>", null);
        String html = KanbanRowModel.buildAvatarsHtml(List.of(avatar));
        assertFalse(html.contains("<script>"), "XSS not escaped");
        assertTrue(html.contains("&lt;script&gt;"));
    }
}
```

- [ ] **Step 3: Run tests — expect PASS (no DB dependency)**

```bash
mvn test -pl . -Dtest=KanbanRowModelTest 2>&1 | tail -20
```

Expected output: `Tests run: 9, Failures: 0, Errors: 0`

- [ ] **Step 4: Commit**

```bash
git add src/test/java/tw/idempiere/requestkanbanform/viewmodel/KanbanRowModelTest.java
git commit -m "test: unit tests for KanbanRowModel avatar logic"
```

---

## Task 4: avatarsHtml Field in KanbanRowModel

**Files:**
- Modify: `src/tw/idempiere/requestkanbanform/viewmodel/KanbanRowModel.java`

- [ ] **Step 1: Add avatarsHtml field and update constructor**

Add field after `dueBadgeStyle`:

```java
    private final String avatarsHtml;
```

Update the constructor signature (add `avatarsHtml` as last parameter):

```java
    public KanbanRowModel(int requestId, int statusId, String statusValue,
                          String documentNo, String summary, int priority,
                          String customer, String responsible,
                          LocalDate startDate, LocalDate endDate,
                          boolean hasAttachment, boolean isMyRequest,
                          String avatarsHtml) {
```

Inside the constructor body, add after the existing `this.myRequest = isMyRequest;` line:

```java
        this.avatarsHtml  = avatarsHtml != null ? avatarsHtml : "";
```

- [ ] **Step 2: Add getter**

After `getDueBadgeStyle()`:

```java
    public String getAvatarsHtml() { return avatarsHtml; }
```

- [ ] **Step 3: Fix the existing call site in RequestKanbanVM.buildKanbanRowModel()**

The current last line of `buildKanbanRowModel()` is:

```java
        return new KanbanRowModel(requestId, statusId, statusVal, documentNo, summary,
                                  priority, customer, responsible,
                                  startDate, endDate, hasAtt, isMyRequest);
```

Change it to pass an empty string for avatarsHtml (will be replaced in Task 5):

```java
        return new KanbanRowModel(requestId, statusId, statusVal, documentNo, summary,
                                  priority, customer, responsible,
                                  startDate, endDate, hasAtt, isMyRequest, "");
```

- [ ] **Step 4: Run tests to confirm no regressions**

```bash
mvn test -pl . -Dtest=KanbanRowModelTest 2>&1 | tail -10
```

Expected: `Tests run: 9, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/tw/idempiere/requestkanbanform/viewmodel/KanbanRowModel.java \
        src/tw/idempiere/requestkanbanform/viewmodel/RequestKanbanVM.java
git commit -m "feat: add avatarsHtml field to KanbanRowModel"
```

---

## Task 5: Two-Pass Loading + Batch Queries in RequestKanbanVM

**Files:**
- Modify: `src/tw/idempiere/requestkanbanform/viewmodel/RequestKanbanVM.java`

This task refactors `refreshKanbanData()` and adds helpers. The `buildKanbanRowModel()` private method is replaced by the new two-pass approach.

- [ ] **Step 1: Add RawRowData private record**

Add inside `RequestKanbanVM` class, before `refreshKanbanData()`:

```java
    private record RawRowData(
        int requestId, int statusId, String statusVal, String documentNo,
        String summary, int priority, String customer, String responsible,
        int requesterId, int salesRepId, boolean isMyRequest, boolean hasAtt,
        LocalDate startDate, LocalDate endDate, java.util.List<Integer> memberIds
    ) {}
```

- [ ] **Step 2: Add loadUserData() helper**

Add after `RawRowData` record:

```java
    private Map<Integer, String> loadUserNames(java.util.Set<Integer> userIds) {
        if (userIds.isEmpty()) return Collections.emptyMap();
        String inClause = userIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        Map<Integer, String> result = new HashMap<>();
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = DB.prepareStatement(
                "SELECT ad_user_id, name FROM ad_user WHERE ad_user_id IN (" + inClause + ")", null);
            rs = pstmt.executeQuery();
            while (rs.next())
                result.put(rs.getInt("ad_user_id"), rs.getString("name"));
        } catch (SQLException ex) {
            log.log(Level.WARNING, "loadUserNames failed", ex);
        } finally {
            DB.close(rs, pstmt);
        }
        return result;
    }

    private Map<Integer, String> loadUserAvatarImages(java.util.Set<Integer> userIds) {
        if (userIds.isEmpty()) return Collections.emptyMap();
        String inClause = userIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        Map<Integer, String> result = new HashMap<>();
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = DB.prepareStatement(
                "SELECT record_id, binarydata, title FROM ad_attachment" +
                " WHERE ad_table_id = 114 AND record_id IN (" + inClause + ")" +
                " AND (lower(title) LIKE '%.png' OR lower(title) LIKE '%.jpg'" +
                "   OR lower(title) LIKE '%.jpeg') AND binarydata IS NOT NULL", null);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                byte[] data = rs.getBytes("binarydata");
                if (data == null) continue;
                String title = rs.getString("title").toLowerCase();
                String mime = title.endsWith(".png") ? "image/png" : "image/jpeg";
                result.put(rs.getInt("record_id"),
                    "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(data));
            }
        } catch (SQLException ex) {
            log.log(Level.WARNING, "loadUserAvatarImages failed", ex);
        } finally {
            DB.close(rs, pstmt);
        }
        return result;
    }

    private List<KanbanRowModel.AvatarModel> buildAvatarList(
            RawRowData raw, Map<Integer, String> nameCache, Map<Integer, String> imgCache) {
        List<KanbanRowModel.AvatarModel> avatars = new ArrayList<>();
        java.util.Set<Integer> seen = new java.util.LinkedHashSet<>();

        // Requester
        if (seen.add(raw.requesterId())) {
            String name = raw.customer() != null ? raw.customer()
                        : nameCache.getOrDefault(raw.requesterId(), "?");
            avatars.add(new KanbanRowModel.AvatarModel(
                KanbanRowModel.getInitials(name),
                KanbanRowModel.avatarColor(raw.requesterId()),
                name, imgCache.get(raw.requesterId())));
        }
        // SalesRep
        if (raw.salesRepId() > 0 && seen.add(raw.salesRepId())) {
            String name = raw.responsible() != null ? raw.responsible()
                        : nameCache.getOrDefault(raw.salesRepId(), "?");
            avatars.add(new KanbanRowModel.AvatarModel(
                KanbanRowModel.getInitials(name),
                KanbanRowModel.avatarColor(raw.salesRepId()),
                name, imgCache.get(raw.salesRepId())));
        }
        // Members
        for (int memberId : raw.memberIds()) {
            if (seen.add(memberId)) {
                String name = nameCache.getOrDefault(memberId, "?");
                avatars.add(new KanbanRowModel.AvatarModel(
                    KanbanRowModel.getInitials(name),
                    KanbanRowModel.avatarColor(memberId),
                    name, imgCache.get(memberId)));
            }
        }
        return avatars;
    }
```

- [ ] **Step 3: Modify the main SQL in refreshKanbanData() to add SalesRep_ID and MemberIds**

Replace the current `StringBuilder sql = new StringBuilder(...)` block with:

```java
        StringBuilder sql = new StringBuilder(
            "SELECT StartTime, EndTime, SalesRep_ID," +
            " (SELECT name FROM ad_user WHERE ad_user_id = R_Request.salesrep_id) Responsible," +
            " (SELECT name FROM ad_user WHERE ad_user_id = R_Request.ad_user_id) Customer," +
            " AD_User_ID, Summary, DocumentNo, StartDate, R_Status_ID, R_Request_ID, Priority," +
            " (SELECT count(*) FROM ad_attachment WHERE ad_table_id = 417" +
            "  AND record_id = R_Request.R_Request_ID) AttachmentCount," +
            " (SELECT string_agg(ru.ad_user_id::text, ',')" +
            "  FROM r_requestupdates ru" +
            "  WHERE ru.r_request_id = R_Request.r_request_id AND ru.isactive = 'Y') MemberIds" +
            " FROM R_Request" +
            " WHERE EXISTS (SELECT 1 FROM R_Status WHERE R_Status_ID = R_Request.R_Status_ID" +
            "               AND IsFinalClose != 'Y')" +
            " AND StartDate IS NOT NULL"
        );
```

- [ ] **Step 4: Refactor the result set loop in refreshKanbanData() to two-pass**

Replace the current `try { pstmt = ... } catch ... finally ...` block with:

```java
        List<RawRowData> rawRows = new ArrayList<>();
        java.util.Set<Integer> allUserIds = new java.util.LinkedHashSet<>();
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = DB.prepareStatement(sql.toString(), null);
            int idx = 1;
            if ("All".equals(ss)) {
                pstmt.setInt(idx++, 1);
                pstmt.setInt(idx++, 1);
            } else if (!"Subordinates".equals(ss)) {
                pstmt.setInt(idx++, userId);
                pstmt.setInt(idx++, userId);
                if ("Private".equals(ss) || "Team".equals(ss))
                    pstmt.setInt(idx++, userId); // third param for member EXISTS
            }
            if (searchFilter != null && !searchFilter.isEmpty()) {
                String f = "%" + searchFilter.toUpperCase() + "%";
                pstmt.setString(idx++, f);
                pstmt.setString(idx++, f);
            }
            rs = pstmt.executeQuery();
            while (rs.next()) {
                int statusId  = rs.getInt("R_Status_ID");
                String statusVal = getStatusValueById(statusId);
                if (statusVal == null || !kanbanRows.containsKey(statusVal)) continue;

                int requestId   = rs.getInt("R_Request_ID");
                int requesterId = rs.getInt("AD_User_ID");
                int salesRepId  = rs.getInt("SalesRep_ID");
                String memberIdsStr = rs.getString("MemberIds");
                List<Integer> memberIds = memberIdsStr == null ? Collections.emptyList()
                    : Arrays.stream(memberIdsStr.split(","))
                            .map(String::trim).filter(s -> !s.isEmpty())
                            .map(Integer::parseInt).collect(Collectors.toList());

                java.sql.Date startSql = rs.getDate("StartDate");
                LocalDate startDate    = startSql != null ? startSql.toLocalDate() : null;
                java.sql.Timestamp endTs = rs.getTimestamp("EndTime");
                LocalDate endDate        = endTs != null ? endTs.toLocalDateTime().toLocalDate() : null;

                rawRows.add(new RawRowData(
                    requestId, statusId, statusVal,
                    rs.getString("DocumentNo"), rs.getString("Summary"),
                    rs.getInt("Priority"), rs.getString("Customer"), rs.getString("Responsible"),
                    requesterId, salesRepId, requesterId == userId,
                    rs.getInt("AttachmentCount") > 0, startDate, endDate, memberIds
                ));
                allUserIds.add(requesterId);
                if (salesRepId > 0) allUserIds.add(salesRepId);
                allUserIds.addAll(memberIds);
            }
        } catch (SQLException ex) {
            throw new AdempiereException("Unable to load request items", ex);
        } finally {
            DB.close(rs, pstmt);
        }

        Map<Integer, String> nameCache = loadUserNames(allUserIds);
        Map<Integer, String> imgCache  = loadUserAvatarImages(allUserIds);

        for (RawRowData raw : rawRows) {
            List<KanbanRowModel.AvatarModel> avatars = buildAvatarList(raw, nameCache, imgCache);
            String avatarsHtml = KanbanRowModel.buildAvatarsHtml(avatars);
            kanbanRows.get(raw.statusVal()).add(new KanbanRowModel(
                raw.requestId(), raw.statusId(), raw.statusVal(),
                raw.documentNo(), raw.summary(), raw.priority(),
                raw.customer(), raw.responsible(),
                raw.startDate(), raw.endDate(),
                raw.hasAtt(), raw.isMyRequest(), avatarsHtml
            ));
        }
```

- [ ] **Step 5: Delete the now-unused buildKanbanRowModel() method**

Remove the entire `private KanbanRowModel buildKanbanRowModel(ResultSet rs, int statusId, String statusVal, int myUserId)` method (lines ~699–721).

- [ ] **Step 6: Run tests**

```bash
mvn test -pl . -Dtest=KanbanRowModelTest 2>&1 | tail -10
```

Expected: `Tests run: 9, Failures: 0, Errors: 0`

- [ ] **Step 7: Commit**

```bash
git add src/tw/idempiere/requestkanbanform/viewmodel/RequestKanbanVM.java \
        src/tw/idempiere/requestkanbanform/viewmodel/KanbanRowModel.java
git commit -m "feat: two-pass kanban loading with batch avatar queries"
```

---

## Task 6: Scope Filter Extension (Private + Team)

**Files:**
- Modify: `src/tw/idempiere/requestkanbanform/viewmodel/RequestKanbanVM.java`

Extend Private and Team scope filters in `refreshKanbanData()`, `loadGanttData()`, and `loadProjectRequestCounts()` so members see cards they've been added to.

- [ ] **Step 1: Update refreshKanbanData() scope switch — Private case**

Replace:
```java
            case "Private":
                sql.append(" AND (AD_User_ID = ? OR SalesRep_ID = ?)");
                break;
```
With:
```java
            case "Private":
                sql.append(" AND (AD_User_ID = ? OR SalesRep_ID = ?" +
                           " OR EXISTS (SELECT 1 FROM r_requestupdates" +
                           " WHERE r_request_id = R_Request.r_request_id" +
                           " AND ad_user_id = ? AND isactive = 'Y'))");
                break;
```

- [ ] **Step 2: Update refreshKanbanData() scope switch — Team case**

Replace:
```java
            case "Team":
                sql.append(" AND (AD_User_ID = ? OR SalesRep_ID = ?")
                   .append(" OR EXISTS (SELECT 1 FROM AD_User_Roles")
                   .append(" WHERE AD_Role_ID = R_Request.AD_Role_ID AND AD_User_ID = ")
                   .append(userId).append("))");
                break;
```
With:
```java
            case "Team":
                sql.append(" AND (AD_User_ID = ? OR SalesRep_ID = ?" +
                           " OR EXISTS (SELECT 1 FROM AD_User_Roles" +
                           " WHERE AD_Role_ID = R_Request.AD_Role_ID AND AD_User_ID = " +
                           userId + ")" +
                           " OR EXISTS (SELECT 1 FROM r_requestupdates" +
                           " WHERE r_request_id = R_Request.r_request_id" +
                           " AND ad_user_id = ? AND isactive = 'Y'))");
                break;
```

- [ ] **Step 3: Update loadGanttData() scope switch — Private case**

Replace:
```java
            case "Private":
                sql.append(" AND (r.AD_User_ID = ? OR r.SalesRep_ID = ?)");
                break;
```
With:
```java
            case "Private":
                sql.append(" AND (r.AD_User_ID = ? OR r.SalesRep_ID = ?" +
                           " OR EXISTS (SELECT 1 FROM r_requestupdates" +
                           " WHERE r_request_id = r.r_request_id" +
                           " AND ad_user_id = ? AND isactive = 'Y'))");
                break;
```

- [ ] **Step 4: Update loadGanttData() scope switch — Team case**

Replace:
```java
            case "Team":
                sql.append(" AND (r.AD_User_ID = ? OR r.SalesRep_ID = ?")
                   .append(" OR EXISTS (SELECT 1 FROM AD_User_Roles")
                   .append(" WHERE AD_Role_ID = r.AD_Role_ID AND AD_User_ID = ")
                   .append(userId).append("))");
                break;
```
With:
```java
            case "Team":
                sql.append(" AND (r.AD_User_ID = ? OR r.SalesRep_ID = ?" +
                           " OR EXISTS (SELECT 1 FROM AD_User_Roles" +
                           " WHERE AD_Role_ID = r.AD_Role_ID AND AD_User_ID = " + userId + ")" +
                           " OR EXISTS (SELECT 1 FROM r_requestupdates" +
                           " WHERE r_request_id = r.r_request_id" +
                           " AND ad_user_id = ? AND isactive = 'Y'))");
                break;
```

- [ ] **Step 5: Fix PreparedStatement param binding for Private/Team in loadGanttData()**

In `loadGanttData()`, find the param-binding block (where `pstmt.setInt` is called for `userId`). The current binding passes `userId` twice for Private/Team. Update it to pass `userId` three times for Private and Team:

Find the block that sets params for Private/Team (it will look like the one in `refreshKanbanData`). Add the third `userId` binding for Private and Team:

```java
            if ("All".equals(ss)) {
                pstmt.setInt(idx++, 1);
                pstmt.setInt(idx++, 1);
            } else if (!"Subordinates".equals(ss)) {
                pstmt.setInt(idx++, userId);
                pstmt.setInt(idx++, userId);
                if ("Private".equals(ss) || "Team".equals(ss))
                    pstmt.setInt(idx++, userId);
            }
```

- [ ] **Step 6: Update loadProjectRequestCounts() — Private and Team cases**

Apply the same OR EXISTS pattern in `loadProjectRequestCounts()`:

Private — replace `" AND (r.AD_User_ID = ? OR r.SalesRep_ID = ?)"` with:
```java
                sql.append(" AND (r.AD_User_ID = ? OR r.SalesRep_ID = ?" +
                           " OR EXISTS (SELECT 1 FROM r_requestupdates" +
                           " WHERE r_request_id = r.r_request_id" +
                           " AND ad_user_id = ? AND isactive = 'Y'))");
```

Team — replace existing Team clause with:
```java
                sql.append(" AND (r.AD_User_ID = ? OR r.SalesRep_ID = ?" +
                           " OR EXISTS (SELECT 1 FROM AD_User_Roles" +
                           " WHERE AD_Role_ID = r.AD_Role_ID AND AD_User_ID = " + userId + ")" +
                           " OR EXISTS (SELECT 1 FROM r_requestupdates" +
                           " WHERE r_request_id = r.r_request_id" +
                           " AND ad_user_id = ? AND isactive = 'Y'))");
```

Also add the third `userId` binding in `loadProjectRequestCounts()` param-binding section for Private/Team.

- [ ] **Step 7: Commit**

```bash
git add src/tw/idempiere/requestkanbanform/viewmodel/RequestKanbanVM.java
git commit -m "feat: extend scope filters to include r_requestupdates members"
```

---

## Task 7: isMember Helper in RequestKanbanVM

**Files:**
- Modify: `src/tw/idempiere/requestkanbanform/viewmodel/RequestKanbanVM.java`

- [ ] **Step 1: Add isMember() public method**

Add after `canEditRequest()`:

```java
    public boolean isMember(int requestId) {
        int userId = Env.getAD_User_ID(Env.getCtx());
        return DB.getSQLValue(null,
            "SELECT count(*) FROM r_requestupdates WHERE r_request_id=? AND ad_user_id=? AND isactive='Y'",
            requestId, userId) > 0;
    }
```

- [ ] **Step 2: Commit**

```bash
git add src/tw/idempiere/requestkanbanform/viewmodel/RequestKanbanVM.java
git commit -m "feat: add isMember helper to RequestKanbanVM"
```

---

## Task 8: Member Chip UI in setupUpdateDialog

**Files:**
- Modify: `src/tw/idempiere/requestkanbanform/form/RequestKanbanForm.java`

- [ ] **Step 1: Add needed imports** (if not already present)

Verify or add at the top of the file:
```java
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zul.Hlayout;
import org.zkoss.zul.Button;
import org.zkoss.zul.Label;
```

- [ ] **Step 2: Add buildMemberChip() private helper**

Add after `makeFieldRow()`:

```java
    private Hlayout buildMemberChip(int memberId, String memberName,
                                     MRequest request, Hlayout chipsRow, boolean canRemove) {
        Hlayout chip = new Hlayout();
        chip.setValign("middle");
        chip.setSpacing("2px");
        chip.setStyle("background:#e3f2fd;border-radius:12px;padding:2px 8px;margin:2px;");
        Label lblName = new Label(memberName != null ? memberName : "?");
        lblName.setStyle("font-size:12px;color:#1565c0;");
        chip.appendChild(lblName);
        if (canRemove) {
            Button btnX = new Button("×");
            btnX.setStyle("border:none;background:none;color:#888;cursor:pointer;padding:0 2px;font-size:14px;min-width:0;");
            btnX.addEventListener(Events.ON_CLICK, e -> {
                PreparedStatement ps = null;
                try {
                    ps = org.compiere.util.DB.prepareStatement(
                        "UPDATE r_requestupdates SET isactive='N', updatedby=?, updated=now()" +
                        " WHERE r_request_id=? AND ad_user_id=?", null);
                    ps.setInt(1, org.compiere.util.Env.getAD_User_ID(org.compiere.util.Env.getCtx()));
                    ps.setInt(2, request.getR_Request_ID());
                    ps.setInt(3, memberId);
                    ps.executeUpdate();
                } catch (SQLException ex) {
                    org.compiere.util.CLogger.getCLogger(getClass()).warning("Remove member: " + ex.getMessage());
                } finally {
                    org.compiere.util.DB.close(ps);
                }
                chipsRow.removeChild(chip);
            });
            chip.appendChild(btnX);
        }
        return chip;
    }
```

- [ ] **Step 3: Insert member section in setupUpdateDialog()**

Find the Attachment row append (line ~214):
```java
        requestDoc.appendChild(makeFieldRow(Msg.getMsg(Env.getCtx(), "Attachment"), attachRow));
```

After it, add the member section:

```java
        // ── 👥 Members ────────────────────────────────────────────
        Label secMembers = new Label("👥 " + Msg.getMsg(Env.getCtx(), "RK_Members"));
        secMembers.setStyle("font-size:11px;font-weight:700;color:#888;letter-spacing:0.5px;margin:10px 0 4px;");
        requestDoc.appendChild(secMembers);

        Hlayout chipsRow = new Hlayout();
        chipsRow.setSpacing("0px");
        chipsRow.setStyle("flex-wrap:wrap;");
        requestDoc.appendChild(chipsRow);

        // Load existing members
        PreparedStatement memberPstmt = null;
        ResultSet memberRs = null;
        try {
            memberPstmt = org.compiere.util.DB.prepareStatement(
                "SELECT ru.ad_user_id, u.name FROM r_requestupdates ru" +
                " JOIN ad_user u ON u.ad_user_id = ru.ad_user_id" +
                " WHERE ru.r_request_id = ? AND ru.isactive = 'Y' ORDER BY ru.created", null);
            memberPstmt.setInt(1, request.getR_Request_ID());
            memberRs = memberPstmt.executeQuery();
            while (memberRs.next()) {
                int mid = memberRs.getInt("ad_user_id");
                String mname = memberRs.getString("name");
                chipsRow.appendChild(buildMemberChip(mid, mname, request, chipsRow, canEditAny));
            }
        } catch (SQLException ex) {
            org.compiere.util.CLogger.getCLogger(getClass()).warning("Load members: " + ex.getMessage());
        } finally {
            org.compiere.util.DB.close(memberRs, memberPstmt);
        }

        // Add member search (only when canEditAny)
        if (canEditAny) {
            org.compiere.model.MLookup userL = org.compiere.model.MLookupFactory.get(
                org.compiere.util.Env.getCtx(), 0, 0, 5434,
                org.compiere.model.DisplayType.Search);
            org.adempiere.webui.editor.WSearchEditor memberSearch =
                new org.adempiere.webui.editor.WSearchEditor(
                    "AD_User_ID", false, false, true, userL);
            memberSearch.addValueChangeListener(evt -> {
                Object val = evt.getValue();
                if (!(val instanceof Integer) || (Integer) val <= 0) return;
                int newUserId = (Integer) val;
                if (newUserId == request.getAD_User_ID() || newUserId == request.getSalesRep_ID()) {
                    memberSearch.setValue(null);
                    return;
                }
                // Check if already active member
                int already = org.compiere.util.DB.getSQLValue(null,
                    "SELECT count(*) FROM r_requestupdates WHERE r_request_id=? AND ad_user_id=? AND isactive='Y'",
                    request.getR_Request_ID(), newUserId);
                if (already > 0) { memberSearch.setValue(null); return; }

                // Insert (or reactivate)
                PreparedStatement ps = null;
                try {
                    int exists = org.compiere.util.DB.getSQLValue(null,
                        "SELECT count(*) FROM r_requestupdates WHERE r_request_id=? AND ad_user_id=?",
                        request.getR_Request_ID(), newUserId);
                    if (exists == 0) {
                        ps = org.compiere.util.DB.prepareStatement(
                            "INSERT INTO r_requestupdates" +
                            " (ad_user_id, r_request_id, ad_client_id, ad_org_id," +
                            "  createdby, updatedby, r_requestupdates_uu, isactive)" +
                            " VALUES (?,?,?,?,?,?,gen_random_uuid()::text,'Y')", null);
                        ps.setInt(1, newUserId);
                        ps.setInt(2, request.getR_Request_ID());
                        ps.setInt(3, org.compiere.util.Env.getAD_Client_ID(org.compiere.util.Env.getCtx()));
                        ps.setInt(4, org.compiere.util.Env.getAD_Org_ID(org.compiere.util.Env.getCtx()));
                        ps.setInt(5, org.compiere.util.Env.getAD_User_ID(org.compiere.util.Env.getCtx()));
                        ps.setInt(6, org.compiere.util.Env.getAD_User_ID(org.compiere.util.Env.getCtx()));
                        ps.executeUpdate();
                    } else {
                        org.compiere.util.DB.executeUpdate(
                            "UPDATE r_requestupdates SET isactive='Y'," +
                            " updatedby=" + org.compiere.util.Env.getAD_User_ID(org.compiere.util.Env.getCtx()) +
                            ", updated=now() WHERE r_request_id=" + request.getR_Request_ID() +
                            " AND ad_user_id=" + newUserId, null);
                    }
                } catch (SQLException ex) {
                    org.compiere.util.CLogger.getCLogger(getClass()).warning("Add member: " + ex.getMessage());
                    memberSearch.setValue(null);
                    return;
                } finally {
                    org.compiere.util.DB.close(ps);
                }
                org.compiere.model.MUser newUser = new org.compiere.model.MUser(
                    org.compiere.util.Env.getCtx(), newUserId, null);
                chipsRow.insertBefore(
                    buildMemberChip(newUserId, newUser.getName(), request, chipsRow, true),
                    memberSearch.getComponent());
                memberSearch.setValue(null);
            });
            chipsRow.appendChild(memberSearch.getComponent());
        }
```

- [ ] **Step 4: Commit**

```bash
git add src/tw/idempiere/requestkanbanform/form/RequestKanbanForm.java
git commit -m "feat: member chip UI in update dialog with add/remove via r_requestupdates"
```

---

## Task 9: Avatar HTML in ZUL Card Template

**Files:**
- Modify: `web/zul/RequestKanbanForm.zul`

- [ ] **Step 1: Add avatar html row to card template**

In `RequestKanbanForm.zul`, find the card `vlayout` (inside the `<template name="model">` block). It ends with:

```xml
         <hlayout hflex="1" valign="middle">
          <label visible="@load(row.startDate ne null)"
                 value="@load(row.startDateDisplay)"
                 style="font-size:10px;color:#888;" />
          <label visible="@load(row.showDueBadge)"
                 value="@load(row.dueBadgeText)"
                 style="@load(row.dueBadgeStyle)" />
          <space hflex="1"/>
          <label visible="@load(row.hasAttachment)"
                 sclass="rk-icon-attach"
                 style="display:inline-block;" />
         </hlayout>
```

After that `</hlayout>`, add:

```xml
         <html content="@load(row.avatarsHtml)" />
```

- [ ] **Step 2: Commit**

```bash
git add web/zul/RequestKanbanForm.zul
git commit -m "feat: render participant avatars on kanban card"
```

---

## Task 10: Deploy and Smoke Test

- [ ] **Step 1: Build the bundle**

```bash
mvn package -pl . -DskipTests 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`

- [ ] **Step 2: Deploy to iDempiere and run i18n SQL**

Copy the built JAR to the iDempiere plugins/dropins directory and restart. Then run:

```bash
psql -h localhost -U dev -d dev -f migration/i18n_setup.sql
```

- [ ] **Step 3: Smoke test checklist**

1. Open Kanban board as user A (SalesRep). Open a request card. Confirm "👥 Members" section appears with `+ Member` search field.
2. Add user B as a member. Confirm chip appears immediately.
3. Close dialog. Confirm avatar(s) appear on the card.
4. Log in as user B. Switch scope to "Private". Confirm the card is visible.
5. Click the card. Confirm dialog opens in readonly mode (Save disabled, no × on chips, no + button).
6. Log back in as user A. Remove user B chip (×). Confirm chip disappears.
7. Log in as user B. Confirm card no longer visible in Private scope.

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "feat: request card members v2.2.1 — avatars, member chips, scope filter"
```
