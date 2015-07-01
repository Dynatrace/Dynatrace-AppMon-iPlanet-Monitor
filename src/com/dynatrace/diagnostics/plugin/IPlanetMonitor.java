
 /**
  * This template file was generated by dynaTrace client.
  * The dynaTrace community portal can be found here: http://community.compuwareapm.com/
  * For information how to publish a plugin please visit http://community.compuwareapm.com/plugins/contribute/
  **/ 

package com.dynatrace.diagnostics.plugin;

import com.dynatrace.diagnostics.pdk.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.security.cert.X509Certificate;


import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Document;

public class IPlanetMonitor implements Monitor {

	private static final Logger log = Logger.getLogger(IPlanetMonitor.class.getName());
	
	//initialize config variables
	private String protocol;
	private String uriPath;
	private int port;
	
	private URL url;
	private URLConnection connection;
	HostnameVerifier defaultVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
	XPath xpath = XPathFactory.newInstance().newXPath();
	private Document doc;
	
	/**
	 * Initializes the Plugin. This method is called in the following cases:
	 * <ul>
	 * <li>before <tt>execute</tt> is called the first time for this
	 * scheduled Plugin</li>
	 * <li>before the next <tt>execute</tt> if <tt>teardown</tt> was called
	 * after the last execution</li>
	 * </ul>
	 * <p>
	 * If the returned status is <tt>null</tt> or the status code is a
	 * non-success code then {@link Plugin#teardown() teardown()} will be called
	 * next.
	 * <p>
	 * Resources like sockets or files can be opened in this method.
	 * @param env
	 *            the configured <tt>MonitorEnvironment</tt> for this Plugin;
	 *            contains subscribed measures, but <b>measurements will be
	 *            discarded</b>
	 * @see Plugin#teardown()
	 * @return a <tt>Status</tt> object that describes the result of the
	 *         method call
	 */

	@Override
	public Status setup(MonitorEnvironment env) throws Exception {
		Status status = new Status(Status.StatusCode.Success);
		//check plugin environment configuration parameter values
		if (env == null || env.getHost() == null) {
			status.setStatusCode(Status.StatusCode.ErrorInternalConfigurationProblem);
			status.setShortMessage("Environment was not properly initialized. env.host must not be null.");
			status.setMessage("Environment was not properly initialized. env.host must not be null.");
			Exception e = new IllegalArgumentException("Environment was not properly initialized. env.host must not be null.");
			status.setException(e);
			log.log(Level.SEVERE, status.getMessage(), e);
			return status;
		}
		
		protocol = env.getConfigString("protocol");
		uriPath = env.getConfigString("uriPath");
		if (!env.getConfigBoolean("isCustomPort")) {
			port = -1;
		} else {

		port = env.getConfigLong("serverPort").intValue();

		}
		Collection<MonitorMeasure> measures = env.getMonitorMeasures("Performance Counters","Availability");		
		for (MonitorMeasure measure : measures){
			measure.setValue(0.0);
		}
		return status;
	}

