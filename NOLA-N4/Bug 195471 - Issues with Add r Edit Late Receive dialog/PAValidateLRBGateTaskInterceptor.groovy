import com.navis.argo.business.atoms.EquipIsoGroupEnum
import com.navis.argo.business.atoms.EquipNominalHeightEnum
import com.navis.argo.business.atoms.EquipNominalLengthEnum
import com.navis.argo.business.reference.EquipType
import com.navis.extension.model.persistence.DynamicHibernatingEntity
import com.navis.extension.model.persistence.IDynamicHibernatingEntity
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.external.road.EGateTaskInterceptor
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.presentation.internationalization.MessageTranslator
import com.navis.framework.util.BizViolation
import com.navis.framework.util.internationalization.TranslationUtils
import com.navis.framework.util.time.TimeUtils
import com.navis.inventory.InventoryEntity
import com.navis.inventory.InventoryField
import com.navis.orders.business.eqorders.EquipmentOrderItem
import com.navis.road.RoadPropertyKeys
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.util.RoadBizUtil
import com.navis.road.business.workflow.TransactionAndVisitHolder
import com.navis.vessel.VesselPropertyKeys
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.log4j.Level
import org.apache.log4j.Logger


/**
 * Copyright 2017 Ports America.  All Rights Reserved.  This code contains the CONFIDENTIAL and PROPRIETARY information of Ports America.
 **/

/**
 * Version #: #BuildNumber#
 * Author: Gopalakrishnan Babu
 * Work Item #:
 * Called From: This code extension is called from RejectCarrierVisitPastCutoff business task.
 * It will first check for vessel visit cutoff, if it already past cutoff then the LRB record will be verified
 * If no LRB, then the standard error based on default biz task will be raised If LRB found and it is past cutoff, then
 * an error based on LRB date will be raised. If the LRB is valid and cutoff date is not past, then the LRB will be linked
 * to the UFV (flexString05).
 * Description:
 * History:
 * {Date}:{Author}:{WorkItem#}:{short issue/solution description}
 **/


public class PAValidateLRBGateTaskInterceptor extends AbstractGateTaskInterceptor implements EGateTaskInterceptor {

    private Logger LOGGER = Logger.getLogger(PAValidateLRBGateTaskInterceptor.class);


    public void execute(TransactionAndVisitHolder inDao) {
        LOGGER.setLevel(Level.DEBUG);
        LOGGER.debug("PAValidateLRBGateTaskInterceptor: BEGIN");

        TruckTransaction thisTran = inDao.getTran();
        Date apptDate = null;
        boolean canExecuteBizTask = true;
        boolean isFromAppt = false;
        try {
            if (inDao.getAppt() != null) {
				isFromAppt = true;
                apptDate = inDao.getAppt() != null ? inDao.getAppt().getGapptRequestedDate() : null;
            }

            if (thisTran.getTranCarrierVisit() != null) {
                thisTran.getTranCarrierVisit().validatePastCutOff(thisTran.getTranLine(), false, false, apptDate)
            }
            //validateLateReceivalBookingForEqoItem(inDao)
        } catch (BizViolation inPastCutoffViolation) {
            canExecuteBizTask = false;
            validateLateReceivalBookingForEqoItem(inDao, inPastCutoffViolation, isFromAppt)
        }

        if (canExecuteBizTask) {
            executeInternal(inDao)
        }
        LOGGER.debug("PAValidateLRBGateTaskInterceptor: END");
    }

