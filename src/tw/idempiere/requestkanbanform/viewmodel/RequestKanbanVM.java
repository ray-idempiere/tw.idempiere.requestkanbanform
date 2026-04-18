/*
 * Copyright (C) 2026 Ray Lee / TopGiga
 * SPDX-License-Identifier: GPL-2.0-only
 */
package tw.idempiere.requestkanbanform.viewmodel;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.adempiere.base.event.EventManager;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.webui.apps.AEnv;
import org.adempiere.webui.editor.WEditor;
import org.compiere.model.MQuery;
import org.compiere.model.MAttachment;
import org.compiere.model.MAttachmentEntry;
import org.compiere.model.MRequest;
import org.compiere.model.MStatus;
import org.compiere.model.MSysConfig;
import org.compiere.model.Query;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.osgi.framework.FrameworkUtil;
import org.zkoss.bind.BindUtils;
import org.zkoss.bind.annotation.BindingParam;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.DependsOn;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.zk.ui.event.DropEvent;
import org.zkoss.zk.ui.util.Clients;

import tw.idempiere.requestkanbanform.dashboard.GanttRenderer;
import tw.idempiere.requestkanbanform.dashboard.StatusConfig;
import tw.idempiere.requestkanbanform.form.RequestKanbanForm;

/**
 * MVVM ViewModel for the Request Kanban Custom Form.
 * Pure POJO — no ZK component references.
 */
public class RequestKanbanVM {

    private static final CLogger logger = CLogger.getCLogger(RequestKanbanVM.class);

    public static final String VIEW_KANBAN = "Kanban";
    public static final String VIEW_LIST   = "List";
    public static final String VIEW_GANTT  = "gantt";

    public static final String TOPIC_KANBAN_REFRESH = "kanbanform/request/refresh";

    // ── Form reference (set by RequestKanbanForm after init) ─────────────────
    private RequestKanbanForm form;
    public void setForm(RequestKanbanForm form) { this.form = form; }

    // ── State ────────────────────────────────────────────────────────────────
    private String currentView  = VIEW_KANBAN;
    private String currentScope = "Private";
    private String searchFilter = "";
    private boolean supervisorFlag;
    private MStatus[] statuses;
    private StatusConfig statusConfig = new StatusConfig("", "Processing,Open");
    private Map<String, List<KanbanRowModel>> kanbanRows = new LinkedHashMap<>();
    private List<MStatus> visibleStatuses = new ArrayList<>();

    // R_Status icon URLs (Base64 data URIs), keyed by R_Status_ID
    private Map<Integer, String> statusIconUrls = new HashMap<>();

    // List view selected tab (status value); null = use first visible status
    private String selectedListStatus = null;

    // Gantt state
    private String    ganttRange     = "month";
    private boolean   showFinalClose = false;
    private LocalDate ganttFrom;
    private LocalDate ganttTo;

    // Project panel (Gantt left sidebar)
    private List<int[]>  cachedProjects     = new ArrayList<>();
    private List<String> cachedProjectNames = new ArrayList<>();
    private Map<Integer, Integer> cachedProjectCounts = new HashMap<>();

    // ── Init ─────────────────────────────────────────────────────────────────

    @Init
    public void init() {
        loadStatusConfig();
        injectPulseAnimation();
        statuses = getRequestStatus();
        rebuildVisibleStatuses();
        loadStatusIcons();
        refreshKanbanData();
        this.supervisorFlag = checkIsSupervisor();
    }

    /**
     * For each visible MStatus, look up its AD_Attachment (AD_Table_ID=776).
     * If the first image entry is found, cache a Base64 data URI in statusIconUrls.
     */
    private void loadStatusIcons() {
        statusIconUrls.clear();
        Properties ctx = Env.getCtx();
        final int R_STATUS_TABLE_ID = 776;
        for (MStatus s : statuses) {
            int statusId = s.getR_Status_ID();
            try {
                MAttachment att = MAttachment.get(ctx, R_STATUS_TABLE_ID, statusId);
                if (att == null) continue;
                MAttachmentEntry[] entries = att.getEntries();
                if (entries == null) continue;
                for (MAttachmentEntry entry : entries) {
                    if (entry != null && entry.isGraphic()) {
                        byte[] data = entry.getData();
                        if (data != null && data.length > 0) {
                            String contentType = entry.getContentType();
                            if (contentType == null || contentType.isBlank())
                                contentType = "image/png";
                            String b64 = Base64.getEncoder().encodeToString(data);
                            statusIconUrls.put(statusId, "data:" + contentType + ";base64," + b64);
                        }
                        break; // use only the first image
                    }
                }
            } catch (Exception ex) {
                logger.log(Level.WARNING, "loadStatusIcons: failed for R_Status_ID=" + statusId, ex);
            }
        }
    }

    /**
     * Returns the Base64 data URI for the icon of the given R_Status_ID,
     * or null if no image attachment exists.
     */
    public String getStatusIconUrl(int r_Status_ID) {
        return statusIconUrls.get(r_Status_ID);
    }

    /**
     * Returns true if the given R_Status_ID has an image attachment to use as icon.
     */
    public boolean hasStatusIcon(int r_Status_ID) {
        return statusIconUrls.containsKey(r_Status_ID);
    }

    private void loadStatusConfig() {
        int clientId = Env.getAD_Client_ID(Env.getCtx());
        String hidden = MSysConfig.getValue("RK_HiddenStatuses", "", clientId, 0);
        String active = MSysConfig.getValue("RK_ActiveStatuses", "Processing,Open", clientId, 0);
        statusConfig = new StatusConfig(hidden, active);
    }

