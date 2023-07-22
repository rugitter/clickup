import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.google.common.io.Files;

public class YeeyiDing {
	private final static int MAXPOSTNUM = 10;		// Maxinum number of posts this tool can support
	public static enum Mode {
	    DEBUG,
	    PROD,
	    DEV
	}
	private static final Logger LOGGER = Logger.getLogger(YeeyiDing.class.getName());
	// Step 1: Change path for dev
	// private final static String path = "";			// For dev env			
	private final static String path = "/home/ubuntu/java/";		// For production env
	private static Mode mode = Mode.DEBUG;	// Default in Debug mode
	
	private static String fileName = "";
	private static String[][] info = new String[MAXPOSTNUM][3];	// A array of arrays-Store lines in config file
								// 3 means we have 3 parameters to store: url, username and password
	private static StringBuilder s3LogString = new StringBuilder();
	
	public static void main(String[] args) throws IOException, InterruptedException {
		Handler fileHandler = null;
        Formatter simpleFormatter = null;
        int sleepSecond;
        int lineNum;
        boolean isSuccess = false;
        int tryCount = 0;	// If exception found, try 3 times before exit
        
		// Config LOGGER
		// In prod, can also use "%h/java/log/yeeyi.log"
		fileHandler = new FileHandler(path + "log/yeeyi.log", true);
		System.setProperty("java.util.logging.SimpleFormatter.format",
				"%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$-6s %5$s%6$s%n");	//Set up default SimpleFormatter
        simpleFormatter = new SimpleFormatter();
        fileHandler.setFormatter(simpleFormatter);      
        LOGGER.addHandler(fileHandler);
        LOGGER.setLevel(Level.ALL);
        fileHandler.setLevel(Level.ALL);

	    AWSHelper awsHelper = new AWSHelper();
             
		try {
            // Start of the execution
			log("******************************************************");
			sleepSecond = 30;
			log(String.format("Sleep for %d sec before start read config......", sleepSecond));
		    Thread.sleep(sleepSecond * 1000);
			
			// Read configuration - Format : {URL, username and password}.
//		    lineNum = readConfigFile();		// (Obsolete): Read from local file
		    String configMode = awsHelper.readConfig();	// Read from AWS S3 bucket
		    if (configMode != null) {
		    	mode = Mode.valueOf(configMode.toUpperCase());
		    	info = awsHelper.info;
		    	lineNum = awsHelper.getLineNum();
		    }
		    // Can add logic here to read from local file if in DEV mode
		    else {
		    	log("Config mode is null - empty config. Stop immediately!");
		    	System.exit(0);		// exit program without shutting down
		    	lineNum = 0;
		    }
		    
		    // If exception thrown out, retry for 3 times (total) and then exit
			while(!isSuccess && tryCount < 3 )
			{
				try {
					// Loop through each configure - Open pages, Log in and Ding it
					for(int l = 0; l < lineNum; l++ ) {
						String url = info[l][0];
						String uname = info[l][1];
						String upw = info[l][2];
						log((l+1) + "." + url + " - " + uname);
						
						ding(l, url, uname, upw);
						
					} // End of for loop for each line (web page)
					log("All posts are Dinged successfully");
					isSuccess = true;
				} // End of inner try
				catch (Exception dingEx) {
//					driver.close();
					log(Level.SEVERE, String.format("Inner Ding Exception: %s", dingEx.getMessage()));
					tryCount++;
				}
			} // End of 3 failure tries
		} // End of outer try
		catch (Exception otherEx) {
			log(Level.SEVERE, String.format("Outer Exception: %s", otherEx.getMessage()));
		}
		
	    awsHelper.uploadLog(s3LogString.toString());
		    
		sleepSecond = 5;
		log(String.format("Sleep for %d sec before shut down......", sleepSecond));
	    Thread.sleep(sleepSecond * 1000);
	    
	    // Step 2: Shutdown Unix VM on EC2
	    Runtime.getRuntime().exec("sudo shutdown -h now");
	    // System.exit(0);	//	for local dev
	}
	
