package tw.idempiere.requestkanbanform.factory;

import java.sql.ResultSet;
import org.adempiere.base.IModelFactory;
import org.compiere.model.PO;
import org.compiere.util.Env;
import tw.idempiere.requestkanbanform.model.MRequestKanban;

/**
 * Model Factory for Request Kanban plugin.
 * Returns custom MRequestKanban for R_Request table.
 */
public class RequestKanbanModelFactory implements IModelFactory {

    @Override
    public Class<?> getClass(String tableName) {
        if ("R_Request".equalsIgnoreCase(tableName))
            return MRequestKanban.class;
        return null;
    }

    @Override
    public PO getPO(String tableName, int Record_ID, String trxName) {
        if ("R_Request".equalsIgnoreCase(tableName))
            return new MRequestKanban(Env.getCtx(), Record_ID, trxName);
        return null;
    }

    @Override
    public PO getPO(String tableName, ResultSet rs, String trxName) {
        if ("R_Request".equalsIgnoreCase(tableName))
            return new MRequestKanban(Env.getCtx(), rs, trxName);
        return null;
    }
}