    private void injectPulseAnimation() {
        Clients.evalJavaScript(
            "if(!document.getElementById('rk-pulse-style')){" +
            "  var s=document.createElement('style');" +
            "  s.id='rk-pulse-style';" +
            "  s.textContent='@keyframes rkBorderPulse{0%,100%{border-left-color:#2563eb}50%{border-left-color:rgba(37,99,235,0.15)}}';" +
            "  document.head.appendChild(s);" +
            "}"
        );
    }

    private void rebuildVisibleStatuses() {
        visibleStatuses = Arrays.stream(statuses)
            .filter(s -> !statusConfig.isHidden(s.getValue()))
            .collect(Collectors.toList());
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getCurrentView()  { return currentView; }
    public String getCurrentScope() { return currentScope; }
    public String getSearchFilter() { return searchFilter; }
    public void   setSearchFilter(String v) { this.searchFilter = v; }

    public List<MStatus> getVisibleStatuses() { return visibleStatuses; }

    /**
     * True when this status is the one rendered first (leftmost kanban column).
     *
     * ZK children-binding renders the list in REVERSE order (last element → leftmost).
     * With ORDER BY SeqNo DESC the Java list is [Closed, Waiting, Open]; ZK displays
     * Open | Waiting | Closed.  The "first rendered" column is therefore the LAST
     * element of the Java list (Open, lowest SeqNo).
     */
    public boolean isFirstStatus(String statusValue) {
        if (statusValue == null || visibleStatuses.isEmpty()) return false;
        return statusValue.equals(visibleStatuses.get(visibleStatuses.size() - 1).getValue());
    }

    /** The status used when creating new requests: lowest SeqNo = first rendered column. */
    private MStatus getNewRequestStatus() {
        return visibleStatuses.isEmpty() ? null : visibleStatuses.get(visibleStatuses.size() - 1);
    }

    public List<KanbanRowModel> getKanbanRowsForStatus(String statusValue) {
        return kanbanRows.getOrDefault(statusValue, Collections.emptyList());
    }

    public boolean isKanbanView() { return VIEW_KANBAN.equals(currentView); }
    public boolean isListView()   { return VIEW_LIST.equals(currentView);   }
    public boolean isGanttView()  { return VIEW_GANTT.equals(currentView);  }

    /** Returns rows for the currently selected list-view tab. */
    public List<KanbanRowModel> getSelectedStatusRows() {
        String sv = selectedListStatus;
        if (sv == null && !visibleStatuses.isEmpty())
            sv = visibleStatuses.get(visibleStatuses.size() - 1).getValue();
        return sv != null ? getKanbanRowsForStatus(sv) : Collections.emptyList();
    }

    /** Tab label: "Status Name (count)". */
    public String getListTabLabel(org.compiere.model.MStatus status) {
        int count = getKanbanCountForStatus(status.getValue());
        return status.getName() + " (" + count + ")";
    }

    /** Sclass for a list-view tab button — active when it matches selectedListStatus. */
    public String getListTabSclass(String statusValue) {
        String sv = selectedListStatus;
        if (sv == null && !visibleStatuses.isEmpty())
            sv = visibleStatuses.get(visibleStatuses.size() - 1).getValue();
        return statusValue.equals(sv) ? "rk-pill rk-pill-active" : "rk-pill";
    }

    public String  getGanttRange()    { return ganttRange; }
    public boolean isShowFinalClose() { return showFinalClose; }
    public void setShowFinalClose(boolean v) { showFinalClose = v; }
    public LocalDate getGanttFrom()   { return ganttFrom; }
    public LocalDate getGanttTo()     { return ganttTo; }

    public java.util.Date getGanttFromDate() {
        return ganttFrom != null ? java.sql.Date.valueOf(ganttFrom) : null;
    }
    public void setGanttFromDate(java.util.Date d) {
        if (d != null) ganttFrom = dateToLocalDate(d);
    }
    public java.util.Date getGanttToDate() {
        return ganttTo != null ? java.sql.Date.valueOf(ganttTo) : null;
    }
    public void setGanttToDate(java.util.Date d) {
        if (d != null) ganttTo = dateToLocalDate(d);
    }

    @DependsOn("currentView")
    public String getToggleViewLabel() {
        if (VIEW_KANBAN.equals(currentView)) return Msg.getMsg(Env.getCtx(), "RK_GanttView");
        if (VIEW_GANTT.equals(currentView))  return Msg.getMsg(Env.getCtx(), "RK_ListView");
        return Msg.getMsg(Env.getCtx(), "RK_KanbanView");
    }

    @DependsOn("currentView")
    public String getToggleViewIconSclass() {
        if (VIEW_KANBAN.equals(currentView)) return "rk-icon-gantt";
        if (VIEW_GANTT.equals(currentView))  return "rk-icon-list";
        return "rk-icon-kanban";
    }

    public String getPluginVersion() {
        org.osgi.framework.Bundle b = FrameworkUtil.getBundle(getClass());
        if (b != null) return "v" + b.getVersion().toString();
        return "v?.?.?";
    }

    private boolean checkIsSupervisor() {
        String sql = "select count(*) from ad_user where supervisor_id = ?";
        int counter = DB.getSQLValue(null, sql,
            Env.getAD_User_ID(Env.getCtx()));
        return counter > 0;
    }

    public boolean isSupervisor() { return supervisorFlag; }

    public int getKanbanCountForStatus(String statusValue) {
        List<KanbanRowModel> rows = kanbanRows.get(statusValue);
        return rows != null ? rows.size() : 0;
    }

    // ── i18n label getters ────────────────────────────────────────────────────

    public String getLabelGanttView()    { return Msg.getMsg(Env.getCtx(), "RK_GanttView"); }
    public String getLabelListView()     { return Msg.getMsg(Env.getCtx(), "RK_ListView"); }
    public String getLabelKanbanView()   { return Msg.getMsg(Env.getCtx(), "RK_KanbanView"); }
    public String getLabelPrivate()      { return Msg.getMsg(Env.getCtx(), "RK_Private"); }
    public String getLabelSubordinates() { return Msg.getMsg(Env.getCtx(), "RK_Subordinates"); }
    public String getLabelTeam()         { return Msg.getMsg(Env.getCtx(), "RK_Team"); }
    public String getLabelAll()          { return Msg.getMsg(Env.getCtx(), "RK_All"); }
    public String getLabelSearch()       { return Msg.getMsg(Env.getCtx(), "RK_SearchPlaceholder"); }
    public String getLabelNewRequest()   { return Msg.getMsg(Env.getCtx(), "RK_NewRequest"); }
    public String getLabelFinalClose()   { return Msg.getMsg(Env.getCtx(), "RK_DisplayFinalClose"); }
    public String getLabelNewProject()   { return Msg.getMsg(Env.getCtx(), "RK_NewProject"); }
    public String getLabelNoRequests()   { return Msg.getMsg(Env.getCtx(), "RK_NoRequests"); }
    public String getLabelDocNo()        { return Msg.getMsg(Env.getCtx(), "DocumentNo"); }
    public String getLabelSummary()      { return Msg.getMsg(Env.getCtx(), "Summary"); }
    public String getLabelSalesRep()     { return Msg.getMsg(Env.getCtx(), "RK_SalesRep"); }
    public String getLabelStartTime()    { return Msg.getMsg(Env.getCtx(), "RK_StartTime"); }
    public String getLabelEndTime()      { return Msg.getMsg(Env.getCtx(), "RK_EndTime"); }
    public String getLabelPriority()     { return Msg.getMsg(Env.getCtx(), "RK_Priority"); }

    // ── Commands ──────────────────────────────────────────────────────────────

    /** Fired by window onCreate to force a clean re-render after doAfterCompose. */
    @Command
    @NotifyChange({"currentView", "kanbanView", "listView", "ganttView",
                   "toggleViewLabel", "toggleViewIconSclass", "kanbanRows", "visibleStatuses"})
    public void postInitRefresh() { /* no-op: notification does the work */ }

    @Command
    @NotifyChange({"currentView", "kanbanView", "listView", "ganttView",
                   "toggleViewLabel", "toggleViewIconSclass", "kanbanRows", "visibleStatuses",
                   "ganttFromDate", "ganttToDate", "ganttRange"})
    public void toggleView() {
        if (VIEW_KANBAN.equals(currentView)) {
            currentView = VIEW_GANTT;
            resolveGanttRange();
            if (form != null) form.ensureGanttBridge(); // lazy bridge init
            refreshGanttHtml();
            refreshProjectPanel();
        } else if (VIEW_GANTT.equals(currentView)) {
            currentView = VIEW_LIST;
            refreshKanbanData();
        } else {
            currentView = VIEW_KANBAN;
            refreshKanbanData();
        }
    }

    @Command
    @NotifyChange({"currentScope", "kanbanRows"})
    public void setScope(@BindingParam("scope") String scope) {
        this.currentScope = scope;
        refreshCurrentView();
    }

    @Command
    @NotifyChange("kanbanRows")
    public void search() {
        refreshCurrentView();
    }

    @Command
    public void setListStatus(@BindingParam("statusValue") String statusValue) {
        this.selectedListStatus = statusValue;
        BindUtils.postNotifyChange(this, "*");
    }

    @Command
    @NotifyChange("showFinalClose")
    public void toggleFinalClose() {
        showFinalClose = !showFinalClose;
        refreshGanttHtml();
    }

    @Command
    @NotifyChange({"ganttRange", "ganttFromDate", "ganttToDate"})
    public void setGanttRange(@BindingParam("range") String range) {
        this.ganttRange = range;
        resolveGanttRange();
        refreshGanttHtml();
    }

    @Command
    public void ganttDateChange(@BindingParam("from") java.util.Date from,
                                @BindingParam("to")   java.util.Date to) {
        if (from != null) ganttFrom = dateToLocalDate(from);
        if (to   != null) ganttTo   = dateToLocalDate(to);
        ganttRange = "custom";
        refreshGanttHtml();
    }

    public void onKanbanDrop(int requestId, int newStatusId) {
        MRequest req = new MRequest(Env.getCtx(), requestId, null);
        if (req.getR_Request_ID() == 0) return;
        req.setR_Status_ID(newStatusId);
        if (!req.save()) {
            Clients.showNotification(Msg.getMsg(Env.getCtx(), "RK_SaveError"),
                Clients.NOTIFICATION_TYPE_ERROR, null, null, 3000);
            return;
        }
        broadcastRefresh();
        refreshKanbanData();
        BindUtils.postNotifyChange(this, "*");
    }

    /**
     * Sets R_Request_ID as a component attribute on each listitem when it is created
     * by the MVVM template.  onKanbanDropCmd reads this attribute from the dragged item.
     */
    @Command
    public void initCard(@BindingParam("component") org.zkoss.zk.ui.Component component,
                         @BindingParam("requestId") int requestId) {
        if (component != null)
            component.setAttribute("R_Request_ID", requestId);
    }

    /**
     * ZK MVVM @Command entry point for kanban drag-drop.
     * Extracts R_Request_ID from the dragged component attribute and delegates to plain-int overload.
     */
    @Command
    public void onKanbanDropCmd(@BindingParam("event") DropEvent event,
                                @BindingParam("statusId") int statusId) {
        if (event == null || event.getDragged() == null) return;
        Object attr = event.getDragged().getAttribute("R_Request_ID");
        if (attr == null) return;
        onKanbanDrop((Integer) attr, statusId);
    }

    public void onGanttDrop(int requestId, int projectId) {
        MRequest req = new MRequest(Env.getCtx(), requestId, null);
        if (req.getR_Request_ID() == 0) {
            Clients.showNotification(Msg.getMsg(Env.getCtx(), "RK_RequestNotFound"),
                Clients.NOTIFICATION_TYPE_WARNING, null, null, 3000);
            return;
        }
        req.setC_Project_ID(projectId);
        if (!req.save()) {
            Clients.showNotification(Msg.getMsg(Env.getCtx(), "RK_SaveError"),
                Clients.NOTIFICATION_TYPE_ERROR, null, null, 3000);
            return;
        }
        broadcastRefresh();
        refreshCurrentView();
        BindUtils.postNotifyChange(this, "*");
    }

    @Command
    public void openRequestZoom(@BindingParam("requestId") int requestId) {
        MRequest req = new MRequest(Env.getCtx(), requestId, null);
        if (req.getR_Request_ID() == 0) return;
        MQuery query = MQuery.getEqualQuery("R_Request_ID", requestId);
        AEnv.zoom(417, query);
    }

    @Command
    public void openNewProjectDialog() {
        if (form != null) form.openNewProjectDialog();
    }

    @Command
    public void openNewRequest() {
        if (form != null) form.openNewRequestDialog();
    }

    @Command
    public void openRequestUpdate(@BindingParam("requestId") int requestId) {
        if (form != null) form.openRequestUpdateDialog(requestId);
    }

    public void saveNewRequest(WEditor fUser, WEditor fDoc, WEditor fPriority,
                               WEditor fDepart, WEditor fSalesRep, WEditor fRole,
                               WEditor fProject, String summary) {
        Properties ctx = Env.getCtx();
        MRequest req = new MRequest(ctx, 0, null);

        // Requester — set_ValueOfColumn avoids generated-setter silent guards
        Object userId = fUser.getValue();
        if (userId instanceof Number)
            req.set_ValueOfColumn("AD_User_ID", ((Number) userId).intValue());

        // Priority
        Object priority = fPriority.getValue();
        if (priority != null)
            req.setPriority(priority.toString());

        // Department (custom column on R_Request — use public set_ValueOfColumn)
        Object deptId = fDepart.getValue();
        if (deptId instanceof Number)
            req.set_ValueOfColumn("HR_Department_ID", ((Number) deptId).intValue());

        // Request type
        Object typeId = fDoc.getValue();
        if (typeId instanceof Number)
            req.set_ValueOfColumn("R_RequestType_ID", ((Number) typeId).intValue());

        // Sales rep — use set_ValueOfColumn to unconditionally mark the column dirty
        // (the generated setSalesRep_ID has a silent "if value < 1 return false" guard
        //  that prevents the column from appearing in the INSERT when it evaluates false)
        Object srId = fSalesRep.getValue();
        int salesRepId = (srId instanceof Number)
            ? ((Number) srId).intValue()
            : Env.getAD_User_ID(ctx);
        req.set_ValueOfColumn("SalesRep_ID", salesRepId);

        // Responsible role (custom column on R_Request — use public set_ValueOfColumn)
        Object roleId = fRole.getValue();
        if (roleId instanceof Number)
            req.set_ValueOfColumn("AD_Role_ID", ((Number) roleId).intValue());

        // Project (optional)
        Object projId = fProject.getValue();
        if (projId instanceof Number)
            req.setC_Project_ID(((Number) projId).intValue());

        // Summary — required; abort with message if blank
        if (summary == null || summary.trim().isEmpty()) {
            Clients.showNotification("Please enter a request summary.",
                Clients.NOTIFICATION_TYPE_WARNING, null, null, 3000);
            return;
        }
        req.setSummary(summary.trim());

        // Default status: Open (first rendered / lowest SeqNo)
        MStatus newStatus = getNewRequestStatus();
        if (newStatus != null)
            req.setR_Status_ID(newStatus.getR_Status_ID());

        // Start date defaults to today
        req.setStartDate(new java.sql.Timestamp(System.currentTimeMillis()));

        if (!req.save()) {
            Clients.showNotification("Failed to save request",
                Clients.NOTIFICATION_TYPE_ERROR, null, null, 3000);
            return;
        }
        broadcastRefresh();
        refreshKanbanData();
        BindUtils.postNotifyChange(this, "*");
    }

    public void saveNewProject(String name, java.util.Date startDate, java.util.Date endDate) {
        if (name == null || name.trim().isEmpty()) {
            Clients.showNotification(Msg.getMsg(Env.getCtx(), "RK_ProjectNameMandatory"),
                Clients.NOTIFICATION_TYPE_WARNING, null, null, 3000);
            return;
        }
        org.compiere.model.MProject proj = new org.compiere.model.MProject(Env.getCtx(), 0, null);
        proj.setName(name.trim());
        proj.setIsSummary(true);
        proj.setC_Currency_ID(org.compiere.model.MClient.get(Env.getCtx()).getC_Currency_ID());
        if (startDate != null)
            proj.set_ValueOfColumn("DateContract", new java.sql.Timestamp(startDate.getTime()));
        if (endDate != null)
            proj.set_ValueOfColumn("DateFinish", new java.sql.Timestamp(endDate.getTime()));
        if (!proj.save()) {
            Clients.showNotification(Msg.getMsg(Env.getCtx(), "RK_ProjectSaveError"),
                Clients.NOTIFICATION_TYPE_ERROR, null, null, 3000);
            return;
        }
        broadcastRefresh();
        refreshProjectPanel();
    }

    public void saveRequestUpdate(int requestId, WEditor fPriority, WEditor fSalesRep,
                                  WEditor fProductSpent) {
        Properties ctx = Env.getCtx();
        MRequest req = new MRequest(ctx, requestId, null);
        if (req.getR_Request_ID() == 0) return;

        Object priority = fPriority.getValue();
        if (priority != null)
            req.setPriority(priority.toString());

        Object srId = fSalesRep.getValue();
        if (srId instanceof Number)
            req.setSalesRep_ID(((Number) srId).intValue());

        if (!req.save()) {
            Clients.showNotification("Failed to save request",
                Clients.NOTIFICATION_TYPE_ERROR, null, null, 3000);
            return;
        }
        broadcastRefresh();
        refreshKanbanData();
        BindUtils.postNotifyChange(this, "*");
    }

    public void zoomToRequest(int requestId) {
        // 417 is the AD_Table_ID of R_Request (standard/constant across instances).
        // The AD_Window_ID for that table varies per instance — look it up at runtime.
        int windowId = DB.getSQLValue(null,
            "SELECT w.AD_Window_ID FROM AD_Window w " +
            "JOIN AD_Tab t ON t.AD_Window_ID = w.AD_Window_ID " +
            "WHERE t.AD_Table_ID = 417 AND w.IsActive = 'Y' AND t.IsActive = 'Y' " +
            "AND t.TabLevel = 0 " +
            "ORDER BY w.AD_Client_ID DESC, t.SeqNo " +
            "LIMIT 1");
        if (windowId <= 0) {
            logger.warning("zoomToRequest: no window found for R_Request table");
            return;
        }
        MQuery query = MQuery.getEqualQuery("R_Request_ID", requestId);
        AEnv.zoom(windowId, query);
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    /** Refreshes whichever view is currently active. */
    public void refreshCurrentView() {
        if (VIEW_GANTT.equals(currentView)) {
            refreshGanttHtml();
            refreshProjectPanel();
        } else {
            refreshKanbanData();
            BindUtils.postNotifyChange(this, "*");
        }
    }

    public void refreshKanbanData() {
        kanbanRows = new LinkedHashMap<>();
        for (MStatus s : visibleStatuses) {
            kanbanRows.put(s.getValue(), new ArrayList<>());
        }

        StringBuilder sql = new StringBuilder(
            "SELECT StartTime, EndTime," +
            " (SELECT name FROM ad_user WHERE ad_user_id = R_Request.salesrep_id) Responsible," +
            " (SELECT name FROM ad_user WHERE ad_user_id = R_Request.ad_user_id) Customer," +
            " AD_User_ID, Summary, DocumentNo, StartDate, R_Status_ID, R_Request_ID, Priority," +
            " (SELECT count(*) FROM ad_attachment WHERE ad_table_id = 417" +
            "  AND record_id = R_Request.R_Request_ID) AttachmentCount" +
            " FROM R_Request" +
            " WHERE EXISTS (SELECT 1 FROM R_Status WHERE R_Status_ID = R_Request.R_Status_ID" +
            "               AND IsFinalClose != 'Y')" +
            " AND StartDate IS NOT NULL"
        );

        Properties ctx = Env.getCtx();
        int userId = Env.getAD_User_ID(ctx);
        String ss = currentScope;

        switch (ss) {
            case "Private":
                sql.append(" AND (AD_User_ID = ? OR SalesRep_ID = ?)");
                break;
            case "Subordinates":
                List<Integer> subs = getSubordinateIds(userId);
                if (subs.isEmpty()) return;
                String inClause = subs.stream().map(String::valueOf).collect(Collectors.joining(","));
                sql.append(" AND (AD_User_ID IN (").append(inClause)
                   .append(") OR SalesRep_ID IN (").append(inClause).append("))");
                break;
            case "Team":
                sql.append(" AND (AD_User_ID = ? OR SalesRep_ID = ?")
                   .append(" OR EXISTS (SELECT 1 FROM AD_User_Roles")
                   .append(" WHERE AD_Role_ID = R_Request.AD_Role_ID AND AD_User_ID = ")
                   .append(userId).append("))");
                break;
            case "All":
            default:
                sql.append(" AND ? = ?");
                break;
        }

        if (searchFilter != null && !searchFilter.isEmpty()) {
            sql.append(" AND (UPPER(Summary) LIKE ? OR UPPER(DocumentNo) LIKE ?)");
        }
        sql.append(" ORDER BY Priority ASC, StartDate DESC");

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
            }
            if (searchFilter != null && !searchFilter.isEmpty()) {
                String f = "%" + searchFilter.toUpperCase() + "%";
                pstmt.setString(idx++, f);
                pstmt.setString(idx++, f);
            }
            rs = pstmt.executeQuery();
            while (rs.next()) {
                int statusId     = rs.getInt("R_Status_ID");
                String statusVal = getStatusValueById(statusId);
                if (statusVal == null || !kanbanRows.containsKey(statusVal)) continue;
                KanbanRowModel row = buildKanbanRowModel(rs, statusId, statusVal, userId);
                kanbanRows.get(statusVal).add(row);
            }
        } catch (SQLException ex) {
            throw new AdempiereException("Unable to load request items", ex);
        } finally {
            DB.close(rs, pstmt);
        }
    }

