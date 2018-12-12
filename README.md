# CQL Execution Service

The CQL Execution Service is an implementation of the [cql_engine](https://github.com/DBCG/cql_engine) as a web service.

## Implementation Endpoints

CQL Execution Service: http://cql.dataphoria.org/cql/evaluate

CQL Formatter Service: http://cql.dataphoria.org/cql/format

## Usage:
- Clone repository
- mvn install
- mvn -Djetty.http.port=xxxx jetty:run
    - This command spins up the server and listens on the port of your choosing (usually 8080 for development).
- Start the CQL Runner (follow instructions [here](https://github.com/DBCG/cql_runner)) or navigate to the publicly available implementation [here](http://cql-runner.dataphoria.org/) and edit the url to point to your execution service implementation in the "Engine URL" tab.

### Execution Service

 - POST the following to [base]/cql/evaluate:
 
    ```
    {
        "code": "Your CQL code",
        "fhirServiceUri": "Terminology Service Endpoint",
        "fhirUser": "Username for authentication",
        "fhirPass": "Password for authentication",
        "dataServiceUri": "Fhir Data Provider Endpoint",
        "dataUser": "Username for authentication",
        "dataPass": "Password for authentication",
        "patientId": "The patient you want to run the library against"
    }
    ```
 
 - This Request will produce a JSON Response in the following format:
 
    ```
    [
        {
            "translator-error": "Translation error message (is the only element returned)",
            "name": "CQL Expression name",
            "location": "[row:col]",
            "resultType": "CQL Type being returned",
            "error": "Runtime error output (this may cause the omission of resultType)"
        }
    ]
    ```
 
 - This service is used by the [cql-runner](https://github.com/DBCG/cql_runner)

### CQL Formatter
 
 - POST the following to [base]/cql/format:
 
    ```
    {
        "code": "Unformatted CQL code"
    }
    ```
 
 - This Request will produce a JSON Response in the following format:
 
    ```
    [
        {
            "formatted-cql": "The formatted CQL code"
        }
    ]
    ```
 
 - This service is used by the [cql-runner](https://github.com/DBCG/cql_runner)