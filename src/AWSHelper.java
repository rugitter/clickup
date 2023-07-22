import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

public class AWSHelper {
    final String bucketName = "yeeyi-ding";
    final String configFileName = "config";
    final String logFolderName = "logs";
    
	public int lineNum;
	public int wordNum;
	public String[][] info;
	private AmazonS3 s3;
	
	public AWSHelper() {
		lineNum = 0;
		wordNum = 0;
		info = new String[20][3];
		
		init();
	}
	
	private void init() {
		/*
         * The ProfileCredentialsProvider will return your [yeeyi_SDK_user]
         * credential profile by reading from the credentials file located at
         * (C:\\Users\\qcheng2\\.aws\\credentials).
         */
        AWSCredentials credentials = null;
        try {
            credentials = new ProfileCredentialsProvider("yeeyi_SDK_user").getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file.", e);
        }

        s3 = AmazonS3ClientBuilder.standard()
            .withCredentials(new AWSStaticCredentialsProvider(credentials))
            .withRegion("us-east-1")		//.withRegion(Regions.DEFAULT_REGION)
            .build();

        System.out.println("Amazon S3 client is built successfully!");
	}
	
	// Read config from S3 bucket
	public String readConfig() {
		String mode = null;
		
        if (s3.doesBucketExistV2(bucketName)) {
            System.out.format("Read config from Bucket \"%s\":", bucketName);
            
            try {          	                  	
                S3Object object = s3.getObject(bucketName, configFileName);
                S3ObjectInputStream s3is = object.getObjectContent();
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(s3is));
                
                String config = reader.readLine();
                mode = config;
        		if (mode.equalsIgnoreCase("DEBUG")) {
        			System.out.println("Debug mode. Program exits!");
        			
//        			LOGGER.info("Debug mode. Program exits!");
        			// Step 5: Need to change to Unix shutdown
        			System.exit(0);
        		}
        		else {
        			System.out.println("mode: " + config);
        		}

                while (( config = reader.readLine()) != null)
                {
                	System.out.println(config);
                	
        			String[] parts = config.split(" ");
        			wordNum = 0;
        			
        			for( String a : parts) {
        				info[lineNum][wordNum] = a;
        				wordNum++;
        			}
        			lineNum++;
                }
                s3is.close();
            } catch (AmazonServiceException e) {
                System.err.println("AWS Exception : " + e.getErrorMessage());
            } catch (FileNotFoundException e) {
                System.err.println("FileNotFound : " + e.getMessage());
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }
            finally {
            	System.out.printf("Total %d posts!\n", lineNum);
            }
        }
        else
        {
        	System.out.println("Bucket not exists");
        }
        
        return mode;
    }
	
	public int getLineNum() {
		return this.lineNum;
	}

	public void uploadLog(String logString) {
		System.out.format("Uploading to S3 bucket...\n");
		File tempFile = null;
		
		Date now = new Date();
		String datePath = new SimpleDateFormat("yyyy-MM-dd").format(now);
		String fileName = new SimpleDateFormat("'yeeyi-'yyyyMMdd_hhmm'.log'").format(now);
		
		String fullPath = String.format("%s/%s/%s", logFolderName, datePath, fileName);
		
		try {
			tempFile = File.createTempFile("tempLog", ".txt");
			FileWriter fw = new FileWriter(tempFile);
			fw.write(logString);
			fw.close();		// write (flush) to file
			
		    s3.putObject(bucketName, fullPath, tempFile);
	        tempFile.deleteOnExit();	// Delete temp file before exiting vm
	        System.out.println("Complete uploading to S3 bucket");
		} 
		catch (AmazonServiceException e) {
		    System.err.println(String.format("Upload Failed: AWS Exception. %s", e.getErrorMessage()));
		}
		catch (IOException e){
		    System.err.println(String.format("Upload Failed: IO Exception. %s", e.getMessage()));
		}
		catch (Exception e){
		    System.err.println(String.format("Upload Failed: Exception. %s", e.getMessage()));
		}
	}

}