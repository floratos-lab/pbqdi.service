package org.geworkbench.service;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.IOException;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;

import org.geworkbench.service.pbqdi.schema.PbqdiResponse;

@Service
public class PbqdiService {

    public PbqdiResponse execute(String tumorType, String sampleFile, String content) {
        PbqdiResponse response = new PbqdiResponse();
        response.setTumorType(tumorType);
        response.setSampleNames("THIS_SHOUDLD_BE_A_LIST");
        response.setClassAssignment("THIS_SHOUDLD_BE_A_MAP");

        // TODO for the very first step, let's return a zip file here
        // the zip file contains one HTML, one PDF, and a number of PNG files
        String zipfile = "F:/deleteme/all_tables.zip"; // TODO create it
        File zipFile = new File(zipfile);
        response.setResultPackage(new DataHandler(new FileDataSource(zipFile)));

        return response;
    }
}