    private KanbanRowModel buildKanbanRowModel(ResultSet rs, int statusId,
                                               String statusVal, int myUserId)
            throws SQLException {
        int requestId       = rs.getInt("R_Request_ID");
        String documentNo   = rs.getString("DocumentNo");
        String summary      = rs.getString("Summary");
        int priority        = rs.getInt("Priority");
        String customer     = rs.getString("Customer");
        String responsible  = rs.getString("Responsible");
        int requesterId     = rs.getInt("AD_User_ID");
        boolean isMyRequest = (requesterId == myUserId);
        boolean hasAtt      = rs.getInt("AttachmentCount") > 0;

        java.sql.Date startSql = rs.getDate("StartDate");
        LocalDate startDate    = startSql != null ? startSql.toLocalDate() : null;

        java.sql.Timestamp endTs = rs.getTimestamp("EndTime");
        LocalDate endDate        = endTs != null ? endTs.toLocalDateTime().toLocalDate() : null;

        return new KanbanRowModel(requestId, statusId, statusVal, documentNo, summary,
                                  priority, customer, responsible,
                                  startDate, endDate, hasAtt, isMyRequest);
    }

    // ── Gantt helpers ─────────────────────────────────────────────────────────

    private void resolveGanttRange() {
        LocalDate today = LocalDate.now();
        switch (ganttRange) {
            case "week":
                ganttFrom = today.with(DayOfWeek.MONDAY);
                ganttTo   = ganttFrom.plusDays(6);
                break;
            case "quarter":
                ganttFrom = today.withDayOfMonth(1)
                                 .withMonth(((today.getMonthValue() - 1) / 3) * 3 + 1);
                ganttTo   = ganttFrom.plusMonths(3).minusDays(1);
                break;
            default:
                if (!"custom".equals(ganttRange)) {
                    ganttFrom = today.withDayOfMonth(1);
                    ganttTo   = today.withDayOfMonth(today.lengthOfMonth());
                }
                break;
        }
    }

