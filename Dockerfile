FROM maven:3.6.0-jdk-8 AS builder
WORKDIR /usr/src/cql_src
ADD . .
RUN git clone https://github.com/DBCG/cql_engine.git
RUN mvn clean install -DskipTests -f /usr/src/cql_src/cql_engine/cql-engine
RUN mvn clean install -DskipTests -f /usr/src/cql_src

FROM tomcat:latest
#move the WAR for contesa to the webapps directory
COPY --from=builder /usr/src/cql_src/target/CQLExecSvc-0.0.1-SNAPSHOT.war /usr/local/tomcat/webapps/cql.war
COPY --from=builder /usr/src/cql_src/src/main/resources/* /usr/local/tomcat/src/main/resources/