The IRMA metrics server is a simple server that keeps track of metrics for our deployed applications. The metrics are collated on the client side, only aggregates are send to the server.

# Running the server

The gradle build file should take care of most of the dependencies. To run the server in development mode simply call:

    gradle appRun

This will also start an H2 database server. You can use the [web console](http://localhost:8082/) to view and edit the database manually.

# Deployment

*Incomplete* You need to tell the web-server how to access the database. Also, you need to create the necessary tables in the database. An example SQL script can be found in `src/main/resources/metrics.sql`.

# Server API

The server provides the following three simple API calls: `register` to register an application, and `measurement` to report a value, and `aggregate` to report aggregates. Values and aggregates should only be reported once.

## register

During registration you send the static values that are not bound to change very often. Server stores record together with timestamp and returns a session token to authenticate future log entries.

    POST https://<server>/<api>/v1/register

Inputs:

 * acraID: A string containing the acra UUID
 * model: something :: String
 * application: The application name, for example `irma_android_cardemu`
 * applicationVersion: The application version, for example `0.10.0`

Output:

 * sessionToken: a base64 encoded authentication token

## measurement

To log a measurement you can make the following API call

    POST https://<server>/<api>/v1/measurement

where you supply your authentication token in the authorization header. The server stores the supplied values together with a timestamp.

Inputs:

 * Authorization: Bearer `sessionToken`
 * key: the name of the variable being measured
 * value: the double representing the measured value

Status codes:

 * 201 CREATED when the measurement is successfully stored
 * 401 UNAUTHORIZED when the authentication token is unknown

## aggregate

To log an agregation of measurements you can make the following API call

    POST https://<server>/<api>/v1/aggregate

where you supply your authentication token in the authorization header.

Inputs:

 * Authorization: Bearer `sessionToken`
 * key: the name of the variable being measured
 * average: the double representing the average of the measured values
 * variance: the double representing the variance of the measured values
 * count: the integer representing the number of measured values

Status codes:

 * 201 CREATED when the measurement is successfully stored
 * 401 UNAUTHORIZED when the authentication token is unknown

## Testing the API using cURL

To register a device

    curl -X POST http://localhost:8080/irma_metrics_server/api/v1/register \
      -H "Content-Type: application/json" \
      -d '{
            "acraID": "550e8400-e29b-41d4-a716-446655440000",
            "model": "Nexus 7",
            "application": "irma_android_cardemu",
            "applicationVersion": "0.8"
          }' 

it should return something like:

    {"sessionToken":"DvfxOFv4tbKGZUFbGhOdbv86FengDRWMEKkz7XYF+qmR"}

We can use this session token as an authorization token in subsequent requests to actually store logged measurements

    curl -v -X POST http://localhost:8080/irma_metrics_server/api/v1/measurement \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer DvfxOFv4tbKGZUFbGhOdbv86FengDRWMEKkz7XYF+qmR" \
      -d '{
            "key": "verification_time",
            "value": 22.3
          }'

and aggregates
    
    curl -v -X POST http://localhost:8080/irma_metrics_server/api/v1/aggregate \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer DvfxOFv4tbKGZUFbGhOdbv86FengDRWMEKkz7XYF+qmR" \
      -d '{
            "key": "verification_time",
            "average": 4.5,
            "variance": 0.34,
            "count": 11
          }'
