package com.hand.pbcs;
/*
File: PbcsRestSamples.java - Created on Feb 19, 2015
Copyright (c) 2015 Oracle Corporation. All Rights Reserved.
This software is the proprietary information of Oracle.
*/

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

import org.json.JSONArray;
import org.json.JSONObject;
import org.apache.commons.io.FilenameUtils;

/*
* PBCS Rest Samples.
*
*/
public class PbcsRestSamples {
    private String userName; // PBCS user name
    private String password; // PBCS user password
    private String serverUrl; // PBCS server URL
    private String apiVersion; // Version of the PBCS API that you are
    // developing/compiling with.
    private String applicationName; // PBCS application used in this sample

    public static void main(String[] args) {
        try {
            PbcsRestSamples samples = new PbcsRestSamples("a487826.manhua.ying@hand-china.com", "HANDhand123",
                    "https://planning-test-a487826.pbcs.us2.oraclecloud.com", "11.1.2.3.600", "Plus");
            //samples.integrationScenarioImportMetadataIntoApplication();//导入主数据
            //samples.integrationScenarioImportDataRunCalcCopyToAso();//执行规则复制数据到ASO
            //samples.integrationScenarioExportMetadataAndDataAndDownloadFiles();//导出主数据
            //samples.integrationScenarioRemoveUnnecessaryFiles();//删除文件
            //samples.integrationScenarioExportDataAndDownloadFiles();//导出数据
            //samples.integrationScenarioRefreshTheApplication();//刷新应用
            //samples.integrationScenarioImportSnapshot();//导入快照
            samples.integrationScenarioExportSnapshot();//导出快照
        } catch (Throwable x) {
            System.err.println("Error: " + x.getMessage());
        }
    }

    public PbcsRestSamples(String userName, String password, String serverUrl, String apiVersion,
                           String applicationName) throws Exception {
        this.userName = userName;
        this.password = password;
        this.serverUrl = serverUrl;
        this.apiVersion = apiVersion;
        this.applicationName = applicationName;
    }

    //
    // BEGIN - Integration scenarios.
    //
    public void integrationScenarioImportMetadataIntoApplication() throws Exception {
        uploadFile("ProjectsMetadata.csv");
        executeJob("IMPORT_METADATA", "ProjectsMetadata", "{importFileName:ProjectsMetadata.csv}");
    }

    public void integrationScenarioImportDataRunCalcCopyToAso() throws Exception {
        uploadFile("data.csv");
        executeJob("IMPORT_DATA", "loadingq1data", "{importFileName:data.csv}");
        executeJob("CUBE_REFRESH", null, null);
        executeJob("PLAN_TYPE_MAP", "CampaignToReporting", "{clearData:false}");
    }

    public void integrationScenarioExportMetadataAndDataAndDownloadFiles() throws Exception {
        executeJob("EXPORT_METADATA", "DimExport",
                "{exportZipFileName:DimExport.zip}");
        listFiles();
        downloadFile("DimExport.zip");
    }

    public void integrationScenarioRemoveUnnecessaryFiles() throws Exception {
        listFiles();
        deleteFile("test.zip");
    }

    public void integrationScenarioExportDataAndDownloadFiles() throws Exception {
        executeJob("EXPORT_DATA", "DataExport_All", "{exportFileName:DataExport_All.zip}");
        listFiles();
        downloadFile("DataExport_All.zip");
    }

    public void integrationScenarioRefreshTheApplication() throws Exception {
        executeRefreshCubeJob("CUBE_REFRESH", "RefreshDB", null);
    }

    public void integrationScenarioCloneServiceInstance() throws Exception {
        // Part 1 : Change serverUrl, username, password, apiVersion variables
        // values to match those of first environment
        // Download file from source instance.
        // Comment out all lines below Part 2
        // Uncomment the below line for the first step.
        // downloadFile("Artifact Snapshot");

        // Part 2 : Change serverUrl, username, password, apiVersion to match
        // those of second environment.
        // Clone the service instance.
        // Comment out code for download file.
        // Uncomment below lines
        // recreateService("PBCS");
        deleteFile("Artifact Snapshot");
        uploadFile("Artifact Snapshot.zip");
        importSnapshot("Artifact Snapshot");
    }

