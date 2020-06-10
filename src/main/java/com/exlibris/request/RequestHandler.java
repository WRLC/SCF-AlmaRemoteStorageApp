package com.exlibris.request;

import javax.net.ssl.HttpsURLConnection;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.exlibris.items.ItemData;
import com.exlibris.logger.ReportUtil;
import com.exlibris.restapis.HttpResponse;
import com.exlibris.util.SCFUtil;

public class RequestHandler {

    final private static Logger logger = Logger.getLogger(RequestHandler.class);

    public static void createItemRequest(ItemData requestData) {
        logger.info("Create Item Request. Barcode: " + requestData.getBarcode());
        JSONObject jsonItemObject = SCFUtil.getSCFItem(requestData);
        if (jsonItemObject != null) {
            String processType = jsonItemObject.getJSONObject("item_data").getJSONObject("process_type")
                    .getString("value");
            if ("LOAN".equals(processType)) {
                logger.info("Item is on loan - Canceling Item Request. Barcode: " + requestData.getBarcode());
                if (!requestData.getSourceInstitution().isEmpty()) {
                    requestData.setInstitution(requestData.getSourceInstitution());
                }
                JSONObject jsonINSTItemObject = SCFUtil.getINSItem(requestData);
                HttpResponse requestResponce = SCFUtil.cancelItemRequest(jsonINSTItemObject, requestData,
                        requestData.getRequestId(), "Item is on loan");
                if (requestResponce.getResponseCode() != HttpsURLConnection.HTTP_NO_CONTENT) {
                    String message = "Can't cancel SCF Item Requests. Barcode : " + requestData.getBarcode();
                    logger.error(message);
                    ReportUtil.getInstance().appendReport("RequestHandler", requestData.getBarcode(),
                            requestData.getInstitution(), message);
                }
                return;
            }
            SCFUtil.createSCFRequest(jsonItemObject, requestData);
        } else {
            String message = "Create Request Failed. Barcode: " + requestData.getBarcode() + "X Does not exist in SCF";
            logger.error(message);
            ReportUtil.getInstance().appendReport("RequestHandler", requestData.getBarcode(),
                    requestData.getInstitution(), message);
            return;
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
        if (jsonBibObject == null) {
            String message = "Create Request Failed. Mms Id : " + itemData.getMmsId();
            logger.error(message);
            ReportUtil.getInstance().appendReport("RequestHandler", itemData.getBarcode(), itemData.getInstitution(),
                    message);
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
                String message = "Failed to create Digitization Item Request. Barcode: " + requestData.getBarcode();
                logger.error("Failed to create Digitization Item Request. Barcode: " + requestData.getBarcode());
                ReportUtil.getInstance().appendReport("RequestHandler", requestData.getBarcode(),
                        requestData.getInstitution(), message);
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
                        String message = "Failed to create Digitization Item Request. Barcode: "
                                + requestData.getBarcode();
                        logger.error(message);
                        ReportUtil.getInstance().appendReport("RequestHandler", requestData.getBarcode(),
                                requestData.getInstitution(), message);
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
                String message = "Failed to create Digitization Item Request. Barcode: " + requestData.getBarcode();
                logger.error(message);
                ReportUtil.getInstance().appendReport("RequestHandler", requestData.getBarcode(),
                        requestData.getInstitution(), message);
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
                String message = "Failed to create Digitization Item Request. Barcode: " + requestData.getBarcode();
                logger.error(message);
                ReportUtil.getInstance().appendReport("RequestHandler", requestData.getBarcode(),
                        requestData.getInstitution(), message);
                return;
            }
        } catch (Exception e) {
            String message = "Create Request Failed. Barcode: " + requestData.getBarcode();
            logger.warn("Create Request Failed. Barcode: " + requestData.getBarcode());
            ReportUtil.getInstance().appendReport("RequestHandler", requestData.getBarcode(),
                    requestData.getInstitution(), message);

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
            String message = "Failed to create Digitization User Request. User Id : " + requestData.getUserId();
            ReportUtil.getInstance().appendReport("RequestHandler", requestData.getBarcode(),
                    requestData.getInstitution(), message);
            logger.error(message);
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
                    String message = "Failed to create Digitization User Request. User Id : " + requestData.getUserId();
                    ReportUtil.getInstance().appendReport("RequestHandler", requestData.getBarcode(),
                            requestData.getInstitution(), message);
                    logger.error(message);
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
            String message = "Failed to create Digitization User Request. User Id : " + requestData.getUserId();
            ReportUtil.getInstance().appendReport("RequestHandler", requestData.getBarcode(),
                    requestData.getInstitution(), message);
            logger.error(message);
            return;
        }
        JSONObject jsonDigitizationRequestObject = SCFUtil.createSCFDigitizationUserRequest(jsonUserObject,
                jsonRequestObject, requestData);
        if (jsonDigitizationRequestObject != null) {
            requestData.setMmsId(jsonRequestObject.getString("mms_id"));
            SCFUtil.cancelTitleRequest(requestData);
        } else {
            String message = "Failed to create Digitization User Request." + requestData.getUserId();
            ReportUtil.getInstance().appendReport("RequestHandler", requestData.getBarcode(),
                    requestData.getInstitution(), message);
            logger.error(message);
            return;
        }

    }

}
