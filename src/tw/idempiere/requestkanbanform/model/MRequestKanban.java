package tw.idempiere.requestkanbanform.model;

import java.sql.ResultSet;
import java.util.Properties;
import org.compiere.model.MRequest;
import org.compiere.util.DB;

/**
 * Custom Model for R_Request.
 * Implements specific EndTime persistence logic.
 */
public class MRequestKanban extends MRequest {

    private static final long serialVersionUID = 1L;
    private static final String END_TIME_ATTR = "Ninniku_OriginalEndTime";

    public MRequestKanban(Properties ctx, int R_Request_ID, String trxName) {
        super(ctx, R_Request_ID, trxName);
    }

    public MRequestKanban(Properties ctx, ResultSet rs, String trxName) {
        super(ctx, rs, trxName);
    }

    @Override
    protected boolean beforeSave(boolean newRecord) {
        // Record EndTime in a PO Attribute (in-memory storage)
        Object endTime = get_Value("EndTime");
        if (endTime != null) {
            set_Attribute(END_TIME_ATTR, endTime);
        }
        return super.beforeSave(newRecord);
    }

    @Override
    protected boolean afterSave(boolean newRecord, boolean success) {
        boolean ok = super.afterSave(newRecord, success);
        if (success) {
            Object stashedEndTime = get_Attribute(END_TIME_ATTR);
            if (stashedEndTime != null) {
                // Direct SQL to avoid re-entering the PO save lifecycle (infinite loop)
                DB.executeUpdateEx(
                    "UPDATE R_Request SET EndTime=? WHERE R_Request_ID=?",
                    new Object[]{stashedEndTime, getR_Request_ID()},
                    get_TrxName()
                );
                set_ValueNoCheck("EndTime", stashedEndTime);
            }
        }
        return ok;
    }
}
