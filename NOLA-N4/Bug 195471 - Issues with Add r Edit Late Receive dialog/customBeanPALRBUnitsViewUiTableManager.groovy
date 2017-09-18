package com.portsamerica.navis.core.nola

import com.navis.external.framework.beans.EBean
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.portal.query.DataQuery
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.presentation.command.VariformUiCommand
import com.navis.framework.presentation.view.DefaultSharedUiTableManager
import com.navis.inventory.business.api.UnitField;

/**
 * Copyright 2017 Ports America.  All Rights Reserved.  This code contains the CONFIDENTIAL and PROPRIETARY information of Ports America.
 **/

/**
 * Version #: #BuildNumber#
 * Author: Gopalakrishnan Babu
 * Work Item #:
 * Called From: This class is used to form the query related to the units that will be displayed in the
 * LRB Inspector
 * Description:
 * History:
 * {Date}:{Author}:{WorkItem#}:{short issue/solution description}
 **/

public class customBeanPALRBUnitsViewUiTableManager extends DefaultSharedUiTableManager implements EBean{
    @Override
    DataQuery createQuery() {
        DomainQuery domainQuery = (DomainQuery)super.createQuery();
        Object parent = getAttribute("parent");
        if ((parent != null) && ((parent instanceof VariformUiCommand))) {
            VariformUiCommand orderInspectorCommand = (VariformUiCommand)parent;
            List<Serializable> gkeys = (List)orderInspectorCommand.getAttribute("source");
            if ((gkeys != null) && (!gkeys.isEmpty())) {
                domainQuery.addDqPredicate(PredicateFactory.eq(MetafieldIdFactory.valueOf("ufvFlexString05"), String.valueOf(gkeys.get(0))));
            }
        }
        domainQuery.setScopingEnabled(Boolean.FALSE);
        return domainQuery;
    }

    @Override
    String getDetailedDiagnostics() {
        return "customBeanPALRBUnitsViewUiTableManager";
    }
}
