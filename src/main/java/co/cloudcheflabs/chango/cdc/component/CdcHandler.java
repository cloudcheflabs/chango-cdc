package co.cloudcheflabs.chango.cdc.component;

import co.cloudcheflabs.chango.cdc.util.JsonUtils;
import co.cloudcheflabs.chango.client.component.ChangoClient;
import io.debezium.config.Configuration;
import io.debezium.embedded.EmbeddedEngine;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static io.debezium.data.Envelope.FieldName.*;
import static java.util.stream.Collectors.toMap;

@Component
public class CdcHandler implements InitializingBean, DisposableBean {

    private static Logger LOG = LoggerFactory.getLogger(CdcHandler.class);

    @Autowired
    private Properties configuration;

    @Autowired
    private Configuration debeziumConnector;

    private String token;
    private String dataApiUrl;
    private String schema;
    private String table;
    private int batchSize;
    private long interval;

    private ChangoClient changoClient;

    private final Executor executor = Executors.newSingleThreadExecutor();
    private EmbeddedEngine engine;

    @Override
    public void afterPropertiesSet() throws Exception {
        token = configuration.getProperty("chango.token");
        dataApiUrl = configuration.getProperty("chango.dataApiUrl");
        schema = configuration.getProperty("chango.schema");
        table = configuration.getProperty("chango.table");
        batchSize = Integer.valueOf(configuration.getProperty("chango.batchSize"));
        interval = Long.valueOf(configuration.getProperty("chango.interval"));

        // construct chango client.
        constructChangoClient();

        // embedded engine.
        this.engine = EmbeddedEngine
                .create()
                .using(debeziumConnector)
                .notifying(s -> {
                    handleEvent(s);
                }).build();

        start();
    }

    private void constructChangoClient() {
        changoClient = new ChangoClient(
                token,
                dataApiUrl,
                schema,
                table,
                batchSize,
                interval
        );
    }

    private void handleEvent(SourceRecord sourceRecord) {
        Struct sourceRecordValue = (Struct) sourceRecord.value();

        if(sourceRecordValue != null) {
            Operation operation = Operation.forCode((String) sourceRecordValue.get(OPERATION));

            //Only if this is a transactional operation.
            if(operation != Operation.READ) {

                Map<String, Object> message;
                String record = AFTER; // For Update & Insert operations.

                if (operation == Operation.DELETE) {
                    record = BEFORE; // For Delete operations.
                }

                // Build a map with all row data received.
                Struct struct = (Struct) sourceRecordValue.get(record);
                message = struct.schema().fields().stream()
                        .map(Field::name)
                        .filter(fieldName -> struct.get(fieldName) != null)
                        .map(fieldName -> Pair.of(fieldName, struct.get(fieldName)))
                        .collect(toMap(Pair::getKey, Pair::getValue));

                // TODO: send message to chango.

                if(Operation.DELETE.name().equals(operation.name())) {

                } else if(Operation.UPDATE.name().equals(operation.name())) {

                } else if(Operation.CREATE.name().equals(operation.name())) {

                }


                String json = JsonUtils.toJson(message);
                LOG.info("Operation {} executed with message {}", operation.name(), json);

//                try {
//                    // send json.
//                    changoClient.add(json);
//                } catch (Exception e) {
//                    LOG.error(e.getMessage());
//
//                    // reconstruct chango client.
//                    constructChangoClient();
//                    LOG.info("Chango client reconstructed.");
//                    pause(1000);
//                }
            }
        }
    }

    private static void pause(long pause) {
        try {
            Thread.sleep(pause);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String padZero(int value) {
        String strValue = String.valueOf(value);
        if(strValue.length() == 1) {
            strValue = "0" + strValue;
        }
        return strValue;
    }

    @Override
    public void destroy() throws Exception {
        stop();
    }

    private void start() {
        this.executor.execute(engine);
    }


    private void stop() {
        if (this.engine != null) {
            this.engine.stop();
        }
    }
}
