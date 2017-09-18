package com.portsamerica.navis.core.nola

import com.navis.argo.ArgoBizMetafield
import com.navis.argo.ArgoField
import com.navis.argo.ArgoRefField
import com.navis.argo.business.model.CarrierVisit
import com.navis.external.framework.beans.EBean
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.metafields.MetafieldIdList
import com.navis.framework.portal.BizResponse
import com.navis.framework.portal.CrudBizDelegate
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.presentation.FrameworkPresentationUtils
import com.navis.framework.presentation.context.PresentationContextUtils
import com.navis.framework.presentation.context.RequestContext
import com.navis.framework.presentation.lovs.Style
import com.navis.framework.presentation.lovs.key.IDomainQueryLovKey
import com.navis.framework.presentation.lovs.key.LovKeyFactory
import com.navis.framework.presentation.ui.FormController
import com.navis.framework.presentation.ui.ICarinaLovWidget
import com.navis.framework.presentation.ui.ICarinaWidget
import com.navis.framework.presentation.ui.event.CarinaFormValueEvent
import com.navis.framework.presentation.ui.event.listener.AbstractCarinaFormValueListener
import com.navis.framework.util.ValueObject
import com.navis.inventory.InvField
import com.navis.inventory.InventoryBizMetafield
import com.navis.inventory.InventoryField
import com.navis.inventory.business.imdg.HazardItem
import com.navis.inventory.business.imdg.Hazards
import com.navis.orders.OrdersField
import com.navis.orders.OrdersQueryUtils
import com.navis.orders.web.OrdersGuiMetafield
import com.navis.vessel.VesselField
import com.navis.vessel.business.schedule.VesselVisitDetails
import com.sun.org.apache.xpath.internal.operations.Bool
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * Copyright 2017 Ports America.  All Rights Reserved.  This code contains the CONFIDENTIAL and PROPRIETARY information of Ports America.
 **/

/**
 * Version #: #BuildNumber#
 * Author: Gopalakrishnan Babu
 * Work Item #:
 * Called From: LRB Form - Add or Edit options
 * Description: This controller is to display the data related to the LRB fields on the Add / Edit Late Receive by Booking form
 *  * History:
 * 9/15/2017: Gopalakrishan Babu : 195471:Make the order item selected if only one item exists for the booking
 **/

public class CustomBeanPALRBFormController extends FormController implements EBean {
    @Override
    public boolean initForm() {
        LOGGER.setLevel(Level.DEBUG);
        LOGGER.debug("Inside the init form method");
        return super.initForm();
    }

