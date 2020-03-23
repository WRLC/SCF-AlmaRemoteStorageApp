package com.exlibris.webhook;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.exlibris.configuration.ConfigurationHandler;
import com.exlibris.items.ItemData;
import com.exlibris.items.ItemsMain;
import com.exlibris.request.RequestsMain;
import com.exlibris.util.SCFUtil;

public class ManageWebHookMessages {

    public enum WebhookEvent {
        LOAN_CREATED, LOAN_RETURNED, LOAN_LOST, LOAN_CLAIMED_RETURNED, LOAN_RENEWED, LOAN_DUE_DATE;
    }

    public enum WebhooksActionType {
        JOB_END, LOAN, REQUEST;
    }

    final private static org.apache.log4j.Logger logger = org.apache.log4j.Logger
            .getLogger(ManageWebHookMessages.class);

    public static void getWebhookMessage(String webhookMessage) {
        if (webhookMessage.isEmpty()) {
            logger.info("message is empty");
            return;
        }
        JSONObject webhookJsonMessage = new JSONObject(webhookMessage);
        try {
            logger.info("institution is :" + webhookJsonMessage.getJSONObject("institution").get("value"));
        } catch (JSONException e) {
            logger.debug("no institution in webhook message");
        }
        logger.info("action is :" + webhookJsonMessage.get("action"));
        try {
            logger.info("event is :" + webhookJsonMessage.getJSONObject("event").get("value"));
        } catch (JSONException e) {
            logger.debug("no event in webhook message");
        }

        messageHandling(webhookJsonMessage);
    }

    public static void messageHandling(JSONObject webhookMessage) {
        String action = webhookMessage.getString("action");

        JSONObject props = ConfigurationHandler.getInstance().getConfiguration();
        String remoteStorageInstitution = props.get("remote_storage_inst").toString();

        switch (WebhooksActionType.valueOf(action)) {
        case LOAN: {
            String event = webhookMessage.getJSONObject("event").getString("value");
            if (WebhookEvent.valueOf(event).equals(WebhookEvent.LOAN_RETURNED)) {
                String webhookMessageInstitution = webhookMessage.getJSONObject("institution").getString("value");
                if (webhookMessageInstitution.equalsIgnoreCase(remoteStorageInstitution)) {
                    loanReturned(webhookMessage);
                } else {
                    logger.debug(
                            "Alma Remote Storage App Handles Loan Returned only For Remote Storage Institution. webhook institution is: "
                                    + webhookMessageInstitution);
                }

            }
            break;
        }
        case JOB_END: {
            String jobId = webhookMessage.getJSONObject("job_instance").getJSONObject("job_info").getString("id");
            String institution = getInstitutionByJobId(jobId, "publishing_job_id");
            if (institution != null) {
                ItemsMain.mergeItemsWithSCF(institution);
                return;
            }
            institution = getInstitutionByJobId(jobId, "requests_job_id");
            if (institution != null) {
                RequestsMain.sendRequestsToSCF(institution);
                return;
            }
            logger.debug("The Job does not exist in the configuration jobs list. Job Id: " + jobId);
        }
        case REQUEST: {
            String event = webhookMessage.getJSONObject("event").getString("value");
            if (event.equals("REQUEST_CANCELED")) {
                String webhookMessageInstitution = webhookMessage.getJSONObject("institution").getString("value");
                if (webhookMessageInstitution.equalsIgnoreCase(remoteStorageInstitution)) {
                    String barcode = null;
                    try {
                        barcode = webhookMessage.getJSONObject("user_request").getString("barcode");
                    } catch (Exception e) {
                        logger.debug("The item barcode does not exist in the webhook message");
                    }
                    if (barcode != null && !barcode.isEmpty()) {
                        if (barcode.endsWith("X")) {
                            barcode = barcode.substring(0, barcode.length() - 1);
                        } else {
                            logger.info("Request Not Part Of SCF.  Barcode: " + barcode);
                            return;
                        }
                        requestCanceled(barcode);
                    }
                }
            }
        }
        default:
            break;

        }
    }

