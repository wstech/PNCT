package com.portsamerica.navis.core.nola

import com.navis.argo.business.model.CarrierVisit
import com.navis.extension.model.persistence.DynamicHibernatingEntity
import com.navis.extension.model.persistence.IDynamicHibernatingEntity
import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.external.framework.util.EFieldChanges
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.inventory.business.imdg.HazardItem
import com.navis.inventory.business.imdg.Hazards
import com.navis.inventory.business.imdg.ImdgClass
import com.navis.orders.business.eqorders.AbstractBooking
import com.navis.orders.business.eqorders.EquipmentOrder
import com.navis.orders.business.eqorders.EquipmentOrderItem
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.jetbrains.annotations.Nullable

/**
 * Copyright 2017 Ports America.  All Rights Reserved.  This code contains the CONFIDENTIAL and PROPRIETARY information of Ports America.
 **/

/**
 * Version #: #BuildNumber#
 * Author: Gopalakrishnan Babu
 * Work Item #:
 * Called From: This is the transacted business function that will be called when data is submitted in the
 * Add / Edit Late Receive form. All business validation before saving the transaction like duplicate check, hazardous
 * comparison and quantity check are done in this class
 * Description:
  * History:
 * {Date}:{Author}:{WorkItem#}:{short issue/solution description}
 **/
public class PALateReceivalBookingCallback extends AbstractExtensionPersistenceCallback {
    @Override
    void execute(@Nullable Map inParams, @Nullable Map inOutResults) {
        EFieldChanges eFieldChanges = inParams.get("FIELD_CHANGES");
        List<Serializable> gkeys = inParams.get("CURRENT_GKEYS");
        log("Error :: " + inOutResults.get("ERROR"))
        boolean canUpdate = false;

        if (gkeys != null && gkeys.size() > 0) {
            IDynamicHibernatingEntity newDynamicEntity;
            for (int i = 0; i < gkeys.size(); i++) {
                canUpdate = false;
                log("Current Gkey :: " + gkeys.get(i))
                if (gkeys.get(i) == null) {
                    newDynamicEntity = new DynamicHibernatingEntity(CUSTOM_LATE_RECEIVAL_BOOKING);
                } else {
                    newDynamicEntity = HibernateApi.getInstance().load(CUSTOM_LATE_RECEIVAL_BOOKING, gkeys.get(i))
                }
                canUpdate = validateFieldChanges(eFieldChanges, newDynamicEntity, inOutResults);
                if (canUpdate) {
                    if (eFieldChanges.hasFieldChange(CUSTOM_LRB_MAX_COUNT)) {
                        newDynamicEntity.setFieldValue(CUSTOM_LRB_MAX_COUNT, (Long) eFieldChanges.findFieldChange(CUSTOM_LRB_MAX_COUNT).getNewValue());
                    }
                    if (eFieldChanges.hasFieldChange(CUSTOM_LRB_CUT_OFF_DATE)) {
                        newDynamicEntity.setFieldValue(CUSTOM_LRB_CUT_OFF_DATE, (Date) eFieldChanges.findFieldChange(CUSTOM_LRB_CUT_OFF_DATE).getNewValue());
                    }
                    if (eFieldChanges.hasFieldChange(CUSTOM_LRB_VESSEL_VISIT_DETAILS)) {
                        CarrierVisit carrierVisit = CarrierVisit.hydrate((Serializable) eFieldChanges.findFieldChange(CUSTOM_LRB_VESSEL_VISIT_DETAILS).getNewValue());
                        VesselVisitDetails vesselVisitDetails = VesselVisitDetails.resolveVvdFromCv(carrierVisit);
                        newDynamicEntity.setFieldValue(CUSTOM_LRB_VESSEL_VISIT_DETAILS, eFieldChanges.findFieldChange(CUSTOM_LRB_VESSEL_VISIT_DETAILS).getNewValue());
                    }
                    if (eFieldChanges.hasFieldChange(CUSTOM_LRB_BOOKING_ORDER)) {
                        newDynamicEntity.setFieldValue(CUSTOM_LRB_BOOKING_ORDER, eFieldChanges.findFieldChange(CUSTOM_LRB_BOOKING_ORDER).getNewValue());
                    }
                    if (eFieldChanges.hasFieldChange(CUSTOM_LRB_EQO_ORDER_ITEM)) {
                        newDynamicEntity.setFieldValue(CUSTOM_LRB_EQO_ORDER_ITEM, eFieldChanges.findFieldChange(CUSTOM_LRB_EQO_ORDER_ITEM).getNewValue())
                    }
                    if (eFieldChanges.hasFieldChange(CUSTOM_LRB_LINE_OPERATOR)) {
                        newDynamicEntity.setFieldValue(CUSTOM_LRB_LINE_OPERATOR, eFieldChanges.findFieldChange(CUSTOM_LRB_LINE_OPERATOR).getNewValue())
                    }
                    if (eFieldChanges.hasFieldChange(CUSTOM_LRB_HAZARDS)) {
                        newDynamicEntity.setFieldValue(CUSTOM_LRB_HAZARDS, eFieldChanges.findFieldChange(CUSTOM_LRB_HAZARDS).getNewValue())
                    }
                    if (eFieldChanges.hasFieldChange(CUSTOM_LAB_OOG)) {
                        newDynamicEntity.setFieldValue(CUSTOM_LAB_OOG, (Boolean) eFieldChanges.findFieldChange(CUSTOM_LAB_OOG).getNewValue())
                    }
                    if (isNotDuplicate(newDynamicEntity, inOutResults)) {
                        HibernateApi.getInstance().save(newDynamicEntity);
                    }
                }
            }
            HibernateApi.getInstance().flush();
        } /*else {
            //getMessageCollector().registerExceptions(BizViolation.create(PropertyKeyFactory.valueOf("lab.max_count_error"), null, "Max Count is greater than the Booking Item Quantity"));
            inOutResults.put("ERROR", "Max Count is greater than the Booking Item Quantity")

        }*/
    }

