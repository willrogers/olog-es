# olog-es   [![Build Status](https://travis-ci.org/Olog/olog-es.svg?branch=master)](https://travis-ci.org/Olog/olog-es)

An online logbook for recroding logs 

[Olog-es Documentation](https://olog-es.readthedocs.io/)

### Installation
Olog 

* Prerequisites

  * JDK 11 or newer
  * Elastic version 6.8.4
  * mongo gridfs


 **Download links for the prerequisites**   
 Download and install elasticsearch (verision 6.8.4) from [elastic.com](https://www.elastic.co/downloads/past-releases/elasticsearch-6-8-4)    
 Download and install mongodb from [mongodb](https://www.mongodb.com/download-center/community)    
  
  
* Configure the service   
The configuration files for olog-es are present under `olog-es/tree/master/src/main/resources` 


* Build 
```
mvn clean install
``` 

* Build deployable jar

To build a jar with dependencies and Tomcat server, use Maven profile `deployable-jar`, e.g.:
```
mvn -Pdeployable-jar clean install
```

#### Start the service  

Using spring boot  

```
mvn org.springframework.boot:spring-boot-maven-plugin:run
```

#### Check if service is running

Once the service is running, the service consists of a welcome page `http://localhost:8080/Olog` 
which will provide information about the version of the Olog service running,
 along with information about the version and connection status of the elastic and mongo
backends.


