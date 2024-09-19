package com.lantanagroup.link.tasks;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import com.lantanagroup.link.auth.OAuth2Helper;
import com.lantanagroup.link.helpers.HttpExecutor;
import com.lantanagroup.link.helpers.HttpExecutorResponse;
import com.lantanagroup.link.helpers.SftpDownloader;
import com.lantanagroup.link.model.Job;
import com.lantanagroup.link.model.UploadFile;
import com.lantanagroup.link.tasks.config.ParklandInventoryImportConfig;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.*;

public class ParklandInventoryImportTask {
    private static final Logger logger = LoggerFactory.getLogger(ParklandInventoryImportTask.class);

    private static ParklandInventoryImportConfig config;

    public static void RunParklandInventoryImportTask(ParklandInventoryImportConfig parklandConfig, String fileType, String fileName) {
        config = parklandConfig;
        try {
            // Check to make sure the downloader & submissionInfo sections
            // exist for the fileType
            if (config.getDownloader().get(fileType) == null) {
                String errorMessage = String.format("parkland-inventory-import.downloader configuration for File Type '%s' is not available.", fileType);
                logger.error(errorMessage);
                throw new Exception(errorMessage);
            }
            if (config.getSubmissionInfo().get(fileType) == null) {
                String errorMessage = String.format("parkland-inventory-import.submission-info configuration for File Type '%s' is not available.", fileType);
                logger.error(errorMessage);
                throw new Exception(errorMessage);
            }

            // If specified file type is CSV, for parkland we need to know icu-identifiers
            if (fileType.equals("csv") &&
                    ((config.getSubmissionInfo().get(fileType).getIcuIdentifiers() == null) ||
                            (config.getSubmissionInfo().get(fileType).getIcuIdentifiers().length < 1))) {
                String errorMessage = String.format("parkland-inventory-import.submission-info configuration for File Type '%s' does not contain icu-identifiers.", fileType);
                logger.error(errorMessage);
                throw new Exception(errorMessage);
            }

            UploadFile uploadFile = new UploadFile();
            uploadFile.setType(fileType);
            uploadFile.setSource("parkland");

            // If csv set the options in UploadFile to have ICU Identifiers
            if (uploadFile.getType().equals("csv")) {
                Map<String, Object> icuCodes = new HashMap<>();
                icuCodes.put("icu-codes",
                        Arrays.asList(config.getSubmissionInfo().get(fileType).getIcuIdentifiers())
                );
                uploadFile.setOptions(icuCodes);
                logger.info("ICU Facility Identifiers: {}", String.join(",",
                        config.getSubmissionInfo().get(fileType).getIcuIdentifiers()));
            }

            // Set the name of the file to download
            String fileToDownload = getFileNameToDownload(uploadFile.getType(), fileName);
            uploadFile.setName(fileToDownload);
            config.getDownloader().get(uploadFile.getType()).setFileName(fileToDownload);
            logger.info("File to be downloaded: {}", fileToDownload);

            // Download file
            SftpDownloader downloader = new SftpDownloader(config.getDownloader().get(uploadFile.getType()));
            byte[] data = downloader.download();
            logger.info("File downloaded, byte size: {}", data.length);

            uploadFile.setContent(Base64.getEncoder().encodeToString(data));

            // Upload file to API
            sendDataToApi(uploadFile);
            logger.info("Data uploaded to API");

            logger.info("Parkland Inventory Import ({}} Completed", fileType);
        } catch (SftpException ex) {
            if (ex.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                logger.error("Attempted to download file that is not on server");
            }
        } catch (Exception ex) {
            logger.error("Parkland Inventory Import execute issue: {}", ex.getMessage());
            System.exit(1);
        }
    }

    private static void sendDataToApi(UploadFile uploadFile) throws Exception {
        try {
            String submissionUrl = config.getSubmissionInfo().get(uploadFile.getType()).getSubmissionUrl();
            logger.info("Submitting data to API, url: {}", submissionUrl);

            HttpPost request = new HttpPost(submissionUrl);
            request.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString());

            String token = OAuth2Helper.getToken(config.getSubmissionInfo().get(uploadFile.getType()).getSubmissionAuth());
            if (OAuth2Helper.validateHeaderJwtToken(token)) {
                request.addHeader(HttpHeaders.AUTHORIZATION, String.format("Bearer %s", token));
            } else {
                throw new JWTVerificationException("Invalid token format");
            }

            ObjectMapper mapper = new ObjectMapper();
            String payloadJson = mapper.writeValueAsString(uploadFile);
            request.setEntity(new StringEntity(payloadJson));

            HttpExecutorResponse response = HttpExecutor.HttpExecutor(request);
            logger.info("HTTP Response Code {}", response.getResponseCode());

            if (response.getResponseCode() != 200) {
                // Didn't get success status from API
                throw new Exception(String.format("Expecting HTTP Status Code 200 from API, received %s", response.getResponseCode()));
            }
            Job job = mapper.readValue(response.getResponseBody(), Job.class);

            logger.info("API has started processing data upload job with ID {}", job.getId());

        } catch (Exception ex) {
            logger.error("Issue with data submission to API: {}", ex.getMessage());
            throw ex;
        }

    }

    private static String getFileNameToDownload(String fileType, String fileName) {
        /* The Parkland server path will have Excel files named by day.  So...
      2023-06-04.xlsx
      2023-06-05.xlsx
      etc...

      CSV files will be named like:

      THSA_Saner_Bed_List20230908.csv

      One can run the command and specify a filename, but if that is blank, here we
      will default to the current date.
     */
        if (fileName == null || fileName.trim().isEmpty()){
            if (fileType.equals("csv")) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                String today = sdf.format(new Date());
                fileName = String.format("THSA_Saner_Bed_List%s.%s", today, fileType);
            } else if (fileType.equals("xlsx")) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                String today = sdf.format(new Date());
                fileName = String.format("%s.%s", today, fileType);
            }
        }

        return fileName;
    }
}