    /**
     * Reloads Gantt data from DB, renders HTML via GanttRenderer, and injects
     * it into the page via Clients.evalJavaScript.
     */
    public void refreshGanttHtml() {
        if (ganttFrom == null || ganttTo == null) resolveGanttRange();

        PreparedStatement[] pstmtHolder = new PreparedStatement[1];
        ResultSet rs = null;
        try {
            ResultSet[] result = loadGanttData(pstmtHolder);
            if (result == null) {
                injectGanttHtml(emptyStateHtml());
                return;
            }
            rs = result[0];
            if (!rs.next()) {
                injectGanttHtml(emptyStateHtml());
                return;
            }
            injectGanttHtml(
                new GanttRenderer(Env.getCtx(), ganttFrom, ganttTo, ganttRange, currentScope, statuses)
                    .build(rs)
            );
        } catch (Exception ex) {
            injectGanttHtml("<div style=\"color:red;padding:20px;\">Error: "
                + escHtml(ex.getMessage()) + "</div>");
        } finally {
            DB.close(rs, pstmtHolder[0]);
        }
    }

    private void injectGanttHtml(String html) {
        String escaped = org.zkoss.lang.Strings.escape(html, org.zkoss.lang.Strings.ESCAPE_JAVASCRIPT);
        Clients.evalJavaScript("(function(){ var c=document.querySelector('.rk-gantt-container'); if(c){c.innerHTML='" + escaped + "';} })()");
    }

