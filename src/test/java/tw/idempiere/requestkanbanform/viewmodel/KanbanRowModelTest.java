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
