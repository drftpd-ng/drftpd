package org.drftpd.common.network;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.drftpd.common.exceptions.SSLServiceException;
import org.elasticsearch.common.ssl.DiagnosticTrustManager;
import org.elasticsearch.common.ssl.SslClientAuthenticationMode;
import org.elasticsearch.common.ssl.SslConfiguration;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * This class is inspired by the SSLService class in elastic search x-pack module
 */
public class SSLService {

    private static final Logger logger = LogManager.getLogger(SSLService.class);

    protected static SSLService _service;

    /**
     * Mapping of setting prefixes to ssl configurations.
     * examples are: master.ssl, slavemanager.ssl
     * This is a handy generic way to find initialized ssl configurations
     */
    private final Map<String, SslConfiguration> _sslConfigurations = new HashMap<>();
    private final Map<SslConfiguration, SSLContext> _sslContexts = new HashMap<>();

    private boolean _diagnoseTrustExceptions = false;

    private static final Map<String, String> ORDERED_PROTOCOL_ALGORITHM_MAP;
    static {
        LinkedHashMap<String, String> protocolAlgorithmMap = new LinkedHashMap<>();
        protocolAlgorithmMap.put("TLSv1.3", "TLSv1.3");
        protocolAlgorithmMap.put("TLSv1.2", "TLSv1.2");
        protocolAlgorithmMap.put("TLSv1.1", "TLSv1.1");
        protocolAlgorithmMap.put("TLSv1", "TLSv1");
        protocolAlgorithmMap.put("SSLv3", "SSLv3");
        protocolAlgorithmMap.put("SSLv2", "SSL");
        protocolAlgorithmMap.put("SSLv2Hello", "SSL");
        ORDERED_PROTOCOL_ALGORITHM_MAP = Collections.unmodifiableMap(protocolAlgorithmMap);
    }

    /**
     * If you're creating a GlobalContext object and it's not part of a TestCase
     * you're not doing it correctly, GlobalContext is a Singleton
     */
    protected SSLService() {}

    public static SSLService getSSLService() {
        if (_service == null) {
            _service = new SSLService();
        }
        return _service;
    }

    public void setTrustExceptionsDiagnose(boolean state) {
        _diagnoseTrustExceptions = state;
    }

    public void registerSSLConfiguration(String prefix, SslConfiguration conf) throws SSLServiceException {
        if (_sslConfigurations.containsKey(prefix)) {
            throw new SSLServiceException("Prefix " + prefix + " already registered");
        }
        _sslConfigurations.put(prefix, conf);
        _sslContexts.put(conf, createSslContext(conf));
    }

    /**
     * Create a new {@link SSLSocketFactory} based on the provided configuration.
     * The socket factory will also properly configure the ciphers and protocols on each socket that is created
     * @param configuration The SSL configuration to use. Typically obtained from {@link #getSSLConfiguration(String)}
     * @return Never {@code null}.
     */
    public SSLSocketFactory sslSocketFactory(SslConfiguration configuration) throws SSLServiceException {
        SSLContext context = sslContext(configuration);
        SSLSocketFactory socketFactory = context.getSocketFactory();
        return new SecuritySSLSocketFactory(context::getSocketFactory, configuration.getSupportedProtocols().toArray(new String[0]),
                supportedCiphers(socketFactory.getSupportedCipherSuites(), configuration.getCipherSuites(), false), configuration.getClientAuth());
    }

    /**
     * Create a new {@link SSLServerSocketFactory} based on the provided configuration.
     * The socket factory will also properly configure the ciphers and protocols on each socket that is created
     * @param configuration The SSL configuration to use. Typically obtained from {@link #getSSLConfiguration(String)}
     * @return Never {@code null}.
     */
    public SSLServerSocketFactory sslServerSocketFactory(SslConfiguration configuration) throws SSLServiceException {
        SSLContext context = sslContext(configuration);
        SSLServerSocketFactory socketFactory = context.getServerSocketFactory();
        return new SecuritySSLServerSocketFactory(context::getServerSocketFactory, configuration.getSupportedProtocols().toArray(new String[0]),
                supportedCiphers(socketFactory.getSupportedCipherSuites(), configuration.getCipherSuites(), false), configuration.getClientAuth());
    }

