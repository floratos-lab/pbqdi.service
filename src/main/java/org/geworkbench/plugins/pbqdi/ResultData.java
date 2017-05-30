package org.geworkbench.plugins.pbqdi;

import java.io.IOException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ResultData {
    private static Log log = LogFactory.getLog(ResultData.class);

    final private String[] dataQualityImages;

    final private DrugResult oncology;
    final private DrugResult nononcology;
    final private DrugResult investigational;

    final private String reportPdf;
    final private String htmlFile;

    final private String workingDIrectory;

    public ResultData(String[] dataQualityImages, DrugResult oncology, DrugResult nononcology,
            DrugResult investigational, final String reportPdf, final String htmlFile,
            final String workingDIrectory) {
        this.dataQualityImages = dataQualityImages;
        this.oncology = oncology;
        this.nononcology = nononcology;
        this.investigational = investigational;

        this.reportPdf = reportPdf;
        this.htmlFile = htmlFile;
        this.workingDIrectory = workingDIrectory;

        createHtmlReport();
    }

    private void createHtmlReport() {
        List<List<String>> images = oncology.images;
        List<List<IndividualDrugInfo>> drugs = oncology.drugs;

        String openingHtmlContent = "<!DOCTYPE html><html lang='en'><head><meta charset='utf-8'><meta http-equiv='X-UA-Compatible' content='IE=edge'><meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<link rel='stylesheet' href='https://maxcdn.bootstrapcdn.com/bootstrap/3.3.4/css/bootstrap.min.css'>"
                + "<link rel='stylesheet' href='https://maxcdn.bootstrapcdn.com/bootstrap/3.3.4/css/bootstrap-theme.min.css'>"
                + "</head><body>";

        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new FileWriter(workingDIrectory + htmlFile));
            pw.print(openingHtmlContent);

            pw.print("<div style='position:fixed;background:white;width:100%;z-index:999'><h1>Drug Prediction Report</h1><a href='./" + reportPdf
                    + "' target=_blank>Download Full Report as PDF</a> <a href='#dataquality'>Data Quality</a> <a href='#ontology'>Ontology drugs</a> <a href='#nonontology'>Nonontology drugs</a> <a href='#investigational'>Investigational drugs</a></div>");

            pw.print("<div style='position:absolute;top:100px'>");
            pw.print("<a id='dataquality' style='display:block;position:relative;top:-100px'></a><h2>Data Quality</h2>"
                    + "<p>The figure below portrays indicators of data quality for the sample:</p>"
                    + "<ul><li>Mapped Reads: the total number of mapped reads</li><li>Detected genes: the number of detected genes with at least 1 mapped read</li><li>Expressed genes: the number of expressed genes inferred from the distribution of the digital expression data</li></ul>");

            for (int i = 0; i < dataQualityImages.length; i++) {
                pw.print("<img src='"+dataQualityImages[i]+"' />");
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

    private static String accessionLink(String drugName, String accession) {
        if (accession == null || accession.equals("NA"))
            return "<b>"+drugName+"</b>. ";
        else
            return "<a href='http://www.drugbank.ca/drugs/" + accession + "' target=_blank><b>" + drugName + "</a></b>. ";
    }

    public void zip(final String zipfile) throws IOException {
        ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(zipfile));
        for(String image: dataQualityImages) {
                Path path = Paths.get(workingDIrectory+image);
                if(!path.toFile().exists()) {
                    log.error("image file "+path.toFile()+" not found");
                    continue; 
                }
                zipOutputStream.putNextEntry(new ZipEntry( path.toFile().getName() ));
                zipOutputStream.write(Files.readAllBytes( path ));
                zipOutputStream.closeEntry();
        }

        Set<String> addedImages = new TreeSet<String>();
        for(List<String> images: oncology.images) {
            for(String image: images) {
                    if(addedImages.contains(image)) {
                        continue;
                    }
                    addedImages.add(image);
                    Path path = Paths.get(workingDIrectory+image);
                    if(!path.toFile().exists()) {
                        log.error("image file "+path.toFile()+" not found");
                        continue; 
                    }
                    zipOutputStream.putNextEntry(new ZipEntry( path.toFile().getName() ));
                    zipOutputStream.write(Files.readAllBytes( path ));
                    zipOutputStream.closeEntry();
            }
        }
        for(List<String> images: nononcology.images) {
            for(String image: images) {
                    if(addedImages.contains(image)) {
                        continue;
                    }
                    addedImages.add(image);
                    Path path = Paths.get(workingDIrectory+image);
                    if(!path.toFile().exists()) {
                        log.error("image file "+path.toFile()+" not found");
                        continue; 
                    }
                    zipOutputStream.putNextEntry(new ZipEntry( path.toFile().getName() ));
                    zipOutputStream.write(Files.readAllBytes( path ));
                    zipOutputStream.closeEntry();
            }
        }
        for(List<String> images: investigational.images) {
            for(String image: images) {
                    if(addedImages.contains(image)) {
                        continue;
                    }
                    addedImages.add(image);
                    Path path = Paths.get(workingDIrectory+image);
                    if(!path.toFile().exists()) {
                        log.error("image file "+path.toFile()+" not found");
                        continue; 
                    }
                    zipOutputStream.putNextEntry(new ZipEntry( path.toFile().getName() ));
                    zipOutputStream.write(Files.readAllBytes( path ));
                    zipOutputStream.closeEntry();
            }
        }

        Path path = Paths.get(workingDIrectory+reportPdf);
        zipOutputStream.putNextEntry(new ZipEntry( path.toFile().getName() ));
        zipOutputStream.write(Files.readAllBytes( path ));
        zipOutputStream.closeEntry();

        path = Paths.get(workingDIrectory+htmlFile);
        zipOutputStream.putNextEntry(new ZipEntry( path.toFile().getName() ));
        zipOutputStream.write(Files.readAllBytes( path ));
        zipOutputStream.closeEntry();

        zipOutputStream.close();
    }

}
