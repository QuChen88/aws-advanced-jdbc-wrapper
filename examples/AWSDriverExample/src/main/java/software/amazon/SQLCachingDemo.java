package software.amazon;

import software.amazon.util.EnvLoader;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

public class SQLCachingDemo {

  private static final EnvLoader env = new EnvLoader();
  private static final Logger LOGGER = Logger.getLogger(SQLCachingDemo.class.getName());

  private static final String DB_CONNECTION_STRING = env.get("DB_CONNECTION_STRING");
  private static final String USERNAME = env.get("DB_USERNAME");
  private static final String PASSWORD = env.get("DB_PASSWORD");

  // No Auth endpoints
  private static final String NO_AUTH_RW = env.get("NO_AUTH_RW_ADDR");
  private static final String NO_AUTH_RO = env.get("NO_AUTH_RO_ADDR");

  // Traditional Auth endpoints and credentials
  private static final String TRAD_AUTH_RW = env.get("TRAD_AUTH_RW_ADDR");
  private static final String TRAD_AUTH_RO = env.get("TRAD_AUTH_RO_ADDR");
  private static final String TRAD_CACHE_USERNAME = env.get("TRAD_CACHE_USERNAME");
  private static final String CACHE_PASSWORD = env.get("CACHE_PASSWORD");

  // IAM Auth endpoints and credentials
  private static final String IAM_AUTH_RW = env.get("IAM_AUTH_RW_ADDR");
  private static final String IAM_AUTH_RO = env.get("IAM_AUTH_RO_ADDR");
  private static final String CACHE_USERNAME = env.get("CACHE_USERNAME");
  private static final String CACHE_NAME = env.get("CACHE_NAME");
  private static final String CACHE_IAM_REGION = env.get("CACHE_IAM_REGION");

  public static void main(String[] args) throws SQLException, InterruptedException {
    if (args.length == 0) {
      System.out.println("Usage: java SQLCachingDemo <demo_type>");
      System.out.println("Demo types: noauth | traditional | iam");
      return;
    }

    String demoType = args[0].toLowerCase();
    System.out.println("=== AWS JDBC Driver Cache Authentication Demo ===\n");

    switch (demoType) {
      case "noauth":
        System.out.println("DEMO: No Authentication Cache");
        runCacheDemo(createNoAuthProperties(), "NO_AUTH", 60);
        break;
      case "traditional":
        System.out.println("DEMO: Traditional Username/Password Authentication");
        runCacheDemo(createTraditionalAuthProperties(), "TRADITIONAL_AUTH", 60);
        break;
      case "iam":
        System.out.println("DEMO: IAM Authentication");
        runCacheDemo(createIamAuthProperties(), "IAM_AUTH", 60);
        break;
      default:
        System.err.println("Invalid demo type. Use: noauth | traditional | iam");
    }
  }

  private static Properties createDefaultProperties(String rw, String ro) {
    Properties props = new Properties();
    props.setProperty("user", USERNAME);
    props.setProperty("password", PASSWORD);
    props.setProperty("wrapperPlugins", "dataRemoteCache");
    props.setProperty("cacheEndpointAddrRw", rw);
    props.setProperty("cacheEndpointAddrRo", ro);
    props.setProperty("cacheUseSSL", "true");
    return props;
  }

  private static Properties createNoAuthProperties() {
    return createDefaultProperties(NO_AUTH_RW, NO_AUTH_RO);
  }

  private static Properties createTraditionalAuthProperties() {
    Properties props = createDefaultProperties(TRAD_AUTH_RW, TRAD_AUTH_RO);
    props.setProperty("cacheUseSSL", "true");
    props.setProperty("cacheUsername", TRAD_CACHE_USERNAME);
    props.setProperty("cachePassword", CACHE_PASSWORD);
    return props;
  }

  private static Properties createIamAuthProperties() {
    Properties props = createDefaultProperties(IAM_AUTH_RW, IAM_AUTH_RO);
    props.setProperty("cacheUsername", CACHE_USERNAME);
    props.setProperty("cacheName", CACHE_NAME);
    props.setProperty("cacheIamRegion", CACHE_IAM_REGION);
    return props;
  }

  private static void runCacheDemo(Properties properties, String authType, int durationSeconds) {
    System.out.println("   Starting " + authType + " demo for " + durationSeconds + " seconds...");

    String[] queries = {
        "/*+ CACHE_PARAM(ttl=5s) */ SELECT 'Cache Test 1' as test_data, NOW() as timestamp",
        "/*+ CACHE_PARAM(ttl=5s) */ SELECT 'Cache Test 2' as test_data, CURRENT_TIMESTAMP as timestamp",
        "/*+ CACHE_PARAM(ttl=5s) */ SELECT 'Cache Test 3' as test_data, 'Demo Data' as info",
        "/*+ CACHE_PARAM(ttl=5s) */ SELECT * from cinemas"
    };

    long endTime = System.currentTimeMillis() + (durationSeconds * 1000L);
    int operationCount = 0;

    try (Connection conn = DriverManager.getConnection(DB_CONNECTION_STRING, properties);
         Statement stmt = conn.createStatement()) {

      while (System.currentTimeMillis() < endTime) {
        String query = queries[operationCount % queries.length];

        try {
          ResultSet rs = stmt.executeQuery(query);
          if (rs.next()) {
            System.out.println("   [" + authType + "] Operation #" + (++operationCount) +
                " - Query executed successfully: " + rs.getString(1));
          }
          rs.close();

          // Sleep for 1 seconds between operations for easy monitoring
          Thread.sleep(1000);

        } catch (SQLException e) {
          System.err.println("   [" + authType + "] SQL Error: " + e.getMessage());
          break;
        }
      }

    } catch (SQLException e) {
      System.err.println("   [" + authType + "] Connection Error: " + e.getMessage());
    } catch (InterruptedException e) {
      System.err.println("   [" + authType + "] Thread interrupted: " + e.getMessage());
    }

    System.out.println("   " + authType + " demo completed. Total operations: " + operationCount);
  }
}
