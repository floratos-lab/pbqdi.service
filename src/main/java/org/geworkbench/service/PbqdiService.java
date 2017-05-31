package org.geworkbench.service;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import java.util.Properties;
import java.util.List;
import java.util.ArrayList;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.geworkbench.service.pbqdi.schema.PbqdiResponse;

import org.geworkbench.plugins.pbqdi.DrugResult;
import org.geworkbench.plugins.pbqdi.IndividualDrugInfo;
import org.geworkbench.plugins.pbqdi.ResultData;

@Service
public class PbqdiService {
    private static Log log = LogFactory.getLog(PbqdiService.class);

    private final String R_PATH;
    private final String BASE_WORKING_DIRECTORY;
    private final String SOURCE_SCRIPT_DIRECTORY;
    private final String ERROR_FILE;
    private final String TEMP_DIR;

    public PbqdiService() {
        Properties prop = new Properties();
        try {
            // InputStream x = PbqdiService.class.getResourceAsStream("/pbqdi.properties");
            // System.out.println("x="+x);
            // prop.load(x);
            prop.load(getClass().getResourceAsStream("/pbqdi.properties"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        R_PATH = prop.getProperty("r.path");
        BASE_WORKING_DIRECTORY = prop.getProperty("pbqdi.working.directory");
        SOURCE_SCRIPT_DIRECTORY = prop.getProperty("source.script.directory");
        ERROR_FILE = prop.getProperty("pbqdi.error.file");
        TEMP_DIR = prop.getProperty("temp.directory");
    }

    public PbqdiResponse execute(String tumorType, String sampleFile, String content) {
        int jobId = new java.util.Random().nextInt(Integer.MAX_VALUE);
        String WORKING_DIRECTORY = BASE_WORKING_DIRECTORY + jobId + "/";

        try {
            prepareSourceFiles(SOURCE_SCRIPT_DIRECTORY, WORKING_DIRECTORY, sampleFile, content);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        ProcessBuilder pb1 = new ProcessBuilder(R_PATH + "Rscript", "--vanilla",
                WORKING_DIRECTORY + "classifySamples.r", tumorType, sampleFile, WORKING_DIRECTORY, ERROR_FILE);
        pb1.directory(new File(WORKING_DIRECTORY));
        try {
            Process process = pb1.start();
            if (log.isDebugEnabled()) {
                InputStream stream = process.getErrorStream();
                byte[] b = new byte[1024];
                int n = -1;
                while ((n = stream.read(b)) >= 0) {
                    System.out.println(":::" + new String(b, 0, n));
                }
            }
            int exit = process.waitFor();
            if (exit != 0) {
                log.error("something went wrong with classification script: exit value " + exit);
                return null;
            }
        } catch (IOException | InterruptedException e1) {
            e1.printStackTrace();
            return null;
        }

        ProcessBuilder pb2 = new ProcessBuilder(R_PATH + "rscript", "--vanilla",
                WORKING_DIRECTORY + "rununsupervised.r", tumorType, sampleFile, WORKING_DIRECTORY, ERROR_FILE);
        pb2.directory(new File(WORKING_DIRECTORY));

        String reportFilename = null;
        try {
            Process process = pb2.start();
            if (log.isDebugEnabled()) {
                InputStream stream = process.getErrorStream();
                byte[] b = new byte[1024];
                int n = -1;
                while ((n = stream.read(b)) >= 0) {
                    System.out.println(":::" + new String(b, 0, n));
                }
            }
            int exit = process.waitFor();
            reportFilename = readPdfFileName(WORKING_DIRECTORY);
            if (exit != 0) {
                log.error("something went wrong with drug report script: exit value " + exit);
                return null;
            } else if (new File(reportFilename).exists()) {
                log.debug("report created");
            } else {
                log.error("this report file does not exist: " + reportFilename);
                return null;
            }
        } catch (IOException | InterruptedException e1) {
            e1.printStackTrace();
            return null;
        }

        String[] qualityImages = readQualitySection(WORKING_DIRECTORY);
        DrugResult resultOncology = readDrugSection(WORKING_DIRECTORY+"oncology.txt", WORKING_DIRECTORY);
        DrugResult resultNononcology = readDrugSection(WORKING_DIRECTORY+"non-oncology.txt", WORKING_DIRECTORY);
        DrugResult resultInvestigational = readDrugSection(WORKING_DIRECTORY+"investigational.txt", WORKING_DIRECTORY);

        String reportPdf = reportFilename.substring(reportFilename.lastIndexOf("/"));
        String htmlReport = sampleFile.substring(0, sampleFile.lastIndexOf(".txt")) + ".html";

        ResultData result = new ResultData(qualityImages, resultOncology, resultNononcology, resultInvestigational,
            reportPdf, htmlReport, WORKING_DIRECTORY);

        if(! new File(WORKING_DIRECTORY + "classAssignments.txt").exists()) { // debug
            log.debug(new File(WORKING_DIRECTORY + "classAssignments.txt").getAbsolutePath()+" does not exists");
        }
        StringBuffer classAssignments = new StringBuffer();
        BufferedReader br;
        try {
            br = new BufferedReader(new FileReader(WORKING_DIRECTORY + "classAssignments.txt"));
            String line = br.readLine();
            while (line != null && line.trim().length() > 0) {
                classAssignments.append(line).append('\n');
                line = br.readLine();
            }
            br.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        PbqdiResponse response = new PbqdiResponse();
        response.setTumorType(tumorType);
        response.setSampleNames("THIS_SHOUDLD_BE_A_LIST");
        response.setClassAssignment(classAssignments.toString());

        String zipfile = TEMP_DIR+"result"+jobId+".zip";
        try{
            result.zip(zipfile);
        } catch(IOException e) {
            e.printStackTrace();
        }

        File file = new File(zipfile);
        response.setResultPackage(new DataHandler(new FileDataSource(file)));

        return response;
    }

    private static void prepareSourceFiles(String sourceDir, String targetDir, String sampleFile, String content)
            throws IOException {
        Path dir = FileSystems.getDefault().getPath(targetDir);
        Files.createDirectories(dir);

        PrintWriter pw = new PrintWriter(new FileWriter(new File(targetDir + sampleFile)));
        pw.print(content);
        pw.close();

        String[] files = { "classifySamples.r", "tumorSubtypes.txt", "rununsupervised.r", "properties.r",
                "unsupervised.Rnw", "data-load-qc.r", "norm-viper.r", "oncoTarget-analysis.r" };
        for (String f : files) {
            Path source = FileSystems.getDefault().getPath(sourceDir + f);
            Path target = FileSystems.getDefault().getPath(targetDir + f);
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String readPdfFileName(String workingDirectory) {
        BufferedReader br;
        String pdf = "";
        try {
            br = new BufferedReader(new FileReader(workingDirectory + "pdfreport.txt"));
            pdf = br.readLine();
            br.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return pdf;
    }

    private String[] readQualitySection(String workingDirectory) {
        List<String> list = new ArrayList<String>();
        BufferedReader br;
        try {
            br = new BufferedReader(new FileReader(workingDirectory + "qc.txt"));
            String line = br.readLine();
            while (line != null) {
                list.add(convertImage(line, workingDirectory));
                line = br.readLine();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return list.toArray(new String[list.size()]);
    }

    private String convertImage(String pdfFile, final String workingDirectory) {
        String shortName = pdfFile.substring(pdfFile.lastIndexOf("/") + 1, pdfFile.lastIndexOf(".pdf"));
        String imageFile = workingDirectory + shortName+".png";
        String os = System.getProperty("os.name").toLowerCase();
        String command = null;
        if(os.contains("win")) {
            command = "C:\\Program Files\\ImageMagick-7.0.5-Q16\\magick";
        } else {
            command = "/usr/bin/convert";
        }
        ProcessBuilder pb = new ProcessBuilder(command, pdfFile, imageFile);
        int exit = -1;
        try {
            Process process = pb.start();
            exit = process.waitFor();
        } catch(IOException | InterruptedException e) {
            e.printStackTrace();
        }
        if (exit != 0) {
            log.error("converting image failed: exit value "+exit);
        }
        return "./"+shortName+".png";
    }

    private DrugResult readDrugSection(String filename, final String workingDirectory) {
        List<List<String>> images = new ArrayList<List<String>>();
        List<List<IndividualDrugInfo>> drugs = new ArrayList<List<IndividualDrugInfo>>();

        char fieldId = 0; // I for image, N for drug names, D for drug
                          // description, A for accessions
        List<String> img = null;
        List<String> drugNames = null;
        List<String> descriptions = null;
        List<String> accessions = null;
        BufferedReader br;
        try {
            br = new BufferedReader(new FileReader(filename));
            String line = br.readLine();
            while (line != null) {
                if (line.equals("images")) {
                    fieldId = 'I';
                    img = new ArrayList<String>();
                } else if (line.equals("drugNames")) {
                    fieldId = 'N';
                    drugNames = new ArrayList<String>();
                } else if (line.equals("drugDescriptions")) {
                    fieldId = 'D';
                    descriptions = new ArrayList<String>();
                } else if (line.equals("drugAccessions")) {
                    fieldId = 'A';
                    accessions = new ArrayList<String>();
                } else if (line.equals("%%")) {
                    // another card
                    fieldId = 0;
                    images.add(img);
                    List<IndividualDrugInfo> drugsForOneRow = new ArrayList<IndividualDrugInfo>();
                    for (int i = 0; i < drugNames.size(); i++) {
                        drugsForOneRow
                                .add(new IndividualDrugInfo(drugNames.get(i), descriptions.get(i), accessions.get(i)));
                    }
                    drugs.add(drugsForOneRow);
                } else {
                    switch (fieldId) {
                    case 'I':
                        img.add(convertImage(line, workingDirectory));
                        break;
                    case 'N':
                        drugNames.add(line);
                        break;
                    case 'D':
                        descriptions.add(line);
                        break;
                    case 'A':
                        accessions.add(line);
                        break;
                    }
                }
                line = br.readLine();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new DrugResult(images, drugs);
    }

}