	// (Obsolete) Read config from a local file on dev PC/EC2 instance.
	private static int readConfigFile() throws FileNotFoundException
	{
		int lineNum = 0;
		int wordNum = 0;
		
		// Use absolute path is safer because don't know which account the program will run on
		Scanner scanner = new Scanner(new File(path + "config.txt"));
		
		// Exit the program immediately if see "Debug" flag at 1st line
		String mode = scanner.nextLine();
		if (mode.equalsIgnoreCase("Debug")) {
			log("Debug mode. Program exits!");
			System.exit(0);
		}
		
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			String[] parts = line.split(" ");
			wordNum = 0;
			
			for( String a : parts) {
				info[lineNum][wordNum] = a;
				wordNum++;		
			}
			lineNum++;
		}
		scanner.close();
		
		return lineNum;
	}
	
	private static void ding(int lineNumber, String url, String uname, String upw) throws Exception {
        // Attach today's date to screenshot file name
		SimpleDateFormat ozFormat = new SimpleDateFormat("'yeeyi-'MM_dd'.png'");
									//use "'yeeyi-'MM_dd_HHmm'.png'" for hourly screenshot
		ozFormat.setTimeZone(TimeZone.getTimeZone("Australia/Sydney"));
		
		// Step 3: Set up ChromeDriver path
		// ChromeDriver Path for local dev purpose
		System.setProperty("webdriver.chrome.driver", path + "chromedriver");	// Prod path
		// System.setProperty("webdriver.chrome.driver", "chromedriver.exe"); //Dev path
		
		// Create Driver Object			
		ChromeOptions chromeOptions = new ChromeOptions();
		chromeOptions.addArguments("--headless");
		chromeOptions.addArguments("--lang=en-GB");
		WebDriver driver = new ChromeDriver(chromeOptions);
		
		driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS); //general timeout for waiting on dynamic web elements
		driver.manage().window().setSize(new Dimension(1500,700)); //set window size
		log("Chrome Browser and Driver Started");

		// Try to open the URL for the post page
		driver.get(url);
		log("Post URL Opened - Before log in");
		// log(driver.getPageSource());		//turn on this switch when in debug mode

		// Click on the Log in link on the post
// 		driver.findElement(By.xpath("/html/body/div[1]/div[1]/div/div[2]/div[1]/span/a")).click();	// fixed address, not working
		driver.findElement(By.xpath("//*[@id=\"__next\"]/div[1]/div/div[2]/div[1]/span/a")).click();
//		driver.findElement(By.xpath("//*[@id=\"login_place\"]/li[1]/span/a")).click();
		log("Redirect to log in page");
			
		// Wait until successfully load the log in page
		WebDriverWait wait5 = new WebDriverWait(driver, 5);
		wait5.until(ExpectedConditions.presenceOfElementLocated(By.id("usr")));
		
		// Log in
		driver.findElement(By.id("usr")).sendKeys(uname);
		driver.findElement(By.id("pwd")).sendKeys(upw);
		driver.findElement(By.xpath("/html/body/div[1]/div[6]/div/div[2]/div/div[1]/div/div[2]/div/button")).click();
		
		// Wait until the "Ding" element present
		By dingButton = By.xpath("/html/body/div[1]/div[9]/div/div[1]/div[1]/div[2]/div[1]/span");
		//     //*[@id="__next"]/div[9]/div/div[1]/div[1]/div[2]/div[1]/span
		wait5.until(ExpectedConditions.presenceOfElementLocated(dingButton));
		driver.findElement(dingButton).click();			
		log("END - Click Refresh button.");
		
		// Catch every pop-up windows, click "Confirm" button to close it
		for (String winhandle: driver.getWindowHandles()) {
		    driver.switchTo().window(winhandle);

			WebDriverWait wait2 = new WebDriverWait(driver, 2);
			By confirmButton = By.xpath("/html/body/div[3]/div/div[2]/div/div[2]/div/button");
			wait2.until(ExpectedConditions.presenceOfElementLocated(confirmButton));
			
			//take screenshot
			fileName = lineNumber + ". " + ozFormat.format(new Date());
			File src = ((TakesScreenshot)driver).getScreenshotAs(OutputType.FILE);	
			FileUtils.copyFile(src, new File(path + "log/" + fileName));
			log("Screenshot took");
			
		    driver.findElement(confirmButton).click();
		}
	    driver.close();
	}
	
	// Overloading method to simulate default parameters
	private static void log(String msg) {
		log(Level.INFO, msg);
	}
	
	private static void log(Level level, String msg) {
		// Log to local using Java Logging
		LOGGER.log(level, msg);
		
		// Build log string for S3 logging
		SimpleDateFormat formatter= new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date date = new Date();
		s3LogString.append(String.format("%s   %s\n", formatter.format(date), msg));
	}
}