    @Override
    protected void configure() {
        LOGGER.setLevel(Level.DEBUG);
        super.configure();

        ICarinaLovWidget lineOPNewFormWidget = (ICarinaLovWidget) getFormWidget(CUSTOM_LRB_LINE_OPERATOR);
        IDomainQueryLovKey lovKey1 = LovKeyFactory.valueOf(getAllLineOperators());
        lovKey1.setStyle(Style.LABEL1_PAREN_LABEL2);
        lineOPNewFormWidget.setLovKey(lovKey1);

        final ICarinaWidget lineFormWidget = getFormWidget(CUSTOM_LRB_LINE_OPERATOR);
        if (lineFormWidget != null) {
            if (getEntityGkey() != null) {
                lineFormWidget.setEnabled(false);
            }
            //fetch all vessel visits for the specific line operator
            lineFormWidget.addFormValueListener(new AbstractCarinaFormValueListener() {
                @Override
                protected void safeValueChanged(CarinaFormValueEvent paramCarinaFormValueEvent) {
                    Serializable lineGkey = (Serializable) lineFormWidget.getValue();
                    //Serializable lineGkey = 1;
                    MetafieldId[] cvId = [ArgoBizMetafield.CV_CARRIER_ID_VEHICLE_NAME_AND_FACILITY]
                    DomainQuery dq = OrdersQueryUtils.getDomainQueryOnActiveVesVisitForSelectedLine(lineGkey, cvId, PresentationContextUtils.getRequestContext(), false);
                    IDomainQueryLovKey lovKey = LovKeyFactory.valueOf(dq);
                    lovKey.setStyle(Style.LABEL_ONLY);
                    ICarinaLovWidget vesselvisitFormWidget = (ICarinaLovWidget) CustomBeanPALRBFormController.this.getFormWidget(CUSTOM_LRB_VESSEL_VISIT);
                    if (vesselvisitFormWidget != null) {
                        vesselvisitFormWidget.setLovKey(lovKey);
                    }
                }
            });
        }
        final ICarinaWidget hazWidget = getFormWidget(CUSTOM_LRB_HAZARDS);
        if (hazWidget!= null) {
            hazWidget.setEnabled(Boolean.FALSE);
        }
        final ICarinaWidget vesselFormWidget = getFormWidget(CUSTOM_LRB_VESSEL_VISIT);

        if (vesselFormWidget != null) {
            //Mark the vessel visit related fields as disabled
            ICarinaWidget vvdCargoCutoffWidget = (ICarinaWidget) this.getFormWidget(CUSTOM_LRB_VESSEL_VISIT_CARGO_CUT_OFF);
            if (vvdCargoCutoffWidget != null) {
                vvdCargoCutoffWidget.setEnabled(false);
            }
            ICarinaWidget vvdHazCutoffWidget = (ICarinaWidget) this.getFormWidget(CUSTOM_LRB_VESSEL_VISIT_HAZ_CUT_OFF);
            if (vvdHazCutoffWidget != null) {
                vvdHazCutoffWidget.setEnabled(false);
            }
            ICarinaWidget vvdReeferCutoffWidget = (ICarinaWidget) this.getFormWidget(CUSTOM_LRB_VESSEL_VISIT_REEFER_CUT_OFF);
            if (vvdReeferCutoffWidget != null) {
                vvdReeferCutoffWidget.setEnabled(false);
            }
            if (getEntityGkey() != null) {
                vesselFormWidget.setEnabled(false);
            }
            //Once the vessel is selected fetch all bookings for that vessel
            vesselFormWidget.addFormValueListener(new AbstractCarinaFormValueListener() {
                @Override
                protected void safeValueChanged(CarinaFormValueEvent paramCarinaFormValueEvent) {
                    Serializable vesselGkey = (Serializable) vesselFormWidget.getValue();
                    ICarinaLovWidget bookingFormWidget = (ICarinaLovWidget) CustomBeanPALRBFormController.this.getFormWidget(CUSTOM_LRB_BOOKING_ORDER);
                    if (bookingFormWidget != null) {
                        bookingFormWidget.setLovKey(LovKeyFactory.valueOf(getEqoFromVesselVisit(vesselGkey)));
                    }
                    Object cvVisitPhase = FrameworkPresentationUtils.getEntityFieldValue("CarrierVisit", (Long) vesselFormWidget.getValue(), ArgoField.CV_VISIT_PHASE);
                    ICarinaWidget vesselVisitPhase = (ICarinaWidget) CustomBeanPALRBFormController.this.getFormWidget(CUSTOM_LRB_VESSEL_VISIT_PHASE);
                    if (vesselVisitPhase != null) {
                        vesselVisitPhase.setValue(cvVisitPhase);
                        vesselVisitPhase.setEnabled(false);
                    }

                    Object visitDetailsObj = FrameworkPresentationUtils.getEntityFieldValue("CarrierVisit", (Long) vesselFormWidget.getValue(), ArgoField.CV_CVD);
                    Object vvdTimeCargoCutoff = FrameworkPresentationUtils.getEntityFieldValue("VesselVisitDetails", visitDetailsObj, VesselField.VVD_TIME_CARGO_CUTOFF);
                    ICarinaWidget vvdTimeCargoCutoffWidget = (ICarinaWidget) CustomBeanPALRBFormController.this.getFormWidget(CUSTOM_LRB_VESSEL_VISIT_CARGO_CUT_OFF);
                    if (vvdTimeCargoCutoffWidget != null) {
                        vvdTimeCargoCutoffWidget.setValue(vvdTimeCargoCutoff);
                    }

                    Object vvdTimeHazCutoff = FrameworkPresentationUtils.getEntityFieldValue("VesselVisitDetails", visitDetailsObj, VesselField.VVD_TIME_HAZ_CUTOFF);
                    ICarinaWidget vvdTimeHazCutoffWidget = (ICarinaWidget) CustomBeanPALRBFormController.this.getFormWidget(CUSTOM_LRB_VESSEL_VISIT_HAZ_CUT_OFF);
                    if (vvdTimeHazCutoffWidget != null) {
                        vvdTimeHazCutoffWidget.setValue(vvdTimeHazCutoff);
                    }

                    Object vvdTimeReeferCutoff = FrameworkPresentationUtils.getEntityFieldValue("VesselVisitDetails", visitDetailsObj, VesselField.VVD_TIME_REEFER_CUTOFF);
                    ICarinaWidget vvdTimeReeferCutoffWidget = (ICarinaWidget) CustomBeanPALRBFormController.this.getFormWidget(CUSTOM_LRB_VESSEL_VISIT_REEFER_CUT_OFF);
                    if (vvdTimeReeferCutoffWidget != null) {
                        vvdTimeReeferCutoffWidget.setValue(vvdTimeReeferCutoff);
                    }
                }
            });
        }

        final ICarinaWidget bookingWidget = getFormWidget(CUSTOM_LRB_BOOKING_ORDER);
        if (bookingWidget != null) {
            if (getEntityGkey() != null) {
                bookingWidget.setEnabled(false);
            }
            //Mark the booking related fields as disabled
            ICarinaWidget bkgPodWidget = (ICarinaWidget) getFormWidget(CUSTOM_LRB_BOOKING_ORDER_POD);
            if (bkgPodWidget != null) {
                bkgPodWidget.setEnabled(Boolean.FALSE);
            }
            ICarinaWidget oogWidget = (ICarinaWidget) getFormWidget(CUSTOM_LAB_OOG);
            if (oogWidget != null) {
                oogWidget.setEnabled(Boolean.FALSE);
            }
            bookingWidget.addFormValueListener(new AbstractCarinaFormValueListener() {
                @Override
                protected void safeValueChanged(CarinaFormValueEvent paramCarinaFormValueEvent) {
                    if (bookingWidget.getValue() != null) {
                        ICarinaLovWidget eqOrderItemWidget = (ICarinaLovWidget) CustomBeanPALRBFormController.this.getFormWidget(CUSTOM_LRB_EQO_ORDER_ITEM)
                        Object bkgOod = FrameworkPresentationUtils.getEntityFieldValue("Booking", (Long) bookingWidget.getValue(), OrdersField.EQO_OOD);
                        if (oogWidget != null && Boolean.TRUE.equals(bkgOod)) {
                            oogWidget.setValue(bkgOod);
                            oogWidget.setEnabled(Boolean.TRUE);
                        }
                        Object bkgPod = FrameworkPresentationUtils.getEntityFieldValue("Booking", (Long) bookingWidget.getValue(), OrdersField.EQO_POD1);
                        if (bkgPodWidget != null) {
                            bkgPodWidget.setValue(bkgPod);
                        }
                        if (eqOrderItemWidget != null) {
                            IDomainQueryLovKey eqOrderLov = LovKeyFactory.valueOf(getEqOrderItemFromBooking(bookingWidget.getValue()));
                            eqOrderLov.setStyle(Style.LABEL1_PAREN_LABEL2)
                            eqOrderItemWidget.setLovKey(eqOrderLov);
                            Boolean isSelected = eqOrderItemWidget.selectSingleChoiceElement();
                            if (isSelected){
                                ICarinaWidget lrbMaxCountWidget = (ICarinaWidget) CustomBeanPALRBFormController.this.getFormWidget(CUSTOM_LRB_MAX_COUNT)
                                if (lrbMaxCountWidget!=null) {
                                    lrbMaxCountWidget.requestFocus();
                                }
                            } else {
                                eqOrderItemWidget.requestFocus();
                            }
                        }

                    }
                }
            });
        }

        final ICarinaWidget eqOrderItemWidget = getFormWidget(CUSTOM_LRB_EQO_ORDER_ITEM);
        if (eqOrderItemWidget != null) {
            if (getEntityGkey() != null) {
                eqOrderItemWidget.setEnabled(false);
            }
            ICarinaWidget bkgItemQtyWidget = (ICarinaWidget) getFormWidget(CUSTOM_LRB_BOOKING_ITEM_QTY);
            if (bkgItemQtyWidget != null) {
                bkgItemQtyWidget.setEnabled(Boolean.FALSE);
            }
            ICarinaWidget bkgItemMaxAllowedWidget = (ICarinaWidget) getFormWidget(CUSTOM_LRB_BOOKING_ITEM_MAX_ALLOWED);
            if (bkgItemMaxAllowedWidget!=null) {
                bkgItemMaxAllowedWidget.setEnabled(Boolean.FALSE);
            }
            eqOrderItemWidget.addFormValueListener(new AbstractCarinaFormValueListener() {
                @Override
                protected void safeValueChanged(CarinaFormValueEvent paramCarinaFormValueEvent) {
                    if (eqOrderItemWidget.getValue() != null) {
                        Object bkgItemQty = FrameworkPresentationUtils.getEntityFieldValue("EquipmentOrderItem", (Long) eqOrderItemWidget.getValue(), OrdersField.EQOI_QTY);
                        if (bkgItemQtyWidget != null) {
                            bkgItemQtyWidget.setValue(bkgItemQty);
                        }
                        Object bkgItemTally = FrameworkPresentationUtils.getEntityFieldValue("EquipmentOrderItem", (Long) eqOrderItemWidget.getValue(), OrdersField.EQOI_TALLY);
                        if (bkgItemMaxAllowedWidget!=null) {
                            bkgItemMaxAllowedWidget.setValue(bkgItemQty-bkgItemTally);
                        }
                        Object bkgItemHaz = FrameworkPresentationUtils.getEntityFieldValue("EquipmentOrderItem", (Long) eqOrderItemWidget.getValue(), OrdersField.EQOI_HAZARDS);
                        Object bkgHaz = FrameworkPresentationUtils.getEntityFieldValue("EquipmentOrder", (Long) bookingWidget.getValue(), OrdersField.EQO_HAZARDS);
                        if (bkgItemHaz != null || bkgHaz != null) {
                            bkgItemHaz = bkgItemHaz != null ? bkgItemHaz : bkgHaz;
                            String hazClasses = getHazardClass(bkgItemHaz);
                            hazWidget.setValue(hazClasses);
                            hazWidget.setEnabled(Boolean.TRUE);
                            //hazWidget.requestFocus();
                        }
                    }
                }
            });
        }
    }

