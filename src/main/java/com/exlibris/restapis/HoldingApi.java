package com.exlibris.restapis;

import org.apache.log4j.Logger;

public class HoldingApi {

    final private static Logger logger = Logger.getLogger(HoldingApi.class);

    public static HttpResponse createHolding(String mmsId, String body, String baseurl, String apiKey) {
        logger.info("Starting to handle creating Holding: " + body + ".");
        logger.info("Mms Id: " + mmsId + " - calling POST");

        String url = baseurl + "/almaws/v1/bibs/" + mmsId + "/holdings?apikey=" + apiKey;
        HttpResponse bibResponse = AlmaRestUtil.sendHttpReq(url, "POST", body);

        return bibResponse;
    }

}