    public void integrationScenarioImportSnapshot() throws Exception {
        //导入用户组
        uploadFile("groupsiptest.zip");
        importSnapshot("groupsiptest");
        //导入权限
        uploadFile("plansecimporttest.zip");
        importSnapshot("plansecimporttest");
    }

    public void integrationScenarioExportSnapshot() throws Exception {
        //导出
        exportSnapshot("CM");
        listFiles();
        downloadFile("CM");//不能指定文件后缀
    }
    //
    // END - Integration scenarios.
    //

    //
    // BEGIN - Methods that invoke REST API
    //

    //
    // Common Helper Methods
    //
    private String getStringFromInputStream(InputStream is) {
        BufferedReader br = null;
        StringBuilder sb = new StringBuilder();
        String line;

        try {
            br = new BufferedReader(new InputStreamReader(is));
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return sb.toString();
    }

    private String executeRequest(String urlString, String requestMethod, String payload, String contentType)
            throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(requestMethod);
            connection.setInstanceFollowRedirects(false);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setRequestProperty("Authorization",
                    "Basic " + new sun.misc.BASE64Encoder().encode((userName + ":" + password).getBytes()));
            connection.setRequestProperty("Content-Type", contentType);
            if (payload != null) {
                OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
                writer.write(payload);
                writer.flush();
            }
            int status = connection.getResponseCode();
            if (status == 200 || status == 201) {
                return getStringFromInputStream(connection.getInputStream());
            }
            throw new Exception("Http status code: " + status);
        } finally {
            if (connection != null)
                connection.disconnect();
        }
    }

    private void getJobStatus(String pingUrlString, String methodType) throws Exception {
        boolean completed = false;
        while (!completed) {
            String pingResponse = executeRequest(pingUrlString, methodType, null, "application/x-www-form-urlencoded");
            JSONObject json = new JSONObject(pingResponse);
            int status = json.getInt("status");
            if (status == -1) {
                try {
                    System.out.println("Please wait...");
                    Thread.sleep(20000);
                } catch (InterruptedException e) {
                    completed = true;
                    throw e;
                }
            } else {
                if (status > 0) {
                    System.out.println("Error occurred: " + json.getString("details"));
                } else {
                    System.out.println("Completed");
                }
                completed = true;
            }
        }
    }

    private void getMigrationJobStatus(String pingUrlString, String methodType) throws Exception {
        boolean completed = false;
        while (!completed) {
            String pingResponse = executeRequest(pingUrlString, methodType, null, "application/x-www-form-urlencoded");
            JSONObject json = new JSONObject(pingResponse);
            int status = json.getInt("status");
            if (status == -1) {
                try {
                    System.out.println("Please wait...");
                    Thread.sleep(20000);
                } catch (InterruptedException e) {
                    completed = true;
                    throw e;
                }
            } else {
                if (status == 1) {
                    System.out.println("Error occured");
                    JSONArray itemsArray = json.getJSONArray("items");
                    JSONObject jObj = null;
                    if (itemsArray.length() <= 0) {
                        System.out.println(json.getString("details"));
                    } else {
                        for (int i = 0; i < itemsArray.length(); i++) {
                            jObj = (JSONObject) itemsArray.get(i);
                            String source = jObj.getString("source");
                            String destination = jObj.getString("destination");
                            String taskURL = null;
                            JSONArray lArray = jObj.getJSONArray("links");
                            for (int j = 0; j < lArray.length(); j++) {
                                JSONObject arr = lArray.getJSONObject(j);
                                if (!JSONObject.NULL.equals(arr) && !JSONObject.NULL.equals(arr.get("rel"))
                                        && arr.get("rel").equals("Job Details")) {
                                    taskURL = (String) arr.get("href");
                                    break;
                                }
                            }
                            System.out.println("Details:");
                            System.out.println("Source: " + source);
                            System.out.println("Destination: " + destination);
                            boolean errorsCompleted = false;
                            String currentMessageCategory = "";
                            String nextPingURL = taskURL;
                            while (!errorsCompleted) {
                                String nextPingResponse = executeRequest(nextPingURL, "GET", null,
                                        "application/x-www-form-urlencoded");
                                JSONObject jsonObj = new JSONObject(nextPingResponse);
                                int status1 = jsonObj.getInt("status");
                                if (status1 == 0) {
                                    JSONArray artifactArray = jsonObj.getJSONArray("items");
                                    JSONObject jRes = null;
                                    for (int k = 0; k < artifactArray.length(); k++) {
                                        jRes = (JSONObject) artifactArray.get(k);
                                        String artifact = jRes.getString("artifact").toString();
                                        String msgCategory = jRes.getString("msgCategory").toString();
                                        String msgText = jRes.getString("msgText").toString();
                                        if (currentMessageCategory.isEmpty()
                                                || !currentMessageCategory.equals(msgCategory)) {
                                            currentMessageCategory = msgCategory;
                                            System.out.println(currentMessageCategory);
                                        }
                                        System.out.println(artifact + " - " + msgText);
                                    }
                                    nextPingURL = "";
                                    JSONArray nextLinks = jsonObj.getJSONArray("links");
                                    for (int j = 0; j < nextLinks.length(); j++) {
                                        JSONObject nextArray = nextLinks.getJSONObject(j);
                                        if (!JSONObject.NULL.equals(nextArray)
                                                && !JSONObject.NULL.equals(nextArray.get("rel"))
                                                && nextArray.get("rel").equals("next")) {
                                            nextPingURL = (String) nextArray.get("href");
                                            break;
                                        }
                                    }
                                    if (nextPingURL.isEmpty())
                                        errorsCompleted = true;
                                } else if (status1 > 0) {
                                    System.out.println("Error occured while fetching error details: "
                                            + jsonObj.getString("details"));
                                    errorsCompleted = true;
                                }
                            }
                        }
                    }
                } else if (status == 0) {
                    System.out.println("Completed");
                }
                completed = true;
            }
        }
    }

    public String fetchPingUrlFromResponse(String response, String retValue) throws Exception {
        String pingUrlString = null;
        JSONObject jsonObj = new JSONObject(response);
        int resStatus = jsonObj.getInt("status");
        if (resStatus == -1) {
            JSONArray lArray = jsonObj.getJSONArray("links");
            for (int i = 0; i < lArray.length(); i++) {
                JSONObject arr = lArray.getJSONObject(i);
                if (arr.get("rel").equals(retValue))
                    pingUrlString = (String) arr.get("href");
            }
        }
        return pingUrlString;
    }
    //
    // END - Common Helper Methods
    //

    //
    // BEGIN - List all the versions in PBCS
    //
    public void getLCMVersions() throws Exception {
        String urlString = String.format("%s/interop/rest", serverUrl);
        String response = executeRequest(urlString, "GET", null, "application/x-www-form-urlencoded");
        JSONObject json = new JSONObject(response);
        int resStatus = json.getInt("status");
        if (resStatus == 0) {
            JSONArray fileList = json.getJSONArray("items");
            System.out.println("List of files are :");
            JSONObject jObj = null;
            for (int i = 0; i < fileList.length(); i++) {// modified by xuedong
                // 3.23.2018
                jObj = (JSONObject) fileList.get(i);
                System.out.println("Version :" + jObj.getString("version"));
                System.out.println("Lifecycle :" + jObj.getString("lifecycle"));
                System.out.println("Latest :" + jObj.getString("latest"));
                System.out.println("Link :"
                        + ((JSONObject) ((JSONArray) jObj.getJSONArray("links")).get(0)).getString("href") + "\n");
            }
        }
    }
    //
    // END - List all the versions in PBCS
    //

    //
    // BEGIN - Get application snapshot details
    //
    public void getApplicationSnapshotDetails(String snapshotName) throws Exception {
        String urlString = String.format("%s/interop/rest/%s/applicationsnapshots/%s", serverUrl, apiVersion,
                snapshotName);
        String response = executeRequest(urlString, "GET", null, "application/x-www-form-urlencoded");
        JSONObject json = new JSONObject(response);

        int resStatus = json.getInt("status");
        if (resStatus == 0) {
            System.out.println("Application details :");
            JSONArray itemsArray = json.getJSONArray("items");
            JSONObject item = (JSONObject) itemsArray.get(0);
            System.out.println("Application snapshot name : " + item.getString("name"));
            System.out.println("Application snapshot type : " + item.getString("type"));
            System.out.println("Can be exported flag : " + item.getString("canExport"));
            System.out.println("Can be imported flag : " + item.getString("canImport"));
            System.out.println("Can be uploaded flag : " + item.getString("canUpload"));
            System.out.println("Can be downloaded flag : " + item.getString("canDownload"));

            JSONArray linksArray = json.getJSONArray("links");
            JSONObject jObj = null;
            System.out.println("Services details :");
            for (int i = 0; i < linksArray.length(); i++) {
                jObj = (JSONObject) linksArray.get(i);
                System.out.println("Service :" + jObj.getString("rel"));
                System.out.println("URL :" + jObj.getString("href"));
                System.out.println("Action :" + jObj.getString("action") + "\n");
            }
        }
    }
    //
    // END - Get application snapshot details
    //

    //
    // BEGIN - List all the files in PBCS
    //
    public void listFiles() throws Exception {
        apiVersion = "11.1.2.3.600";
        String urlString = String.format("%s/interop/rest/%s/applicationsnapshots", serverUrl, apiVersion);
        String response = executeRequest(urlString, "GET", null, "application/x-www-form-urlencoded");
        JSONObject json = new JSONObject(response);
        int resStatus = json.getInt("status");
        if (resStatus == 0) {
            if (json.get("items").equals(JSONObject.NULL))
                System.out.println("No files found");
            else {
                System.out.println("List of files :");
                JSONArray itemsArray = json.getJSONArray("items");
                JSONObject jObj = null;
                for (int i = 0; i < itemsArray.length(); i++) {
                    jObj = (JSONObject) itemsArray.get(i);
                    System.out.println(jObj.getString("name"));
                }
            }
        }
    }
    //
    // END - List all the files in PBCS
    //

    //
    // BEGIN - Delete a file in PBCS
    //
    public void deleteFile(String fileName) throws Exception {
        String urlString = String.format("%s/interop/rest/%s/applicationsnapshots/%s", serverUrl, apiVersion, fileName);
        String response = executeRequest(urlString, "DELETE", null, "application/x-www-form-urlencoded");
        JSONObject json = new JSONObject(response);
        int resStatus = json.getInt("status");
        if (resStatus == 0)
            System.out.println("File deleted successfully");
        else
            System.out.println("Error deleting file : " + json.getString("details"));
    }
    //
    // END - Delete a file in PBCS
    //

    //
    // BEGIN - Download a file from PBCS
    //
    public void downloadFile(String fileName) throws Exception {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        FileOutputStream outputStream = null;

        try {
            URL url = new URL(String.format("%s/interop/rest/%s/applicationsnapshots/%s/contents", serverUrl,
                    apiVersion, fileName));
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setRequestProperty("Authorization",
                    "Basic " + new sun.misc.BASE64Encoder().encode((userName + ":" + password).getBytes()));
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            int status = connection.getResponseCode();
            if (status == 200) {
                if (connection.getContentType() != null && connection.getContentType().equals("application/json")) {
                    JSONObject json = new JSONObject(getStringFromInputStream(connection.getInputStream()));
                    System.out.println("Error downloading file : " + json.getString("details"));
                } else {
                    inputStream = connection.getInputStream();
                    outputStream = new FileOutputStream(new File(fileName));
                    int bytesRead = -1;
                    byte[] buffer = new byte[5 * 1024 * 1024];
                    while ((bytesRead = inputStream.read(buffer)) != -1)
                        outputStream.write(buffer, 0, bytesRead);
                    System.out.println("File download completed.");
                }
            } else {
                throw new Exception("Http status code: " + status);
            }
        } finally {
            if (connection != null)
                connection.disconnect();
            if (outputStream != null)
                outputStream.close();
            if (inputStream != null)
                inputStream.close();
        }
    }
    //
    // END - Download a file from PBCS
    //

    //
    // BEGIN - Upload a file to PBCS
    //
    public void uploadFile(String fileName) throws Exception {
        final int DEFAULT_CHUNK_SIZE = 50 * 1024 * 1024;
        InputStream fis = null;
        byte[] lastChunk = null;
        long totalFileSize = new File(fileName).length(), totalbytesRead = 0;
        boolean isLast = false, status = true;
        Boolean isFirst = true;
        int packetNo = 1, lastPacketNo = (int) (Math.ceil(totalFileSize / (double) DEFAULT_CHUNK_SIZE));
        System.out.println("totalFileSize：" + totalFileSize);
        try {
            fis = new BufferedInputStream(new FileInputStream(fileName));
            while (totalbytesRead < totalFileSize && status) {
                int nextChunkSize = (int) Math.min(DEFAULT_CHUNK_SIZE, totalFileSize - totalbytesRead);
                if (lastChunk == null) {
                    lastChunk = new byte[nextChunkSize];
                    totalbytesRead += fis.read(lastChunk);
                    if (packetNo == lastPacketNo)
                        isLast = true;
                    status = sendFileContents(isFirst, isLast, lastChunk, fileName);
                    isFirst = false;
                    packetNo = packetNo + 1;
                    lastChunk = null;
                }
            }
            System.out.println("Uploaded successfully");
        } finally {
            if (fis != null)
                fis.close();
        }
    }

    //该函数需要重写
    private boolean sendFileContents(Boolean isFirst, boolean isLast, byte[] lastChunk, String fileName)
            throws Exception {
        HttpURLConnection connection = null;

        try {

            String basename = FilenameUtils.getBaseName(fileName);
            System.out.println("basename：" + basename);
            String suffix = fileName.substring(fileName.lastIndexOf(".") + 0);
            System.out.println("suffix：" + suffix);
            URL url = new URL(String.format(
                    "%s/interop/rest/%s/applicationsnapshots/%s/contents?q={chunkSize:%d,isFirst:%b,isLast:%b}",
                    serverUrl, apiVersion, basename + suffix, lastChunk.length, isFirst, isLast));
            System.out.println("url：" + url);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setInstanceFollowRedirects(false);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setRequestProperty("Authorization",
                    "Basic " + new sun.misc.BASE64Encoder().encode((userName + ":" + password).getBytes()));
            connection.setRequestProperty("Content-Type", "application/octet-stream");

            DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
            wr.write(lastChunk);
            wr.flush();

            int statusCode = connection.getResponseCode();
            String status = getStringFromInputStream(connection.getInputStream());
            System.out.println("statusCode" + statusCode);
            System.out.println("status" + status);
            if (statusCode == 200 && status != null) {
                int commandStatus = getCommandStatus(status);
                if (commandStatus == 0) {
                    isFirst = false;
                    return true;
                } else if (commandStatus == -1 && isLast) {
                    getJobStatus(fetchPingUrlFromResponse(status, "Job Status"), "GET");
                }
            }

            return false;
        } finally {
            if (connection != null)
                connection.disconnect();
        }
    }

    public int getCommandStatus(String response) throws Exception {
        JSONObject json = new JSONObject(response);
        if (!JSONObject.NULL.equals(json.get("status")))
            return json.getInt("status");
        else
            return Integer.MIN_VALUE;
    }
    //
    // END - Upload a file to PBCS
    //

    //
    // BEGIN - Import an application snapshot
    //
    public void importSnapshot(String applicationSnapshotName) throws Exception {
        JSONObject params = new JSONObject();
        params.put("type", "import");
        String urlString = String.format("%s/interop/rest/%s/applicationsnapshots/%s/migration?q=%s", serverUrl,
                apiVersion, applicationSnapshotName, params.toString());
        String response = executeRequest(urlString, "POST", null, "application/x-www-form-urlencoded");
        System.out.println("Import started successfully");
        getMigrationJobStatus(fetchPingUrlFromResponse(response, "Job Status"), "POST");
    }
    //
    // END - Import an application snapshot
    //

    //
    // BEGIN - Export an application snapshot
    //
    public void exportSnapshot(String applicationSnapshotName) throws Exception {
        JSONObject params = new JSONObject();
        params.put("type", "export");
        String urlString = String.format("%s/interop/rest/%s/applicationsnapshots/%s/migration?q=%s", serverUrl,
                apiVersion, applicationSnapshotName, params.toString());
        System.out.println(urlString);
        String response = executeRequest(urlString, "POST", null, "application/x-www-form-urlencoded");
        System.out.println("Export started successfully");
        getMigrationJobStatus(fetchPingUrlFromResponse(response, "Job Status"), "POST");
    }
    //
    // END - Export an application snapshot
    //

    //
    // BEGIN - Provide Feedback
    //
    public void provideFeedback(String description) throws Exception {
        JSONObject params = new JSONObject();
        JSONObject config = new JSONObject();
        config.put("URL", serverUrl);
        params.put("configuration", config);
        params.put("description", description);

        String urlString = String.format("%s/interop/rest/%s/feedback", serverUrl, apiVersion);
        String response = executeRequest(urlString, "POST", params.toString(), "application/json");
        JSONObject json = new JSONObject(response);
        int resStatus = json.getInt("status");
        if (resStatus == 0) {
            System.out.println("Feedback successful");
        } else {
            System.out.println("Error occured: " + json.getString("details"));
        }
    }
    //
    // END - Provide Feedback
    //

    //
    // BEGIN - Reset services
    //
    public void hardReset(String comment) throws Exception {
        Scanner in = new Scanner(System.in);
        System.out.println("Are you sure you want to restart the service instance (yes/no): no ?[Press Enter]");
        String s = in.nextLine();
        if (!s.equals("yes")) {
            System.out.println("User cancelled the recreate command");
            System.exit(0);
        }

        JSONObject params = new JSONObject();
        params.put("comment", java.net.URLEncoder.encode(comment));

        String urlString = String.format("%s/interop/rest/%s/services/PBCS/resetservice", serverUrl, apiVersion);
        String response = executeRequest(urlString, "POST", params.toString(), "application/x-www-form-urlencoded");
        // waitForCompletion(fetchPingUrlFromResponse(response, "Job Status"));
    }

    //
    // END - Reset services
    //
    //
    // BEGIN - Execute a Job (EXPORT_DATA, EXPORT_METADATA, IMPORT_DATA,
    // IMPORT_METADATA, CUBE_REFRESH, ...)
    //
    public void executeJob(String jobType, String jobName, String parameters) throws Exception {
        apiVersion = "v3";
        String urlString = String.format("%s/HyperionPlanning/rest/%s/applications/%s/jobs", serverUrl, apiVersion,
                applicationName);
        System.out.println(urlString);
        JSONObject payload = new JSONObject();
        payload.put("jobName", jobName);
        payload.put("jobType", jobType);
        payload.put("parameters", new JSONObject(parameters));
        String response = executeRequest(urlString, "POST", payload.toString(), "application/json");
        System.out.println("Job started successfully");
        getJobStatus(fetchPingUrlFromResponse(response, "self"), "GET");
    }

    //
    // END - Execute a Job (EXPORT_DATA, EXPORT_METADATA, IMPORT_DATA,
    // IMPORT_METADATA, CUBE_REFRESH, ...)
    //
    public void executeRefreshCubeJob(String jobType, String jobName, String parameters) throws Exception {
        apiVersion = "v3";
        String urlString = String.format("%s/HyperionPlanning/rest/%s/applications/%s/jobs", serverUrl, apiVersion, applicationName);
        System.out.println(urlString);
        JSONObject payload = new JSONObject();
        payload.put("jobName", jobName);
        payload.put("jobType", jobType);
        //payload.put("parameters", new JSONObject(parameters));
        String response = executeRequest(urlString, "POST", payload.toString(), "application/json");
        System.out.println("Job started successfully");
        getJobStatus(fetchPingUrlFromResponse(response, "self"), "GET");
    }
}
