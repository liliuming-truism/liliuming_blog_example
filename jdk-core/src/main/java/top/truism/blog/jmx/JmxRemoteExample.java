package top.truism.blog.jmx;

import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import java.lang.management.ManagementFactory;
import java.rmi.registry.LocateRegistry;

/**
 * JMX Remote Connector: expose the MBeanServer over RMI so external tools
 * (JConsole, VisualVM, jmxterm) can attach without needing access to the JVM process.
 *
 * How to connect externally once the connector is running:
 *   jconsole  service:jmx:rmi:///jndi/rmi://localhost:9999/jmxrmi
 *   jmxterm   open service:jmx:rmi:///jndi/rmi://localhost:9999/jmxrmi
 *
 * Production notes:
 *   - Add authentication: env.put(JMXConnectorServer.AUTHENTICATOR, ...)
 *   - Add TLS:            env.put("jmx.remote.tls.enabled.protocols", ...)
 *   - Or configure via JVM flags at startup:
 *       -Dcom.sun.management.jmxremote.port=9999
 *       -Dcom.sun.management.jmxremote.authenticate=false
 *       -Dcom.sun.management.jmxremote.ssl=false
 */
public class JmxRemoteExample {

    static final int PORT = 9999;

    public static void main(String[] args) throws Exception {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();

        // Register something to inspect remotely
        ObjectName name = new ObjectName("top.truism.blog:type=CacheManager,name=remote");
        CacheManager cache = new CacheManager(200);
        mbs.registerMBean(cache, name);
        cache.put("config:timeout", "30s");
        cache.put("config:maxRetry", "3");
        cache.get("config:timeout");
        cache.get("config:missing"); // miss

        // Start an RMI registry and attach a JMX connector to it
        LocateRegistry.createRegistry(PORT);
        JMXServiceURL url = new JMXServiceURL(
                "service:jmx:rmi:///jndi/rmi://localhost:" + PORT + "/jmxrmi");
        JMXConnectorServer server =
                JMXConnectorServerFactory.newJMXConnectorServer(url, null, mbs);
        server.start();

        System.out.println("=== JMX Remote Connector ===");
        System.out.println("Listening : " + url);
        System.out.println("Connect   : jconsole " + url);

        // Programmatic remote client — same code a separate JVM would run
        try (JMXConnector connector = JMXConnectorFactory.connect(url)) {
            MBeanServerConnection remote = connector.getMBeanServerConnection();

            System.out.println("\nRemote MBean count : " + remote.getMBeanCount());
            System.out.println("Cache size (remote): " + remote.getAttribute(name, "Size"));
            System.out.println("HitCount (remote)  : " + remote.getAttribute(name, "HitCount"));
            System.out.printf ("HitRate  (remote)  : %.2f%n", remote.getAttribute(name, "HitRate"));

            // Invoke an operation remotely
            remote.invoke(name, "clear", new Object[0], new String[0]);
            System.out.println("Size after clear   : " + remote.getAttribute(name, "Size"));
        }

        server.stop();
        mbs.unregisterMBean(name);
    }
}