    /**
     * Creates an {@link SSLEngine} based on the provided configuration. This SSLEngine can be used for a connection that requires
     * hostname verification assuming the provided
     * host and port are correct. The SSLEngine created by this method is most useful for clients with hostname verification enabled
     *
     * @param configuration the ssl configuration
     * @param host          the host of the remote endpoint. If using hostname verification, this should match what is in the remote
     *                      endpoint's certificate
     * @param port          the port of the remote endpoint
     * @return {@link SSLEngine}
     * @see #getSSLConfiguration(String)
     */
    public SSLEngine createSSLEngine(SslConfiguration configuration, String host, int port) throws SSLServiceException {
        SSLContext sslContext = sslContext(configuration);
        SSLEngine sslEngine = sslContext.createSSLEngine(host, port);
        String[] ciphers = supportedCiphers(sslEngine.getSupportedCipherSuites(), configuration.getCipherSuites(), false);
        String[] supportedProtocols = configuration.getSupportedProtocols().toArray(new String[0]);
        SSLParameters parameters = new SSLParameters(ciphers, supportedProtocols);
        if (configuration.getVerificationMode().isHostnameVerificationEnabled() && host != null ) {
            // By default, an SSLEngine will not perform hostname verification. In order to perform hostname verification
            // we need to specify a EndpointIdentificationAlgorithm. We use the HTTPS algorithm to prevent against
            // man in the middle attacks for all of our connections.
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
        }
        // we use the cipher suite order so that we can prefer the ciphers we set first in the list
        parameters.setUseCipherSuitesOrder(true);
        configuration.getClientAuth().configure(parameters);

        // many SSLEngine options can be configured using either SSLParameters or direct methods on the engine itself, but there is one
        // tricky aspect; if you set a value directly on the engine and then later set the SSLParameters the value set directly on the
        // engine will be overwritten by the value in the SSLParameters
        logger.debug("SSLEngine SSLParameters have been setup as {}", SSLParametersToString(parameters));
        sslEngine.setSSLParameters(parameters);
        return sslEngine;
    }

    /**
     * Indicates whether client authentication is enabled for a particular configuration
     */
    public boolean isSSLClientAuthEnabled(SslConfiguration sslConfiguration) {
        Objects.requireNonNull(sslConfiguration, "SslConfiguration cannot be null");
        return sslConfiguration.getClientAuth().enabled();
    }

    /**
     * Returns the {@link SSLContext} for the configuration. Mainly used for testing
     */
    public SSLContext sslContext(SslConfiguration configuration) {
        Objects.requireNonNull(configuration, "SslConfiguration cannot be null");
        return _sslContexts.get(configuration);
    }

    /**
     * Accessor to the loaded ssl configuration objects at the current point in time. This is useful for testing
     */
    Collection<SslConfiguration> getLoadedSSLConfigurations() {
        return Set.copyOf(_sslContexts.keySet());
    }

    /**
     * Returns the intersection of the supported ciphers with the requested ciphers. This method will also optionally log if unsupported
     * ciphers were requested.
     *
     * @throws IllegalArgumentException if no supported ciphers are in the requested ciphers
     */
    public String[] supportedCiphers(String[] supportedCiphers, List<String> requestedCiphers, boolean log) throws SSLServiceException {
        List<String> supportedCiphersList = new ArrayList<>(requestedCiphers.size());
        List<String> unsupportedCiphers = new LinkedList<>();
        boolean found;
        for (String requestedCipher : requestedCiphers) {
            found = false;
            for (String supportedCipher : supportedCiphers) {
                if (supportedCipher.equals(requestedCipher)) {
                    found = true;
                    supportedCiphersList.add(requestedCipher);
                    break;
                }
            }

            if (!found) {
                unsupportedCiphers.add(requestedCipher);
            }
        }

        if (supportedCiphersList.isEmpty()) {
            throw new SSLServiceException("none of the ciphers " + Arrays.toString(requestedCiphers.toArray()) + " are supported by this JVM");
        }

        if (log && !unsupportedCiphers.isEmpty()) {
            logger.error("unsupported ciphers [{}] were requested but cannot be used in this JVM, however there are supported ciphers " +
                    "that will be used [{}]. If you are trying to use ciphers with a key length greater than 128 bits on an Oracle JVM, " +
                    "you will need to install the unlimited strength JCE policy files.", unsupportedCiphers, supportedCiphersList);
        }

        return supportedCiphersList.toArray(new String[0]);
    }

    /**
     * Creates an {@link SSLContext} based on the provided configuration
     *
     * @param sslConfiguration the configuration to use for context creation
     * @return the created SSLContext
     */
    private SSLContext createSslContext(SslConfiguration sslConfiguration) throws SSLServiceException {
        logger.debug("using ssl settings [{}]", sslConfiguration);
        return createSslContext(sslConfiguration.getKeyConfig().createKeyManager(), sslConfiguration.getTrustConfig().createTrustManager(), sslConfiguration);
    }