    private DomainQuery getEqoFromVesselVisit(Serializable inVesselGkey) {
        return QueryUtils.createDomainQuery("EquipmentOrder").addDqField(InvField.EQBO_NBR).addDqPredicate(PredicateFactory.eq(OrdersField.EQO_VESSEL_VISIT, inVesselGkey));
    }

    private DomainQuery getAllLineOperators() {
        MetafieldIdList desiredList = new MetafieldIdList([ArgoRefField.BZU_ID, ArgoRefField.BZU_NAME]);
        return QueryUtils.createDomainQuery("LineOperator").addDqFields(desiredList);
    }

    private DomainQuery getEqOrderItemFromBooking(Serializable inEqoGkey) {
        return QueryUtils.createDomainQuery("EquipmentOrderItem").addDqField(OrdersField.EQOI_EQ_SIZE).addDqField(OrdersField.EQOI_EQ_HEIGHT).addDqField(OrdersField.EQOI_EQ_ISO_GROUP).addDqPredicate(PredicateFactory.eq(InventoryField.EQBOI_ORDER, inEqoGkey));
    }

    private DomainQuery getHazardItemDQ(Serializable inHazardGkey) {
        return QueryUtils.createDomainQuery("HazardItem").addDqPredicate(PredicateFactory.eq(InvField.HZRDI_HAZARDS, inHazardGkey)).addDqField(InvField.HZRDI_IMDG_CLASS).addDqField(InvField.HZRDI_U_NNUM);
    }