    private String emptyStateHtml() {
        return "<div style=\"display:flex;align-items:center;justify-content:center;" +
               "min-height:120px;color:#aaa;font-size:13px;\">" +
               escHtml(Msg.getMsg(Env.getCtx(), "RK_NoRequests")) +
               "</div>";
    }

    /**
     * Loads Gantt data from the DB.
     * Caller is responsible for closing rs and pstmt via DB.close().
     * Returns null if scope is "Subordinates" and user has no subordinates.
     */
    private ResultSet[] loadGanttData(PreparedStatement[] pstmtOut) throws SQLException {
        Properties ctx = Env.getCtx();
        int userId = Env.getAD_User_ID(ctx);
        String ss = currentScope;

        StringBuilder sql = new StringBuilder(
            "SELECT r.R_Request_ID, r.DocumentNo, r.Summary, r.Priority," +
            " r.StartTime, r.EndTime, r.CloseDate, r.R_Status_ID," +
            " (SELECT Name FROM AD_User WHERE AD_User_ID = r.SalesRep_ID) AS Responsible," +
            " (SELECT Name FROM AD_User WHERE AD_User_ID = r.AD_User_ID)  AS Customer," +
            " r.AD_User_ID AS RequesterID, r.C_Project_ID, p.Name AS ProjectName" +
            " FROM R_Request r" +
            " LEFT JOIN C_Project p ON p.C_Project_ID = r.C_Project_ID" +
            " WHERE r.StartDate IS NOT NULL" +
            "   AND r.IsActive = 'Y'"
        );
        if (!showFinalClose) {
            sql.append("   AND EXISTS (SELECT 1 FROM R_Status WHERE R_Status_ID = r.R_Status_ID" +
                       "               AND IsFinalClose != 'Y')");
        }

        switch (ss) {
            case "Private":
                sql.append(" AND (r.AD_User_ID = ? OR r.SalesRep_ID = ?)");
                break;
            case "Subordinates":
                List<Integer> subs = getSubordinateIds(userId);
                if (subs.isEmpty()) return null;
                String inClause = subs.stream().map(String::valueOf)
                                     .collect(Collectors.joining(","));
                sql.append(" AND (r.AD_User_ID IN (").append(inClause)
                   .append(") OR r.SalesRep_ID IN (").append(inClause).append("))");
                break;
            case "Team":
                sql.append(" AND (r.AD_User_ID = ? OR r.SalesRep_ID = ?")
                   .append(" OR EXISTS (SELECT 1 FROM AD_User_Roles")
                   .append(" WHERE AD_Role_ID = r.AD_Role_ID AND AD_User_ID = ")
                   .append(userId).append("))");
                break;
            case "All":
            default:
                sql.append(" AND ? = ?");
                break;
        }

        if (searchFilter != null && !searchFilter.isEmpty()) {
            sql.append(" AND (UPPER(r.Summary) LIKE ? OR UPPER(r.DocumentNo) LIKE ?)");
        }

        sql.append(" AND (r.StartTime IS NULL OR r.StartTime <= ?)")
           .append(" AND (r.EndTime   IS NULL OR r.EndTime   >= ?)")
           .append(" ORDER BY r.C_Project_ID NULLS LAST," +
                   " r.SalesRep_ID NULLS LAST," +
                   " COALESCE(r.StartTime, '9999-01-01'::timestamp)");

        PreparedStatement pstmt = DB.prepareStatement(sql.toString(), null);
        pstmtOut[0] = pstmt;

        int idx = 1;
        if ("All".equals(ss)) {
            pstmt.setInt(idx++, 1);
            pstmt.setInt(idx++, 1);
        } else if (!"Subordinates".equals(ss)) {
            pstmt.setInt(idx++, userId);
            pstmt.setInt(idx++, userId);
        }
        if (searchFilter != null && !searchFilter.isEmpty()) {
            String f = "%" + searchFilter.toUpperCase() + "%";
            pstmt.setString(idx++, f);
            pstmt.setString(idx++, f);
        }
        pstmt.setTimestamp(idx++,
            java.sql.Timestamp.valueOf(ganttTo.atTime(23, 59, 59)));
        pstmt.setTimestamp(idx,
            java.sql.Timestamp.valueOf(ganttFrom.atStartOfDay()));

        return new ResultSet[]{ pstmt.executeQuery() };
    }

