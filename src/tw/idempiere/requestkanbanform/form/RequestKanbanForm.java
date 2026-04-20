/*
 * Copyright (C) 2026 Ray Lee / TopGiga
 * SPDX-License-Identifier: GPL-2.0-only
 */
package tw.idempiere.requestkanbanform.form;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.adempiere.base.event.EventManager;
import org.adempiere.webui.editor.WSearchEditor;
import org.adempiere.webui.editor.WTableDirEditor;
import org.adempiere.webui.event.ValueChangeEvent;
import org.adempiere.webui.event.ValueChangeListener;
import org.adempiere.webui.panel.ADForm;
import org.adempiere.webui.panel.IFormController;
import org.adempiere.webui.panel.WAttachment;
import org.compiere.model.MColumn;
import org.compiere.model.MLookup;
import org.compiere.model.MLookupFactory;
import org.compiere.model.MRequest;
import org.compiere.model.MRequestUpdate;
import org.compiere.model.MUser;
import org.compiere.util.DB;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.osgi.service.event.EventHandler;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Page;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.select.Selectors;
import org.zkoss.zul.Button;
import org.zkoss.zul.Column;
import org.zkoss.zul.Columns;
import org.zkoss.zul.Datebox;
import org.zkoss.zul.Div;
import org.zkoss.zul.Doublespinner;
import org.zkoss.zul.Grid;
import org.zkoss.zul.Hlayout;
import org.zkoss.zul.Label;
import org.zkoss.zul.Listbox;
import org.zkoss.zul.Listcell;
import org.zkoss.zul.Listheader;
import org.zkoss.zul.Listitem;
import org.zkoss.zul.Row;
import org.zkoss.zul.Rows;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.Vlayout;
import org.zkoss.zul.Window;

import tw.idempiere.requestkanbanform.viewmodel.RequestKanbanVM;

