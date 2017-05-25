package org.geworkbench.service;

import org.geworkbench.service.pbqdi.schema.PbqdiRequest;
import org.geworkbench.service.pbqdi.schema.PbqdiResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.BufferedReader;

import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;

import javax.activation.DataHandler;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPException;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * This is an example client demonstrating how to use PBQDI web service.
 * Outside the maven project, this program would depend on spring framework and the schema-based generated classes.
 * So you might need to run it like this:
 * java -cp .;"C:\apache-tomcat-7.0.41\webapps\pbqdi\WEB-INF\lib\*";"C:\apache-tomcat-7.0.41\webapps\pbqdi\WEB-INF\classes" org.geworkbench.service.DemoClient
 */
public class DemoClient {
    Log log = LogFactory.getLog(DemoClient.class);

    static public void main(String[] args) throws JAXBException, SOAPException, IOException { 
        /* test web services locally deployed */
        String SERVICE_URL = "http://localhost:8080/pbqdi";

        /* inputs */
        String TUMOR_TYPE = "gbm";
        String SAMPLE_FILE = "F:\\cptac_project\\test1\\CUAC1468.txt";

        /* output directory */
        String OUTPUT_DIRECTORY = "F:/deleteme/demo";

        PbqdiRequest request = new PbqdiRequest();
        request.setTumorType(TUMOR_TYPE);
        request.setSampleFile(new java.io.File(SAMPLE_FILE).getName());

        BufferedReader br = new BufferedReader(new FileReader(SAMPLE_FILE));
        StringBuffer sb = new StringBuffer();
        String line = br.readLine();
        while(line!=null) {
            sb.append(line).append('\n');
            line = br.readLine();
        }
        br.close();
        request.setFileContent(sb.toString());

        QName qname = new QName(PbqdiEndpoint.NAMESPACE_URI, PbqdiEndpoint.REQUEST_LOCAL_NAME);
        JAXBElement<PbqdiRequest> requestElement = new JAXBElement<PbqdiRequest>(qname, PbqdiRequest.class, request);

        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath("org.geworkbench.service.pbqdi.schema");
        marshaller.setMtomEnabled(true);
        MessageFactory mf = MessageFactory.newInstance();
        WebServiceTemplate template = new WebServiceTemplate(new SaajSoapMessageFactory(mf));
        template.setMarshaller(marshaller);
        template.setUnmarshaller(marshaller);

        PbqdiResponse response = (PbqdiResponse)template.marshalSendAndReceive(SERVICE_URL, requestElement);

        DataHandler resultPackage = response.getResultPackage();
        /* the following code works perfectly (if we want the zip file itself), 
            but I want to unzip it directly. */
        /*
        try {
            InputStream inputStream = resultPackage.getInputStream();

            File targetFile = new File(OUTPUT_DIRECTORY+File.separator+"output.zip");
            OutputStream outStream = new FileOutputStream(targetFile);
            byte[] buffer = new byte[8 * 1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outStream.write(buffer, 0, bytesRead);
            }
            inputStream.close();
            outStream.close();
        } catch(IOException e) {
            e.printStackTrace();
        }*/
        ZipEntry entry;
        byte[] buffer = new byte[8 * 1024];

        try {
            InputStream inputStream = resultPackage.getInputStream();
            ZipInputStream zipInputStream = new ZipInputStream(inputStream);
            while( (entry = zipInputStream.getNextEntry())!=null ) {
                String s = String.format("Entry: %s len %d added %TD",
                            entry.getName(), entry.getSize(),
                            new java.util.Date(entry.getTime()));
                System.out.println(s);

                String outpath = OUTPUT_DIRECTORY + "/" + entry.getName();
                FileOutputStream outputFileStream = new FileOutputStream(outpath);
                int len = 0;
                while ((len = zipInputStream.read(buffer)) > 0)
                {
                    outputFileStream.write(buffer, 0, len);
                }
                outputFileStream.close();
            }
            zipInputStream.close();
            inputStream.close();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }
}
