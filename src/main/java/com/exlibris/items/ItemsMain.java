package com.exlibris.items;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.marc4j.marc.DataField;
import org.marc4j.marc.Record;
import org.marc4j.marc.VariableField;

import com.exlibris.configuration.ConfigurationHandler;
import com.exlibris.ftp.FTPClient;
import com.exlibris.ftp.FTPUtil;
import com.exlibris.ftp.SFTPUtil;
import com.exlibris.util.XmlUtil;

public class ItemsMain {

    final static String MAINLOCALFOLDER = "files/items/";

    final static Logger logger = Logger.getLogger(ItemsMain.class);

    public static synchronized void mergeItemsWithSCF(String institution) {
        FTPClient ftpUtil;
        try {
            JSONObject props = ConfigurationHandler.getInstance().getConfiguration();
            String ftpFolder = props.getJSONObject("ftp_server").getString("main_folder");
            String ftpPort = props.getJSONObject("ftp_server").getString("ftp_port");
            String mainLocalFolder = MAINLOCALFOLDER;
            if (props.has("main_local_folder") && props.get("main_local_folder") != null) {
                mainLocalFolder = props.getString("main_local_folder") + "/" + MAINLOCALFOLDER;
            }
            logger.info("Starting Merge Items With SCF For Institution: " + institution);
            logger.debug("empty the local folder");
            File tarGzFolder = new File(mainLocalFolder + "targz/");
            if (tarGzFolder.isDirectory()) {
                FileUtils.cleanDirectory(tarGzFolder);
            } else {
                tarGzFolder.mkdirs();
            }
            logger.debug("get files from ftp");
            if (ftpPort.equals("22")) {
                ftpUtil = new SFTPUtil();
            } else {
                ftpUtil = new FTPUtil();
            }

            ftpUtil.getFiles(ftpFolder + "/" + institution + "/items/", mainLocalFolder + "targz/");

            logger.debug("loop over tar gz files and exact them to xml folder");
            XmlUtil.unTarGzFolder(tarGzFolder, mainLocalFolder + "xml/");
            logger.debug("loop over xml files and convert them to records");
            File xmlFolder = new File(mainLocalFolder + "xml/");
            File[] xmlFiles = xmlFolder.listFiles();
            int totalRecords = 0;
            for (File xmlFile : xmlFiles) {
                String methodName = null;
                if (xmlFile.getName().toLowerCase().contains("_delete")) {
                    methodName = "itemDeleted";
                } else {
                    methodName = "itemUpdated";
                }
                logger.debug("convert xml file to marc4j");
                List<Record> records = XmlUtil.xmlFileToMarc4jRecords(xmlFile);
                logger.debug("loop over records and merge with SCF");
                for (Record record : records) {
                    logger.debug("get network system number");
                    String NZMmsId = getNetworkNumber(record.getVariableFields("035"));
                    List<VariableField> variableFields = record.getVariableFields("ITM");
                    for (VariableField variableField : variableFields) {
                        ItemData itemData = ItemData.dataFieldToItemData(record.getControlNumber(),
                                (DataField) variableField, institution, NZMmsId);
                        try {
                            if (itemData.getBarcode() == null) {
                                logger.warn("Synchronize Item Failed. Barcode is null Item Data: " + variableField);
                                continue;
                            }
                            if (itemData.getNetworkNumber() == null) {
                                itemData.setRecord(record);
                            }
                            Method method = Class.forName("com.exlibris.items.ItemsHandler").getMethod(methodName,
                                    ItemData.class);
                            method.invoke(null, itemData);
                        } catch (Exception e) {
                            logger.error("Failed to handle item " + itemData.getBarcode(), e);
                        }
                    }
                }
                totalRecords += records.size();
            }
            logger.info("Total Records from FTP: " + totalRecords);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private static String getNetworkNumber(List<VariableField> variableFields) {
        for (VariableField variableField : variableFields) {
            String subfield = ((DataField) variableField).getSubfieldsAsString("a");
            if (subfield != null && subfield.contains("EXLNZ")) {
                return subfield.replaceAll("^\\(EXLNZ(.*)\\)", "");
            }
        }
        return null;
    }

}