	@Override
	public Status execute(MonitorEnvironment env) throws Exception {
		Status status = new Status();				
		logFine("Begin Plugin Execution");
		String host = env.getHost().getAddress();
		//create URL
		url = new URL(protocol, host, port, uriPath);
		

		Collection<MonitorMeasure> measures = env.getMonitorMeasures();
		
		StringBuilder messageBuffer = new StringBuilder("URL: ");
		messageBuffer.append(url).append("\r\n");
		
		try {
				logInfo("Executing method: " + /**config.method +**/ ", URI: " + url.toString()); //httpRequest.getURI());
			
			//grab iPlanet XML file
			if(env.getConfigBoolean("ignoreSSLErrors")){
				TrustSSL.trustAllCerts();	
			}
			connection = url.openConnection();
						
			logFine("Parsing XML from: " + url.toString());
			//parse XML response
			try {
			parseXmlFile(connection.getInputStream());
			} catch(Throwable e) {
				for (MonitorMeasure measure : measures) {
					if (measure.getMetricName().equals("Availability")){
						measure.setValue(0);
					}		
				}
				return status;
			}
			HttpsURLConnection.setDefaultHostnameVerifier(defaultVerifier);			
			logFine("Retrieving XML Results...");
			for (MonitorMeasure measure : measures) {
				double value = 1234567890;
				if (measure.getMetricName().equals("Uptime")) {
					value = System.currentTimeMillis()/1000 -  Double.parseDouble(retrieveXmlElementTags("process","timeStarted"));
					measure.setValue(value);
				} else if (measure.getMetricName().equals("Busy Threads")) {
					value = Double.parseDouble(retrieveXmlElementTags("thread-pool-bucket","countThreads")) - Double.parseDouble(retrieveXmlElementTags("thread-pool-bucket","countThreadsIdle"));
					measure.setValue(value);
				} else if (measure.getMetricName().equals("Availability")) {
					value = 1.0;
					measure.setValue(value);	
				} else if (measure.getParameter("node")!=null){
					String nodeName = measure.getParameter("node");
					String attribute = measure.getParameter("attributeNumerator");
					String denomConfig = measure.getParameter("attributeDenominator");
					double denominator = 1;
					value = Double.parseDouble(retrieveXmlElementTags(nodeName,attribute));
					
					if (!denomConfig.equals("1")){
						int tempDenom = Integer.parseInt(retrieveXmlElementTags(nodeName,denomConfig));
						if (tempDenom!=0)
							denominator = 0.01*tempDenom;
					}
					value = value / denominator;
					measure.setValue(value);
				}
				else {
					measure.setValue(value);
				}
				logFine("Measure " + measure.getMetricName() + " set to " + value);
			}
			/*
			//retrieve measure for last time Endeca index was updated			
			this.indexUpdateTime = retrieveXmlElementTags(DATA_INFO, DATA_DATE, "", "");			
			//retrieve measure for number of Endeca requests during monitoring interval
			this.numRequests = Double.parseDouble(retrieveXmlElementTags(REQ_INFO, NUM_REQS, "", ""));
			//retrieve measure for CPU usage for Endeca
			this.cpuUsageString = retrieveXmlElementTags(RUSAGE, USER_CPU_TIME, "", "");
			//retrieve measure for Request Time Avg
			this.reqTimeAvg = Double.parseDouble(retrieveXmlElementTags(STAT, STAT_ATTR_NAME, STAT_ATTR_AVG, SERVER_STAT_NAME_REQ_TIME));
			//retrieve measure for Request Time Max
			this.reqTimeMax = Double.parseDouble(retrieveXmlElementTags(STAT, STAT_ATTR_NAME, STAT_ATTR_MAX, SERVER_STAT_NAME_REQ_TIME));
			//retrieve measure for Endeca Avg Query Performance
			this.queryPerfAvg = Double.parseDouble(retrieveXmlElementTags(STAT, STAT_ATTR_NAME, STAT_ATTR_AVG, STAT_QUERY_PERF_NAME));
			//retrieve measure for Endeca Max Query Performance
			this.queryPerfMax = Double.parseDouble(retrieveXmlElementTags(STAT, STAT_ATTR_NAME, STAT_ATTR_MAX, STAT_QUERY_PERF_NAME));
								*/
		} catch (ConnectException ce) {
			status.setException(ce);
			status.setStatusCode(Status.StatusCode.PartialSuccess);
			status.setShortMessage(ce == null ? "" : ce.getClass().getSimpleName());
			messageBuffer.append(ce == null ? "" : ce.getMessage());
			log.log(Level.SEVERE, status.getMessage(), ce);
		} catch (IOException ioe) {
			status.setException(ioe);
			status.setStatusCode(Status.StatusCode.ErrorTargetServiceExecutionFailed);
			status.setShortMessage(ioe == null ? "" : ioe.getClass().getSimpleName());
			messageBuffer.append(ioe == null ? "" : ioe.getMessage());
			//if (log.isLoggable(Level.SEVERE))
				log.severe("Requesting URL " + url.toString() /**httpRequest.getURI()**/ + " caused exception: " + ioe);
		} 	
		// calculate and set the measurements
		/*
		Collection<MonitorMeasure> measures;						
		if (status.getStatusCode().getCode() == Status.StatusCode.Success.getCode()) {
			if ((measures = env.getMonitorMeasures(METRIC_GROUP, MSR_NUM_OF_REQUESTS)) != null) {				 
				for (MonitorMeasure measure : measures) {
					if (log.isLoggable(Level.FINE)){
						log.fine("Number of requests=" + numRequests);
					}
					measure.setValue(this.numRequests);
					
				}
			}		
			if ((measures = env.getMonitorMeasures(METRIC_GROUP, MSR_INDEX_LAST_UPDATE_TIME)) != null) {				 				
				//convert and compare current date/time against the date of the last index update
				calcLastIndexUpdate();				
				for (MonitorMeasure measure : measures) {										  
					//log.severe("Time Since Last Update= " + timeSinceLastUpdate);
					measure.setValue(this.timeSinceLastUpdate);					
				}
			}
			if ((measures = env.getMonitorMeasures(METRIC_GROUP, MSR_CPU_USAGE)) != null) {				 
				//log.severe("CPU user time= " + cpuUsageString);
				this.cpuUsage = Double.parseDouble(parseCpuUsage(cpuUsageString));
				for (MonitorMeasure measure : measures) {
					//log.severe("CPU user time=" + cpuUsageString);
					measure.setValue(this.cpuUsage);
					
				}
			}	
			if ((measures = env.getMonitorMeasures(METRIC_GROUP, MSR_REQ_TIME_AVG)) != null) {				 
				for (MonitorMeasure measure : measures) {
					//log.severe("Request time Avg=" + reqTimeAvg);
					measure.setValue(this.reqTimeAvg);
					
				}
			}		
			if ((measures = env.getMonitorMeasures(METRIC_GROUP, MSR_REQ_TIME_MAX)) != null) {				 
				for (MonitorMeasure measure : measures) {
					//log.severe("Request Time Maxe=" + reqTimeMax);
					measure.setValue(this.reqTimeMax);
					
				}
			}	
			if ((measures = env.getMonitorMeasures(METRIC_GROUP, MSR_QUERY_PERF_AVG)) != null) {				 
				for (MonitorMeasure measure : measures) {
					
					measure.setValue(this.queryPerfAvg);
					
				}
			}		
			if ((measures = env.getMonitorMeasures(METRIC_GROUP, MSR_QUERY_PERF_MAX)) != null) {				 
				for (MonitorMeasure measure : measures) {
					
					measure.setValue(this.queryPerfMax);
					
				}
			}		
		}
*/
		status.setMessage(messageBuffer.toString());
			logFine("Plugin Status: " + messageBuffer.toString());
		
		return status;
	}



