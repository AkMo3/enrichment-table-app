package org.nrnb.gsoc.enrichment.RequestEngine;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.work.TaskMonitor;

import org.json.simple.JSONArray;
import org.json.simple.JSONValue;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.nrnb.gsoc.enrichment.utils.ModelUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.*;

/**
 * @author ighosh98
 * @description Handles all the API requests to gProfiler. The api firing request must be a task which is run on the task thread
 */
public class HTTPRequestEngine {

    private final String basicURL = "https://biit.cs.ut.ee/gprofiler/api/";

    public HTTPRequestEngine(){
    }

    /**
     * @description function fires GET Request
     * @param endpoint API endpoint
     * @return
     */
    public JSONArray makeGetRequest(String endpoint) {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        StringBuffer urlConverter = new StringBuffer();
        urlConverter.append(this.basicURL);
        urlConverter.append(endpoint);
        String url = urlConverter.toString();
        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("Accept", "application/json");
        CloseableHttpResponse response = null;
        try {
            response = httpclient.execute(httpGet);
        } catch (IOException e) {
            e.printStackTrace();
        }
        int statusCode = response.getStatusLine().getStatusCode();
        if(statusCode!=200 && statusCode!=202){
            return null;
        }
        JSONArray jsonResponse=null;
        try {
            jsonResponse = (JSONArray) new JSONParser().parse(new InputStreamReader(response.getEntity().getContent()));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return jsonResponse;
    }

    /**
     * @description function fires POST Request by filling in necessary parameters
     * @param network Network being used for fetching data
     * @param endpoint API endpoint
     * @param parameters all parameters fetched from the network or set by the user
     * @param monitor Task Monitor
     * @param isBackgroundNeeded decides if we need the background nodes or not
     * @return
     */
    public JSONObject makePostRequest(CyNetwork network,String endpoint , Map<String,Object> parameters, TaskMonitor monitor, boolean isBackgroundNeeded) {

        if(ModelUtils.getNetUserThreshold(network)!=null){
            parameters.put("user_threshold",ModelUtils.getNetUserThreshold(network));
        }
        if(ModelUtils.getNetAllResults(network)!=null){
            parameters.put("all_results",ModelUtils.getNetAllResults(network));
        }
        if(ModelUtils.getNetNoIEA(network)!=null){
            parameters.put("no_iea",ModelUtils.getNetNoIEA(network));
        } else{
            parameters.put("no_iea",true);
        }


        if(ModelUtils.getNetSignificanceThresholdMethod(network)!=null){
            parameters.put("significance_threshold_method",ModelUtils.getNetSignificanceThresholdMethod(network));
        } else{
            parameters.put("significance_threshold_method","g_SCS");

        }

        StringBuffer backgroundNodes = new StringBuffer("");
        if(isBackgroundNeeded) {
            List<CyNode> nodeList = network.getNodeList();
            Set<String> nodeNameList = new HashSet<>();
            for (CyNode node : nodeList) {
                String canonicalName;
                if (ModelUtils.getNetGeneIDColumn(network) == null) {
                    canonicalName = network.getDefaultNodeTable().getRow(node.getSUID()).get("name", String.class);
                } else {
                    canonicalName = network.getDefaultNodeTable().getRow(node.getSUID()).get(ModelUtils.getNetGeneIDColumn(network), String.class);
                }
                if (canonicalName != null && canonicalName.length() > 0 && !canonicalName.contains(" ")) {
                    nodeNameList.add(canonicalName);
                }
            }
            Iterator<String> setIterator = nodeNameList.iterator();
            while (setIterator.hasNext()) {
                backgroundNodes.append(setIterator.next());
                if (setIterator.hasNext()) {
                    backgroundNodes.append(" ");
                }
            }
        }
        if(!backgroundNodes.toString().isEmpty())
            parameters.put("background",backgroundNodes.toString());
        if(backgroundNodes.toString().isEmpty()){
            parameters.put("domain_scope","annotated");
        } else{
            parameters.put("domain_scope","custom_annotated");
        }
        CloseableHttpClient httpclient = HttpClients.createDefault();
        StringBuffer urlConverter = new StringBuffer();
        urlConverter.append(this.basicURL);
        urlConverter.append(endpoint);
        String url = urlConverter.toString();
        HttpPost httpPost = new HttpPost(url);
        String jsonBody = JSONValue.toJSONString(parameters);
        //System.out.println(jsonBody);
        StringEntity entity = null;
        try {
            entity = new StringEntity(jsonBody);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            monitor.setStatusMessage("Could not fetch data. Check your internet connection");
        }
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept", "application/json");
        httpPost.setHeader("Content-type", "application/json");
        CloseableHttpResponse response = null;
        try {
            response = httpclient.execute(httpPost);
        } catch (IOException e) {
            e.printStackTrace();
            monitor.setStatusMessage("Could not fetch data. Check your internet connection");
        }
        int statusCode = response.getStatusLine().getStatusCode();
        if(statusCode!=200 && statusCode!=202){
            monitor.showMessage(TaskMonitor.Level.ERROR, "Got "+
                    response.getStatusLine().getStatusCode()+" code from server");
            monitor.setStatusMessage("Invalid Query Parameters");
            return null;
        }
        JSONObject jsonResponse=null;
        try {
            jsonResponse = (JSONObject) new JSONParser().parse(new InputStreamReader(response.getEntity().getContent()));
        } catch (IOException e) {
            e.printStackTrace();
            monitor.setStatusMessage("Could not fetch data. Check your internet connection");
        } catch (ParseException e) {
            e.printStackTrace();
            monitor.setStatusMessage("Could not fetch data. Check your internet connection");
        }
        return jsonResponse;
    }
};