    private boolean validateFieldChanges(EFieldChanges eFieldChanges, IDynamicHibernatingEntity dynamicHibernatingEntity, Map inOutResults) {
        log("Inside validate method :: ")
        boolean isValid = Boolean.TRUE;

        EquipmentOrderItem equipmentOrderItem = null;
        if (eFieldChanges.hasFieldChange(CUSTOM_LRB_EQO_ORDER_ITEM)) {
            equipmentOrderItem = HibernateApi.getInstance().get(EquipmentOrderItem.class, (Serializable) eFieldChanges.findFieldChange(CUSTOM_LRB_EQO_ORDER_ITEM).getNewValue())
        } else if (dynamicHibernatingEntity != null && dynamicHibernatingEntity.getField(CUSTOM_LRB_EQO_ORDER_ITEM) != null) {
            equipmentOrderItem = (EquipmentOrderItem) dynamicHibernatingEntity.getField(CUSTOM_LRB_EQO_ORDER_ITEM);
        }

        Object paVesselVisitRulesLibrary = getLibrary("PAVesselVisitRulesLibrary");
        if (paVesselVisitRulesLibrary != null) {
            CarrierVisit carrierVisit = null;
            if (eFieldChanges.hasFieldChange(CUSTOM_LRB_VESSEL_VISIT_DETAILS)) {
                carrierVisit = CarrierVisit.hydrate((Serializable) eFieldChanges.findFieldChange(CUSTOM_LRB_VESSEL_VISIT_DETAILS).getNewValue());
            } else if (dynamicHibernatingEntity != null && dynamicHibernatingEntity.getFieldValue(CUSTOM_LRB_VESSEL_VISIT_DETAILS)) {
                carrierVisit = CarrierVisit.hydrate((Serializable) dynamicHibernatingEntity.getFieldValue(CUSTOM_LRB_VESSEL_VISIT_DETAILS));
            }
            log("Inside validate method to check carrier visit phase " + carrierVisit);
            if (carrierVisit != null) {
                VesselVisitDetails vesselVisitDetails = VesselVisitDetails.resolveVvdFromCv(carrierVisit);
                if (vesselVisitDetails != null) {
                    log("Inside validate method :: carrier visit phase " + vesselVisitDetails.getVvdVisitPhase());
                    if (!paVesselVisitRulesLibrary.allowBookingLateReceivals(getUserContext(), vesselVisitDetails, Boolean.TRUE)) {
                        inOutResults.put("ERROR", "User not allowed to create or update Late receive for this vessel");
                        isValid = Boolean.FALSE;
                    }
                    Date lrbCutoff;
                    if (eFieldChanges.hasFieldChange(CUSTOM_LRB_CUT_OFF_DATE)) {
                        lrbCutoff = eFieldChanges.findFieldChange(CUSTOM_LRB_CUT_OFF_DATE).getNewValue();
                    } else {
                        lrbCutoff = dynamicHibernatingEntity.getFieldValue(CUSTOM_LRB_CUT_OFF_DATE);
                    }
                    if (lrbCutoff==null) {
                        inOutResults.put("ERROR", "LRB Cutoff date should be provided");
                        isValid = Boolean.FALSE;
                    } else {
                        if (vesselVisitDetails.getVvdTimeHazCutoff() == null && vesselVisitDetails.getVvdTimeCargoCutoff() == null && vesselVisitDetails.getVvdTimeReeferCutoff()==null) {
                            inOutResults.put("ERROR", "Vessel visit does not have any cut-off, LRB cannot be created");
                            isValid = Boolean.FALSE;
                        } else if (equipmentOrderItem.isHazardous() && (vesselVisitDetails.getVvdTimeHazCutoff() != null && lrbCutoff.before(vesselVisitDetails.getVvdTimeHazCutoff()))) {
                            inOutResults.put("ERROR", "LRB cutoff cannot be before Vessel visit Hazardous cutoff");
                            isValid = Boolean.FALSE;
                        } else if (equipmentOrderItem.getEqoiTempRequired() != null &&
                                (vesselVisitDetails.getVvdTimeReeferCutoff() != null && lrbCutoff.before(vesselVisitDetails.getVvdTimeReeferCutoff()))) {
                            inOutResults.put("ERROR", "LRB cutoff cannot be before Vessel visit Reefer cutoff");
                            isValid = Boolean.FALSE;
                        } else if (vesselVisitDetails.getVvdTimeCargoCutoff() != null && lrbCutoff.before(vesselVisitDetails.getVvdTimeCargoCutoff())) {
                            inOutResults.put("ERROR", "LRB cutoff cannot be before Vessel visit cargo cutoff");
                            isValid = Boolean.FALSE;
                        }
                    }
                }
            }
        }

        EquipmentOrder equipmentOrder = null;
        if (eFieldChanges.hasFieldChange(CUSTOM_LRB_BOOKING_ORDER)) {
            equipmentOrder = HibernateApi.getInstance().get(EquipmentOrder.class, (Serializable) eFieldChanges.findFieldChange(CUSTOM_LRB_BOOKING_ORDER).getNewValue())
        } else if (dynamicHibernatingEntity != null && dynamicHibernatingEntity.getField(CUSTOM_LRB_BOOKING_ORDER) != null) {
            equipmentOrder = (EquipmentOrder) dynamicHibernatingEntity.getField(CUSTOM_LRB_BOOKING_ORDER);
        }
        //validate quantity set in LAB with quantity of order item
        if (eFieldChanges.hasFieldChange(CUSTOM_LRB_MAX_COUNT)) {
            Long maxLABAllowedCout = (Long) eFieldChanges.findFieldChange(CUSTOM_LRB_MAX_COUNT).getNewValue();
            log("Inside validate method :: max count :: $maxLABAllowedCout");
            if (equipmentOrderItem != null) {
                if (equipmentOrderItem.getEqoiQty() != null && equipmentOrderItem.getEqoiQty().compareTo(maxLABAllowedCout + equipmentOrderItem.getEqoiTally()) < 0) {
                    inOutResults.put("ERROR", "Max Count is greater than the Booking Item Quantity");
                    isValid = Boolean.FALSE;
                }
            }
        }
        String lrbHaz = null;
        if (eFieldChanges.hasFieldChange(CUSTOM_LRB_HAZARDS)) {
            lrbHaz = (String) eFieldChanges.findFieldChange(CUSTOM_LRB_HAZARDS).getNewValue();
        } else {
            lrbHaz = (String) dynamicHibernatingEntity.getFieldValue(CUSTOM_LRB_HAZARDS);
        }
        if (!isHazardValid(lrbHaz,inOutResults)) {
            isValid = Boolean.FALSE;
        }
        return isValid;
    }

