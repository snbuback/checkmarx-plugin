package com.checkmarx.jenkins;

import com.checkmarx.jenkins.logger.CxPluginLogger;
import hudson.PluginWrapper;
import hudson.model.Action;
import hudson.model.Hudson;
import hudson.util.IOUtils;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.servlet.ServletOutputStream;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import static com.checkmarx.jenkins.CxResultSeverity.*;
import hudson.model.Run;

/**
 * @author denis
 * @since 3/11/13
 */
public class CxScanResult implements Action {

    private transient CxPluginLogger logger = new CxPluginLogger();

    public final Run<?, ?> owner;
    private final long projectId;
    private final boolean scanRanAsynchronous;
    private String serverUrl;

    private int highCount;
    private int mediumCount;
    private int lowCount;
    private int infoCount;

    private LinkedList<QueryResult> highQueryResultList;
    private LinkedList<QueryResult> mediumQueryResultList;
    private LinkedList<QueryResult> lowQueryResultList;
    private LinkedList<QueryResult> infoQueryResultList;

    private OsaScanResult osaScanResult;

    @NotNull
    private String resultDeepLink;
    private File pdfReport;

    public static final String PDF_REPORT_NAME = "ScanReport.pdf";
    @Nullable
    private String scanStart;
    @Nullable
    private String scanTime;
    @Nullable
    private String linesOfCodeScanned;
    @Nullable
    private String filesScanned;
    @Nullable
    private String scanType;

    private boolean resultIsValid;
    private String errorMessage;

    //Thresholds
    private boolean thresholdsEnabled = false;
    private boolean osaThresholdsEnabled = false;

    @Nullable
    private Integer highThreshold;
    @Nullable
    private Integer mediumThreshold;
    @Nullable
    private Integer lowThreshold;
    @Nullable
    private Integer osaHighThreshold;
    @Nullable
    private Integer osaMediumThreshold;
    @Nullable
    private Integer osaLowThreshold;


    public CxScanResult(final Run owner, String serverUrl, long projectId, boolean scanRanAsynchronous) {
        this.projectId = projectId;
        this.scanRanAsynchronous = scanRanAsynchronous;
        this.owner = owner;
        this.serverUrl = serverUrl;
        this.resultIsValid = true;
        this.errorMessage = "No Scan Results"; // error message to appear if results were not parsed
        this.highQueryResultList = new LinkedList<>();
        this.mediumQueryResultList = new LinkedList<>();
        this.lowQueryResultList = new LinkedList<>();
        this.infoQueryResultList = new LinkedList<>();
    }


    public void setOsaThresholds(ThresholdConfig thresholdConfig) {
        this.setOsaThresholdsEnabled(true);
        this.setOsaHighThreshold(thresholdConfig.getHighSeverity());
        this.setOsaMediumThreshold(thresholdConfig.getMediumSeverity());
        this.setOsaLowThreshold(thresholdConfig.getLowSeverity());
    }

    public void setThresholds(ThresholdConfig thresholdConfig) {
        this.setThresholdsEnabled(true);
        this.setHighThreshold(thresholdConfig.getHighSeverity());
        this.setMediumThreshold(thresholdConfig.getMediumSeverity());
        this.setLowThreshold(thresholdConfig.getLowSeverity());
    }

    @Override
    public String getIconFileName() {
        if (isShowResults()) {
            return getIconPath() + "CxIcon24x24.png";
        } else {
            return null;
        }
    }

    @Override
    public String getDisplayName() {
        if (isShowResults()) {
            return "Checkmarx Scan Results";
        } else {
            return null;
        }
    }

    @Override
    public String getUrlName() {
        if (isShowResults()) {
            return "checkmarx";
        } else {
            return null;
        }
    }

    @NotNull
    public String getIconPath() {
        PluginWrapper wrapper = Hudson.getInstance().getPluginManager().getPlugin(CxPlugin.class);
        return "/plugin/" + wrapper.getShortName() + "/";

    }