    private DomainQuery getVVVisitPhase(Serializable inVesselGkey) {
        return QueryUtils.createDomainQuery("VesselVisitDetails").addDqPredicate(PredicateFactory.eq(ArgoField.CVD_GKEY, inVesselGkey)).addDqField(ArgoField.CV_VISIT_PHASE);
    }

    private String getHazardClass(Long inHazGkey) {
        StringBuffer hazClasses = new StringBuffer();
        DomainQuery dq = QueryUtils.createDomainQuery("Hazards").addDqPredicate(PredicateFactory.eq(InventoryField.HZRD_GKEY, inHazGkey)).addDqField(InventoryField.HZRD_ITEMS);
        RequestContext context = PresentationContextUtils.getRequestContext();
        CrudBizDelegate crudDelegate = (CrudBizDelegate) context.getBean("crudBizDelegate");
        BizResponse bizResponse = crudDelegate.processQuery(context, dq);
        ValueObject valueObject = bizResponse.getValueObject("Hazards");
        if (valueObject != null) {
            Object hazItems = valueObject.getFieldValue(InventoryField.HZRD_ITEMS);
            if (hazItems != null) {
                Iterator iterator = hazItems.iterator();
                while (iterator.hasNext()) {
                    Long hazItemGkey = (Long) iterator.next();
                    if (hazItemGkey != null) {
                        Object hazClass = FrameworkPresentationUtils.getEntityFieldValue("HazardItem", hazItemGkey, InventoryField.HZRDI_IMDG_CLASS);
                        if (hazClass != null) {
                            if (hazClasses != null && hazClasses.length() > 0) {
                                hazClasses.append(",");
                            }
                            hazClasses.append(hazClass.getKey());
                        }
                    }
                }
            } else {
                hazClasses.append("Cannot get haz");
            }
        } else {
            hazClasses.append("Haz not found");
        }
        LOGGER.warn("haz class "+hazClasses.toString());
        return hazClasses.toString();
    }

