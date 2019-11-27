package com.exlibris.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.FileUtils;
import org.marc4j.MarcReader;
import org.marc4j.MarcXmlReader;
import org.marc4j.marc.Record;
import org.rauschig.jarchivelib.Archiver;
import org.rauschig.jarchivelib.ArchiverFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

public class XmlUtil {

    public static List<Record> xmlFileToMarc4jRecords(File xmlFile) throws Exception {
        InputStream inputStream = new FileInputStream(xmlFile);
        MarcReader marcReader = new MarcXmlReader(inputStream);

        List<Record> records = new ArrayList<Record>();
        while (marcReader.hasNext()) {
            Record marc4jRecord = marcReader.next();
            records.add(marc4jRecord);
        }
        return records;
    }

    public static List<Record> xmlStringToMarc4jRecords(String xmlString) throws Exception {
        InputStream inputStream = new ByteArrayInputStream(xmlString.getBytes(StandardCharsets.UTF_8));
        MarcReader marcReader = new MarcXmlReader(inputStream);

        List<Record> records = new ArrayList<Record>();
        while (marcReader.hasNext()) {
            Record marc4jRecord = marcReader.next();
            records.add(marc4jRecord);
        }
        return records;
    }

    public static void unTarGzFolder(File tarGzFolder, String targetFolder) throws Exception {
        // create target folder empty folder if exists
        File xmlFolderFiles = new File(targetFolder);
        if (xmlFolderFiles.isDirectory()) {
            FileUtils.cleanDirectory(xmlFolderFiles);
        } else {
            xmlFolderFiles.mkdirs();
        }
        Archiver archiver = ArchiverFactory.createArchiver("tar", "gz");
        File[] tarGzFolderFiles = tarGzFolder.listFiles();
        if (tarGzFolderFiles != null) {
            for (File tarGzFile : tarGzFolderFiles) {
                archiver.extract(tarGzFile, xmlFolderFiles);
            }
        }
    }

    public static String recordXmlToMarcXml(String recordXml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(recordXml)));

        Node oldRoot = doc.getDocumentElement();
        Document newDoc = builder.newDocument();
        Element newRoot = newDoc.createElement("collection");
        newDoc.appendChild(newRoot);
        newRoot.appendChild(newDoc.importNode(oldRoot, true));

        DOMSource domSource = new DOMSource(newDoc);

        TransformerFactory tFactory = TransformerFactory.newInstance();
        Transformer transformer = tFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty("indent", "no");
        StringWriter sw = new StringWriter();
        StreamResult result = new StreamResult(sw);
        transformer.transform(domSource, result);

        return sw.toString();

    }

}
