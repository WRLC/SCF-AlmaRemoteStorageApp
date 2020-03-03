package com.exlibris.items;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.marc4j.marc.DataField;
import org.marc4j.marc.Record;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.exlibris.library.LibraryHandler;

public class ItemData {

    final static String BARCODE_SUB_FIELD = "b";
    final static String LIBRARY_SUB_FIELD = "c";
    final static String LOCATION_SUB_FIELD = "l";
    final static String INTERNAL_NOTE_2 = "n";

    private String barcode;
    private String institution;
    private String library;
    private String location;
    private String networkNumber;
    private String mmsId;
    private String description;
    private String note;
    private Record record;

    public ItemData(String mmsId, String barcode, String institution, String library, String location,
            String networkNumber, String note) {
        this.mmsId = mmsId;
        this.barcode = barcode;
        this.institution = institution;
        this.library = library;
        this.location = location;
        this.networkNumber = networkNumber;
        this.note = note;
    }

    public void setInstitution(String institution) {
        this.institution = institution;
    }

    public ItemData(String barcode) {
        this.barcode = barcode;
    }

    public String getMmsId() {
        return mmsId;
    }

    public String getNote() {
        return note;
    }

    public String getDescription() {
        return description;
    }

    public ItemData(String barcode, String institution, String mmsId, String description, String library) {
        this.barcode = barcode;
        this.institution = institution;
        this.mmsId = mmsId;
        this.description = description;
        this.library = library;
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

    public Record getRecord() {
        return record;
    }

    public void setRecord(Record record) {
        this.record = record;
    }

    public static ItemData dataFieldToItemData(String mmsId, DataField dataField, String institution, String nZMmsId) {
        String barcode = dataField.getSubfieldsAsString(BARCODE_SUB_FIELD);
        String library = dataField.getSubfieldsAsString(LIBRARY_SUB_FIELD);
        String location = dataField.getSubfieldsAsString(LOCATION_SUB_FIELD);
        String note = dataField.getSubfieldsAsString(INTERNAL_NOTE_2);
        ItemData itemData = new ItemData(mmsId, barcode, institution, library, location, nZMmsId, note);
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
            String library = null;
            String libraryInstitution = institution;
            if (element.getElementsByTagName("xb:institutionCode").item(0) != null) {
                libraryInstitution = element.getElementsByTagName("xb:institutionCode").item(0) == null ? null
                        : element.getElementsByTagName("xb:institutionCode").item(0).getTextContent();
                String libraryName = null;
                try {
                    libraryName = ((Element) element.getElementsByTagName("xb:pickup").item(0))
                            .getElementsByTagName("xb:library").item(0).getTextContent();
                    library = LibraryHandler.getLibraryCode(libraryInstitution, libraryName);
                } catch (Exception e) {
                }
            } else {
                library = element.getElementsByTagName("xb:libraryCode").item(0) == null ? null
                        : element.getElementsByTagName("xb:libraryCode").item(0).getTextContent();
            }

            requestDataList.add(new ItemData(barcode, libraryInstitution, mmsId, description, library));
        }

        return requestDataList;

    }
}
