package testzeus.base;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.jayway.jsonpath.JsonPath;

import net.minidev.json.JSONArray;

/**
 * @author Robin
 * @date: 04/02/2021
 * @purpose: This class gets the UI layout from UI API and tries to make the
 *           xpath for all the elements 👼
 * @see: A lot of these methods are implemented using JSONPATH to parse the
 *       response we get from UI API
 */

public class SFPageBase extends PageBase {

	public SFPageBase(WebDriver driver) {
		super(driver);
	}

	protected static String uiapi_record_json;
	private static ArrayList<String> listoflabels;
	protected static HashMap<String, String> labelandtype;

	public void waitForSFPagetoLoad() throws InterruptedException {
		// Below is a custom wait method specifically built for Salesforce based on the
		// concept of EPT
		// https://trailhead.salesforce.com/en/content/learn/modules/lightning-experience-performance-optimization/measure-lightning-experience-performance-and-experience-page-time-ept
		Thread.sleep(3000);
		try {
			WebDriverWait wait1 = new WebDriverWait(driver, 50);

			ExpectedCondition<Boolean> jsLoad = new ExpectedCondition<Boolean>() {
				@Override
				public Boolean apply(WebDriver driver) {
					return ((JavascriptExecutor) driver).executeScript("return document.readyState").toString()
							.equals("complete");
				}
			};

			ExpectedCondition<Boolean> aurascriptLoad = new ExpectedCondition<Boolean>() {
				@Override
				public Boolean apply(WebDriver driver) {
					String WAIT_FOR_AURA_SCRIPT = "return (typeof $A !== 'undefined' && $A && $A.metricsService.getCurrentPageTransaction().config.context.ept > 0)";
					String EPT_COUNTER_SCRIPT = "return ($A.metricsService.getCurrentPageTransaction().config.context.ept)";
					Boolean result = (Boolean) ((JavascriptExecutor) driver).executeScript((WAIT_FOR_AURA_SCRIPT));

					if (result.equals(true)) {
						System.out.println("Experienced Page Load time in milliseconds on the current page is : "
								+ ((JavascriptExecutor) driver).executeScript(EPT_COUNTER_SCRIPT));
						return true;
					} else {
						return false;
					}

				}
			};
			if (wait1.until(jsLoad) && wait1.until(aurascriptLoad)) {
				System.out.println("Page load complete");
			} else {
				Thread.sleep(2000);
			}
		}

		catch (Exception e) {
			System.out.println("Exception happened in waiting for page to load , so sleeping for 5 seconds");
			System.out.println("Exception is " + e.getMessage());
			Thread.sleep(5000);

		}
	}

	public static void uiApiHitter(String recordID) {
		// This method call is the heart of the UI API based automation and gets the UI
		// API
		// Json for further operations ♥
		// Here 0015g00000S9lfUAAR is the record ID of an ACCOUNT, but the same API and
		// general methods below can be used for the other sbjects.
		uiapi_record_json = (HTTPClientWrapper
				.runGetRequest("/ui-api/record-ui/" + recordID + "?formFactor=Large&modes=View,Edit")).toString();
		System.out.println(uiapi_record_json);

	}

	public static void sectionGetter() throws Exception {
		// This method brings in the count of sections displayed on the UI
		String sectionspath = "$.layouts.Account..sections";
		JSONArray sectionsparent = JsonPath.read(uiapi_record_json, sectionspath);
		JSONArray sectionsarray = (JSONArray) sectionsparent.get(0);
		System.out.println("Count of Sections is : " + sectionsarray.size());

	}

	public static void labelGetter() throws Exception {
		// These labels are gathered from layoutComponents as we get labels which are
		// actually displayed on the UI rather than all the fields for the sObject
		String labelpath = "$..[?(@.editableForUpdate == true)].layoutComponents..label";
		JSONArray listofduplicatelabels = JsonPath.read(uiapi_record_json, labelpath);
		// As we are hitting modes=View, Edit, hence we are getting duplicates.
		LinkedHashSet<String> labels = new LinkedHashSet<String>();
		for (int i = 0; i < listofduplicatelabels.size(); i++) {
			labels.add((String) listofduplicatelabels.get(i));
		}

		listoflabels = new ArrayList<String>();
		listoflabels.addAll(labels);
		System.out.println("Labels are " + labels);

	}