    /**
     * Refreshes project panel HTML in the Gantt sidebar.
     */
    public void refreshProjectPanel() {
        loadProjectList();
        String panelHtml = buildProjectPanelHtml();
        String escaped = org.zkoss.lang.Strings.escape(panelHtml, org.zkoss.lang.Strings.ESCAPE_JAVASCRIPT);
        Clients.evalJavaScript("(function(){ var p=document.querySelector('.rk-project-panel'); if(p){p.innerHTML='" + escaped + "';} })()");
    }

    /** Loads active C_Project records for the current client into cachedProjects / cachedProjectNames. */
    private void loadProjectList() {
        cachedProjects.clear();
        cachedProjectNames.clear();
        String sql = "SELECT C_Project_ID, Name FROM C_Project " +
                     "WHERE AD_Client_ID = ? AND IsActive = 'Y' AND IsSummary = 'Y' ORDER BY Name";
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = DB.prepareStatement(sql, null);
            pstmt.setInt(1, Env.getAD_Client_ID(Env.getCtx()));
            rs = pstmt.executeQuery();
            while (rs.next()) {
                cachedProjects.add(new int[]{ rs.getInt(1) });
                cachedProjectNames.add(rs.getString(2));
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "loadProjectList failed", ex);
        } finally {
            DB.close(rs, pstmt);
        }
        loadProjectRequestCounts();
    }

    /**
     * Loads active request counts per project for the current scope into cachedProjectCounts.
     * Called at the end of loadProjectList().
     */
    private void loadProjectRequestCounts() {
        cachedProjectCounts.clear();
        Properties ctx = Env.getCtx();
        int userId = Env.getAD_User_ID(ctx);
        String ss = currentScope;

        // Subordinates: resolve list first; if empty, all counts are 0
        List<Integer> subs = null;
        if ("Subordinates".equals(ss)) {
            subs = getSubordinateIds(userId);
            if (subs.isEmpty()) {
                logger.log(Level.FINE, "loadProjectRequestCounts: no subordinates for user {0}", userId);
                return;
            }
        }

        StringBuilder sql = new StringBuilder(
            "SELECT C_Project_ID, COUNT(*) " +
            "FROM R_Request r " +
            "WHERE r.AD_Client_ID = ? " +
            "  AND C_Project_ID IS NOT NULL " +
            "  AND r.IsActive = 'Y'"
        );
        if (!showFinalClose) {
            sql.append("  AND EXISTS (SELECT 1 FROM R_Status WHERE R_Status_ID = r.R_Status_ID" +
                       "              AND IsFinalClose != 'Y')");
        }

        switch (ss) {
            case "Private":
                sql.append(" AND (r.AD_User_ID = ? OR r.SalesRep_ID = ?)");
                break;
            case "Subordinates":
                String inClause = subs.stream().map(String::valueOf)
                                      .collect(Collectors.joining(","));
                sql.append(" AND (r.AD_User_ID IN (").append(inClause)
                   .append(") OR r.SalesRep_ID IN (").append(inClause).append("))");
                break;
            case "Team":
                sql.append(" AND (r.AD_User_ID = ? OR r.SalesRep_ID = ?")
                   .append(" OR EXISTS (SELECT 1 FROM AD_User_Roles")
                   .append(" WHERE AD_Role_ID = r.AD_Role_ID AND AD_User_ID = ")
                   .append(userId).append("))");
                break;
            case "All":
            default:
                sql.append(" AND ? = ?");
                break;
        }

        sql.append(" GROUP BY C_Project_ID");

        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = DB.prepareStatement(sql.toString(), null);
            int idx = 1;
            pstmt.setInt(idx++, Env.getAD_Client_ID(ctx));
            if ("All".equals(ss)) {
                pstmt.setInt(idx++, 1);
                pstmt.setInt(idx++, 1);
            } else if (!"Subordinates".equals(ss)) {
                pstmt.setInt(idx++, userId);
                pstmt.setInt(idx++, userId);
            }
            rs = pstmt.executeQuery();
            while (rs.next()) {
                cachedProjectCounts.put(rs.getInt(1), rs.getInt(2));
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "loadProjectRequestCounts failed", ex);
        } finally {
            DB.close(rs, pstmt);
        }
    }

    /** Renders the left project list as an HTML fragment. */
    private String buildProjectPanelHtml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"padding:4px 0;\">");
        if (cachedProjects.isEmpty()) {
            sb.append("<div style=\"color:#aaa;font-size:11px;padding:8px 10px;\">")
              .append(escHtml(Msg.getMsg(Env.getCtx(), "RK_NoProjects")))
              .append("</div>");
        } else {
            for (int i = 0; i < cachedProjects.size(); i++) {
                int projectId = cachedProjects.get(i)[0];
                String name   = cachedProjectNames.get(i);
                int count = cachedProjectCounts.getOrDefault(projectId, 0);
                sb.append("<div")
                  .append(" id=\"proj-").append(projectId).append("\"")
                  .append(" ondragover=\"event.preventDefault();this.style.background='#dbeafe';\"")
                  .append(" ondragleave=\"this.style.background='';\"")
                  .append(" ondrop=\"event.preventDefault();this.style.background='';window._zkGanttDrop(event,").append(projectId).append(");\"")
                  .append(" ondblclick=\"window._zkGanttProjectDblClick(").append(projectId).append(");\"")
                  .append(" style=\"display:flex;align-items:center;justify-content:space-between;")
                  .append("padding:6px 10px;font-size:12px;cursor:pointer;border-radius:4px;")
                  .append("border:1px solid #e0e0e0;margin-bottom:4px;background:#fff;overflow:hidden;\"")
                  .append(" title=\"").append(escHtml(name)).append("\">")
                  .append("<span style=\"min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;\">")
                  .append("📁 ").append(escHtml(name))
                  .append("</span>");
                if (count > 0) {
                    sb.append("<span style=\"box-sizing:border-box;background:#0052cc;color:#fff;border-radius:10px;")
                      .append("min-width:18px;height:18px;font-size:10px;display:flex;")
                      .append("align-items:center;justify-content:center;")
                      .append("padding:0 4px;flex-shrink:0;margin-left:4px;\">")
                      .append(count)
                      .append("</span>");
                }
                sb.append("</div>");
            }
        }
        sb.append("</div>");
        return sb.toString();
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private String getStatusValueById(int statusId) {
        for (MStatus s : statuses) {
            if (s.getR_Status_ID() == statusId) return s.getValue();
        }
        return null;
    }

    private MStatus[] getRequestStatus() {
        String where = MStatus.COLUMNNAME_IsFinalClose + "='N'"
                     + " AND AD_Client_ID = ?"
                     + " AND IsActive = 'Y'";
        List<MStatus> list = new Query(Env.getCtx(), MStatus.Table_Name, where, null)
            .setParameters(Integer.valueOf(Env.getCtx().getProperty("#AD_Client_ID")))
            .setOrderBy(MStatus.COLUMNNAME_SeqNo + " DESC")
            .list();
        return list.toArray(new MStatus[0]);
    }

    public List<Integer> getSubordinateIds(int supervisorId) {
        List<Integer> result = new ArrayList<>();
        String sql = "WITH RECURSIVE sub AS (" +
            "  SELECT ad_user_id FROM ad_user WHERE supervisor_id = ?" +
            "  UNION" +
            "  SELECT e.ad_user_id FROM ad_user e JOIN sub s ON s.ad_user_id = e.supervisor_id" +
            ") SELECT ad_user_id FROM sub";
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = DB.prepareStatement(sql, null);
            pstmt.setInt(1, supervisorId);
            rs = pstmt.executeQuery();
            while (rs.next()) result.add(rs.getInt(1));
        } catch (Exception e) {
            logger.log(Level.SEVERE, sql, e);
        } finally {
            DB.close(rs, pstmt);
        }
        return result;
    }

    public boolean canEditRequest(MRequest request) {
        int userId = Env.getAD_User_ID(Env.getCtx());
        return request.getSalesRep_ID() == userId || request.getAD_User_ID() == userId;
    }

    public boolean isMySubordinate(int ad_User_ID) {
        int myId = Env.getAD_User_ID(Env.getCtx());
        return getSubordinateIds(myId).contains(ad_User_ID);
    }

    public void broadcastRefresh() {
        // postEvent is async — handler runs on OSGi thread (not ZK request thread),
        // so Executions.schedule() in the subscriber works correctly for other browsers.
        EventManager.getInstance().postEvent(
            new org.osgi.service.event.Event(TOPIC_KANBAN_REFRESH, new java.util.HashMap<>()));
    }

    public boolean isSupervisorOf(int adUserId) {
        int myId = Env.getAD_User_ID(Env.getCtx());
        int supervisorId = DB.getSQLValue(null,
            "SELECT supervisor_id FROM ad_user WHERE ad_user_id = ?", adUserId);
        return supervisorId == myId;
    }

    public int getSalesRepByRequestType(int R_RequestType_ID) {
        String sql = "select ad_user_id From R_RequestTypeUpdates Where R_RequestType_ID = ?";
        return DB.getSQLValue(null, sql, R_RequestType_ID);
    }

    public boolean isAttachment(int r_Request_ID) {
        String sql = "select count(*) from ad_attachment where ad_table_id = 417 and record_id = ?";
        return DB.getSQLValue(null, sql, r_Request_ID) > 0;
    }

    public int getHighPriorityCounter(MRequest currentRequest) {
        int myId = Env.getAD_User_ID(Env.getCtx());
        List<Integer> subs = getSubordinateIds(myId);
        String userFilter;
        if (subs.isEmpty()) {
            userFilter = "ad_user_id = " + myId;
        } else {
            String inStr = subs.stream().map(String::valueOf).collect(Collectors.joining(","));
            userFilter = "ad_user_id IN (" + inStr + ") OR ad_user_id = " + myId;
        }
        String sql = "SELECT count(*) FROM r_request" +
            " WHERE (" + userFilter + ")" +
            " AND EXISTS (SELECT 1 FROM R_Status WHERE R_Status_ID = r_request.R_Status_ID" +
            "  AND value IN (" + statusConfig.getActiveStatusesInClause() + "))" +
            " AND priority = '1' AND r_request_id != ?";
        return DB.getSQLValue(null, sql, currentRequest.getR_Request_ID());
    }

    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static LocalDate dateToLocalDate(java.util.Date d) {
        if (d instanceof java.sql.Date) return ((java.sql.Date) d).toLocalDate();
        return d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    }
}
