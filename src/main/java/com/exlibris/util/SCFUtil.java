package com.exlibris.util;

import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.marc4j.marc.DataField;
import org.marc4j.marc.Record;
import org.marc4j.marc.VariableField;

import com.exlibris.configuration.ConfigurationHandler;
import com.exlibris.items.ItemData;
import com.exlibris.restapis.BibApi;
import com.exlibris.restapis.HoldingApi;
import com.exlibris.restapis.HttpResponse;
import com.exlibris.restapis.ItemApi;
import com.exlibris.restapis.LoanApi;
import com.exlibris.restapis.RequestApi;

public class SCFUtil {

    final private static Logger logger = Logger.getLogger(SCFUtil.class);
    final private static String HOL_XML_TEMPLATE = "<holding><record><datafield ind1=\"0\" ind2=\" \" tag=\"852\"><subfield code=\"b\">_LIB_CODE_</subfield><subfield code=\"c\">_LOC_CODE_</subfield></datafield></record><suppress_from_publishing>false</suppress_from_publishing></holding>";

    public static String getSCFHoldingFromRecordAVA(String record) {
        try {
            String r = XmlUtil.recordXmlToMarcXml(record);
            List<Record> Marcrecord = XmlUtil.xmlStringToMarc4jRecords(r);
            List<VariableField> variableFields = Marcrecord.get(0).getVariableFields("AVA");
            for (VariableField variableField : variableFields) {
                String holdingsID = ((DataField) variableField).getSubfieldsAsString("8");
                String library = ((DataField) variableField).getSubfieldsAsString("b");
                String location = ((DataField) variableField).getSubfieldsAsString("j");
                JSONObject props = ConfigurationHandler.getInstance().getConfiguration();
                String remote_storage_inst = props.getString("remote_storage_inst");
                JSONArray institutions = props.getJSONArray("institutions");
                for (int i = 0; i < institutions.length(); i++) {
                    JSONObject inst = institutions.getJSONObject(i);
                    if (inst.get("code").toString().equals(remote_storage_inst)) {
                        JSONArray libraries = inst.getJSONArray("libraries");
                        for (int j = 0; j < libraries.length(); j++) {
                            if (library.equals(libraries.getJSONObject(j).get("code").toString())) {
                                if (libraries.getJSONObject(j).getJSONArray("remote_storage_location").toString()
                                        .contains(location)) {
                                    return holdingsID;
                                }
                            }
                        }
                        // only one institution can be equal
                        break;
                    }
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    public static JSONObject getSCFBib(ItemData itemData) {
        logger.debug("get SCF Bib. Barcode : " + itemData.getBarcode());
        JSONObject props = ConfigurationHandler.getInstance().getConfiguration();
        String remoteStorageApikey = props.get("remote_storage_apikey").toString();
        String baseUrl = props.get("gateway").toString();
        String networkNumber = itemData.getNetworkNumber();
        HttpResponse bibResponse = BibApi.retrieveBibsbyNZ(networkNumber, "full", "p_avail", baseUrl,
                remoteStorageApikey);
        JSONObject jsonBibObject = new JSONObject(bibResponse.getBody());
        if (bibResponse.getResponseCode() == HttpsURLConnection.HTTP_BAD_REQUEST) {
            logger.debug(
                    "No bib found for NZ :" + itemData.getNetworkNumber() + ". Barcode : " + itemData.getBarcode());
            return null;
        }
        return jsonBibObject;
    }

    public static JSONObject getSCFItem(ItemData itemData) {
        logger.debug("get SCF Item. Barcode : " + itemData.getBarcode());
        JSONObject props = ConfigurationHandler.getInstance().getConfiguration();
        String remoteStorageApikey = props.get("remote_storage_apikey").toString();
        String baseUrl = props.get("gateway").toString();
        HttpResponse itemResponce = ItemApi.retrieveItem(itemData.getBarcode() + "X", baseUrl, remoteStorageApikey);
        JSONObject jsonItemObject = new JSONObject(itemResponce.getBody());
        if (itemResponce.getResponseCode() == HttpsURLConnection.HTTP_BAD_REQUEST) {
            logger.debug("No items found . Barcode : " + itemData.getBarcode());
            return null;
        }
        return jsonItemObject;
    }

    public static boolean isItemInRemoteStorage(ItemData itemData) {
        if (itemData.getLibrary() == null || itemData.getLocation() == null) {
            return false;
        }
        JSONObject props = ConfigurationHandler.getInstance().getConfiguration();
        String institution = itemData.getInstitution();
        String library = itemData.getLibrary();
        String location = itemData.getLocation();
        JSONArray institutions = props.getJSONArray("institutions");
        for (int i = 0; i < institutions.length(); i++) {
            JSONObject inst = institutions.getJSONObject(i);
            if (inst.get("code").toString().equals(institution)) {
                JSONArray libraries = inst.getJSONArray("libraries");
                for (int j = 0; j < libraries.length(); j++) {
                    if (library.equals(libraries.getJSONObject(j).get("code").toString())) {
                        if (libraries.getJSONObject(j).getJSONArray("remote_storage_location").toString()
                                .contains(location)) {
                            return true;
                        }
                    }
                }
                break;
            }
        }
        logger.debug("Item is not located in a remote-storage location. library: " + library + "location: " + location
                + "Inst: " + institution);
        return false;
    }

    public static JSONObject createSCFBib(ItemData itemData) {
        logger.debug("create SCF Bib. Barcode : " + itemData.getBarcode());
        JSONObject props = ConfigurationHandler.getInstance().getConfiguration();
        String remoteStorageApikey = props.get("remote_storage_apikey").toString();
        String baseUrl = props.get("gateway").toString();

        HttpResponse bibResponse = BibApi.createBib(itemData.getNetworkNumber(), null, "false", "true", "<bib></bib>",
                baseUrl, remoteStorageApikey);
        JSONObject jsonNewBibObject = new JSONObject(bibResponse.getBody());
        if (bibResponse.getResponseCode() == HttpsURLConnection.HTTP_BAD_REQUEST) {
            logger.warn("Can't create SCF holding. Barcode : " + itemData.getBarcode());
            return null;
        }
        return jsonNewBibObject;
    }

    public static String createSCFHoldingAndGetId(JSONObject jsonBibObject, String mmsId) {
        return createSCFHolding(jsonBibObject, mmsId);
    }

    private static String createSCFHolding(JSONObject jsonBibObject, String mmsId) {
        logger.debug("create SCF Holding. MMS ID : " + mmsId);
        JSONObject props = ConfigurationHandler.getInstance().getConfiguration();
        String remoteStorageApikey = props.get("remote_storage_apikey").toString();
        String baseUrl = props.get("gateway").toString();
        String holdingLib = props.get("remote_storage_holding_library").toString();
        String holdingLoc = props.get("remote_storage_holding_location").toString();
        String holdingBody = HOL_XML_TEMPLATE.replace("_LIB_CODE_", holdingLib).replace("_LOC_CODE_", holdingLoc);
        HttpResponse holdingResponse = HoldingApi.createHolding(mmsId, holdingBody, baseUrl, remoteStorageApikey);
        JSONObject jsonHoldingObject = new JSONObject(holdingResponse.getBody());
        if (holdingResponse.getResponseCode() == HttpsURLConnection.HTTP_BAD_REQUEST) {
            logger.warn("Can't create SCF holding. MMS ID : " + mmsId);
            return null;
        }
        return jsonHoldingObject.getString("holding_id");
    }

    public static JSONObject getINSItem(ItemData itemData) {
        logger.debug("get institution : " + itemData.getInstitution() + " Item. Barcode : " + itemData.getBarcode());
        JSONObject props = ConfigurationHandler.getInstance().getConfiguration();
        String baseUrl = props.get("gateway").toString();
        String institutionApiKey = null;
        for (int i = 0; i < props.getJSONArray("institutions").length(); i++) {
            JSONObject inst = props.getJSONArray("institutions").getJSONObject(i);
            if (inst.get("code").toString().equals(itemData.getInstitution())) {
                institutionApiKey = inst.getString("apikey");
                break;
            }
        }
        HttpResponse itemResponce = ItemApi.retrieveItem(itemData.getBarcode(), baseUrl, institutionApiKey);
        if (itemResponce.getResponseCode() == HttpsURLConnection.HTTP_BAD_REQUEST) {
            logger.warn("Can't get institution : " + itemData.getInstitution() + " item. Barcode : "
                    + itemData.getBarcode());
            return null;
        }
        return new JSONObject(itemResponce.getBody());
    }

    public static String createSCFItemAndGetId(ItemData itemData, String mmsId, String holdingId) {
        JSONObject jsonItemObject = createSCFItem(itemData, mmsId, holdingId);
        return jsonItemObject.getJSONObject("item_data").getString("pid");
    }

    public static JSONObject createSCFItem(ItemData itemData, String mmsId, String holdingId) {
        logger.debug("create SCF Item. Barcode : " + itemData.getBarcode());
        JSONObject instItem = getINSItem(itemData);
        instItem.getJSONObject("item_data").put("barcode", itemData.getBarcode() + "X");
        JSONObject provenance = new JSONObject();
        provenance.put("value", itemData.getInstitution());
        instItem.getJSONObject("item_data").put("provenance", provenance);
        instItem.getJSONObject("item_data").remove("po_line");
        instItem.getJSONObject("item_data").remove("library");
        instItem.getJSONObject("item_data").remove("location");
        instItem.getJSONObject("item_data").remove("policy");
        instItem.getJSONObject("holding_data").remove("temp_library");
        instItem.getJSONObject("holding_data").remove("in_temp_location");
        instItem.getJSONObject("holding_data").remove("temp_location");
        instItem.getJSONObject("holding_data").remove("temp_policy");

        JSONObject props = ConfigurationHandler.getInstance().getConfiguration();
        String remoteStorageApikey = props.get("remote_storage_apikey").toString();
        String baseUrl = props.get("gateway").toString();
        HttpResponse itemResponse = ItemApi.createItem(mmsId, holdingId, baseUrl, remoteStorageApikey,
                instItem.toString());
        if (itemResponse.getResponseCode() == HttpsURLConnection.HTTP_BAD_REQUEST) {
            logger.warn("Can't create SCF item. Barcode : " + itemData.getBarcode());
        }
        return new JSONObject(itemResponse.getBody());
    }

    public static boolean deleteSCFItem(JSONObject jsonItemObject) {
        logger.debug("delete SCF Item. Barcode: " + jsonItemObject.getJSONObject("item_data").getString("barcode"));
        JSONObject props = ConfigurationHandler.getInstance().getConfiguration();
        String remoteStorageApikey = props.get("remote_storage_apikey").toString();
        String baseUrl = props.get("gateway").toString();
        String mmsId = jsonItemObject.getJSONObject("bib_data").getString("mms_id");
        String holdingId = jsonItemObject.getJSONObject("holding_data").getString("holding_id");
        String itemPid = jsonItemObject.getJSONObject("item_data").getString("pid");
        HttpResponse itemResponse = ItemApi.deleteItem(mmsId, holdingId, itemPid, null, null, baseUrl,
                remoteStorageApikey);
        if (itemResponse.getResponseCode() == HttpsURLConnection.HTTP_BAD_REQUEST) {
            logger.warn("Can't delete SCF item : " + itemPid);
            return false;
        }
        return true;
    }

    public static void updateSCFItem(ItemData itemData, JSONObject scfItem) {
        logger.debug("update SCF Item. Barcode : " + itemData.getBarcode());
        JSONObject instItem = getINSItem(itemData);
        JSONObject scfItemData = scfItem.getJSONObject("item_data");
        instItem.getJSONObject("item_data").put("pid", scfItemData.getString("pid"));
        instItem.getJSONObject("item_data").put("barcode", itemData.getBarcode() + "X");
        instItem.getJSONObject("item_data").put("provenance", scfItemData.get("provenance"));
        instItem.getJSONObject("item_data").remove("po_line");
        instItem.getJSONObject("item_data").put("library", scfItemData.get("library"));
        instItem.getJSONObject("item_data").put("location", scfItemData.get("location"));
        instItem.getJSONObject("item_data").remove("policy");
        instItem.getJSONObject("item_data").put("alternative_call_number", scfItemData.get("alternative_call_number"));
        instItem.getJSONObject("item_data").put("alternative_call_number_type",
                scfItemData.get("alternative_call_number_type"));
        instItem.getJSONObject("item_data").put("storage_location_id", scfItemData.get("storage_location_id"));
        instItem.getJSONObject("item_data").put("internal_note_1", scfItemData.get("internal_note_1"));
        instItem.put("holding_data", scfItem.get("holding_data"));
        instItem.put("bib_data", scfItem.get("bib_data"));

        String mmsId = scfItem.getJSONObject("bib_data").getString("mms_id");
        String holdingId = scfItem.getJSONObject("holding_data").getString("holding_id");
        String itemPid = scfItemData.getString("pid");
        JSONObject props = ConfigurationHandler.getInstance().getConfiguration();
        String remoteStorageApikey = props.get("remote_storage_apikey").toString();
        String baseUrl = props.get("gateway").toString();
        String body = instItem.toString();
        HttpResponse itemResponse = ItemApi.updateItem(mmsId, holdingId, itemPid, baseUrl, remoteStorageApikey, body);
        if (itemResponse.getResponseCode() == HttpsURLConnection.HTTP_BAD_REQUEST) {
            // "Can't update item";
            logger.warn("Can't update SCF item. Barcode : " + itemData.getBarcode());
        }

    }

    public static JSONObject getINSBib(ItemData itemData) {
        logger.debug("get institution : " + itemData.getInstitution() + "Bib. Mms Id : " + itemData.getMmsId());
        JSONObject props = ConfigurationHandler.getInstance().getConfiguration();
        String baseUrl = props.get("gateway").toString();
        String institutionApiKey = null;
        for (int i = 0; i < props.getJSONArray("institutions").length(); i++) {
            JSONObject inst = props.getJSONArray("institutions").getJSONObject(i);
            if (inst.get("code").toString().equals(itemData.getInstitution())) {
                institutionApiKey = inst.getString("apikey");
                break;
            }
        }
        HttpResponse itemResponce = BibApi.getBib(itemData.getMmsId(), "full", "None", baseUrl, institutionApiKey);
        JSONObject jsonItemObject = new JSONObject(itemResponce.getBody());
        if (itemResponce.getResponseCode() == HttpsURLConnection.HTTP_BAD_REQUEST) {
            logger.warn("Can't get institution : " + itemData.getInstitution() + " Bib. MMS Id : "
                    + itemData.getNetworkNumber());
            return null;
        }
        return jsonItemObject;
    }

    public static void createSCFRequest(JSONObject jsonItemObject, ItemData itemData) {
        logger.debug("create SCF Request. Barcode: " + jsonItemObject.getJSONObject("item_data").getString("barcode"));
        JSONObject props = ConfigurationHandler.getInstance().getConfiguration();
        String remoteStorageApikey = props.get("remote_storage_apikey").toString();
        String baseUrl = props.get("gateway").toString();

        String mmsId = jsonItemObject.getJSONObject("bib_data").getString("mms_id");
        String holdingId = jsonItemObject.getJSONObject("holding_data").getString("holding_id");
        String itemPid = jsonItemObject.getJSONObject("item_data").getString("pid");
        String userId = getUserIdByIns(itemData);
        JSONObject jsonRequest = getRequestObj();
        jsonRequest.put("user_primary_id", userId);

        HttpResponse requestResponse = RequestApi.createRequest(mmsId, holdingId, itemPid, baseUrl, remoteStorageApikey,
                jsonRequest.toString(), userId);
        if (requestResponse.getResponseCode() == HttpsURLConnection.HTTP_BAD_REQUEST) {
            logger.warn("Can't create SCF request. Itewm Pid : " + itemPid);
        }
    }

    public static void createSCFBibRequest(JSONObject jsonBibObject, ItemData itemData) {
        logger.debug("create SCF Request. Bib: " + jsonBibObject.getString("mms_id"));
        JSONObject props = ConfigurationHandler.getInstance().getConfiguration();
        String remoteStorageApikey = props.get("remote_storage_apikey").toString();
        String baseUrl = props.get("gateway").toString();
        String mmsId = jsonBibObject.getString("mms_id");
        String userId = getUserIdByIns(itemData);

        JSONObject jsonRequest = getRequestObj();
        jsonRequest.put("user_primary_id", userId);
        jsonRequest.put("description", itemData.getDescription());
        HttpResponse requestResponse = RequestApi.createBibRequest(mmsId, baseUrl, remoteStorageApikey,
                jsonRequest.toString(), userId);
        if (requestResponse.getResponseCode() == HttpsURLConnection.HTTP_BAD_REQUEST) {
            logger.warn("Can't create SCF request. Bib Id : " + jsonBibObject.getString("mms_id"));
        }

    }

    private static JSONObject getRequestObj() {
        JSONObject jsonRequest = new JSONObject();
        jsonRequest.put("request_type", "HOLD");
        JSONObject jsonRequestSubType = new JSONObject();
        jsonRequestSubType.put("value", "PATRON_PHYSICAL");
        jsonRequestSubType.put("desc", "Patron physical item request");
        jsonRequest.put("request_sub_type", jsonRequestSubType);
        jsonRequest.put("pickup_location_type", "USER_HOME_ADDRESS");
        jsonRequest.put("task_name", "Pickup From Shelf");

        return jsonRequest;
    }

    private static String getUserIdByIns(ItemData itemData) {
        return itemData.getInstitution() + "-" + itemData.getLibrary();
    }

    public static void scanINSRequest(JSONObject jsonItemObject, ItemData requestData) {
        logger.debug(
                "return request : " + requestData.getInstitution() + " Item. Barcode : " + requestData.getBarcode());
        String library = jsonItemObject.getJSONObject("item_data").getJSONObject("library").getString("value");
        if (jsonItemObject.getJSONObject("holding_data").getBoolean("in_temp_location")) {
            library = jsonItemObject.getJSONObject("holding_data").getJSONObject("temp_library").getString("value");
        }
        JSONObject props = ConfigurationHandler.getInstance().getConfiguration();
        String baseUrl = props.get("gateway").toString();

        String institutionApiKey = null;
        String circ_desk = null;
        for (int i = 0; i < props.getJSONArray("institutions").length(); i++) {
            JSONObject inst = props.getJSONArray("institutions").getJSONObject(i);
            if (inst.get("code").toString().equals(requestData.getInstitution())) {
                institutionApiKey = inst.getString("apikey");
                JSONArray libraries = inst.getJSONArray("libraries");
                for (int j = 0; j < libraries.length(); j++) {
                    if (library.equals(libraries.getJSONObject(j).get("code").toString())) {
                        circ_desk = libraries.getJSONObject(j).getString("circ_desc");
                    }
                }
                break;
            }
        }
        String mmsId = jsonItemObject.getJSONObject("bib_data").getString("mms_id");
        String holdingId = jsonItemObject.getJSONObject("holding_data").getString("holding_id");
        String itemPid = jsonItemObject.getJSONObject("item_data").getString("pid");

        HttpResponse itemResponce = ItemApi.scanIn(mmsId, holdingId, itemPid, "scan", baseUrl, library, circ_desk,
                "true", institutionApiKey);

        if (itemResponce.getResponseCode() == HttpsURLConnection.HTTP_BAD_REQUEST) {
            logger.warn("Can't scan in institution : " + requestData.getInstitution() + " item. Barcode : "
                    + requestData.getBarcode());
        } else {
            logger.debug("Success scan in institution : " + requestData.getInstitution() + " item. Barcode : "
                    + requestData.getBarcode());
        }

    }

    public static void createSCFLoan(ItemData itemData, String itemPid) {

        logger.debug("create SCF Loan. Item Pid: " + itemPid);
        JSONObject props = ConfigurationHandler.getInstance().getConfiguration();
        String remoteStorageApikey = props.get("remote_storage_apikey").toString();
        String baseUrl = props.get("gateway").toString();
        String loanCircDesc = props.get("remote_storage_circ_desc").toString();
        String loanLibrary = props.get("remote_storage_holding_library").toString();
        String userId = getUserIdByIns(itemData);
        JSONObject jsonLoan = new JSONObject();
        JSONObject jsonCircDesk = new JSONObject();
        jsonCircDesk.put("value", loanCircDesc);
        jsonLoan.put("circ_desk", jsonCircDesk);
        JSONObject jsonLibrary = new JSONObject();
        jsonLibrary.put("value", loanLibrary);
        jsonLoan.put("library", jsonLibrary);

        HttpResponse requestResponse = LoanApi.createLoan(userId, itemPid, baseUrl, remoteStorageApikey,
                jsonLoan.toString());
        if (requestResponse.getResponseCode() == HttpsURLConnection.HTTP_BAD_REQUEST) {
            logger.warn("Can't create SCF loan. Item Pid : " + itemPid);
        }
    }

}