    private void validateLateReceivalBookingForEqoItem(TransactionAndVisitHolder inDao, BizViolation inPastCutoffViolation, boolean isFromAppt) {
        LOGGER.setLevel(Level.DEBUG);
        LOGGER.debug("Inside the calculation for validation method :: ")
        TruckTransaction thisTran = inDao.getTran();
        VesselVisitDetails vesselVisitDetails;
        if (thisTran.getTranCarrierVisit() != null) {
            vesselVisitDetails = VesselVisitDetails.resolveVvdFromCv(thisTran.getTranCarrierVisit());
        }
        boolean shouldRaiseViolation = Boolean.TRUE;
        Long oiGkey = 0;
		Date currentTime = new Date(TimeUtils.getCurrentTimeMillis());
        if (isFromAppt) {
            oiGkey = GetEqoOrderItemGkey(inDao)
			currentTime =  inDao.getAppt() != null ? inDao.getAppt().getGapptRequestedDate() : null;
        } else {
            oiGkey = thisTran.getTranEqoItem().getEqboiGkey()
        }
        LOGGER.debug("Inside the calculation for validation method :: " + vesselVisitDetails);
        if (vesselVisitDetails != null && thisTran.getTranEqo() != null && thisTran.getTranEqoItem() != null && oiGkey > 0) {
            IDynamicHibernatingEntity newDynamicEntity = new DynamicHibernatingEntity(CURRENT_DYNAMIC_ENTITY);
            DomainQuery dq = QueryUtils.createDomainQuery(CURRENT_DYNAMIC_ENTITY)
                    .addDqPredicate(PredicateFactory.eq(CUSTOM_LRB_VESSEL_VISIT, thisTran.getTranCarrierVisit().getCvGkey()))
                    .addDqPredicate(PredicateFactory.eq(CUSTOM_LRB_BOOKING_ORDER, thisTran.getTranEqo().getEqboGkey()))
                    .addDqPredicate(PredicateFactory.eq(CUSTOM_LRB_EQO_ORDER_ITEM, oiGkey));
            if (thisTran.getTranIsHazard()) {
                dq.addDqPredicate(PredicateFactory.isNotNull(CUSTOM_LRB_HAZARDS));
            } else {
                dq.addDqPredicate(PredicateFactory.isNull(CUSTOM_LRB_HAZARDS));
            }
            dq.setScopingEnabled(Boolean.FALSE);
            List deList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            LOGGER.debug("current list :: " + deList.size())

            if (deList != null && !deList.isEmpty()) {
                shouldRaiseViolation = Boolean.FALSE;
                LOGGER.debug("Inside setting the value for Entity :: ")
                newDynamicEntity = (IDynamicHibernatingEntity) deList.get(0);
                Date lrbCutoff = (Date) newDynamicEntity.getFieldValue(CUSTOM_LRB_CUT_OFF);

                if (lrbCutoff != null && lrbCutoff.before(currentTime)) {
                    RoadBizUtil.appendExceptionChain(BizViolation.create(VesselPropertyKeys.PAST_CARGO_CUTOFF, null, lrbCutoff));
                } else {
                    if (!isLateReceiveQtyAllowed(newDynamicEntity)) {
                        RoadBizUtil.appendExceptionChain(BizViolation.create(RoadPropertyKeys.GATE__USER_MESSAGE_3, null, newDynamicEntity.getFieldValue(CUSTOM_LRB_MAX_COUNT)));
                    } else {
                        thisTran.setTranUfvFlexString05(String.valueOf(newDynamicEntity.getFieldValue(CUSTOM_LRB_GKEY)));
                    }
                }
            }
        }
        if (shouldRaiseViolation) {
            RoadBizUtil.appendExceptionChain(inPastCutoffViolation);
        }
    }