	private void parseXmlFile(InputStream stream)throws Exception {						
		
		//get the factory
		DocumentBuilderFactory dbfactory = DocumentBuilderFactory.newInstance();
		dbfactory.setValidating(false);
		dbfactory.setNamespaceAware(true);
		dbfactory.setFeature("http://xml.org/sax/features/namespaces", false);
		dbfactory.setFeature("http://xml.org/sax/features/validation", false);
		dbfactory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
		dbfactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		try {			
			//Using factory get an instance of document builder
			DocumentBuilder dBuilder = dbfactory.newDocumentBuilder();						
			//parse using builder to get DOM representation of the XML file
			doc = dBuilder.parse(stream);						 
		}catch(ParserConfigurationException pce) {
			log.severe(pce.getMessage());			
		}catch(IOException ioe) {
			log.severe(ioe.getMessage());			
		}
		 
				
	}

	
	private String retrieveTagByXpath(String elementTag, String attribute) throws Exception {
		
		String xmlAttribute = "0";		
		
		//get the root elememt
		doc.getDocumentElement().normalize();		
		//get a nodelist of <EndecaMeasures> elements		
		NodeList nodes = doc.getElementsByTagName(elementTag);
		if (log.isLoggable(Level.FINE)){
			log.fine ("The element tag is set to: " + elementTag);
			//log.severe ("The element tag is set to: " + elementTag);
			log.fine("The XML attribute is equal to: " + attribute);
			//log.severe("The XML attribute is equal to: " + attribute + ":" + attribute2);
		}
							
		try {		
			if (nodes != null && nodes.getLength() > 0) {
				
				//log.info("Node Count: " + nodes.getLength());				
				//if only one node attribute exists, assign out the values; else, loop through the nodelist for the correct attribute string
				if (nodes.getLength() == 1){ 
					
					//get the EndecaMeasure element
					Element el = (Element)nodes.item(0);
					xmlAttribute = el.getAttribute(attribute);
					if (log.isLoggable(Level.FINE)){
						log.fine ("The xml attribute measure value is equal to: " + xmlAttribute);
						//log.severe ("The xml attribute measure value is equal to: " + xmlAttribute);
					}
				}
				else if (nodes.getLength() > 1) {										
					for(int i = 0 ; i < nodes.getLength();i++) {	
						//get the EndecaMeasure element
						Element el = (Element)nodes.item(i);						
						
						//capture the String value of each attribute associated with the elementTag being passed in (i.e. name)
						String value = el.getAttribute(attribute); 				         
						//log.severe ("The attribute value is: " + attribute);
						//Capture the numeric value of the specific attribute to be charted with this monitor (i.e. avg, max, etc)
						//String value2 = el.getAttribute(attribute1);
						//log.severe ("The attribute1 value is: " + attribute1);
 			           
					}										
					
				}
				else {
					
					log.severe ("Unknown error with parsing XML elements...");
				}
								
			} 			
			else {
				
				log.severe("Node(s) are null: " + elementTag + " and " + attribute);
			}
		}catch(Exception ex) {
			log.severe(ex.getMessage());			
		}
		
		return xmlAttribute;		
				
	}		
	
	
	private String retrieveXmlElementTags(String elementTag, String attribute) throws Exception {
		
		String xmlAttribute = "0";		
		
		//get the root elememt
		doc.getDocumentElement().normalize();		
		//get a nodelist of <EndecaMeasures> elements
		NodeList nodes;
		if (elementTag.startsWith("/")) {
			//nodes = (NodeList) xpath.evaluate(elementTag, new InputSource(doc) , XPathConstants.NODESET);
			  XPathExpression expression=xpath.compile(elementTag);
			  nodes=(NodeList)expression.evaluate(doc,XPathConstants.NODESET);
		} else {
		nodes = doc.getElementsByTagName(elementTag);
		}
		if (log.isLoggable(Level.FINE)){
			log.fine ("The element tag is set to: " + elementTag);
			//log.severe ("The element tag is set to: " + elementTag);
			log.fine("The XML attribute is equal to: " + attribute);
			//log.severe("The XML attribute is equal to: " + attribute + ":" + attribute2);
		}
							
		try {		
			if (nodes != null && nodes.getLength() > 0) {
				
				//log.info("Node Count: " + nodes.getLength());				
				//if only one node attribute exists, assign out the values; else, loop through the nodelist for the correct attribute string
				if (nodes.getLength() == 1){ 
					
					//get the EndecaMeasure element
					Element el = (Element)nodes.item(0);
					xmlAttribute = el.getAttribute(attribute);
					if (log.isLoggable(Level.FINE)){
						log.fine ("The xml attribute measure value is equal to: " + xmlAttribute);
						//log.severe ("The xml attribute measure value is equal to: " + xmlAttribute);
					}
				}
				else if (nodes.getLength() > 1) {										
					for(int i = 0 ; i < nodes.getLength();i++) {	
						//get the EndecaMeasure element
						Element el = (Element)nodes.item(i);						
						
						//capture the String value of each attribute associated with the elementTag being passed in (i.e. name)
						String value = el.getAttribute(attribute); 				         
						//log.severe ("The attribute value is: " + attribute);
						//Capture the numeric value of the specific attribute to be charted with this monitor (i.e. avg, max, etc)
						//String value2 = el.getAttribute(attribute1);
						//log.severe ("The attribute1 value is: " + attribute1);
 			           
					}										
					
				}
				else {
					
					log.severe ("Unknown error with parsing XML elements...");
				}
								
			} 			
			else {
				
				log.severe("Node(s) are null: " + elementTag + " and " + attribute);
			}
		}catch(Exception ex) {
			log.severe(ex.getMessage());			
		}
		
		return xmlAttribute;		
				
	}		
	