    public boolean isShowResults() {
        @Nullable
        CxScanBuilder.DescriptorImpl descriptor = (CxScanBuilder.DescriptorImpl) Jenkins.getInstance().getDescriptor(CxScanBuilder.class);
        return descriptor != null && !descriptor.isHideResults() && !isScanRanAsynchronous();
    }

    public boolean isThresholdsEnabled() {
        return thresholdsEnabled;
    }

    public void setThresholdsEnabled(boolean thresholdsEnabled) {
        this.thresholdsEnabled = thresholdsEnabled;
    }

    public boolean isOsaThresholdsEnabled() {
        return osaThresholdsEnabled;
    }

    public void setOsaThresholdsEnabled(boolean osaThresholdsEnabled) {
        this.osaThresholdsEnabled = osaThresholdsEnabled;
    }

    @Nullable
    public Integer getHighThreshold() {
        return highThreshold;
    }

    public void setHighThreshold(@Nullable Integer highThreshold) {
        this.highThreshold = highThreshold;
    }

    @Nullable
    public Integer getMediumThreshold() {
        return mediumThreshold;
    }

    public void setMediumThreshold(@Nullable Integer mediumThreshold) {
        this.mediumThreshold = mediumThreshold;
    }

    @Nullable
    public Integer getLowThreshold() {
        return lowThreshold;
    }

    public void setLowThreshold(@Nullable Integer lowThreshold) {
        this.lowThreshold = lowThreshold;
    }

    @Nullable
    public Integer getOsaHighThreshold() {
        return osaHighThreshold;
    }

    public void setOsaHighThreshold(@Nullable Integer osaHighThreshold) {
        this.osaHighThreshold = osaHighThreshold;
    }

    @Nullable
    public Integer getOsaMediumThreshold() {
        return osaMediumThreshold;
    }

    public void setOsaMediumThreshold(@Nullable Integer osaMediumThreshold) {
        this.osaMediumThreshold = osaMediumThreshold;
    }

    @Nullable
    public Integer getOsaLowThreshold() {
        return osaLowThreshold;
    }

    public void setOsaLowThreshold(@Nullable Integer osaLowThreshold) {
        this.osaLowThreshold = osaLowThreshold;
    }

    public int getHighCount() {
        return highCount;
    }

    public int getMediumCount() {
        return mediumCount;
    }

    public int getLowCount() {
        return lowCount;
    }

    public int getInfoCount() {
        return infoCount;
    }

    @NotNull
    public String getResultDeepLink() {
        return resultDeepLink;
    }

    @Nullable
    public String getScanStart() {
        return scanStart;
    }

    @Nullable
    public String getScanTime() {
        return scanTime;
    }

    @Nullable
    public String getLinesOfCodeScanned() {
        return linesOfCodeScanned;
    }

    @Nullable
    public String getFilesScanned() {
        return filesScanned;
    }

