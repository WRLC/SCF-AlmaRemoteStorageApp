package com.exlibris.items;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.marc4j.marc.DataField;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class ItemData {

    final static String BARCODE_SUB_FIELD = "b";
    final static String LIBRARY_SUB_FIELD = "c";
    final static String LOCATION_SUB_FIELD = "l";

    private String barcode;
    private String institution;
    private String library;
    private String location;
    private String networkNumber;
    private String mmsId;
    private String description;

    public ItemData(String barcode, String institution, String library, String location, String networkNumber) {
        this.barcode = barcode;
        this.institution = institution;
        this.library = library;
        this.location = location;
        this.networkNumber = networkNumber;

    }

    public String getMmsId() {
        return mmsId;
    }

    public String getDescription() {
        return description;
    }

    public ItemData(String barcode, String institution, String mmsId, String description, String library,
            String location) {
        this.barcode = barcode;
        this.institution = institution;
        this.mmsId = mmsId;
        this.description = description;
        this.library = library;
        this.location = location;
    }

    public String getBarcode() {
        return barcode;
    }

    public String getInstitution() {
        return institution;
    }

    public String getLibrary() {
        return library;
    }

    public String getLocation() {
        return location;
    }

    public String getNetworkNumber() {
        return networkNumber;
    }

    public void setNetworkNumber(String networkNumber) {
        this.networkNumber = networkNumber;
    }

    public static ItemData dataFieldToItemData(DataField dataField, String institution, String nZMmsId) {
        String barcode = dataField.getSubfieldsAsString(BARCODE_SUB_FIELD);
        String library = dataField.getSubfieldsAsString(LIBRARY_SUB_FIELD);
        String location = dataField.getSubfieldsAsString(LOCATION_SUB_FIELD);
        ItemData itemData = new ItemData(barcode, institution, library, location, nZMmsId);
        return itemData;
    }

    public static List<ItemData> xmlStringToRequestData(String xml, String institution) throws Exception {
        List<ItemData> requestDataList = new ArrayList<ItemData>();
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        InputSource src = new InputSource();
        src.setCharacterStream(new StringReader(xml));
        Document doc = builder.parse(src);
        NodeList nl = doc.getDocumentElement().getChildNodes();
        for (int x = 0; x < nl.getLength(); x++) {
            Element element = (Element) nl.item(x);
            String barcode = element.getElementsByTagName("xb:barcode").item(0) == null ? null
                    : element.getElementsByTagName("xb:barcode").item(0).getTextContent();
            String description = element.getElementsByTagName("xb:description").item(0) == null ? null
                    : element.getElementsByTagName("xb:description").item(0).getTextContent();
            String mmsId = element.getElementsByTagName("xb:mmsId").item(0) == null ? null
                    : element.getElementsByTagName("xb:mmsId").item(0).getTextContent();
            String library = element.getElementsByTagName("xb:mmsId").item(0) == null ? null
                    : element.getElementsByTagName("xb:libraryCode").item(0).getTextContent();
            String location = null;
            try {
                location = ((Element) element.getElementsByTagName("xb:operationalRecordinformation").item(0))
                        .getElementsByTagName("xb:permanent_physical_location_code").item(0).getTextContent();
            } catch (Exception e) {
            }
            requestDataList.add(new ItemData(barcode, institution, mmsId, description, library, location));
        }

        return requestDataList;

    }

}