    //Validate LAB hazards against booking / item hazards
    private boolean isHazardValid(String inHaz, Map inOutResults) {
        Boolean isValidHazard = Boolean.TRUE;
            if (inHaz != null && inHaz.length() > 0) {
                StringTokenizer stringTokenizer = new StringTokenizer(inHaz, ",");
                while (stringTokenizer.hasMoreTokens()) {
                    String lrbHaz = stringTokenizer.nextToken();
                    if (lrbHaz != null && lrbHaz.length() > 0) {
                        if (ImdgClass.getEnum(lrbHaz.trim()) == null) {
                            isValidHazard = Boolean.FALSE;
                            inOutResults.put("ERROR", lrbHaz + " is not a valid imdg class");
                        }
                    }
                }
            }
        return isValidHazard;
    }

    //check for duplicate LAB
    private boolean isNotDuplicate(IDynamicHibernatingEntity newDynamicEntity, Map inOutResults) {
        Boolean notDuplicate = Boolean.TRUE;
        if (newDynamicEntity != null && newDynamicEntity.getPrimaryKey() == null) {
            DomainQuery dq = QueryUtils.createDomainQuery(CUSTOM_LATE_RECEIVAL_BOOKING)
                    .addDqPredicate(PredicateFactory.eq(CUSTOM_LRB_EQO_ORDER_ITEM, (Serializable) newDynamicEntity.getFieldValue(CUSTOM_LRB_EQO_ORDER_ITEM)))
                    .addDqPredicate(PredicateFactory.eq(CUSTOM_LRB_VESSEL_VISIT_DETAILS, (Serializable) newDynamicEntity.getFieldValue(CUSTOM_LRB_VESSEL_VISIT_DETAILS)));
            if (newDynamicEntity.getFieldValue(CUSTOM_LRB_HAZARDS)!=null) {
                dq.addDqPredicate(PredicateFactory.isNotNull(CUSTOM_LRB_HAZARDS));
            } else {
                dq.addDqPredicate(PredicateFactory.isNull(CUSTOM_LRB_HAZARDS));
            }
            dq.setScopingEnabled(Boolean.FALSE);
            int bkgItemCount = HibernateApi.getInstance().findCountByDomainQuery(dq);
            if (bkgItemCount > 0) {
                inOutResults.put("ERROR", "Late Receive already exists for this booking item and vessel visit");
                notDuplicate = Boolean.FALSE;
            }
        }
        return notDuplicate;
    }

