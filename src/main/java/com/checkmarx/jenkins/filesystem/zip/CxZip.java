package com.checkmarx.jenkins.filesystem.zip;

import com.checkmarx.jenkins.logger.CxPluginLogger;
import com.checkmarx.jenkins.filesystem.zip.callable.OsaZipperCallable;
import com.checkmarx.jenkins.filesystem.zip.callable.SastZipperCallable;
import com.checkmarx.jenkins.filesystem.zip.dto.CxZipResult;
import hudson.AbortException;
import hudson.FilePath;
import hudson.model.*;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.io.Serializable;

/**
 * Created by tsahib on 7/5/2016.
 */
public class CxZip implements Serializable {

    private static final long serialVersionUID = 1L;

    private transient CxPluginLogger logger;

    private static String CANNOT_FIND_WORKSPACE = "Cannot acquire Jenkins workspace location. It can be due to workspace residing on a disconnected slave.";

    private Run<?, ?> build;
    
    private FilePath workspace;

    public CxZip(final Run<?, ?> build, FilePath workspace, final TaskListener listener) {
        this.build = build;
        this.workspace = workspace;
        this.logger = new CxPluginLogger(listener);
    }

    public FilePath ZipWorkspaceFolder(String filterPattern) throws IOException, InterruptedException {
        FilePath baseDir = this.workspace;
        if (baseDir == null) {
            throw new AbortException(
                    "Checkmarx Scan Failed: "+CANNOT_FIND_WORKSPACE);
        }
        logger.info("Started zipping the workspace, this may take a while.");

        SastZipperCallable sastZipperCallable = new SastZipperCallable(filterPattern);
        final CxZipResult zipResult = zipFileAndGetResult(baseDir, sastZipperCallable);

        logZippingCompletionSummery(zipResult, "Temporary file with zipped and base64 encoded sources", 64);

        return zipResult.getTempFile();
    }

    public FilePath zipSourceCode(String filterPattern) throws Exception {
        FilePath baseDir = this.workspace;
        if (baseDir == null) {
            throw new Exception(CANNOT_FIND_WORKSPACE);
        }

        logger.info("Started zipping files for OSA, this may take a while.");
        OsaZipperCallable osaZipperCallable = new OsaZipperCallable(filterPattern);
        final CxZipResult zipResult = zipFileAndGetResult(baseDir, osaZipperCallable);
        logZippingCompletionSummery(zipResult, "Temporary zip file",1);

        return zipResult.getTempFile();
    }

    private CxZipResult zipFileAndGetResult(FilePath baseDir, FilePath.FileCallable<CxZipResult> callable) throws InterruptedException, IOException {
        try {
            return baseDir.act(callable);
            //Handles the case where "act" method works on a remote system catches the ZipperException and make it's own IOException
            //Does NOT work with isInstance
        }catch (IOException e){
            if(e.getCause() != null){
                if(e.getCause().getClass() == (Zipper.NoFilesToZip.class)) {
                    throw (Zipper.NoFilesToZip) e.getCause();
                }
                if(e.getCause().getClass() == (Zipper.MaxZipSizeReached.class)) {
                    throw (Zipper.MaxZipSizeReached) e.getCause();
                }
                if(e.getCause().getClass() == (Zipper.ZipperException.class)){
                    throw (Zipper.ZipperException)e.getCause();
                }
            }
            throw e;
        }
    }

    private void logZippingCompletionSummery(CxZipResult zipResult, String tempFileDescription, int base) throws IOException, InterruptedException {
        logger.info("Zipping complete with " + zipResult.getZippingDetails().getNumOfZippedFiles() + " files, total compressed size: " +
                FileUtils.byteCountToDisplaySize(zipResult.getTempFile().length() / base)); // We print here the size of compressed sources before encoding to base
        logger.info(tempFileDescription+" was created at: " + zipResult.getTempFile().getRemote());
    }

}
