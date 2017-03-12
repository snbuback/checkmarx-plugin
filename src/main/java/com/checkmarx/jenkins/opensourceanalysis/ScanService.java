package com.checkmarx.jenkins.opensourceanalysis;

import com.checkmarx.jenkins.CxConfig;
import com.checkmarx.jenkins.CxWebService;
import com.checkmarx.jenkins.OsaScanResult;
import com.checkmarx.jenkins.filesystem.FolderPattern;
import com.checkmarx.jenkins.filesystem.zip.CxZip;
import com.checkmarx.jenkins.filesystem.zip.Zipper;
import com.checkmarx.jenkins.logger.CxPluginLogger;
import hudson.FilePath;
import org.apache.commons.io.FileUtils;

/**
 * @author tsahi
 * @since 02/02/16
 */
public class ScanService {

    private transient CxPluginLogger logger;

    private static final String OSA_RUN_STARTED = "OSA (open source analysis) Run has started";
    private static final String OSA_RUN_ENDED = "OSA (open source analysis) Run has finished successfully";
    private static final String OSA_RUN_SUBMITTED = "OSA (open source analysis) submitted successfully";
    public static final String NO_LICENSE_ERROR = "Open Source Analysis License is not enabled for this project. Please contact your CxSAST Administrator";
    private DependencyFolder dependencyFolder;
    private CxWebService webServiceClient;
    private final CxZip cxZip;
    private final FolderPattern folderPattern;
    private ScanResultsPresenter scanResultsPresenter;
    private ScanSender scanSender;
    private LibrariesAndCVEsExtractor librariesAndCVEsExtractor;

    public ScanService(ScanServiceTools scanServiceTools) {
        this.dependencyFolder = scanServiceTools.getDependencyFolder();
        this.webServiceClient = scanServiceTools.getWebServiceClient();
        this.cxZip = new CxZip(scanServiceTools.getBuild(), scanServiceTools.getWorkspace(), scanServiceTools.getListener());
        this.folderPattern = new FolderPattern(scanServiceTools.getBuild(), scanServiceTools.getListener());
        this.scanResultsPresenter = new ScanResultsPresenter(scanServiceTools.getListener());
        this.scanSender = new ScanSender(scanServiceTools.getOsaScanClient(), scanServiceTools.getProjectId());
        this.librariesAndCVEsExtractor = new LibrariesAndCVEsExtractor(scanServiceTools.getOsaScanClient());
        this.logger = new CxPluginLogger(scanServiceTools.getListener());
    }

    public OsaScanResult scan(boolean asynchronousScan) {
        OsaScanResult osaScanResult = new OsaScanResult();
        FilePath sourceCodeZip = null;

        try {
            if (!validLicense()) {
                logger.error(NO_LICENSE_ERROR);
                osaScanResult.setIsOsaReturnedResult(false);
                return osaScanResult;
            }

            sourceCodeZip = zipOpenSourceCode();
            if (asynchronousScan) {
                logger.info(OSA_RUN_SUBMITTED);
                scanSender.sendAsync(sourceCodeZip);
                return null;
            } else {
                logger.info(OSA_RUN_STARTED);
                scanSender.sendScanAndSetResults(sourceCodeZip, osaScanResult);
                logger.info(OSA_RUN_ENDED);
                scanResultsPresenter.printResultsToOutput(osaScanResult.getGetOpenSourceSummaryResponse());
            }
        } catch (Zipper.MaxZipSizeReached zipSizeReached) {
            exposeZippingLogToJobConsole(zipSizeReached);
            logger.error("Open Source Analysis failed: When zipping file " + zipSizeReached.getCurrentZippedFileName() + ", reached maximum upload size limit of "
                    + FileUtils.byteCountToDisplaySize(CxConfig.maxOSAZipSize()) + "\n");
        } catch (Zipper.NoFilesToZip noFilesToZip) {
            exposeZippingLogToJobConsole(noFilesToZip);
            logger.error("Open Source Analysis failed: No files to scan");
        } catch (Zipper.ZipperException zipException) {
            exposeZippingLogToJobConsole(zipException);
            logger.error("Open Source Analysis failed: " + zipException.getMessage(), zipException);
        } catch (Exception e) {
            logger.error("Open Source Analysis failed: " + e.getMessage(), e);
        } finally {
            //delete temp file
            if(sourceCodeZip != null) {
                deleteTemporaryFile(sourceCodeZip);
            }

        }
        return osaScanResult;
    }

    private boolean validLicense() {
        return webServiceClient.isOsaLicenseValid();
    }

    private FilePath zipOpenSourceCode() throws Exception {
        String combinedFilterPattern = folderPattern.generatePattern(dependencyFolder.getInclude(), dependencyFolder.getExclude());
        return cxZip.zipSourceCode(combinedFilterPattern);
    }

    private void exposeZippingLogToJobConsole(Zipper.ZipperException zipperException){
        logger.info(zipperException.getZippingDetails().getZippingLog());
    }

    private void deleteTemporaryFile(FilePath file) {
        try {
            if (file.exists()) {
                if (file.delete()) {
                    logger.info("Temporary file deleted");
                } else {
                    logger.info("Fail to delete temporary file");
                }
            }
        } catch (Exception e) {
            logger.error("Fail to delete temporary file", e);
        }
    }

}