	/*
	private void calcLastIndexUpdate () throws Exception {
    	
		long seconds = 0;    	
    	    	
    	Date currTime = new Date();
    	String dateStr = this.indexUpdateTime;    	
    	
    	try {
    		DateFormat formatter = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy");
    		Date date = formatter.parse(dateStr);
    		//log.severe("After parse: " + date.getTime());
    		//log.severe ("The Last Index Update was at: " + date);    		
    		//log.severe ("The current time is: " + currTime.getTime());
    		//timeSinceLastUpdate = ((System.currentTimeMillis() - date.getTime())) / 1000;
    		seconds = ((currTime.getTime() - date.getTime()) / 1000);    		
    		//log.severe ("Time Update Diff: " + seconds);
    		this.timeSinceLastUpdate = seconds;
    		if (log.isLoggable(Level.FINE)){
    			log.fine ("The time elapsed since the Index was last updated is: " + timeSinceLastUpdate);
    		}
    	
    	} catch(Exception ex) {
    		log.severe (ex.getMessage());    	
    	}
    }	
	
	private String parseCpuUsage (String cpuString) {
		
		String cpuUsage;
		String cpuStr;
		
		cpuStr = cpuString;
	
	    double minutes = 0;
		double seconds = 0;
		
		if (cpuStr.contains("minutes")){
			minutes = Double.parseDouble(cpuStr.substring(0,cpuStr.indexOf(' ')));
			cpuStr = cpuStr.substring(cpuStr.indexOf(',')+2);
			//log.severe ("New cpuStr = " + cpuStr);
		}
		
		if (cpuStr.contains("seconds")) {
			seconds = Double.parseDouble(cpuStr.substring(0,cpuStr.indexOf(' ')));
		}
		
		seconds = seconds + minutes*60;
		cpuUsage = Double.toString(seconds);
		return cpuUsage;
	}
	*/
	/**
	 * Shuts the Plugin down and frees resources. This method is called in the
	 * following cases:
	 * <ul>
	 * <li>the <tt>setup</tt> method failed</li>
	 * <li>the Plugin configuration has changed</li>
	 * <li>the execution duration of the Plugin exceeded the schedule timeout</li>
	 * <li>the schedule associated with this Plugin was removed</li>
	 * </ul>
	 *
	 * <p>
	 * The Plugin methods <tt>setup</tt>, <tt>execute</tt> and
	 * <tt>teardown</tt> are called on different threads, but they are called
	 * sequentially. This means that the execution of these methods does not
	 * overlap, they are executed one after the other.
	 *
	 * <p>
	 * Examples:
	 * <ul>
	 * <li><tt>setup</tt> (failed) -&gt; <tt>teardown</tt></li>
	 * <li><tt>execute</tt> starts, configuration changes, <tt>execute</tt>
	 * ends -&gt; <tt>teardown</tt><br>
	 * on next schedule interval: <tt>setup</tt> -&gt; <tt>execute</tt> ...</li>
	 * <li><tt>execute</tt> starts, execution duration timeout,
	 * <tt>execute</tt> stops -&gt; <tt>teardown</tt></li>
	 * <li><tt>execute</tt> starts, <tt>execute</tt> ends, schedule is
	 * removed -&gt; <tt>teardown</tt></li>
	 * </ul>
	 * Failed means that either an unhandled exception is thrown or the status
	 * returned by the method contains a non-success code.
	 *
	 *
	 * <p>
	 * All by the Plugin allocated resources should be freed in this method.
	 * Examples are opened sockets or files.
	 *
	 * @see Monitor#setup(MonitorEnvironment)
	 */	@Override
	public void teardown(MonitorEnvironment env) throws Exception {
			HttpsURLConnection.setDefaultHostnameVerifier(defaultVerifier);	
	}

		private void logSevere(String message) {
			if (log.isLoggable(Level.SEVERE)){
				log.severe(message);
			}
		}	 
	 	 
	 
	private void logWarn(String message) {
		if (log.isLoggable(Level.WARNING)){
			log.warning(message);
		}
	}	 
 
	private void logInfo(String message) {
		if (log.isLoggable(Level.INFO)){
			log.info(message);
		}
	}
	private void logFine(String message) {
		if (log.isLoggable(Level.FINE)){
			log.fine(message);
		}
	}
}


class TrustSSL {

    public static void trustAllCerts() throws Exception {
        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
            }
            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }
            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
        } };
        // Install the all-trusting trust manager
        final SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        // Create all-trusting host name verifier
        HostnameVerifier allHostsValid = new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };
        
        // Install the all-trusting host verifier
        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);


    } 
} 