    @Nullable
    public String getScanType() {
        return scanType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isResultIsValid() {
        return resultIsValid;
    }

    public List<QueryResult> getHighQueryResultList() {
        return highQueryResultList;
    }

    public List<QueryResult> getMediumQueryResultList() {
        return mediumQueryResultList;
    }

    public List<QueryResult> getLowQueryResultList() {
        return lowQueryResultList;
    }

    public List<QueryResult> getInfoQueryResultList() {
        return infoQueryResultList;
    }

    public boolean isPdfReportReady() {
        File buildDirectory = owner.getRootDir();
        pdfReport = new File(buildDirectory, "/checkmarx/" + PDF_REPORT_NAME);
        return pdfReport.exists();
    }

    public String getPdfReportUrl() {
        return "/pdfReport";
    }

    public void doPdfReport(StaplerRequest req, StaplerResponse rsp) throws IOException {
        rsp.setContentType("application/pdf");
        ServletOutputStream outputStream = rsp.getOutputStream();
        File buildDirectory = owner.getRootDir();
        File a = new File(buildDirectory, "/checkmarx/" + PDF_REPORT_NAME);

        IOUtils.copy(a, outputStream);

        outputStream.flush();
        outputStream.close();
    }

    public void doOsaPdfReport(StaplerRequest req, StaplerResponse rsp) throws IOException {

        rsp.setContentType("application/pdf");
        ServletOutputStream outputStream = rsp.getOutputStream();
        File buildDirectory = owner.getRootDir();
        File a = new File(buildDirectory, "/checkmarx/" + "OSAReport.pdf");

        IOUtils.copy(a, outputStream);

        outputStream.flush();
        outputStream.close();
    }


    public void doOsaHtmlReport(StaplerRequest req, StaplerResponse rsp) throws IOException {
        rsp.setContentType("text/html");
        ServletOutputStream outputStream = rsp.getOutputStream();
        File buildDirectory = owner.getRootDir();
        File a = new File(buildDirectory, "/checkmarx/" + "OSAReport.html");

        IOUtils.copy(a, outputStream);

        outputStream.flush();
        outputStream.close();
    }

    /**
     * Gets the test result of the previous build, if it's recorded, or null.
     */

    public CxScanResult getPreviousResult() {
        Run<?, ?> b = owner;
        while (true) {
            b = b.getPreviousBuild();
            if (b == null) {
                return null;
            }
            CxScanResult r = b.getAction(CxScanResult.class);
            if (r != null) {
                return r;
            }
        }
    }

    public void readScanXMLReport(File scanXMLReport) {
        ResultsParseHandler handler = new ResultsParseHandler();

        try {
            SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();

            highCount = 0;
            mediumCount = 0;
            lowCount = 0;
            infoCount = 0;

            saxParser.parse(scanXMLReport, handler);

            resultIsValid = true;
            errorMessage = null;

        } catch (ParserConfigurationException e) {
            logger.error(e.getMessage(), e);
        } catch (SAXException | IOException e) {
            resultIsValid = false;
            errorMessage = e.getMessage();
            logger.error(e.getMessage(), e);
        }
    }

    public long getProjectId() {
        return projectId;
    }

    public boolean isScanRanAsynchronous() {
        return scanRanAsynchronous;
    }

    public String getProjectStateUrl() {
        return serverUrl + "/CxWebClient/portal#/projectState/" + projectId + "/Summary";
    }

    public OsaScanResult getOsaScanResult() {
        return osaScanResult;
    }

    public void setOsaScanResult(OsaScanResult osaScanResult) {
        this.osaScanResult = osaScanResult;
    }

    private class ResultsParseHandler extends DefaultHandler {

        @Nullable
        private String currentQueryName;
        @Nullable
        private String currentQuerySeverity;
        private int currentQueryNumOfResults;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            super.startElement(uri, localName, qName, attributes);

            switch (qName) {
                case "Result":
                    @Nullable
                    String falsePositive = attributes.getValue("FalsePositive");
                    if (!"True".equals(falsePositive)) {
                        currentQueryNumOfResults++;
                        @Nullable
                        String severity = attributes.getValue("SeverityIndex");
                        if (severity != null) {
                            if (severity.equals(HIGH.xmlParseString)) {
                                highCount++;

                            } else if (severity.equals(MEDIUM.xmlParseString)) {
                                mediumCount++;

                            } else if (severity.equals(LOW.xmlParseString)) {
                                lowCount++;

                            } else if (severity.equals(INFO.xmlParseString)) {
                                infoCount++;
                            }
                        } else {
                            logger.error("\"SeverityIndex\" attribute was not found in element \"Result\" in XML report. "
                                    + "Make sure you are working with Checkmarx server version 7.1.6 HF3 or above.");
                        }
                    }
                    break;
                case "Query":
                    currentQueryName = attributes.getValue("name");
                    if (currentQueryName == null) {
                        logger.error("\"name\" attribute was not found in element \"Query\" in XML report");
                    }
                    currentQuerySeverity = attributes.getValue("SeverityIndex");
                    if (currentQuerySeverity == null) {
                        logger.error("\"SeverityIndex\" attribute was not found in element \"Query\" in XML report. "
                                + "Make sure you are working with Checkmarx server version 7.1.6 HF3 or above.");
                    }
                    currentQueryNumOfResults = 0;

                    break;
                default:
                    if ("CxXMLResults".equals(qName)) {
                        resultDeepLink = constructDeepLink(attributes.getValue("DeepLink"));
                        scanStart = attributes.getValue("ScanStart");
                        scanTime = attributes.getValue("ScanTime");
                        linesOfCodeScanned = attributes.getValue("LinesOfCodeScanned");
                        filesScanned = attributes.getValue("FilesScanned");
                        scanType = attributes.getValue("ScanType");
                    }
                    break;
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            super.endElement(uri, localName, qName);
            if ("Query".equals(qName)) {
                QueryResult qr = new QueryResult();
                qr.setName(currentQueryName);
                qr.setSeverity(currentQuerySeverity);
                qr.setCount(currentQueryNumOfResults);

                if (StringUtils.equals(qr.getSeverity(), HIGH.xmlParseString)) {
                    highQueryResultList.add(qr);
                } else if (StringUtils.equals(qr.getSeverity(), MEDIUM.xmlParseString)) {
                    mediumQueryResultList.add(qr);
                } else if (StringUtils.equals(qr.getSeverity(), LOW.xmlParseString)) {
                    lowQueryResultList.add(qr);
                } else if (StringUtils.equals(qr.getSeverity(), INFO.xmlParseString)) {
                    infoQueryResultList.add(qr);
                } else {
                    logger.error("Encountered a result query with unknown severity: " + qr.getSeverity());
                }
            }
        }

        @NotNull
        private String constructDeepLink(@Nullable String rawDeepLink) {
            if (rawDeepLink == null) {
                logger.error("\"DeepLink\" attribute was not found in element \"CxXMLResults\" in XML report");
                return "";
            }
            String token = "CxWebClient";
            String[] tokens = rawDeepLink.split(token);
            if (tokens.length < 1) {
                logger.error("DeepLink value found in XML report is of unexpected format: " + rawDeepLink + "\n"
                        + "\"Open Code Viewer\" button will not be functional");
            }
            return serverUrl + "/" + token + tokens[1];
        }
    }


    public boolean isThresholdExceeded() {
        boolean ret = isThresholdExceededByLevel(highCount, highThreshold);
        ret |= isThresholdExceededByLevel(mediumCount, mediumThreshold);
        ret |= isThresholdExceededByLevel(lowCount, lowThreshold);
        return ret;
    }

    public boolean isOsaThresholdExceeded() {
        boolean ret = isThresholdExceededByLevel(osaScanResult.getOsaHighCount(), osaHighThreshold);
        ret |= isThresholdExceededByLevel(osaScanResult.getOsaMediumCount(), osaMediumThreshold);
        ret |= isThresholdExceededByLevel(osaScanResult.getOsaLowCount(), osaLowThreshold);
        return ret;
    }

    private boolean isThresholdExceededByLevel(int count, Integer threshold) {
        boolean ret = false;
        if (threshold != null && count > threshold) {
            ret = true;
        }
        return ret;
    }

    public static class QueryResult {
        @Nullable
        private String name;
        @Nullable
        private String severity;
        private int count;

        @Nullable
        public String getName() {
            return name;
        }

        public void setName(@Nullable String name) {
            this.name = name;
        }

        @Nullable
        public String getSeverity() {
            return severity;
        }

        public void setSeverity(@Nullable String severity) {
            this.severity = severity;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        @NotNull
        public String getPrettyName() {
            if (this.name != null) {
                return this.name.replace('_', ' ');
            } else {
                return "";
            }
        }
    }
}