    public Serializable getEntityGkey() {
        List<Serializable> source = (List) getAttribute("source");
        return (Serializable) source.get(0);
    }

    private static final MetafieldId CUSTOM_LRB_VESSEL_VISIT = MetafieldIdFactory.valueOf("customEntityFields.customlrbVesselVisit");
    private static final MetafieldId CUSTOM_LRB_BOOKING_ORDER = MetafieldIdFactory.valueOf("customEntityFields.customlrbBookingOrder");
    private static final MetafieldId CUSTOM_LRB_EQO_ORDER_ITEM = MetafieldIdFactory.valueOf("customEntityFields.customlrbEqOrderItem");
    private static final MetafieldId CUSTOM_LRB_HAZARDS = MetafieldIdFactory.valueOf("customEntityFields.customlrbHazards");
    public static MetafieldId CUSTOM_LRB_LINE_OPERATOR = MetafieldIdFactory.valueOf("customEntityFields.customlrbLineOperator");
    public static MetafieldId CUSTOM_LRB_VESSEL_VISIT_PHASE = MetafieldIdFactory.valueOf("customEntityFields.customlrbVesselVisit.cvVisitPhase");
    public static MetafieldId CUSTOM_LRB_VESSEL_VISIT_CARGO_CUT_OFF = MetafieldIdFactory.valueOf("customEntityFields.customlrbVesselVisit.cvCvd.vvdTimeCargoCutoff");
    public static MetafieldId CUSTOM_LRB_VESSEL_VISIT_HAZ_CUT_OFF = MetafieldIdFactory.valueOf("customEntityFields.customlrbVesselVisit.cvCvd.vvdTimeHazCutoff");
    public static MetafieldId CUSTOM_LRB_VESSEL_VISIT_REEFER_CUT_OFF = MetafieldIdFactory.valueOf("customEntityFields.customlrbVesselVisit.cvCvd.vvdTimeReeferCutoff");
    public static String CUSTOM_LATE_RECEIVAL_BOOKING = "com.portsamerica.navis.core.CustomPALateReceivalBooking";
    private static final MetafieldId CUSTOM_LAB_OOG = MetafieldIdFactory.valueOf("customEntityFields.customlrbIsOOG");
    private static final MetafieldId CUSTOM_LRB_BOOKING_ORDER_POD = MetafieldIdFactory.valueOf("customEntityFields.customlrbBookingOrder.eqoPod1");
        private static final MetafieldId CUSTOM_LRB_BOOKING_ITEM_QTY = MetafieldIdFactory.valueOf("customEntityFields.customlrbEqOrderItem.eqoiQty");
    private static final MetafieldId CUSTOM_LRB_BOOKING_ITEM_MAX_ALLOWED = MetafieldIdFactory.valueOf("customEntityFields.customlrbEqOrderItem.eqoiTally");
    private static MetafieldId CUSTOM_LRB_MAX_COUNT = MetafieldIdFactory.valueOf("customEntityFields.customlrbMaxCount");
    private static final Logger LOGGER = Logger.getLogger(this.class);
}