    /**
     * Creates an {@link SSLContext} based on the provided configuration and trust/key managers
     *
     * @param sslConfiguration the configuration to use for context creation
     * @param keyManager       the key manager to use
     * @param trustManager     the trust manager to use
     * @return the created SSLContext
     */
    private SSLContext createSslContext(X509ExtendedKeyManager keyManager, X509ExtendedTrustManager trustManager,
                                        SslConfiguration sslConfiguration) throws SSLServiceException {
        trustManager = wrapWithDiagnostics(trustManager, sslConfiguration);
        // Initialize sslContext
        try {
            SSLContext sslContext = SSLContext.getInstance(sslContextAlgorithm(sslConfiguration.getSupportedProtocols()));
            sslContext.init(new X509ExtendedKeyManager[]{keyManager}, new X509ExtendedTrustManager[]{trustManager}, null);

            // check the supported ciphers and log them here to prevent spamming logs on every call
            supportedCiphers(sslContext.getSupportedSSLParameters().getCipherSuites(), sslConfiguration.getCipherSuites(), true);

            return sslContext;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new SSLServiceException("failed to initialize the SSLContext", e);
        }
    }

    public X509ExtendedTrustManager wrapWithDiagnostics(X509ExtendedTrustManager trustManager, SslConfiguration configuration) {
        if (_diagnoseTrustExceptions && !(trustManager instanceof DiagnosticTrustManager)) {
            final Logger diagnosticLogger = LogManager.getLogger(DiagnosticTrustManager.class);
            // A single configuration might be used in many place, if there are multiple, we just list "shared" because
            // that is better than the alternatives. Just listing would be misleading (it might not be the right one)
            // but listing all of them would be confusing (e.g. some might be the default realms)
            // This needs to be a supplier (deferred evaluation) because we might load more configurations after this context is built.
            final Supplier<String> contextName = () -> {
                final List<String> names = _sslConfigurations.entrySet().stream()
                        .filter(e -> e.getValue().equals(configuration))
                        .limit(2) // we only need to distinguishing between 0/1/many
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toUnmodifiableList());
                return switch (names.size()) {
                    case 0 -> "(unknown)";
                    case 1 -> names.get(0);
                    default -> "(shared)";
                };
            };
            logger.info("Enabling diagnose of trust exceptions for configuration: [{}]", configuration);
            trustManager = new DiagnosticTrustManager(trustManager, contextName, diagnosticLogger::warn);
        }
        return trustManager;
    }

    /**
     * This socket factory wraps an existing SSLSocketFactory and sets the protocols and ciphers on each SSLSocket after it is created. This
     * is needed even though the SSLContext is configured properly as the configuration does not flow down to the sockets created by the
     * SSLSocketFactory obtained from the SSLContext.
     */
    private static class SecuritySSLSocketFactory extends SSLSocketFactory {

        private final Supplier<SSLSocketFactory> _delegateSupplier;
        private final String[] _supportedProtocols;
        private final String[] _ciphers;
        private final SslClientAuthenticationMode _clientAuthMode;

        private volatile SSLSocketFactory _delegate;

        SecuritySSLSocketFactory(Supplier<SSLSocketFactory> delegateSupplier, String[] supportedProtocols, String[] ciphers, SslClientAuthenticationMode clientAuthMode) {
            _delegateSupplier = delegateSupplier;
            _delegate = _delegateSupplier.get();
            _supportedProtocols = supportedProtocols;
            _ciphers = ciphers;
            _clientAuthMode = clientAuthMode;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return _ciphers;
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return _delegate.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket() throws IOException {
            //SSLSocket sslSocket = createWithPermissions(delegate::createSocket);
            SSLSocket sslSocket = (SSLSocket) _delegate.createSocket();
            configureSSLSocket(sslSocket);
            return sslSocket;
        }

        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
            SSLSocket sslSocket = (SSLSocket) _delegate.createSocket(socket, host, port, autoClose);
            configureSSLSocket(sslSocket);
            return sslSocket;
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            SSLSocket sslSocket = (SSLSocket) _delegate.createSocket(host, port);
            configureSSLSocket(sslSocket);
            return sslSocket;
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            SSLSocket sslSocket = (SSLSocket) _delegate.createSocket(host, port, localHost, localPort);
            configureSSLSocket(sslSocket);
            return sslSocket;
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            SSLSocket sslSocket = (SSLSocket) _delegate.createSocket(host, port);
            configureSSLSocket(sslSocket);
            return sslSocket;
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            SSLSocket sslSocket = (SSLSocket) _delegate.createSocket(address, port, localAddress, localPort);
            configureSSLSocket(sslSocket);
            return sslSocket;
        }

        public void reload() {
            _delegate = _delegateSupplier.get();
        }

        private void configureSSLSocket(SSLSocket socket) {
            SSLParameters parameters = new SSLParameters(_ciphers, _supportedProtocols);
            // we use the cipher suite order so that we can prefer the ciphers we set first in the list
            parameters.setUseCipherSuitesOrder(true);
            _clientAuthMode.configure(parameters);
            logger.debug("Socket SSLParameters have been setup as {}", SSLParametersToString(parameters));
            socket.setSSLParameters(parameters);
        }
    }

    /**
     * This socket factory wraps an existing SSLSocketFactory and sets the protocols and ciphers on each SSLSocket after it is created. This
     * is needed even though the SSLContext is configured properly as the configuration does not flow down to the sockets created by the
     * SSLSocketFactory obtained from the SSLContext.
     */
    private static class SecuritySSLServerSocketFactory extends SSLServerSocketFactory {

        private final Supplier<SSLServerSocketFactory> _delegateSupplier;
        private final String[] _supportedProtocols;
        private final String[] _ciphers;
        private final SslClientAuthenticationMode _clientAuthMode;

        private volatile SSLServerSocketFactory _delegate;

        SecuritySSLServerSocketFactory(Supplier<SSLServerSocketFactory> delegateSupplier, String[] supportedProtocols, String[] ciphers, SslClientAuthenticationMode clientAuthMode) {
            _delegateSupplier = delegateSupplier;
            _delegate = _delegateSupplier.get();
            _supportedProtocols = supportedProtocols;
            _ciphers = ciphers;
            _clientAuthMode = clientAuthMode;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return _ciphers;
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return _delegate.getSupportedCipherSuites();
        }

        @Override
        public ServerSocket createServerSocket(int port) throws IOException {
            SSLServerSocket sslServerSocket = (SSLServerSocket) _delegate.createServerSocket(port);
            configureSSLServerSocket(sslServerSocket);
            return sslServerSocket;
        }

        @Override
        public ServerSocket createServerSocket(int port, int backlog) throws IOException {
            SSLServerSocket sslServerSocket = (SSLServerSocket) _delegate.createServerSocket(port, backlog);
            configureSSLServerSocket(sslServerSocket);
            return sslServerSocket;
        }

        @Override
        public ServerSocket createServerSocket(int port, int backlog, InetAddress ifAddress) throws IOException {
            SSLServerSocket sslServerSocket = (SSLServerSocket) _delegate.createServerSocket(port, backlog, ifAddress);
            configureSSLServerSocket(sslServerSocket);
            return sslServerSocket;
        }

        public void reload() {
            this._delegate = _delegateSupplier.get();
        }

        private void configureSSLServerSocket(SSLServerSocket socket) {
            SSLParameters parameters = new SSLParameters(_ciphers, _supportedProtocols);
            // we use the cipher suite order so that we can prefer the ciphers we set first in the list
            parameters.setUseCipherSuitesOrder(true);
            _clientAuthMode.configure(parameters);
            logger.debug("ServerSocket SSLParameters have been setup as {}", SSLParametersToString(parameters));
            socket.setSSLParameters(parameters);
        }
    }

    public SslConfiguration getSSLConfiguration(String contextName) {
        if (contextName.endsWith(".")) {
            contextName = contextName.substring(0, contextName.length() - 1);
        }
        final SslConfiguration configuration = _sslConfigurations.get(contextName);
        if (configuration == null) {
            logger.warn("Cannot find SSL configuration for context {}. Known contexts are: {}", contextName,
                    Arrays.toString(_sslConfigurations.keySet().toArray()));
        }
        return configuration;
    }

    /**
     * Maps the supported protocols to an appropriate ssl context algorithm. We make an attempt to use the "best" algorithm when
     * possible. The names in this method are taken from the
     * <a href="https://docs.oracle.com/en/java/javase/15/docs/specs/security/standard-names.html#sslcontext-algorithms">Java Security
     * Standard Algorithm Names Documentation for Java 15</a>.
     */
    private static String sslContextAlgorithm(List<String> supportedProtocols) {
        if (supportedProtocols.isEmpty()) {
            throw new IllegalArgumentException("no SSL/TLS protocols have been configured");
        }
        for (Map.Entry<String, String> entry : ORDERED_PROTOCOL_ALGORITHM_MAP.entrySet()) {
            if (supportedProtocols.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        throw new IllegalArgumentException("no supported SSL/TLS protocol was found in the configured supported protocols: "
                + supportedProtocols);
    }

    public static String SSLParametersToString(SSLParameters params) {
        return params.getClass().getSimpleName() + '{' +
                "CipherSuites=" + Arrays.toString(params.getCipherSuites()) +
                ", Protocols=" + Arrays.toString(params.getProtocols()) +
                ", ApplicationProtocols=" + Arrays.toString(params.getApplicationProtocols()) +
                ", EndpointIdentificationAlgorithm=" + params.getEndpointIdentificationAlgorithm() +
                ", EnableRetransmissions=" + params.getEnableRetransmissions() +
                ", NeedClientAuth=" + params.getNeedClientAuth() +
                ", UseCipherSuitesOrder=" + params.getUseCipherSuitesOrder() +
                ", WantClientAuth=" + params.getWantClientAuth() +
                ", MaximumPacketSize=" + params.getMaximumPacketSize() +
                '}';
    }
}