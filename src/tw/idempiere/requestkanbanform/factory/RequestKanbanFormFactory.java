/*
 * Copyright (C) 2026 Ray Lee / TopGiga
 * SPDX-License-Identifier: GPL-2.0-only
 */
package tw.idempiere.requestkanbanform.factory;

import org.adempiere.webui.factory.IFormFactory;
import org.adempiere.webui.panel.ADForm;
import tw.idempiere.requestkanbanform.form.RequestKanbanForm;

/**
 * OSGi IFormFactory service — instantiates RequestKanbanForm.
 * Registered via OSGI-INF/requestkanban_form_factory.xml.
 */
public class RequestKanbanFormFactory implements IFormFactory {

    @Override
    public ADForm newFormInstance(String formName) {
        if (RequestKanbanForm.class.getName().equals(formName))
            return new RequestKanbanForm();
        return null;
    }
}
