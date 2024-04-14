package co.cloudcheflabs.chango.cdc.component;

import co.cloudcheflabs.chango.cdc.util.JsonUtils;
import co.cloudcheflabs.chango.client.component.ChangoClient;
import io.debezium.config.Configuration;
import io.debezium.embedded.EmbeddedEngine;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Properties;
import java.util.Set;
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
    private boolean tx = false;

    private ChangoClient changoClient;

    private final Executor executor = Executors.newSingleThreadExecutor();
    private EmbeddedEngine engine;

    private Map<String, Object> sampleMessageForTableSchema;

    @Override
    public void afterPropertiesSet() throws Exception {
        token = configuration.getProperty("chango.token");
        dataApiUrl = configuration.getProperty("chango.dataApiUrl");
        schema = configuration.getProperty("chango.schema");
        table = configuration.getProperty("chango.table");
        batchSize = Integer.valueOf(configuration.getProperty("chango.batchSize"));
        interval = Long.valueOf(configuration.getProperty("chango.interval"));
        String txString = configuration.getProperty("chango.tx");
        if(txString != null) {
            tx = Boolean.valueOf(txString);
        }

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
                interval,
                tx
        );
    }

    private void handleEvent(SourceRecord sourceRecord) {
        try {
            Struct sourceRecordValue = (Struct) sourceRecord.value();

            if (sourceRecordValue != null) {
                Operation operation = Operation.forCode((String) sourceRecordValue.get(OPERATION));

                // operations except READ.
                if (operation != Operation.READ) {

                    Map<String, Object> message;
                    // for insert and update.
                    String record = AFTER;

                    if (operation == Operation.DELETE) {
                        // for delete.
                        record = BEFORE;
                    }

                    // construct map with fields.
                    Struct struct = (Struct) sourceRecordValue.get(record);
                    message = struct.schema().fields().stream()
                            .map(Field::name)
                            .filter(fieldName -> struct.get(fieldName) != null)
                            .map(fieldName -> Pair.of(fieldName, struct.get(fieldName)))
                            .collect(toMap(Pair::getKey, Pair::getValue));


                    // set sample message to get table schema later.
                    if(this.sampleMessageForTableSchema == null) {
                        if (!Operation.DELETE.name().equals(operation.name())) {
                            this.sampleMessageForTableSchema = message;
                        }
                    }

                    // send message to chango.

                    final Set<String> fieldSet = message.keySet();

                    DateTime dt = DateTime.now();

                    String year = String.valueOf(dt.getYear());
                    String month = padZero(dt.getMonthOfYear());
                    String day = padZero(dt.getDayOfMonth());
                    long ts = dt.getMillis(); // in milliseconds.

                    // if the fields 'year', 'month', 'day', 'ts', 'op' exist in the table, then change the field names.
                    if(fieldSet.contains("year")) {
                        message.put("_year", message.get("year"));
                    }
                    if(fieldSet.contains("month")) {
                        message.put("_month", message.get("month"));
                    }
                    if(fieldSet.contains("day")) {
                        message.put("_day", message.get("day"));
                    }
                    if(fieldSet.contains("ts")) {
                        message.put("_ts", message.get("ts"));
                    }
                    if(fieldSet.contains("op")) {
                        message.put("_op", message.get("op"));
                    }

                    // add chango specific fields.
                    message.put("year", year);
                    message.put("month", month);
                    message.put("day", day);
                    message.put("ts", ts);

                    if (Operation.DELETE.name().equals(operation.name())) {
                        // add fields from the sample message for delete message.
                        if(this.sampleMessageForTableSchema != null) {
                            for(String key : this.sampleMessageForTableSchema.keySet()) {
                                if(message.keySet().contains(key)) {
                                    continue;
                                }
                                Object value = this.sampleMessageForTableSchema.get(key);
                                if(value == null) {
                                    message.put(key, null);
                                } else {
                                    if (value instanceof String) {
                                        message.put(key, "");
                                    } else if (value instanceof Boolean) {
                                        message.put(key, Boolean.valueOf(false));
                                    } else {
                                        message.put(key, -1);
                                    }
                                }
                            }
                        } else {
                            LOG.warn("Sample Message for Table Schema Not Set!");
                            return;
                        }
                        message.put("op", "delete");
                    } else if (Operation.UPDATE.name().equals(operation.name())) {
                        message.put("op", "update");
                    } else if (Operation.CREATE.name().equals(operation.name())) {
                        message.put("op", "insert");
                    }


                    // convert message to json.
                    String json = JsonUtils.toJson(message);
                    //LOG.info("Operation {} executed with message {}", operation.name(), json);
                    try {
                        // send json.
                        changoClient.add(json);
                    } catch (Exception e) {
                        LOG.error(e.getMessage());

                        // reconstruct chango client.
                        constructChangoClient();
                        LOG.info("Chango client reconstructed.");
                        pause(1000);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("ERROR: {}", e.getMessage());
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
