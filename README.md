# CQL Execution Service

The CQL Execution Service is an implementation of the [cql_engine](https://github.com/DBCG/cql_engine) as a web service.

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
        "terminologyServiceUri": "Terminology Service Endpoint",
        "terminologyUser": "Username for authentication",
        "terminologyPass": "Password for authentication",
        "dataServiceUri": "Fhir Data Provider Endpoint",
        "dataUser": "Username for authentication",
        "dataPass": "Password for authentication",
        "patientId": "The patient you want to run the library against"
        "parameters": [
          {
            "name": "Name of the parameter as specified in the CQL",
            "type": "Name of the type (currently only singleton CQL types are supported)",
            "value": String (String, DateTime, and Time) | Integer | Decimal | Object (Code, Concept, Quantity, Interval)
          }
        ]
    }
    ```
    
  - Parameter Examples:
  
    ```
    {
      ...
      "parameters": [
        {
          "name": "MeasurementPeriod",
          "type": "Interval<DateTime>",
          "value": {
            "start": "@2019-01-01",
            "end": "@2019-12-31"
          }
        },
        {
          "name": "MyCode",
          "type": "Code",
          "value": {
            "system": "http://example.org",
            "code": "example-code",
            "display": "My exmaple code",
            "version": "1.0"
          }
        },
        {
          "name": "MyConcept",
          "type": "Concept",
          "value": {
            "codes": [
              {
                "system": "http://example.org",
                "code": "example-code"							
              },
              {
                "code": "another-example-code"							
              }
            ],
            "display": "My Concept"
          }
        },
        {
          "name": "MyQuantityInterval",
          "type": "Interval<Quantity>",
          "value": {
            "start": {
              "value": 12.5,
              "unit": "mg"
            },
            "end": {
              "value": 17.5,
              "unit": "mg"
            }
          }
        },
        {
          "name": "ClosingTime",
          "type": "Time",
          "value": "T17:00:00.000-07:00"
        }
      ]
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