	public static void dataTypeGetter() throws Exception {
		// This method fetches the data type for all labels from the UI API JSON
		labelandtype = new HashMap<>();
		for (int i = 0; i < listoflabels.size(); i++) {
			String label = listoflabels.get(i);
			String typepath = "$..[?(@.label =='" + label + "')].dataType";
			String datatype = JsonPath.read(uiapi_record_json, typepath).toString();
			labelandtype.put(label, datatype);
		}
		labelandtype.entrySet().forEach(entry -> {
			System.out.println("Label and types are : " + entry.getKey() + " " + entry.getValue());
		});
	}

	public void uiApiParser(String recordid) throws Exception {
		uiApiHitter(recordid);
		sectionGetter();
		labelGetter();
		dataTypeGetter();
	}

	public void formValueFiller(String label, String targetvalue) throws Exception {
		// This method automagically uses the label and datatypes to fill the form on
		// the fly
		// And reduces the pain for creation and maintenance of separate pageobjects and
		// web elements
		WebElement we;
		String type = labelandtype.get(label);
		switch (type) {
		case "[\"String\"]":
		case "[\"Url\"]":
		case "[\"Int\"]":
		case "[\"Phone\"]":
		case "[\"Currency\"]":
		case "[\"Double\"]":
		case "[\"Date\"]":
		case "[\"Boolean\"]":
			Thread.sleep(5000);
			// Locator design inspired by
			// https://trailblazers.salesforce.com/_ui/core/chatter/groups/GroupProfilePage?g=0F93A000000DQPd&fId=0D54S000008HKSK
			we = driver.findElement(By.xpath("//input[@id=string(//label[text()='" + label + "']/@for)]"));
			we.sendKeys(targetvalue);
			System.out.println("Sent values as " + targetvalue);
			break;
		case "[\"TextArea\"]":
			we = driver.findElement(By.xpath("//textarea[@id=string(//label[text()='" + label + "']/@for)]"));
			we.sendKeys(targetvalue);
			break;
		case "[\"Picklist\"]":
			we = driver.findElement(By.xpath("//input[@id=string(//label[text()='" + label + "']/@for)]"));
			we.sendKeys(targetvalue);
			Thread.sleep(2000);
			we.sendKeys(Keys.ENTER);
			break;
		case "[\"Reference\"]":
			we = driver.findElement(By.xpath("//input[@id=string(//label[text()='" + label + "']/@for)]"));
			we.sendKeys(Keys.ARROW_DOWN);
			Thread.sleep(2000);
			we.sendKeys(Keys.ENTER);
			break;
		}

	}

	public static void verifyRequiredFields(String testdatajson, String objname) {
		// This method checks whether the specified value is mandatory in the UI or not
		String valuename = objname + "Name";
		String isrequiredexpected = readJsonFile(testdatajson, "$." + valuename + ".isRequired");

		String objjson = HTTPClientWrapper.runGetRequest("/sobjects/" + objname + "/describe/layouts/").toString();
		String jsonpath = "$..[?(@.label==\"Account Name\")]..required";

		String isrequiredactual = JsonPath.read(objjson, jsonpath).toString();
		System.out.print("Validating that the object contains the right mandatory fields");
		if (isrequiredactual.contains(isrequiredexpected)) {
			System.out.println(
					"THIS IS A TEST-------------------------------------SHOULD BE REPLACED BY TESTNG/JUNIT ASSERTS");
			System.out.println("Required fields verified correctly");
			System.out.println("--------------------------------------------------------------------------");

		} else {
			System.out.println(
					"THIS IS A TEST-------------------------------------SHOULD BE REPLACED BY TESTNG/JUNIT ASSERTS");
			System.out.println("Required fields couldnt be verified correctly");
			System.out.println("--------------------------------------------------------------------------");
		}

	}

	public void globalSearch(String searchterm) {
		// To be implemented in the future

	}

	public String getURL(String appname) { // Method to get SF Apps URL and simulate 9 dot navigation
		GetSFApps getSfApps = new GetSFApps();
		return getSfApps.getAppNavURL(appname);

	}

	public void appLauncher(String appname) throws InterruptedException {
		String accountappurl = getURL(appname);
		System.out.println("account URL is" + accountappurl);
		String cleanurl = accountappurl.replace("[\"", "").replace("\"]", "");
		System.out.println("Navigating to App URL as : " + cleanurl);
		openHomepage(cleanurl + "?eptVisible=1");

		waitForSFPagetoLoad();

	}

}