    private static void loanReturned(JSONObject webhookMessage) {

        String userId = webhookMessage.getJSONObject("item_loan").getString("user_id");
        String barcode = webhookMessage.getJSONObject("item_loan").getString("item_barcode");
        if (barcode.endsWith("X")) {
            barcode = barcode.substring(0, barcode.length() - 1);
        } else {
            logger.info("Request Not Part Of SCF. userId: " + userId + " Barcode: " + barcode);
            return;
        }
        ItemData itemData = new ItemData(barcode);
        String institution = null;
        try {
            JSONObject jsonScfItemObject = SCFUtil.getSCFItem(itemData);
            institution = jsonScfItemObject.getJSONObject("item_data").getJSONObject("provenance").getString("value");
        } catch (Exception e) {
            logger.info("Can't get Request institution .Barcode: " + barcode);
            logger.info("Request Not Part Of SCF. userId: " + userId);
            return;
        }

        if (institution == null) {
            logger.info("Request Not Part Of SCF. userId: " + userId + " Barcode: " + barcode);
            return;
        }
        itemData.setInstitution(institution);
        logger.info("Request source institution is :" + institution + ". userId: " + userId);
        logger.info("Scan In Request. Source Institution Barcode: " + itemData.getBarcode());
        JSONObject jsonItemObject = SCFUtil.getINSItem(itemData);
        if (jsonItemObject != null) {
            SCFUtil.scanINSRequest(jsonItemObject, itemData);
        }

    }

    private static void requestCanceled(String barcode) {
        ItemData itemData = new ItemData(barcode);
        JSONObject jsonScfItemObject = null;
        String institution = null;
        try {
            jsonScfItemObject = SCFUtil.getSCFItem(itemData);
            institution = jsonScfItemObject.getJSONObject("item_data").getJSONObject("provenance").getString("value");
        } catch (Exception e) {
            logger.info("Can't get Request institution, Request Not Part Of SCF .Barcode: " + barcode);
            return;
        }
        if (institution == null) {
            logger.info("Can't get Request institution, Request Not Part Of SCF .Barcode: " + barcode);
            return;
        }
        itemData.setInstitution(institution);
        logger.info("Request source institution is :" + institution);
        logger.info("Cancel Request. Source Institution Barcode: " + itemData.getBarcode());
        JSONObject jsonInsItemObject = SCFUtil.getINSItem(itemData);
        if (jsonInsItemObject == null) {
            logger.info("Can't get institution item. Barcode: " + barcode);
            return;
        }
        JSONObject jsonRequestsObject = SCFUtil.getItemRequests(jsonInsItemObject, itemData);
        if (jsonRequestsObject == null) {
            logger.info("Can't get institution requests. Barcode: " + barcode);
            return;
        }
        String requestId = null;
        for (int i = 0; i < jsonRequestsObject.getJSONArray("user_request").length(); i++) {
            JSONObject request = jsonRequestsObject.getJSONArray("user_request").getJSONObject(i);
            requestId = request.getString("request_id");
            logger.info("Request id is :" + requestId);
            SCFUtil.cancelItemRequest(jsonInsItemObject, itemData, requestId);
        }

        if (requestId == null) {
            logger.error("Can't get SCF request id . Barcode: " + barcode);
            return;
        }
    }

    private static String getInstitutionByJobId(String jobId, String jobType) {
        JSONObject props = ConfigurationHandler.getInstance().getConfiguration();
        JSONArray institutions = props.getJSONArray("institutions");
        for (int i = 0; i < institutions.length(); i++) {
            if (!institutions.getJSONObject(i).getString("code").equals(props.getString("remote_storage_inst"))) {
                try {
                    if (institutions.getJSONObject(i).getString(jobType).equals(jobId)) {
                        return institutions.getJSONObject(i).getString("code");
                    }
                } catch (JSONException e) {
                    logger.debug("there isn't job Id configuration for Type: " + jobType);
                    return null;
                }
            }
        }
        return null;
    }
}
