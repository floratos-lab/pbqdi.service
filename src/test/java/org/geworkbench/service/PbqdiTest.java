package org.geworkbench.service;

import junit.framework.TestCase;

import org.geworkbench.service.pbqdi.schema.PbqdiRequest;
import org.geworkbench.service.pbqdi.schema.PbqdiResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.File;

import javax.activation.DataHandler;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPException;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;

public class PbqdiTest extends TestCase {

     public void test() {
        PbqdiRequest request = new PbqdiRequest();
        request.setTumorType("tumor1");
        request.setSampleFile("sample1");
        PbqdiEndpoint endpoint = new PbqdiEndpoint(new PbqdiService());
        JAXBElement<PbqdiResponse> responseElement = endpoint.handleRequest(request);
        PbqdiResponse response = responseElement.getValue();
        assertTrue("incorrect tumor type '"+response.getTumorType()+"'", response.getTumorType().equals("tumor1"));
        //assertTrue("incorrect class assignment "+response.getClassAssignment(), response.getClassAssignment().equals("... to be added"));
        //assertTrue("incorrect sample names "+response.getSampleNames(), response.getSampleNames().equals("...to be added"));

        DataHandler resultPackage = response.getResultPackage();
        // re-create the zip file from the client side
        try {
            InputStream inputStream = resultPackage.getInputStream();
            assertNotNull(inputStream);

            File targetFile = new File("F:/deleteme/test_output.zip");
            OutputStream outStream = new FileOutputStream(targetFile);
            byte[] buffer = new byte[8 * 1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outStream.write(buffer, 0, bytesRead);
            }
            inputStream.close();
            outStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // TODO do something with ZIP file, e.g. checking the number of files and some file names
     }

    public void test_web() throws JAXBException, SOAPException { /* test web services locally deployed */
        PbqdiRequest request = new PbqdiRequest();
        request.setTumorType("tumor2");
        request.setSampleFile("sample2");
        QName qname = new QName(PbqdiEndpoint.NAMESPACE_URI, PbqdiEndpoint.REQUEST_LOCAL_NAME);
        JAXBElement<PbqdiRequest> requestElement = new JAXBElement<PbqdiRequest>(qname, PbqdiRequest.class, request);
        assertFalse(requestElement==null);

        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath("org.geworkbench.service.pbqdi.schema");
        marshaller.setMtomEnabled(true);
        MessageFactory mf = MessageFactory.newInstance();
        WebServiceTemplate template = new WebServiceTemplate(new SaajSoapMessageFactory(mf));
        template.setMarshaller(marshaller);
        template.setUnmarshaller(marshaller);

        PbqdiResponse response = (PbqdiResponse)template.marshalSendAndReceive("http://localhost:8080/pbqdi", requestElement);
        assertTrue("incorrect tumor type '"+response.getTumorType()+"'", response.getTumorType().equals("tumor2"));
        //assertTrue("incorrect class assignment "+response.getClassAssignment(), response.getClassAssignment().equals("... to be added"));
        //assertTrue("incorrect sample names "+response.getSampleNames(), response.getSampleNames().equals("...to be added"));

        DataHandler resultPackage = response.getResultPackage();
        // TODO do something with ZIP file, e.g. checking the number of files and some file names
        // re-create the file/stream
        try {
            InputStream inputStream = resultPackage.getInputStream();
            assertNotNull(inputStream);
            File targetFile = new File("F:/deleteme/test_output_2.zip");
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
        }
    }
}
