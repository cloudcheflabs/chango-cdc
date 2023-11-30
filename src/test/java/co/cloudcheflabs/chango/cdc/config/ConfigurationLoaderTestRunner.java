package co.cloudcheflabs.chango.cdc.config;

import co.cloudcheflabs.chango.cdc.Chango;
import co.cloudcheflabs.chango.cdc.util.StringUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Iterator;
import java.util.Properties;

import static co.cloudcheflabs.chango.cdc.Chango.ENV_CHANGO_CDC_CONFIGURATION_PATH;

public class ConfigurationLoaderTestRunner {
    private static Logger LOG = LoggerFactory.getLogger(ConfigurationLoaderTestRunner.class);
    @Test
    public void loadConfiguration() throws Exception {
        String confPath = System.getProperty("confPath", "/Users/mykidong/project/chango-cdc/src/test/resources/configuration-test.yml");
        StringUtils.setEnv(ENV_CHANGO_CDC_CONFIGURATION_PATH, confPath);

        ApplicationContext applicationContext = new AnnotationConfigApplicationContext(
                ConfigurationLoader.class
        );

        Properties configuration = applicationContext.getBean("configuration", Properties.class);
        String debeziumConnnectorPropString = configuration.getProperty("debezium.connector");
        Properties debeziumConnectorProps = StringUtils.stringToProperties(debeziumConnnectorPropString);

        Iterator<Object> iter = debeziumConnectorProps.keys().asIterator();
        while(iter.hasNext()) {
            Object key = iter.next();
            Object value = debeziumConnectorProps.get(key);
            LOG.info("key: {}, value: {}", key, value);
        }
    }
}
