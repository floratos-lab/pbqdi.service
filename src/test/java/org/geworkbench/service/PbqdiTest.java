package org.geworkbench.service;

import junit.framework.TestCase;

import org.geworkbench.service.pbqdi.schema.PbqdiRequest;
import org.geworkbench.service.pbqdi.schema.PbqdiResponse;

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
        QName qname = new QName(PbqdiEndpoint.NAMESPACE_URI, PbqdiEndpoint.REQUEST_LOCAL_NAME);
        JAXBElement<PbqdiRequest> requestElement = new JAXBElement<PbqdiRequest>(qname, PbqdiRequest.class, request);
        assertFalse(requestElement==null);
        PbqdiEndpoint endpoint = new PbqdiEndpoint(); //"http://localhost:8008/pbqdi");
        JAXBElement<PbqdiResponse> responseElement = endpoint.handleRequest(requestElement);
        PbqdiResponse response = responseElement.getValue();
        assertTrue("incorrect PDF "+response.getPdfreport(), response.getPdfreport().equals("PDF for tumor1"));
        assertTrue("incorrect ontology "+response.getOncology(), response.getOncology().equals("ontology for sample1"));
        assertTrue("incorrect non-ontology "+response.getNonOncology(), response.getNonOncology().equals("non-ontology result"));
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
        MessageFactory mf = MessageFactory.newInstance();
        WebServiceTemplate template = new WebServiceTemplate(new SaajSoapMessageFactory(mf));
        template.setMarshaller(marshaller);
        template.setUnmarshaller(marshaller);

        PbqdiResponse response = (PbqdiResponse)template.marshalSendAndReceive("http://localhost:8080/pbqdi", requestElement);
        assertTrue("incorrect PDF "+response.getPdfreport(), response.getPdfreport().equals("PDF for tumor2"));
        assertTrue("incorrect investigational "+response.getInvestigational(), response.getInvestigational().equals("investigational result"));
    }
}
