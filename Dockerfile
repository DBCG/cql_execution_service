FROM maven:3.5.3-jdk-8 as builder

COPY . /build
WORKDIR /build
RUN mvn package -Dmaven.test.skip=true

#Use the tomcat image as a base
FROM tomcat:8.5.13-jre8 as runner

#move the WAR for contesa to the webapps directory
RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=builder ./build/target/CQLExecSvc-1.4.0-SNAPSHOT.war /usr/local/tomcat/webapps/ROOT.war
COPY ./src/main/resources/* /usr/local/tomcat/src/main/resources/
