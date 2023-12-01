# Chango CDC

Chango CDC is Change Data Capture application to catch CDC data of database and send CDC data to 
[Chango](https://cloudcheflabs.github.io/chango-private-docs).

## Package Chango CDC

You need to choose which Debezium version will be used for your database, see the [release notice of Debezium](https://debezium.io/releases/).
After that, choose the concrete maven dependency version of Debezium connector. 
For example, maven dependency version of Debezium connector for PostgreSQL can be found [here](https://mvnrepository.com/artifact/io.debezium/debezium-connector-postgres).

Package `Chango CDC` distribution with Debezium maven dependency version.
```agsl
export DEBEZIUM_VERSION=1.9.7.Final

./package-dist.sh \
--debezium.version=${DEBEZIUM_VERSION} \
;
```


