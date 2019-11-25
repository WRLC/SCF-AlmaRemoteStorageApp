package com.exlibris.items;

import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.json.JSONObject;

import com.exlibris.util.SCFUtil;

public class ItemsHandler {

    final private static Logger logger = Logger.getLogger(ItemsHandler.class);

    public static void itemUpdated(ItemData itemData) {
        logger.info("New/Update Item. Barcode: " + itemData.getBarcode());

        JSONObject jsonItemObject = SCFUtil.getSCFItem(itemData);
        if (jsonItemObject == null) {
            logger.debug("The item does not exist in the remote Storage");
            if (!SCFUtil.isItemInRemoteStorage(itemData)) {
                return;
            } else {
                if (itemData.getNetworkNumber() == null) {
                    logger.error("Missing Network Number - Can't find SCF bib for item : " + itemData.getBarcode()
                            + " Institution : " + itemData.getInstitution());
                    return;
                }
                logger.debug("get matching bib from SCF");
                JSONObject jsonBibObject = SCFUtil.getSCFBib(itemData);
                String mmsId = null;
                String holdingId = null;
                if (jsonBibObject == null) {
                    logger.debug("The Bib does not exist in the remote Storage - Creating Bib and Holding");
                    jsonBibObject = SCFUtil.createSCFBib(itemData);
                    mmsId = jsonBibObject.getString("mms_id");
                    holdingId = SCFUtil.createSCFHoldingAndGetId(jsonBibObject, mmsId);
                } else {
                    logger.debug(
                            "The Bib exists in the remote Storage - Check for Holding get Mms Id from exist SCF Bib");
                    mmsId = jsonBibObject.getJSONArray("bib").getJSONObject(0).getString("mms_id");
                    holdingId = SCFUtil.getSCFHoldingFromRecordAVA(
                            jsonBibObject.getJSONArray("bib").getJSONObject(0).getJSONArray("anies").getString(0));
                    if (holdingId == null) {
                        logger.debug("The Holding does not exist in the remote Storage - Creating Holding");
                        holdingId = SCFUtil.createSCFHoldingAndGetId(jsonBibObject, mmsId);
                    }
                }
                if (holdingId != null) {
	                logger.debug("Creating Item Based SCF on mmsId and holdingId");
	                String itemPid = SCFUtil.createSCFItemAndGetId(itemData, mmsId, holdingId);
	                try {
						TimeUnit.SECONDS.sleep(3);
					} catch (InterruptedException e) {}
	                logger.debug("Loan the new Item who was created");
	                SCFUtil.createSCFLoan(itemData, itemPid);
                }

            }
        } else {
            logger.debug("The item exists in the remote Storage");
            if (!SCFUtil.isItemInRemoteStorage(itemData)) {
                logger.debug(
                        "Item exists in the SCF, but in the Institution it's no in a remote-storage location need to delete it from SCF");
                SCFUtil.deleteSCFItem(jsonItemObject);
            } else {
                logger.debug("Item exists merge between INST item and SCF item");
                SCFUtil.updateSCFItem(itemData, jsonItemObject);
            }
        }
    }

    public static void itemDeleted(ItemData itemData) {
        logger.info("Deleted Item. Barcode: " + itemData.getBarcode());
        JSONObject jsonItemObject = SCFUtil.getSCFItem(itemData);
        if (jsonItemObject != null) {
            logger.debug("The item exists in the remote Storage");
            SCFUtil.deleteSCFItem(jsonItemObject);
        }

    }

}
