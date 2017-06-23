# PBQDI Web Service

This web service provies Patient-Based Query and Data Integration (PBQDI) for the geWorkbench.

## The input contains:
1. tumor type
1. the name of sample file
1. the content of the same file

## The output contains:
1. tumor type
1. class assignments (map from sample names to subtype ID's)
1. result package as a zipped package

### This zipped package includes the following entries:

  * one HTML file
  * one PDF file
  * a number of image files. They are referenced from four different sections of the HTML file (the 'report'):
  one group for data quality, and the three groups for different categories of drugs, namely ontological, non-oncological, and investigational.
  The same image may show up in more than one categories of the drugs.

## Dependency:

For this service to run, you need R installed, a number of required libraries, and the following R scripts and starting data files that are not included in the github repository
due to copyright difference:
1. classifySamples.r
1. rununsupervised.r
1. properties.r
1. data-load-qc.r
1. norm-viper.r
1. oncoTarget-analysis.r
1. tumorSubtypes.txt
1. unsupervised.Rnw

### dependency within R

* R version needs to be 3.3 or later
* 5 local packages need to be copied to the R library directory (specific location depending on your R installation). Their names are clinicalTrials, drugbank, n1database, n1platform, and tth. The actual code is not open-sourced.
  * Biobase: used by n1platform. It is not avaiable in the public repository. To install, see https://bioconductor.org/packages/release/bioc/html/Biobase.html (No everything works forthrightly from this site. I ended up fiddling around more with BioInstaller to get it work on Linux.)

## Clients:

An example client program in Java is provided. The client does not store the ZIP file; instead, it unzips the contained files 
so they are ready to be used, e.g. the HTML file to be shown by a web browser or a browser component in other applications.
