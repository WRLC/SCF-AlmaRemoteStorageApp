package com.exlibris.webhook;

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
		JOB_END, LOAN;
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
			String jobId = null;
			try {
				jobId = webhookMessage.getJSONObject("job_instance").getJSONObject("job_info").getString("id");
			} catch (JSONException e) {
				logger.info("no job_info in webhook message");
			}
			if (jobId != null) {
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
		}
		default:
			break;

		}
	}

	private static void loanReturned(JSONObject webhookMessage) {
		String userId = webhookMessage.getJSONObject("item_loan").getString("user_id");
		String institution = getInsByUserId(userId);
		if (institution == null) {
			logger.info("Request Not Part Of RCF. userId: " + userId);
			return;
		}
		logger.info("Request source institution is :" + institution + ". userId: " + userId);
		String barcode = webhookMessage.getJSONObject("item_loan").getString("item_barcode");
		if (barcode.endsWith("X")) {
			barcode = barcode.substring(0, barcode.length() - 1);
		}
		ItemData itemData = new ItemData(barcode, institution, null, null,
				webhookMessage.getJSONObject("item_loan").getString("mms_id"));
		logger.info("Scan In Request. Source Institution Barcode: " + itemData.getBarcode());
		JSONObject jsonItemObject = SCFUtil.getINSItem(itemData);
		if (jsonItemObject != null) {
			SCFUtil.scanINSRequest(jsonItemObject, itemData);
		}

	}

	private static String getInsByUserId(String userId) {
		try {
			return userId.substring(0, userId.indexOf("-"));
		} catch (StringIndexOutOfBoundsException e) {
			return null;
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