public class RequestKanbanForm extends ADForm
        implements IFormController, ValueChangeListener {

    private static final long serialVersionUID = 1L;
    private static final Logger log = Logger.getLogger(RequestKanbanForm.class.getName());

    private RequestKanbanVM vm;

    // New-request dialog editors
    private WTableDirEditor  fPriority;
    private WSearchEditor    fUser;
    private WTableDirEditor  fDoc;
    private WSearchEditor    fRole;
    private WTableDirEditor  fDepart;
    private WSearchEditor    fSalesRep;
    private WSearchEditor    fProject;

    // Update dialog editors (re-created each time the dialog opens)
    private WTableDirEditor  fUpdatePriority;
    private WSearchEditor    fUpdateSalesRep;
    private WSearchEditor    fProductSpent;
    private Datebox          fStartTime;
    private Datebox          fEndTime;
    private Textbox          fUpdateResult;
    private Doublespinner    fSpinnerQuantity;

    // Cross-session real-time refresh (article pattern: capture desktop at init)
    private Desktop myDesktop;
    private EventHandler eventSubscriber;

    // Gantt bridge — initialized lazily on first switch to Gantt view
    private boolean ganttBridgeReady = false;

    @Override
    protected void initForm() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            vm = new RequestKanbanVM();
            vm.setForm(this);
            // Store VM as component attribute — args map is gone by the time
            // BindComposer fires (during addWindow/onPageAttached, not createComponents).
            // ZUL resolves it via @init(self.parent.attributes.vm).
            setAttribute("vm", vm);

            Executions.createComponents("~./zul/RequestKanbanForm.zul", this, null);
            Selectors.wireComponents(this, this, false);

            // Capture desktop reference now (article pattern: must be done
            // in ZK thread, not inside the OSGi event handler thread).
            myDesktop = Executions.getCurrent().getDesktop();
            if (!myDesktop.isServerPushEnabled()) myDesktop.enableServerPush(true);

            // NOTE: setupGanttBridge() is NOT called here.
            // It is called lazily via ensureGanttBridge() when the user first
            // switches to Gantt view, at which point the ganttLayout Div is
            // fully mounted in ZK's widget registry.
            registerOsgiEventHandler();

        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to init RequestKanbanForm", e);
        } finally {
            Thread.currentThread().setContextClassLoader(cl);
        }
    }

    /**
     * Builds the full update-request dialog content — mirrors reference setupUpdateDialog.
     */
    private void setupUpdateDialog(Window dialog, MRequest request, MRequestUpdate[] updates) {
        Div requestDoc = (Div) dialog.getFellow("requestDoc");
        if (requestDoc == null) return;
        requestDoc.getChildren().clear();

        dialog.setTitle(Msg.getMsg(Env.getCtx(), "RK_RequestFormTitle") + " — #" + request.getDocumentNo());

        boolean canEdit = vm.canEditRequest(request);
        boolean isSupervisor = vm.isSupervisorOf(request.getAD_User_ID());
        boolean canEditAny = canEdit || isSupervisor;

        // ── 📋 Basic Info ─────────────────────────────────────────
        Label sec1 = new Label("📋 " + Msg.getMsg(Env.getCtx(), "RK_BasicInfo"));
        sec1.setStyle("font-size:11px;font-weight:700;color:#888;letter-spacing:0.5px;margin:10px 0 6px;");
        requestDoc.appendChild(sec1);

        // Priority — only supervisor can edit
        MLookup priorityL = MLookupFactory.get(Env.getCtx(), 0, 0, 5426, DisplayType.List);
        fUpdatePriority = new WTableDirEditor("Priority", false, false, true, priorityL);
        fUpdatePriority.setMandatory(true);
        fUpdatePriority.setValue(request.getPriority());
        fUpdatePriority.setReadWrite(isSupervisor);
        requestDoc.appendChild(makeFieldRow(Msg.getMsg(Env.getCtx(), "RK_Priority"), fUpdatePriority.getComponent()));

        // SalesRep — supervisor or canEdit
        MLookup srL = MLookupFactory.get(Env.getCtx(), 0, 0, 5432, DisplayType.Search);
        fUpdateSalesRep = new WSearchEditor("SalesRep_ID", false, false, true, srL);
        fUpdateSalesRep.setValue(request.getSalesRep_ID());
        fUpdateSalesRep.setReadWrite(canEditAny);
        requestDoc.appendChild(makeFieldRow(Msg.getMsg(Env.getCtx(), "RK_SalesRep"), fUpdateSalesRep.getComponent()));

        // StartTime / EndTime — supervisor or canEdit
        fStartTime = new Datebox();
        fStartTime.setHflex("1");
        fStartTime.setFormat("yyyy-MM-dd HH:mm");
        if (request.getStartTime() != null) fStartTime.setValue(request.getStartTime());
        fStartTime.setDisabled(!canEditAny);

        fEndTime = new Datebox();
        fEndTime.setHflex("1");
        fEndTime.setFormat("yyyy-MM-dd HH:mm");
        if (request.getEndTime() != null) fEndTime.setValue(request.getEndTime());
        fEndTime.setDisabled(!canEditAny);

        Hlayout timeRow = new Hlayout();
        timeRow.setHflex("1");
        timeRow.setSpacing("6px");
        timeRow.appendChild(fStartTime);
        Label lblArrow = new Label("→");
        lblArrow.setStyle("color:#888;");
        timeRow.appendChild(lblArrow);
        timeRow.appendChild(fEndTime);
        requestDoc.appendChild(makeFieldRow(
            Msg.getMsg(Env.getCtx(), "RK_StartTime") + " / " + Msg.getMsg(Env.getCtx(), "RK_EndTime"),
            timeRow));

        // Attachment
        Hlayout attachRow = new Hlayout();
        attachRow.setValign("middle");
        attachRow.setSpacing("8px");
        Button btnManageAttach = new Button(Msg.getMsg(Env.getCtx(), "Attachment"));
        btnManageAttach.setImage("~./images/kanban/Attachment24.png");
        btnManageAttach.addEventListener(Events.ON_CLICK, e -> {
            WAttachment wa = new WAttachment(0, 0, MRequest.Table_ID,
                request.getR_Request_ID(), null, ev -> vm.refreshGanttHtml());
            wa.setWidth("800px"); wa.setHeight("600px");
            wa.setSclass("attachment-all"); wa.setShadow(true);
            wa.setBorder("normal"); wa.setClosable(true);
            wa.setSizable(true); wa.setMaximizable(true);
            wa.doModal();
        });
        attachRow.appendChild(btnManageAttach);
        int attachCount = DB.getSQLValue(null,
            "SELECT COUNT(*) FROM AD_Attachment WHERE AD_Table_ID=? AND Record_ID=?",
            MRequest.Table_ID, request.getR_Request_ID());
        if (attachCount > 0) {
            Label lblCount = new Label("(" + attachCount + ")");
            lblCount.setStyle("font-weight:bold;color:#336699;");
            attachRow.appendChild(lblCount);
        }
        requestDoc.appendChild(makeFieldRow(Msg.getMsg(Env.getCtx(), "Attachment"), attachRow));

        // ── 👥 Members ────────────────────────────────────────────────────────────
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

        // ── 📝 Summary ────────────────────────────────────────────
        Label sec2 = new Label("📝 " + Msg.getMsg(Env.getCtx(), "RK_Summary"));
        sec2.setStyle("font-size:11px;font-weight:700;color:#888;letter-spacing:0.5px;margin:10px 0 4px;");
        requestDoc.appendChild(sec2);

        Textbox summaryTxt = new Textbox(request.getSummary());
        summaryTxt.setMultiline(true);
        summaryTxt.setHeight("80px");
        summaryTxt.setHflex("1");
        summaryTxt.setDisabled(true);
        summaryTxt.setStyle("background-color:rgba(255,255,128,.5);opacity:1;color:#333;");
        requestDoc.appendChild(summaryTxt);

        // ── 💬 Update Message ─────────────────────────────────────
        Label sec3 = new Label("💬 " + Msg.getMsg(Env.getCtx(), "RK_UpdateMessage"));
        sec3.setStyle("font-size:11px;font-weight:700;color:#888;letter-spacing:0.5px;margin:10px 0 4px;");
        requestDoc.appendChild(sec3);

        Listbox boxUpdates = (Listbox) dialog.getFellow("boxUpdates");
        boxUpdates.getItems().clear();
        Listheader listHeaderUpdate = (Listheader) dialog.getFellow("listHeaderUpdate");
        listHeaderUpdate.setLabel(Msg.getMsg(Env.getCtx(), "RK_UpdateHistory"));

        if (updates.length == 0) {
            boxUpdates.appendChild(new Listitem(Msg.getMsg(Env.getCtx(), "RK_NoUpdates")));
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm");
            for (MRequestUpdate upd : updates) {
                MUser user = new MUser(Env.getCtx(), upd.getUpdatedBy(), null);
                String userName = user.getName() != null ? user.getName() : "?";
                String initials = getInitials(userName);

                Listitem item = new Listitem();
                Listcell cell = new Listcell();
                Hlayout h = new Hlayout();
                h.setValign("top"); h.setSpacing("8px");

                Div avatar = new Div();
                avatar.setWidth("28px"); avatar.setHeight("28px");
                avatar.setStyle("border-radius:50%;background:#1976d2;color:#fff;" +
                    "display:flex;align-items:center;justify-content:center;" +
                    "font-size:11px;font-weight:700;flex-shrink:0;");
                avatar.appendChild(new Label(initials));

                Vlayout content = new Vlayout();
                content.setSpacing("2px");
                Label lblMeta = new Label(userName + " · " + sdf.format(upd.getUpdated()));
                lblMeta.setStyle("color:#888;font-size:11px;");
                content.appendChild(lblMeta);
                content.appendChild(new Label(upd.getResult()));
                if (upd.getQtySpent() != null && upd.getQtySpent().floatValue() != 0) {
                    String unit = upd.getQtySpent().floatValue() > 1 ? "Hours" : "Hour";
                    Label lblQty = new Label("[" + upd.getM_ProductSpent().getValue()
                        + ": " + upd.getQtySpent() + " "
                        + Msg.getMsg(Env.getCtx(), unit) + "]");
                    lblQty.setStyle("color:#555;font-size:11px;");
                    content.appendChild(lblQty);
                }
                h.appendChild(avatar);
                h.appendChild(content);
                cell.appendChild(h);
                item.appendChild(cell);
                boxUpdates.appendChild(item);
            }
        }

        // New update message
        fUpdateResult = new Textbox();
        fUpdateResult.setRows(3);
        fUpdateResult.setMultiline(true);
        fUpdateResult.setHflex("1");
        fUpdateResult.setReadonly(!canEditAny);
        requestDoc.appendChild(fUpdateResult);

        // Product / Quantity (only when canEditAny)
        if (canEditAny) {
            MLookup productL = MLookupFactory.get(Env.getCtx(), 0, 0, 13497, DisplayType.Search);
            fProductSpent = new WSearchEditor("M_ProductSpent_ID", false, false, true, productL);
            fProductSpent.setValue(MColumn.get(Env.getCtx(), 13497).getDefaultValue());
            fSpinnerQuantity = new Doublespinner();
            fSpinnerQuantity.setStep(0.5);
            Hlayout hProd = new Hlayout();
            hProd.setSpacing("6px");
            hProd.appendChild(fProductSpent.getComponent());
            hProd.appendChild(new Label(Msg.getMsg(Env.getCtx(), "RK_Quantity")));
            hProd.appendChild(fSpinnerQuantity);
            requestDoc.appendChild(hProd);
        }

        // ── Buttons ───────────────────────────────────────────────
        Button btnSave = (Button) dialog.getFellow("closeBtn");
        btnSave.setLabel(Msg.getMsg(Env.getCtx(), "RK_SaveAndClose"));
        btnSave.setDisabled(!canEditAny);
        btnSave.addEventListener(Events.ON_CLICK, e -> {
            if (fUpdatePriority.getValue() == null) {
                Clients.showNotification(Msg.getMsg(Env.getCtx(), "RK_PriorityMandatory"));
                return;
            }
            boolean anyChange = false;
            String newMsg = fUpdateResult != null ? fUpdateResult.getValue() : "";
            if (!newMsg.isEmpty() || !fUpdatePriority.getValue().equals(request.getPriority())) {
                request.setPriorityUser((String) fUpdatePriority.getValue());
                request.setPriority((String) fUpdatePriority.getValue());
                if (fSpinnerQuantity != null && fSpinnerQuantity.getValue() != null
                        && fSpinnerQuantity.getValue() > 0 && fProductSpent != null) {
                    request.setM_ProductSpent_ID(Integer.parseInt(
                        String.valueOf(fProductSpent.getValue())));
                    request.setQtySpent(new BigDecimal(fSpinnerQuantity.getValue()));
                }
                if (!newMsg.isEmpty()) request.addToResult(newMsg);
                anyChange = true;
            }
            Timestamp newStart = fStartTime.getValue() != null
                ? new Timestamp(fStartTime.getValue().getTime()) : null;
            Timestamp newEnd   = fEndTime.getValue()   != null
                ? new Timestamp(fEndTime.getValue().getTime())   : null;
            if (!Objects.equals(request.getStartTime(), newStart)
                    || !Objects.equals(request.getEndTime(), newEnd)) {
                request.setStartTime(newStart);
                request.setEndTime(newEnd);
                anyChange = true;
            }
            if (fUpdateSalesRep.getValue() != null
                    && (int) fUpdateSalesRep.getValue() != request.getSalesRep_ID()) {
                request.setSalesRep_ID((int) fUpdateSalesRep.getValue());
                anyChange = true;
            }
            if (anyChange) {
                request.save();
                vm.broadcastRefresh();
            }
            dialog.detach();
            vm.refreshCurrentView();
        });

        Button btnZoom = (Button) dialog.getFellow("zoomBtn");
        btnZoom.setLabel(Msg.getMsg(Env.getCtx(), "RK_Zoom"));
        btnZoom.addEventListener(Events.ON_CLICK, e -> {
            vm.zoomToRequest(request.getR_Request_ID());
            dialog.detach();
        });
    }

    private Hlayout makeFieldRow(String labelText, Component field) {
        Hlayout row = new Hlayout();
        row.setValign("middle");
        row.setSpacing("8px");
        row.setStyle("margin-bottom:4px;");
        Label lbl = new Label(labelText);
        lbl.setStyle("font-weight:600;color:#555;font-size:12px;min-width:120px;");
        row.appendChild(lbl);
        row.appendChild(field);
        return row;
    }

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

    private static String getInitials(String name) {
        if (name == null || name.isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2 && !parts[0].isEmpty() && !parts[parts.length - 1].isEmpty())
            return (String.valueOf(parts[0].charAt(0))
                + String.valueOf(parts[parts.length - 1].charAt(0))).toUpperCase();
        return name.length() >= 2 ? name.substring(0, 2) : name.substring(0, 1).toUpperCase();
    }

    /**
     * Build iDempiere editor components for the new-request dialog using a
     * label+editor grid that matches the reference dashboard layout.
     *
     *   Row 1: Requester        – column 5434 (AD_User_ID / Search)
     *   Row 2: Priority         – column 5426 (Priority / List)
     *   Row 3: Department       – column 54792 (HR_Department_ID / TableDir)
     *   Row 4: Type             – column 7791 (R_RequestType_ID / TableDir)
     *   Row 5: Sales Rep        – column 5432 (SalesRep_ID / Search)
     *   Row 6: Responsible Role – column 13488 (AD_Role_ID / Search)
     */
    private void buildNewRequestDialogEditors(Window dialog) {
        Component container = dialog.getFellow("requestDoc");
        if (container == null) return;

        // Build editors
        MLookup bpL = MLookupFactory.get(Env.getCtx(), 0, 0, 5434, DisplayType.Search);
        fUser = new WSearchEditor("AD_User_ID", false, false, true, bpL);
        fUser.setValue(Env.getAD_User_ID(Env.getCtx()));
        fUser.setMandatory(true);

        MLookup userPriorityL = MLookupFactory.get(Env.getCtx(), 0, 0, 5426, DisplayType.List);
        fPriority = new WTableDirEditor("Priority", false, false, true, userPriorityL);
        fPriority.setMandatory(true);
        fPriority.setValue("5");
        fPriority.addValueChangeListener(this);

        MLookup departL = MLookupFactory.get(Env.getCtx(), 0, 0, 54792, DisplayType.TableDir);
        fDepart = new WTableDirEditor("HR_Department_ID", false, false, true, departL);
        fDepart.setMandatory(true);

        MLookup docL = MLookupFactory.get(Env.getCtx(), 0, 0, 7791, DisplayType.TableDir);
        fDoc = new WTableDirEditor("R_RequestType_ID", false, false, true, docL);
        fDoc.setMandatory(true);
        fDoc.addValueChangeListener(this);

        MLookup srL = MLookupFactory.get(Env.getCtx(), 0, 0, 5432, DisplayType.Search);
        fSalesRep = new WSearchEditor("SalesRep_ID", false, false, true, srL);
        fSalesRep.setMandatory(true);
        fSalesRep.addValueChangeListener(this);

        MLookup roleL = MLookupFactory.get(Env.getCtx(), 0, 0, 13488, DisplayType.Search);
        fRole = new WSearchEditor("AD_Role_ID", false, false, true, roleL);
        fRole.setValue(Env.getAD_Role_ID(Env.getCtx()));

        int projColId = MColumn.getColumn_ID("R_Request", "C_Project_ID");
        MLookup projL = MLookupFactory.get(Env.getCtx(), 0, 0, projColId, DisplayType.Search);
        fProject = new WSearchEditor("C_Project_ID", false, false, false, projL);

        // Assemble label+editor grid (matches reference dashboard layout)
        Grid grid = new Grid();
        grid.setHflex("1");
        grid.setSclass("rk-dialog-grid");
        Columns cols = new Columns();
        Column cLabel = new Column(); cLabel.setWidth("160px");
        Column cEditor = new Column(); cEditor.setHflex("1");
        cols.appendChild(cLabel);
        cols.appendChild(cEditor);
        grid.appendChild(cols);

        Rows rows = new Rows();
        rows.appendChild(makeRow("Requester:",         fUser.getComponent()));
        rows.appendChild(makeRow("Priority:",          fPriority.getComponent()));
        rows.appendChild(makeRow("Department:",        fDepart.getComponent()));
        rows.appendChild(makeRow("Type:",              fDoc.getComponent()));
        rows.appendChild(makeRow("Sales Rep:",         fSalesRep.getComponent()));
        rows.appendChild(makeRow("Responsible Role/Team:", fRole.getComponent()));
        rows.appendChild(makeRow("Project:",           fProject.getComponent()));
        grid.appendChild(rows);

        container.appendChild(grid);
    }

    private Row makeRow(String labelText, Component editor) {
        Row row = new Row();
        row.setValign("middle");
        Label lbl = new Label(labelText);
        lbl.setStyle("font-size:12px;color:#555;");
        row.appendChild(lbl);
        row.appendChild(editor);
        return row;
    }

    private void bindNewRequestButtons(Window dialog) {
        Button btnSave   = (Button) dialog.getFellow("btnSave");
        Button btnCancel = (Button) dialog.getFellow("btnCancel");
        org.zkoss.zul.Textbox txtSummary =
            (org.zkoss.zul.Textbox) dialog.getFellow("txtSummary");
        if (btnCancel != null) btnCancel.addEventListener(Events.ON_CLICK, e -> dialog.detach());
        if (btnSave != null) btnSave.addEventListener(Events.ON_CLICK, e -> {
            String summary = txtSummary != null ? txtSummary.getValue().trim() : "";
            vm.saveNewRequest(fUser, fDoc, fPriority, fDepart, fSalesRep, fRole, fProject, summary);
            dialog.detach();
        });
    }

    public void openNewRequestDialog() {
        Thread t = Thread.currentThread();
        ClassLoader orig = t.getContextClassLoader();
        try {
            t.setContextClassLoader(getClass().getClassLoader());
            Map<String, Object> args = new HashMap<>();
            Window dialog = (Window) Executions.createComponents("~./zul/request-new.zul", this, args);
            buildNewRequestDialogEditors(dialog);
            bindNewRequestButtons(dialog);
            dialog.doModal();
        } catch (Exception e) {
            log.warning("Cannot open new request dialog: " + e.getMessage());
        } finally {
            t.setContextClassLoader(orig);
        }
    }

    public void openNewProjectDialog() {
        Thread t = Thread.currentThread();
        ClassLoader orig = t.getContextClassLoader();
        try {
            t.setContextClassLoader(getClass().getClassLoader());
            Window dialog = (Window) Executions.createComponents("~./zul/project-new.zul", this, null);
            // Set i18n labels
            ((Label) dialog.getFellow("lblName")).setValue(Msg.getMsg(Env.getCtx(), "RK_ProjectName"));
            ((Label) dialog.getFellow("lblStart")).setValue(Msg.getMsg(Env.getCtx(), "RK_ProjectStart"));
            ((Label) dialog.getFellow("lblEnd")).setValue(Msg.getMsg(Env.getCtx(), "RK_ProjectEnd"));
            Button btnSave   = (Button)  dialog.getFellow("btnProjectSave");
            Button btnCancel = (Button)  dialog.getFellow("btnProjectCancel");
            Textbox txtName  = (Textbox) dialog.getFellow("txtProjectName");
            Datebox dtStart  = (Datebox) dialog.getFellow("dtStart");
            Datebox dtEnd    = (Datebox) dialog.getFellow("dtEnd");
            if (btnCancel != null) btnCancel.addEventListener(Events.ON_CLICK, e -> dialog.detach());
            if (btnSave != null) btnSave.addEventListener(Events.ON_CLICK, e -> {
                vm.saveNewProject(
                    txtName  != null ? txtName.getValue()  : "",
                    dtStart  != null ? dtStart.getValue()  : null,
                    dtEnd    != null ? dtEnd.getValue()    : null);
                dialog.detach();
            });
            dialog.doModal();
        } catch (Exception e) {
            log.warning("Cannot open new project dialog: " + e.getMessage());
        } finally {
            t.setContextClassLoader(orig);
        }
    }

    public void openRequestUpdateDialog(int requestId) {
        Thread t = Thread.currentThread();
        ClassLoader orig = t.getContextClassLoader();
        try {
            t.setContextClassLoader(getClass().getClassLoader());
            MRequest request = new MRequest(Env.getCtx(), requestId, null);
            if (request.getR_Request_ID() == 0) return;
            MRequestUpdate[] updates = request.getUpdates(null);
            Window dialog = (Window) Executions.createComponents("~./zul/request-update.zul", this, null);
            setupUpdateDialog(dialog, request, updates);
            dialog.doModal();
        } catch (Exception e) {
            log.warning("Cannot open update dialog: " + e.getMessage());
        } finally {
            t.setContextClassLoader(orig);
        }
    }

    /**
     * Called by RequestKanbanVM when first switching to Gantt view.
     * Idempotent — no-op after first call.
     */
    public void ensureGanttBridge() {
        if (ganttBridgeReady) return;
        setupGanttBridge();
        ganttBridgeReady = true;
    }

    /**
     * Sets up Gantt drag-drop and click JS bridges — mirrors the reference
     * RequestKanbanDashboard pattern: obtain the ganttLayout Div UUID, inject
     * global JS functions that call zk.Widget.$(uuid) directly (ZK's own widget
     * registry lookup), then register server-side listeners on the same Div.
     * Must be called while ganttLayout is visible (mounted in ZK widget registry).
     */
    @SuppressWarnings("unchecked")
    private void setupGanttBridge() {
        Window win = (Window) this.getFellow("requestkanban");
        if (win == null) return;
        org.zkoss.zul.Div ganttLayout =
            (org.zkoss.zul.Div) win.getFellow("ganttLayout");
        if (ganttLayout == null) return;

        final String uuid = ganttLayout.getUuid();
        Clients.evalJavaScript(
            "window._zkGanttClick = function(id) {" +
            "  zAu.send(new zk.Event(zk.Widget.$('" + uuid + "'), 'onGanttClick', id));" +
            "};" +
            "window._zkGanttDrop = function(e, projectId) {" +
            "  var reqId = window._zkGanttDragging || '';" +
            "  if (!reqId) return;" +
            "  window._zkGanttDragging = null;" +
            "  zAu.send(new zk.Event(zk.Widget.$('" + uuid + "'), 'onGanttDrop'," +
            "    {requestId: reqId, projectId: projectId}));" +
            "};" +
            "window._zkGanttProjectDblClick = function(projectId) {" +
            "  zAu.send(new zk.Event(zk.Widget.$('" + uuid + "'), 'onGanttProjectOpen', projectId));" +
            "};" +
            "window._zkGanttDragging = null;"
        );

        ganttLayout.addEventListener("onGanttClick", ev -> {
            if (ev.getData() instanceof Number)
                openRequestUpdateDialog(((Number) ev.getData()).intValue());
        });
        ganttLayout.addEventListener("onGanttProjectOpen", ev -> {
            if (!(ev.getData() instanceof Number)) return;
            int projectId = ((Number) ev.getData()).intValue();
            int windowId = DB.getSQLValue(null,
                "SELECT w.AD_Window_ID FROM AD_Window w " +
                "JOIN AD_Tab t ON t.AD_Window_ID = w.AD_Window_ID " +
                "WHERE t.AD_Table_ID = ? AND w.IsActive = 'Y' AND t.IsActive = 'Y' " +
                "AND t.TabLevel = 0 " +
                "ORDER BY w.AD_Client_ID DESC, t.SeqNo " +
                "LIMIT 1",
                org.compiere.model.MProject.Table_ID);
            if (windowId > 0) {
                org.compiere.model.MQuery query =
                    org.compiere.model.MQuery.getEqualQuery("C_Project_ID", projectId);
                org.adempiere.webui.apps.AEnv.zoom(windowId, query);
            } else {
                log.warning("onGanttProjectOpen: no window found for C_Project table");
            }
        });
        ganttLayout.addEventListener("onGanttDrop", ev -> {
            if (!(ev.getData() instanceof java.util.Map)) return;
            java.util.Map<String, Object> d =
                (java.util.Map<String, Object>) ev.getData();
            Object reqObj  = d.get("requestId");
            Object projObj = d.get("projectId");
            if (reqObj == null || projObj == null) return;
            vm.onGanttDrop(Integer.parseInt(String.valueOf(reqObj)),
                           Integer.parseInt(String.valueOf(projObj)));
        });
    }

    private void registerOsgiEventHandler() {
        eventSubscriber = osgiEvent -> {
            if (myDesktop == null || !myDesktop.isAlive()) return;
            try {
                Executions.schedule(myDesktop,
                    e -> vm.refreshCurrentView(),
                    new Event("onServerPushRefresh"));
            } catch (Exception ex) {
                log.log(Level.WARNING, "Kanban refresh schedule failed", ex);
            }
        };
        // postEvent (async) ensures handler runs on OSGi thread, not ZK request thread,
        // so Executions.schedule() correctly triggers server push for this desktop.
        EventManager.getInstance().register(RequestKanbanVM.TOPIC_KANBAN_REFRESH, eventSubscriber);
    }

    @Override
    public void onPageAttached(Page newpage, Page oldpage) {
        super.onPageAttached(newpage, oldpage);
        if (newpage != null && !newpage.getDesktop().isServerPushEnabled())
            newpage.getDesktop().enableServerPush(true);
    }

    @Override
    public void onPageDetached(Page page) {
        if (eventSubscriber != null) {
            EventManager.getInstance().unregister(eventSubscriber);
            eventSubscriber = null;
        }
        super.onPageDetached(page);
    }

    @Override
    public void valueChange(ValueChangeEvent evt) {
        if ("R_RequestType_ID".equals(evt.getPropertyName())) {
            if (evt.getNewValue() == null) return;
            int requestTypeId = ((Number) evt.getNewValue()).intValue();
            int salesRepId = vm.getSalesRepByRequestType(requestTypeId);
            if (fSalesRep != null) fSalesRep.setValue(salesRepId);
            if (fUpdateSalesRep != null) fUpdateSalesRep.setValue(salesRepId);
            int roleId = vm.getRoleByRequestType(requestTypeId);
            if (fRole != null && roleId > 0) fRole.setValue(roleId);
        }
    }

    // ── IFormController ───────────────────────────────────────────────────────

    @Override
    public ADForm getForm() { return this; }
}
