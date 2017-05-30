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
import org.geworkbench.plugins.pbqdi.ResultData;

@Service
public class PbqdiService {
    Log log = LogFactory.getLog(PbqdiService.class);

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

        ResultData result = new ResultData(qualityImages, resultOncology, resultNononcology, resultInvestigational);

        String reportPdf = reportFilename.substring(reportFilename.lastIndexOf("/"));
        String htmlReport = WORKING_DIRECTORY + sampleFile.substring(0, sampleFile.lastIndexOf(".txt")) + ".html";
        createHtmlReport(result, reportPdf, htmlReport);

        PbqdiResponse response = new PbqdiResponse();
        response.setTumorType(tumorType);
        response.setSampleNames("THIS_SHOUDLD_BE_A_LIST");
        response.setClassAssignment("THIS_SHOUDLD_BE_A_MAP");

        // the zip file contains one HTML, one PDF, and a number of PNG files
        String zipfile = TEMP_DIR+"result"+jobId+".zip";
        try{
            ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(zipfile));
            for(String image: qualityImages) {
                Path path = Paths.get(WORKING_DIRECTORY+image);
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

    private static String accessionLink(String drugName, String accession) {
        if (accession == null || accession.equals("NA"))
            return "<b>"+drugName+"</b>. ";
        else
            return "<a href='http://www.drugbank.ca/drugs/" + accession + "' target=_blank><b>" + drugName + "</a></b>. ";
    }

    private void createHtmlReport(final ResultData result, final String reportPdf, final String htmlFile) {
        DrugResult oncology = result.oncology;
        List<List<String>> images = oncology.images;
        List<List<IndividualDrugInfo>> drugs = oncology.drugs;

        String openingHtmlContent = "<!DOCTYPE html><html lang='en'><head><meta charset='utf-8'><meta http-equiv='X-UA-Compatible' content='IE=edge'><meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<link rel='stylesheet' href='https://maxcdn.bootstrapcdn.com/bootstrap/3.3.4/css/bootstrap.min.css'>"
                + "<link rel='stylesheet' href='https://maxcdn.bootstrapcdn.com/bootstrap/3.3.4/css/bootstrap-theme.min.css'>"
                + "</head><body>";

        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new FileWriter(htmlFile));
            pw.print(openingHtmlContent);

            pw.print("<div style='position:fixed;background:white;width:100%;z-index:999'><h1>Drug Prediction Report</h1><a href='./" + reportPdf
                    + "' target=_blank>Download Full Report as PDF</a> <a href='#dataquality'>Data Quality</a> <a href='#ontology'>Ontology drugs</a> <a href='#nonontology'>Nonontology drugs</a> <a href='#investigational'>Investigational drugs</a></div>");

            pw.print("<div style='position:absolute;top:100px'>");
            pw.print("<a id='dataquality' style='display:block;position:relative;top:-100px'></a><h2>Data Quality</h2>"
                    + "<p>The figure below portrays indicators of data quality for the sample:</p>"
                    + "<ul><li>Mapped Reads: the total number of mapped reads</li><li>Detected genes: the number of detected genes with at least 1 mapped read</li><li>Expressed genes: the number of expressed genes inferred from the distribution of the digital expression data</li></ul>");

            for (int i = 0; i < result.dataQualityImages.length; i++) {
                pw.print("<img src='"+result.dataQualityImages[i]+"' />");
            }

            pw.print("<h2>FDA Approved Drugs</h2><a id='ontology' style='display:block;position:relative;top:-100px'></a><h3>Oncology Drugs</h3><hr><table>");
            for (int i = 0; i < images.size(); i++) {
                pw.print("<tr style='border-top:1px solid black; border-bottom:1px solid black'><td>" + (i + 1) + "</td><td width='30%'>");
                for (String img : images.get(i)) {
                    pw.print("<img src='" + img + " '/>");
                }
                pw.print("</td><td valign=top width='70%'><ul>");
                for (IndividualDrugInfo d : drugs.get(i)) {
                    pw.print("<li>" + accessionLink(d.name, d.accession) + d.description + "</li>");
                }
                pw.print("</ul></td></tr>");
            }
            pw.print("</table><a id='nonontology' style='display:block;position:relative;top:-100px'></a><h3>Non-oncology Drugs</h3><table>");

            DrugResult nononcology = result.nononcology;
            images = nononcology.images;
            drugs = nononcology.drugs;
            for (int i = 0; i < images.size(); i++) {
                pw.print("<tr style='border-top:1px solid black; border-bottom:1px solid black'><td>" + (i + 1) + "</td><td width='30%'>");
                for (String img : images.get(i)) {
                    pw.print("<img src='" + img + " '/>");
                }
                pw.print("</td><td valign=top width='70%'><ul>");
                for (IndividualDrugInfo d : drugs.get(i)) {
                    pw.print("<li>" + accessionLink(d.name, d.accession) + d.description + "</li>");
                }
                pw.print("</ul></td></tr>");
            }
            pw.print("</table>");

            DrugResult investigational = result.investigational;
            images = investigational.images;
            drugs = investigational.drugs;

            pw.print("<a id='investigational' style='display:block;position:relative;top:-100px'></a><h2>Investigational drugs</h1><table>");
            for (int i = 0; i < images.size(); i++) {
                pw.print("<tr style='border-top:1px solid black; border-bottom:1px solid black'><td>" + (i + 1) + "</td><td width='30%'>");
                for (String img : images.get(i)) {
                    pw.print("<img src='" + img + "' />");
                }
                pw.print("</td><td valign=top width='70%'><ul>");
                for (IndividualDrugInfo d : drugs.get(i)) {
                    pw.print("<li>" + accessionLink(d.name, d.accession) + d.description + "</li>");
                }
                pw.print("</ul></td></tr>");
            }
            pw.print("</table>");

            pw.print("</div></body></html>");
            pw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
