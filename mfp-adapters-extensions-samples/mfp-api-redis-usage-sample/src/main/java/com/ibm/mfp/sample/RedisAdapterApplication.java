/*
 *    Licensed Materials - Property of IBM
 *    5725-I43 (C) Copyright IBM Corp. 2015. All Rights Reserved.
 *    US Government Users Restricted Rights - Use, duplication or
 *    disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
*/

package com.ibm.mfp.sample;

import com.ibm.mfp.adapter.api.ConfigurationAPI;
import com.ibm.mfp.adapter.api.MFPJAXRSApplication;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.InvalidURIException;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.util.JedisURIHelper;

import javax.ws.rs.core.Context;
import java.net.URI;
import java.util.logging.Logger;

/**
 * Initializes the Adapter API and hosts global objects that get used thro the various requests
 *
 */
public class RedisAdapterApplication extends MFPJAXRSApplication {

    /**
     * The logger used by the app
     */
	static Logger logger = Logger.getLogger(RedisAdapterApplication.class.getName());

    /**
     * Injected application configuration variable (injected by the MobileFirst server)
     */
	@Context
	ConfigurationAPI configApi;

    /**
     * A Pool of Redis connections to be used by API Calls
     */
	private JedisPool pool;

    /**
     * Return a Redis connection from the connection pool that was initialised
     *
     * @return a Jedis connection object
     */
    public Jedis getConnection() {

        return pool.getResource();
    }

    /**
     * Initializes the adapter application by allocatng a Redis connection pool.
     *
     * Init is called by the MobileFirst Server whenever an Adapter application is deployed or reconfigured. The method
     * than get the redis URL from the configuration parameters and try to open a connection to the Redis server
     *
     * @throws Exception if the Redis URL is invalid
     */
    protected void init() throws Exception {

        logger.info("Initializing a Redis Adapter application");

        final String redisURL = configApi.getPropertyValue("redisURL");
        logger.config(String.format("Redis URL is [%s]", redisURL));

        if (!JedisURIHelper.isValid(new URI(redisURL))) {
            logger.severe(String.format("Redis URL [%s] is invalid", redisURL));
            throw new InvalidURIException(String.format(
                    "Cannot open Redis connection due invalid URI. %s", redisURL));
        }

        try {
            /*
             * Test the connection to the server to validate that the server can be reached
             *
             * 1. Connect to the server
             * 2. Put a string into the server
             * 3. Read the string back, check that it is the same that was put
             * 4. Delete the key in preperation for next time
             */
            logger.fine("Testing connection to the Redis Server");
            // Do we have a server running there ???
            final Jedis j = new Jedis(redisURL);
            j.set("foo", "bar");
            final String foobar = j.get("foo");
            j.del("foo");

            if (foobar.equals("bar")) {
                logger.info(String.format("Sucess: connecting and pinging the Redis server as [%s]", redisURL));
            } else {
                // Something is wrong here... maybe not a redis server? Lets warn the admin
                logger.warning(String.format("Failed: reading data from the Redis server at [%s] failed. Read [%s].",
                        redisURL, foobar));
            }

            // Make sure the connection is closed
            j.close();
        } catch(JedisConnectionException ex) {
            // The Redis server is likely down, warn the administratoor
            ex.printStackTrace();
            logger.warning(String.format("Failed: connecting to the Redis server at [%s] failed. Check if the server is up.",
                    redisURL));
        }
        pool = new JedisPool(new URI(redisURL));

        logger.info("Adapter initialized!");
	}

    /**
     * Deinitilize the adapter application.
     *
     * Called by the MobileFirst server when the adapter is uninstalled and delete the connection pool to the redis server.
     *
     * @throws Exception in case of an error
     */
	protected void destroy() throws Exception {

        if(pool != null)
            pool.destroy();

		logger.info("Adapter destroyed!");
	}
	

	protected String getPackageToScan() {
		//The package of this class will be scanned (recursively) to find JAX-RS resources. 
		//It is also possible to override "getPackagesToScan" method in order to return more than one package for scanning
		return getClass().getPackage().getName();
	}
}