    private Long GetEqoOrderItemGkey(TransactionAndVisitHolder inDao) {
        MessageTranslator messageTranslator = TranslationUtils.getTranslationContext(getUserContext()).getMessageTranslator();
        Set<EquipmentOrderItem> orderItems = inDao.getAppt().getGapptOrder().getEqboOrderItems();
        //If this is called from Appointment business rules, Appontment used Equivalents . In that case we need to check if LRB size type belongs to one of the
        //equivelants group.
        EquipNominalLengthEnum eqApptLength;
        EquipNominalHeightEnum eqApptHeight;
        String strApptLength;
        String strApptHeight;
        String strApptType;
        if (inDao.getAppt() != null) {
            //Get Appointment size, type, height.
            TruckTransaction inTT = inDao.getTran();
            EquipType tranIsoCode = inTT.getTranEquipType();
            eqApptLength = tranIsoCode.getEqtypNominalLength()
            eqApptHeight = tranIsoCode.getEqtypNominalHeight()
            EquipIsoGroupEnum eqType = tranIsoCode.getEqtypIsoGroup()
            strApptLength = eqApptLength == null ? "" : messageTranslator.getMessage(eqApptLength.getDescriptionPropertyKey())
            strApptHeight = eqApptHeight == null ? "" : messageTranslator.getMessage(eqApptHeight.getDescriptionPropertyKey())
            strApptType = eqType == null ? "" : messageTranslator.getMessage(eqType.getDescriptionPropertyKey())
            LOGGER.debug("PAValidateApptLRBGateTaskInterceptor Appointment size " + strApptLength + " " + strApptHeight + " " + strApptType)
        }

        Long orderGKey = 0;
        for (EquipmentOrderItem equipmentOrderItem : orderItems) {
            EquipIsoGroupEnum isoGroupEnum = equipmentOrderItem.getEqoiEqIsoGroup();
            EquipNominalHeightEnum enumheight = equipmentOrderItem.getEqoiEqHeight()
            EquipNominalLengthEnum enumLength = equipmentOrderItem.getEqoiEqSize();
            String height = enumheight == null ?
                    "" :
                    messageTranslator.getMessage(enumheight.getDescriptionPropertyKey())
            String length = enumLength == null ?
                    "" :
                    messageTranslator.getMessage(enumLength.getDescriptionPropertyKey())
            String type = isoGroupEnum == null ? "" : messageTranslator.getMessage(isoGroupEnum.getDescriptionPropertyKey())
            LOGGER.debug("PAValidateApptLRBGateTaskInterceptor height " + height + " " + length + " " + type)
            def eqEquivalintslib = getLibrary("NOLAGetEquipmenEquivalentsLib");

            if (eqApptLength == enumLength && eqApptHeight == enumheight && eqEquivalintslib.CheckEquivalents(length, height, type, strApptType)) {
                orderGKey = equipmentOrderItem.getEqboiGkey()
                break;
            }
        }

        return orderGKey;
    }
    //Method to check if all allowed containers have been already received

    private Boolean isLateReceiveQtyAllowed(IDynamicHibernatingEntity newDynamicEntity) {
        Boolean isQtyAllowed = Boolean.TRUE;
        if (newDynamicEntity != null) {
            DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT_FACILITY_VISIT)
                    .addDqPredicate(PredicateFactory.eq(InventoryField.UFV_FLEX_STRING05, newDynamicEntity.getPrimaryKey()));
            int count = HibernateApi.getInstance().findCountByDomainQuery(dq);
            if (count > 0 && count >= newDynamicEntity.getFieldValue(CUSTOM_LRB_MAX_COUNT)) {
                isQtyAllowed = Boolean.FALSE;
            }
        }
        return isQtyAllowed;
    }

    private String CURRENT_DYNAMIC_ENTITY = "com.portsamerica.navis.core.CustomPALateReceivalBooking";
    private static
    final MetafieldId CUSTOM_LRB_VESSEL_VISIT = MetafieldIdFactory.valueOf("customEntityFields.customlrbVesselVisit");
    private static
    final MetafieldId CUSTOM_LRB_BOOKING_ORDER = MetafieldIdFactory.valueOf("customEntityFields.customlrbBookingOrder");
    private static
    final MetafieldId CUSTOM_LRB_EQO_ORDER_ITEM = MetafieldIdFactory.valueOf("customEntityFields.customlrbEqOrderItem");
    private static
    final MetafieldId CUSTOM_LRB_CUT_OFF = MetafieldIdFactory.valueOf("customEntityFields.customlrbCutoffDate");
    public static final MetafieldId CUSTOM_LRB_GKEY = MetafieldIdFactory.valueOf("customEntityGkey");
    public static
    final MetafieldId CUSTOM_LRB_MAX_COUNT = MetafieldIdFactory.valueOf("customEntityFields.customlrbMaxCount");
    public static MetafieldId CUSTOM_LRB_HAZARDS = MetafieldIdFactory.valueOf("customEntityFields.customlrbHazards");
}
