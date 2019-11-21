package com.exlibris.restapis;

import org.apache.log4j.Logger;

public class ItemApi {

    final private static Logger logger = Logger.getLogger(AlmaRestUtil.class);

    public static HttpResponse retrieveItem(String barcode, String baseUrl, String apiKey) {

        logger.info("Starting to handle retrieve Item: " + barcode + ".");
        logger.info("Item barcode: " + barcode + " - calling GET");

        String url = baseUrl + "/almaws/v1/items?item_barcode=" + barcode + "&apikey=" + apiKey;
        HttpResponse itemResponse = AlmaRestUtil.sendHttpReq(url, "GET", null);

        return itemResponse;
    }

    public static HttpResponse createItem(String mmsId, String holdingId, String baseUrl, String apiKey, String body) {

        logger.info("Starting to handle create Item: " + body + ".");
        logger.info("Item Mms Id: " + mmsId + " Item Holding Id: " + holdingId + "- calling POST");

        String url = baseUrl + "/almaws/v1/bibs/" + mmsId + "/holdings/" + holdingId + "/items?apikey=" + apiKey;
        HttpResponse itemResponse = AlmaRestUtil.sendHttpReq(url, "POST", body);

        return itemResponse;
    }

    public static HttpResponse deleteItem(String mmsId, String holdingId, String itemPid, String override,
            String holdings, String baseUrl, String apiKey) {

        logger.info("Starting to handle delete Item: " + itemPid + ".");
        logger.info("Item Mms Id: " + mmsId + " Item Holding Id: " + holdingId + "- calling DELETE");

        String url = baseUrl + "/almaws/v1/bibs/" + mmsId + "/holdings/" + holdingId + "/items/" + itemPid + "?apikey="
                + apiKey;
        HttpResponse itemResponse = AlmaRestUtil.sendHttpReq(url, "DELETE", null);

        return itemResponse;
    }

    public static HttpResponse updateItem(String mmsId, String holdingId, String itemPid, String baseUrl, String apiKey,
            String body) {

        logger.info("Starting to handle Update Item: " + itemPid + ".");
        logger.info("Item Mms Id: " + mmsId + " Item Holding Id: " + holdingId + "- calling PUT");

        String url = baseUrl + "/almaws/v1/bibs/" + mmsId + "/holdings/" + holdingId + "/items/" + itemPid + "?apikey="
                + apiKey;
        HttpResponse itemResponse = AlmaRestUtil.sendHttpReq(url, "PUT", body);

        return itemResponse;
    }

    public static HttpResponse scanIn(String mmsId, String holdingId, String itemPid, String op, String baseUrl,
            String library, String circDesk, String done, String apiKey) {
        logger.info("Starting to handle Scan In Item: " + itemPid + ".");
        logger.info("Item Mms Id: " + mmsId + " Item Holding Id: " + holdingId + "- calling POST");

        String url = baseUrl + "/almaws/v1/bibs/" + mmsId + "/holdings/" + holdingId + "/items/" + itemPid + "?op="
                + op + "&library=" + library + "&circ_desk=" + circDesk+ "&done=" + done + "&apikey=" + apiKey;
        HttpResponse itemResponse = AlmaRestUtil.sendHttpReq(url, "POST", null);

        return itemResponse;
    }
}
