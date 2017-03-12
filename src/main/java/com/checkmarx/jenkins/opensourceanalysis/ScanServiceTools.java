package com.checkmarx.jenkins.opensourceanalysis;

import com.checkmarx.jenkins.CxWebService;
import com.checkmarx.jenkins.web.client.OsaScanClient;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;


/**
 * Created by zoharby on 10/01/2017.
 */
public class ScanServiceTools {

    private DependencyFolder dependencyFolder;
    private CxWebService webServiceClient;
    private OsaScanClient osaScanClient;
    private Run<?, ?> build;
    private TaskListener listener;
    private FilePath workspace;
    private long projectId;

    public DependencyFolder getDependencyFolder() {
        return dependencyFolder;
    }

    public void setDependencyFolder(DependencyFolder dependencyFolder) {
        this.dependencyFolder = dependencyFolder;
    }

    public CxWebService getWebServiceClient() {
        return webServiceClient;
    }

    public void setWebServiceClient(CxWebService webServiceClient) {
        this.webServiceClient = webServiceClient;
    }

    public OsaScanClient getOsaScanClient() {
        return osaScanClient;
    }

    public void setOsaScanClient(OsaScanClient osaScanClient) {
        this.osaScanClient = osaScanClient;
    }

    public Run<?, ?> getBuild() {
        return build;
    }

    public void setBuild(Run<?, ?> build) {
        this.build = build;
    }

    public TaskListener getListener() {
        return listener;
    }

    public void setListener(TaskListener listener) {
        this.listener = listener;
    }

    public long getProjectId() {
        return projectId;
    }

    public void setProjectId(long projectId) {
        this.projectId = projectId;
    }

    public FilePath getWorkspace() {
        return workspace;
    }

    public void setWorkspace(FilePath workspace) {
        this.workspace = workspace;
    }
}
