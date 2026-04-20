/*
 * Copyright (C) 2026 Ray Lee / TopGiga
 * SPDX-License-Identifier: GPL-2.0-only
 */
package tw.idempiere.requestkanbanform.viewmodel;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Immutable display model for a single R_Request card in the Kanban or List view.
 * All fields are pre-computed from the ResultSet so the ZUL template never touches SQL.
 * Note: dueBadgeText and dueBadgeStyle are computed once at construction time relative
 * to the current date. Callers must recreate instances if date boundaries matter.
 */
public class KanbanRowModel {

    public record AvatarModel(String initials, String color, String name, String imageDataUri) {}

    private static final String[] AVATAR_COLORS = {
        "#1976d2", "#e65100", "#2e7d32", "#6a1b9a",
        "#00838f", "#c62828", "#4527a0", "#00695c"
    };

    public static String avatarColor(int userId) {
        return AVATAR_COLORS[Math.abs(userId) % AVATAR_COLORS.length];
    }

    private final int requestId;
    private final int statusId;
    private final String statusValue;
    private final String documentNo;
    private final String summary;
    private final int priority;
    private final String customer;
    private final String responsible;
    private final LocalDate startDate;
    private final LocalDate endDate;     // null if EndTime is null
    private final boolean hasAttachment;
    private final boolean myRequest;

    // Computed display helpers
    private final String cardStyle;
    private final String dueBadgeText;   // null if no EndTime
    private final String dueBadgeStyle;  // null if no EndTime
    private final String avatarsHtml;

    public KanbanRowModel(int requestId, int statusId, String statusValue,
                          String documentNo, String summary, int priority,
                          String customer, String responsible,
                          LocalDate startDate, LocalDate endDate,
                          boolean hasAttachment, boolean isMyRequest,
                          String avatarsHtml) {
        this.requestId    = requestId;
        this.statusId     = statusId;
        this.statusValue  = statusValue;
        this.documentNo   = documentNo;
        this.summary      = summary;
        this.priority     = priority;
        this.customer     = customer;
        this.responsible  = responsible;
        this.startDate    = startDate;
        this.endDate      = endDate;
        this.hasAttachment = hasAttachment;
        this.myRequest    = isMyRequest;
        this.avatarsHtml  = avatarsHtml != null ? avatarsHtml : "";
        this.cardStyle    = buildCardStyle(priority, isMyRequest);
        if (endDate != null) {
            long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), endDate);
            if (daysLeft <= 0) {
                this.dueBadgeText  = "🔴 逾期";
                this.dueBadgeStyle = "background:#ffebe6;color:#bf2600;padding:1px 5px;border-radius:3px;font-size:10px;font-weight:600;margin-left:6px;";
            } else if (daysLeft <= 3) {
                this.dueBadgeText  = "⏰ " + endDate.getMonthValue() + "/" + endDate.getDayOfMonth();
                this.dueBadgeStyle = "background:#fffae6;color:#7a6200;padding:1px 5px;border-radius:3px;font-size:10px;font-weight:600;margin-left:6px;";
            } else {
                this.dueBadgeText  = "⏰ " + endDate.getMonthValue() + "/" + endDate.getDayOfMonth();
                this.dueBadgeStyle = "background:#e3fcef;color:#006644;padding:1px 5px;border-radius:3px;font-size:10px;font-weight:600;margin-left:6px;";
            }
        } else {
            this.dueBadgeText  = null;
            this.dueBadgeStyle = null;
        }
    }

    private static String buildCardStyle(int priority, boolean isMyRequest) {
        String bg = getPriorityColor(priority);
        String base = "padding:10px;border-radius:8px;background-color:" + bg
                    + ";cursor:pointer;margin-bottom:8px;box-shadow:0 2px 4px rgba(0,0,0,0.05);";
        if (isMyRequest)
            return base + "border-left:3px solid #2563eb;animation:rkBorderPulse 5s ease-in-out infinite;";
        return base + "border:1px solid #ddd;";
    }

    /**
     * Maps priority integer to background colour — mirrors RequestKanbanDashboard.getPriorityColor().
     * Uses range-based logic to match the original implementation:
     * - priority <= 1: Urgent (Pink)
     * - priority <= 3: High (Orange)
     * - priority <= 5: Medium (Yellow)
     * - priority <= 7: Low (Light Green)
     * - priority > 7: Minor (Grey)
     */
    public static String getPriorityColor(int priority) {
        if (priority <= 1) return "#F8BBD0";      // Urgent - Pink
        if (priority <= 3) return "#FFE0B2";      // High - Orange
        if (priority <= 5) return "#FFF9C4";      // Medium - Yellow
        if (priority <= 7) return "#DCEDC8";      // Low - Light Green
        return "#F5F5F5";                         // Minor - Grey
    }

    // --- Getters (required for @load / @bind in ZUL) ---

    public int    getRequestId()     { return requestId; }
    public int    getStatusId()      { return statusId; }
    public String getStatusValue()   { return statusValue; }
    public String getDocumentNo()    { return documentNo; }
    public String getSummary()       { return summary != null ? summary : ""; }
    public int    getPriority()      { return priority; }
    public String getCustomer()      { return customer; }
    public String getResponsible()   { return responsible; }
    public LocalDate getStartDate()  { return startDate; }
    public LocalDate getEndDate()    { return endDate; }
    public boolean getHasAttachment() { return hasAttachment; }
    public boolean isMyRequest()     { return myRequest; }
    public String getCardStyle()     { return cardStyle; }
    public String getDueBadgeText()  { return dueBadgeText; }
    public String getDueBadgeStyle() { return dueBadgeStyle; }
    public String getAvatarsHtml() { return avatarsHtml; }

    /** Formatted start date for display, e.g. "2026/3/1". Null if startDate is null. */
    public String getStartDateDisplay() {
        if (startDate == null) return null;
        return startDate.getYear() + "/" + startDate.getMonthValue() + "/" + startDate.getDayOfMonth();
    }

    /** True if the customer label row should be rendered. */
    public boolean isShowCustomer()    { return customer != null && !customer.isEmpty(); }
    /** True if the responsible label row should be rendered. */
    public boolean isShowResponsible() { return responsible != null && !responsible.isEmpty(); }
    /** True if the due badge should be rendered. */
    public boolean isShowDueBadge()    { return dueBadgeText != null; }

    /** Regular listcell background for list view — priority color. */
    public String getListCellStyle() {
        return "background:" + getPriorityColor(priority) + ";";
    }

    /** Priority cell style — bold, priority color background, centered. */
    public String getPriorityCellStyle() {
        return "background:" + getPriorityColor(priority) + ";font-weight:700;text-align:center;";
    }

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
}
