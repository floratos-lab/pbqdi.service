package org.geworkbench.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

import javax.xml.namespace.QName;
import javax.xml.bind.JAXBElement;
import org.geworkbench.service.pbqdi.schema.PbqdiRequest;
import org.geworkbench.service.pbqdi.schema.PbqdiResponse;

@Endpoint
public class PbqdiEndpoint {
    public static final String NAMESPACE_URI = "http://www.geworkbench.org/service/pbqdi";
    public static final String REQUEST_LOCAL_NAME = "pbqdiRequest";
    public static final String RESPONSE_LOCAL_NAME = "pbqdiResponse";

    private final PbqdiService pbqdiService;

    @Autowired
    public PbqdiEndpoint(PbqdiService pbqdiService) {
        this.pbqdiService = pbqdiService;
    }

    @PayloadRoot(localPart = REQUEST_LOCAL_NAME, namespace = NAMESPACE_URI)
    @ResponsePayload
    public JAXBElement<PbqdiResponse> handleRequest(@RequestPayload PbqdiRequest x) {
        String tumorType = x.getTumorType();
        String sampleFile = x.getSampleFile();
        String content = x.getFileContent();

        PbqdiResponse response = pbqdiService.execute(tumorType, sampleFile, content);

        QName qname = new QName(PbqdiEndpoint.NAMESPACE_URI, PbqdiEndpoint.RESPONSE_LOCAL_NAME);
        JAXBElement<PbqdiResponse> responseElement = new JAXBElement<PbqdiResponse>(qname, PbqdiResponse.class, response);

        return responseElement;
    }
}
