/*
 * MetricsResource.java
 *
 * Copyright (c) 2015, Wouter Lueks, Radboud University
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the IRMA project nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package org.irmacard.metrics.web;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.codec.binary.Base64;
import org.irmacard.metrics.common.ApplicationInformation;
import org.irmacard.metrics.common.Aggregate;
import org.irmacard.metrics.common.Measurement;
import org.irmacard.metrics.common.SessionToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("v1")
public class MetricsResource {
    private static Logger logger = LoggerFactory.getLogger(MetricsResource.class);
    private DataSource ds = null;
    private SecureRandom rnd;

    private static final int SESSION_TOKEN_LENGTH = 33;
    private static final String BEARER_STRING = "Bearer ";

    public MetricsResource() {
        rnd = new SecureRandom();

        try {
            Context initCtx = new InitialContext();
            Context envCtx = (Context) initCtx.lookup("java:comp/env");
            ds = (DataSource) envCtx.lookup("jdbc/metrics");
        } catch (NamingException e) {
            e.printStackTrace();
            logger.error("Cannot access database! {}", e);
            throw new RuntimeException("Cannot access database!");
        }
    }

    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public SessionToken registerApplication(ApplicationInformation info) {
        SessionToken sessionToken = new SessionToken(generateSessionToken());
        Connection conn = null;

        logger.info("Trying to insert ApplicationInformation {}", info);
        logger.info("Application gets sessionToken {}", sessionToken);
        try {
            conn = ds.getConnection();

            PreparedStatement insertRegistration = conn
                    .prepareStatement("INSERT INTO registrations "
                            + "(acraID, model, application, application_version,"
                            + " access_token) VALUES (?, ?, ?, ?, ?)");

            insertRegistration.setString(1, info.getAcraID());
            insertRegistration.setString(2, info.getModel());
            insertRegistration.setString(3, info.getApplication());
            insertRegistration.setString(4, info.getApplicationVersion());
            insertRegistration.setString(5, sessionToken.getSessionToken());

            insertRegistration.executeUpdate();

            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
            logger.warn("Unable to access database or database error", e);
            return null;
        }

        return sessionToken;
    }

    @POST
    @Path("/measurement")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response logMeasurement(Measurement measurement,
            @HeaderParam("Authorization") String authorizationToken) {
        logger.info("Received measurement:\n{}", measurement);
        logger.info("From: {}", authorizationToken);

        String sessionToken = getSessionToken(authorizationToken);

        Connection conn = null;
        try {
            int registrationID = getRegistrationID(sessionToken);

            if (registrationID == -1) {
                logger.warn("Unauthorized access attempt!");
                return Response.status(Status.UNAUTHORIZED).build();
            }

            conn = ds.getConnection();

            // Log measurement
            String logQuery = "INSERT INTO measurements "
                    + "(key, value, registration_id) " + "VALUES (?, ?, ?)";
            PreparedStatement insertReport = conn.prepareStatement(logQuery);
            insertReport.setString(1, measurement.getKey());
            insertReport.setDouble(2, measurement.getValue());
            insertReport.setInt(3, registrationID);
            insertReport.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            logger.warn("Unable to access database or database error. {}", e);

            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException e) {
            }
        }

        return Response.status(Status.CREATED).build();
    }

    @POST
    @Path("/aggregate")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response logAggregate(Aggregate aggregate,
            @HeaderParam("Authorization") String authorizationToken) {
        logger.info("Received aggregate:\n{}", aggregate);
        logger.info("From: {}", authorizationToken);

        String sessionToken = getSessionToken(authorizationToken);

        Connection conn = null;
        try {
            int registrationID = getRegistrationID(sessionToken);

            if (registrationID == -1) {
                logger.warn("Unauthorized access attempt!");
                return Response.status(Status.UNAUTHORIZED).build();
            }

            conn = ds.getConnection();

            // Log aggregate
            String logQuery = "INSERT INTO aggregates "
                    + "(key, average, variance, count, registration_id) "
                    + "VALUES (?, ?, ?, ?, ?)";
            PreparedStatement insertReport = conn.prepareStatement(logQuery);
            insertReport.setString(1, aggregate.getKey());
            insertReport.setDouble(2, aggregate.getAverage());
            insertReport.setDouble(3, aggregate.getVariance());
            insertReport.setInt(4, aggregate.getCount());
            insertReport.setInt(5, registrationID);
            insertReport.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            logger.warn("Unable to access database or database error. {}", e);

            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException e) {
            }
        }

        return Response.status(Status.CREATED).build();
    }

    private String getSessionToken(String authorizationToken) {
        int bearerIndex = authorizationToken.indexOf(BEARER_STRING);
        String sessionToken;

        if (bearerIndex == -1) {
            sessionToken = null;
        } else {
            sessionToken = authorizationToken
                    .substring(bearerIndex + BEARER_STRING.length());
        }

        return sessionToken;
    }

    private int getRegistrationID(String sessionToken) throws SQLException {
        int registrationID = -1;

        // Determine if application has been registered
        try (Connection conn = ds.getConnection()) {
            String selectQuery = "SELECT * FROM registrations WHERE access_token = ?";
            PreparedStatement testSessionToken = conn.prepareStatement(selectQuery);
            testSessionToken.setString(1, sessionToken);
            ResultSet results = testSessionToken.executeQuery();

            if (results.next()) {
                logger.info("Access token found");
                registrationID = results.getInt("id");
            }
        }

        return registrationID;
    }

    /**
     * A random session token. Returns a base64 encoded string representing the
     * session token.
     *
     * @return the random session token
     */
    private String generateSessionToken() {
        byte[] token = new byte[SESSION_TOKEN_LENGTH];
        rnd.nextBytes(token);
        return Base64.encodeBase64String(token);
    }
}