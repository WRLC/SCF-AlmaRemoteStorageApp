package com.exlibris.request;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.exlibris.items.ItemData;
import com.exlibris.util.SCFUtil;

public class RequestHandler {

    final private static Logger logger = Logger.getLogger(RequestHandler.class);

    public static void createItemRequest(ItemData requestData) {
        logger.info("Create Item Request. Barcode: " + requestData.getBarcode());
        JSONObject jsonItemObject = SCFUtil.getSCFItem(requestData);
        if (jsonItemObject != null) {
            SCFUtil.createSCFRequest(jsonItemObject, requestData);
        } else {
            logger.warn("Create Request Failed. Barcode: " + requestData.getBarcode() + "X Does not exist in SCF");
        }
    }

    public static void createBibRequest(ItemData itemData) {
        logger.info("Create Bib Request. Mms Id : " + itemData.getMmsId());
        logger.debug("get Institution Bib to get NZ MMS ID");
        JSONObject jsonINSBibObject = SCFUtil.getINSBib(itemData);
        String networkNumber = getNetworkNumber(jsonINSBibObject.getJSONArray("network_number"));
        JSONObject jsonBibObject = null;
        if (networkNumber != null) {
            itemData.setNetworkNumber(networkNumber);
            logger.debug("get SCF Bibbased on NZ MMS ID");
            jsonBibObject = SCFUtil.getSCFBibByNZ(itemData);
        } else {
            logger.debug("get SCF Bibbased on Local Institution MMS ID");
            jsonBibObject = SCFUtil.getSCFBibByINST(itemData);
        }
        SCFUtil.createSCFBibRequest(jsonBibObject, itemData);
    }

    private static String getNetworkNumber(JSONArray networkNumbers) {
        for (int i = 0; i < networkNumbers.length(); i++) {
            String networkNumber = networkNumbers.getString(i);
            if (networkNumber != null && networkNumber.contains("EXLNZ")) {
                return networkNumber.replaceAll("^\\(EXLNZ(.*)\\)", "");
            }
        }
        return null;
    }

    public static void createDigitizationItemRequest(ItemData requestData) {
        try {
            logger.info("Create Digitization Item Request. Barcode: " + requestData.getBarcode());
            JSONObject jsonRequestObject = SCFUtil.getINSRequest(requestData);
            String userId = jsonRequestObject.getString("user_primary_id");
            JSONObject jsonINSUserObject = SCFUtil.getINSUser(requestData, userId, "all_unique",
                    requestData.getInstitution());
            JSONObject jsonUserObject = null;
            if (jsonINSUserObject == null) {
                logger.error("Failed to create Digitization Item Request. Barcode: " + requestData.getBarcode());
                return;
            }
            String userLinkingId = userId;
            String userSourceInstitution = requestData.getInstitution();
            if (jsonINSUserObject.has("source_link_id") && jsonINSUserObject.has("source_institution_code")) {
                userLinkingId = jsonINSUserObject.getString("source_link_id");
                userSourceInstitution = jsonINSUserObject.getString("source_institution_code");
                jsonUserObject = SCFUtil.getSCFUser(requestData, userLinkingId, userSourceInstitution);
                if (jsonUserObject == null) {
                    JSONObject jsonSourceUserObject = SCFUtil.getINSUser(requestData, userLinkingId, "linking_id",
                            userSourceInstitution);
                    if (jsonSourceUserObject != null) {
                        userLinkingId = jsonSourceUserObject.getString("primary_id");
                    } else {
                        logger.error(
                                "Failed to create Digitization Item Request. Barcode: " + requestData.getBarcode());
                        return;
                    }
                }
            } else if (jsonINSUserObject.has("linking_id")) {
                userLinkingId = jsonINSUserObject.getString("linking_id");
                jsonUserObject = SCFUtil.getSCFUser(requestData, userLinkingId, requestData.getInstitution());
            }
            if (jsonUserObject == null) {
                jsonUserObject = SCFUtil.createSCFUser(requestData, userLinkingId, userSourceInstitution);
            }
            if (jsonUserObject == null) {
                logger.error("Failed to create Digitization Item Request. Barcode: " + requestData.getBarcode());
                return;
            }
            JSONObject jsonItemObject = SCFUtil.getSCFItem(requestData);
            if (jsonItemObject != null) {
                JSONObject jsonDigitizationRequestObject = SCFUtil.createSCFDigitizationRequest(jsonUserObject,
                        jsonRequestObject, jsonItemObject, requestData);
                if (jsonDigitizationRequestObject != null) {
                    SCFUtil.cancelTitleRequest(requestData);
                }
            } else {
                logger.warn("Create Request Failed. Barcode: " + requestData.getBarcode());
            }
        } catch (Exception e) {
            logger.warn("Create Request Failed. Barcode: " + requestData.getBarcode());
        }

    }

    public static void createDigitizationUserRequest(ItemData requestData) {
        logger.info("Create Digitization User Request. User Id : " + requestData.getUserId());
        JSONObject jsonRequestObject = SCFUtil.getINSUserRequest(requestData);
        String userId = jsonRequestObject.getString("user_primary_id");
        JSONObject jsonINSUserObject = SCFUtil.getINSUser(requestData, userId, "all_unique",
                requestData.getInstitution());
        JSONObject jsonUserObject = null;
        if (jsonINSUserObject == null) {
            logger.error("Failed to create Digitization User Request. User Id : " + requestData.getUserId());
            return;
        }
        String userLinkingId = userId;
        String userSourceInstitution = requestData.getInstitution();
        if (jsonINSUserObject.has("source_link_id") && jsonINSUserObject.has("source_institution_code")) {
            userLinkingId = jsonINSUserObject.getString("source_link_id");
            userSourceInstitution = jsonINSUserObject.getString("source_institution_code");
            jsonUserObject = SCFUtil.getSCFUser(requestData, userLinkingId, userSourceInstitution);
            if (jsonUserObject == null) {
                JSONObject jsonSourceUserObject = SCFUtil.getINSUser(requestData, userLinkingId, "linking_id",
                        userSourceInstitution);
                if (jsonSourceUserObject != null) {
                    userLinkingId = jsonSourceUserObject.getString("primary_id");
                } else {
                    logger.error("Failed to create Digitization User Request. User Id : " + requestData.getUserId());
                    return;
                }
            }
        } else if (jsonINSUserObject.has("linking_id")) {
            userLinkingId = jsonINSUserObject.getString("linking_id");
            jsonUserObject = SCFUtil.getSCFUser(requestData, userLinkingId, requestData.getInstitution());
        }
        if (jsonUserObject == null) {
            jsonUserObject = SCFUtil.createSCFUser(requestData, userLinkingId, userSourceInstitution);
        }
        if (jsonUserObject == null) {
            logger.error("Failed to create Digitization User Request. User Id : " + requestData.getUserId());
            return;
        }
        JSONObject jsonDigitizationRequestObject = SCFUtil.createSCFDigitizationUserRequest(jsonUserObject,
                jsonRequestObject, requestData);
        if (jsonDigitizationRequestObject != null) {
            requestData.setMmsId(jsonRequestObject.getString("mms_id"));
            SCFUtil.cancelTitleRequest(requestData);
        }

    }

}
