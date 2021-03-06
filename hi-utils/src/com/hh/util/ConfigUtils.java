package com.hh.util;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

/**
 * @since 11/03/2014
 * @author HienDM
 */

public class ConfigUtils {
    private static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(ConfigUtils.class.getSimpleName());
    private String CONFIG_PATH;
    private String CONNECTOR_PATH;
    private String PROCESS_SERVER_PATH;
    private String PROCESS_CLIENT_PATH;
    private String PAGE_PATH;
    private String ACCEPT_PATH;
    private Properties config;
    private Properties connector;
    private Properties processServer;
    private Properties processClient;
    private Properties page;
    private Properties acceptConnector;
    private String configPath = "";
    
    public ConfigUtils(String path) {
        loadAllConfig(path);
    }
    
    public void loadAllConfig(String path) {
        configPath = path;
        CONFIG_PATH = configPath + "/server.conf";
        CONNECTOR_PATH = configPath + "/output.conf";
        PROCESS_SERVER_PATH = configPath + "/process-server.conf";
        PROCESS_CLIENT_PATH = configPath + "/process-client.conf";
        PAGE_PATH = configPath + "/frontend-page.conf";
        ACCEPT_PATH = configPath + "/input.conf";
        config = loadConfig();
        connector = loadConnector();
        processServer = loadProcessServer();
        processClient = loadProcessClient();
        page = loadPage();
        acceptConnector = loadAccept();     
    }
    
    public Properties loadConfig() {
        Properties prop = new Properties();
        File file = new File(CONFIG_PATH);
        if (file.exists()) {        
            try (FileInputStream inputStream = new FileInputStream(CONFIG_PATH)){
                prop.load(inputStream);
            } catch (Exception ex) {
                log.error("Error when load server.conf", ex);
            }
        }
        return prop;
    }
        
    public Properties loadConnector() {
        Properties prop = new Properties();
        File file = new File(CONNECTOR_PATH);
        if (file.exists()) {
            try (FileInputStream inputStream = new FileInputStream(CONNECTOR_PATH)){
                prop.load(inputStream);
            } catch (Exception ex) {
                log.error("Error when load output.conf", ex);
            }
        }
        return prop;
    }

    public Properties loadProcessServer() {
        Properties prop = new Properties();
        File file = new File(PROCESS_SERVER_PATH);
        if (file.exists()) {
            try (FileInputStream inputStream = new FileInputStream(PROCESS_SERVER_PATH)){
                prop.load(inputStream);
            } catch (Exception ex) {
                log.error("Error when load process-server.conf", ex);
            }
        }
        return prop;
    }    

    public Properties loadProcessClient() {
        Properties prop = new Properties();
        File file = new File(PROCESS_CLIENT_PATH);
        if (file.exists()) {
            try (FileInputStream inputStream = new FileInputStream(PROCESS_CLIENT_PATH)){
                prop.load(inputStream);
            } catch (Exception ex) {
                log.error("Error when load process-client.conf", ex);
            }
        }
        return prop;
    }    
    
    public Properties loadPage() {
        Properties prop = new Properties();
        File file = new File(PAGE_PATH);
        if (file.exists()) {
            try (FileInputStream inputStream = new FileInputStream(PAGE_PATH)){
                prop.load(inputStream);
            } catch (Exception ex) {
                log.error("Error when load page.conf", ex);
            }
        }
        return prop;
    }    
    
    public Properties loadAccept() {
        Properties prop = new Properties();
        File file = new File(ACCEPT_PATH);
        if (file.exists()) {
            try (FileInputStream inputStream = new FileInputStream(ACCEPT_PATH)){
                prop.load(inputStream);
            } catch (Exception ex) {
                log.error("Error when load input.conf", ex);
            }
        }
        return prop;
    }    
        
    public String getConfig(String key) {
        String value = (String) config.get(key);
        if(value == null) value = "";
        return value;
    }
    
    public String getMimeType(String key) {
        String value = MimeType.getInstance().getMimeType(key);
        if(value == null) value = "";
        return value;
    }
    
    public String getConnector(String key) {
        String value = (String) connector.get(key);
        if(value == null) value = "";
        return value;
    }

    public String getProcessServer(String key) {
        String value = (String) processServer.get(key);
        if(value == null) value = "";
        return value;
    }    

    public String getProcessClient(String key) {
        String value = (String) processClient.get(key);
        if(value == null) value = "";
        return value;
    }    
    
    public String getPage(String key) {
        String value = (String) page.get(key);
        if(value == null) value = "";
        return value;
    }    
    
    public String getAccept(String key) {
        String value = (String) acceptConnector.get(key);
        if (value == null) value = "";
        return value;
    }    
    
}