    public static String CUSTOM_LATE_RECEIVAL_BOOKING = "com.portsamerica.navis.core.CustomPALateReceivalBooking";
    public static MetafieldId CUSTOM_LRB_MAX_COUNT = MetafieldIdFactory.valueOf("customEntityFields.customlrbMaxCount");
    public static MetafieldId CUSTOM_LRB_CUT_OFF_DATE = MetafieldIdFactory.valueOf("customEntityFields.customlrbCutoffDate");
    public static MetafieldId CUSTOM_LRB_VESSEL_VISIT_DETAILS = MetafieldIdFactory.valueOf("customEntityFields.customlrbVesselVisit");
    public
    static MetafieldId CUSTOM_LRB_BOOKING_ORDER = MetafieldIdFactory.valueOf("customEntityFields.customlrbBookingOrder");
    public
    static MetafieldId CUSTOM_LRB_EQO_ORDER_ITEM = MetafieldIdFactory.valueOf("customEntityFields.customlrbEqOrderItem");
    public
    static MetafieldId CUSTOM_LRB_LINE_OPERATOR = MetafieldIdFactory.valueOf("customEntityFields.customlrbLineOperator");
    public static MetafieldId CUSTOM_LRB_HAZARDS = MetafieldIdFactory.valueOf("customEntityFields.customlrbHazards");
    public static final MetafieldId CUSTOM_LAB_OOG = MetafieldIdFactory.valueOf("customEntityFields.customlrbIsOOG");
}
