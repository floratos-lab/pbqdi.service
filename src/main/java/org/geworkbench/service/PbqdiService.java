package org.geworkbench.service;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import java.util.Properties;
import java.util.List;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.geworkbench.service.pbqdi.schema.PbqdiResponse;

import org.geworkbench.plugins.pbqdi.DrugResult;
import org.geworkbench.plugins.pbqdi.IndividualDrugInfo;

@Service
public class PbqdiService {
    Log log = LogFactory.getLog(PbqdiService.class);

    private final String R_PATH;
    private final String OUTPUT_PATH;
    private final String BASE_WORKING_DIRECTORY;
    private final String SOURCE_SCRIPT_DIRECTORY;
    private final String ERROR_FILE;
    private final String HTML_LOCATION;
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
        OUTPUT_PATH = prop.getProperty("pbqdi.output.path");
        BASE_WORKING_DIRECTORY = prop.getProperty("pbqdi.working.directory");
        SOURCE_SCRIPT_DIRECTORY = prop.getProperty("source.script.directory");
        ERROR_FILE = prop.getProperty("pbqdi.error.file");
        HTML_LOCATION = prop.getProperty("html.location");
        TEMP_DIR = prop.getProperty("temp.directory");
    }

    public PbqdiResponse execute(String tumorType, String sampleFile, String content) {
        int jobId = new java.util.Random().nextInt(Integer.MAX_VALUE);
        String WORKING_DIRECTORY = BASE_WORKING_DIRECTORY + jobId + "/";

        try {
            prepareSourceFiles(SOURCE_SCRIPT_DIRECTORY, WORKING_DIRECTORY, sampleFile, content);
            Files.createDirectories(FileSystems.getDefault().getPath(HTML_LOCATION + "pbqdi_run/" + jobId + "/"));
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
            String reportFilename = readPdfFileName(WORKING_DIRECTORY);
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

        String[] qualityImages = readQualitySection(WORKING_DIRECTORY, jobId);
        DrugResult result1 = readDrugSection(WORKING_DIRECTORY+"oncology.txt", jobId);
        DrugResult result2 = readDrugSection(WORKING_DIRECTORY+"non-oncology.txt", jobId);
        DrugResult result3 = readDrugSection(WORKING_DIRECTORY+"investigational.txt", jobId);
        // FIXME - not finished

        PbqdiResponse response = new PbqdiResponse();
        response.setTumorType(tumorType);
        response.setSampleNames("THIS_SHOUDLD_BE_A_LIST");
        response.setClassAssignment("THIS_SHOUDLD_BE_A_MAP");

        // the zip file contains one HTML, one PDF, and a number of PNG files
        String zipfile = TEMP_DIR+"result"+jobId+".zip";
        try{
            ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(zipfile));
            for(String image: qualityImages) {
                Path path = Paths.get(HTML_LOCATION+image);
                if(!path.toFile().exists()) {
                    log.error("image file "+path.toFile()+" not found");
                    continue; 
                }
                zipOutputStream.putNextEntry(new ZipEntry( path.toFile().getName() ));
                zipOutputStream.write(Files.readAllBytes( path ));
                zipOutputStream.closeEntry();
            }

            zipOutputStream.close();
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

    private String[] readQualitySection(String workingDirectory, final int jobId) {
        List<String> list = new ArrayList<String>();
        BufferedReader br;
        try {
            br = new BufferedReader(new FileReader(workingDirectory + "qc.txt"));
            String line = br.readLine();
            while (line != null) {
                list.add(convertImage(line, jobId));
                line = br.readLine();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return list.toArray(new String[list.size()]);
    }

    private String convertImage(String pdfFile, final int jobId) {
        String shortName = pdfFile.substring(pdfFile.lastIndexOf("/") + 1, pdfFile.lastIndexOf(".pdf"));
        String imageFile = "pbqdi_run/"+jobId+"/"+shortName+".png";
        String os = System.getProperty("os.name").toLowerCase();
        String command = null;
        if(os.contains("win")) {
            command = "C:\\Program Files\\ImageMagick-7.0.5-Q16\\magick";
        } else {
            command = "/usr/bin/convert";
        }
        ProcessBuilder pb = new ProcessBuilder(command, pdfFile, HTML_LOCATION+imageFile);
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
        return "/"+imageFile;
    }

    private DrugResult readDrugSection(String filename, final int jobId) {
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
                        img.add(convertImage(line, jobId));
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
