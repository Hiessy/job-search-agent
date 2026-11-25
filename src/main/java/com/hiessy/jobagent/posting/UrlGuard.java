package com.hiessy.jobagent.posting;

import com.hiessy.jobagent.config.JobAgentProperties;
import com.hiessy.jobagent.guardrails.ToolInputException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Checks a URL before anything is fetched: http(s) only, no credentials, and (unless tests allow it)
 * no loopback, private, link-local or cloud-metadata addresses. Stops the model, or text in a
 * posting, from turning the fetch tool into a way to read the local network.
 */
@Component
public class UrlGuard {

    public static final int MAX_LENGTH = 2048;

    /** Looks a host name up. Tests swap in a fixed answer so they don't depend on DNS. */
    @FunctionalInterface
    public interface Resolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final boolean allowPrivateHosts;
    private final Resolver resolver;

    @Autowired
    public UrlGuard(JobAgentProperties props) {
        this(props.fetch().allowPrivateHosts(), InetAddress::getAllByName);
    }

    public UrlGuard(boolean allowPrivateHosts) {
        this(allowPrivateHosts, InetAddress::getAllByName);
    }

    public UrlGuard(boolean allowPrivateHosts, Resolver resolver) {
        this.allowPrivateHosts = allowPrivateHosts;
        this.resolver = resolver;
    }

    public URI check(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ToolInputException("url: must not be blank");
        }
        String value = raw.strip();
        if (value.length() > MAX_LENGTH) {
            throw new ToolInputException("url: longer than " + MAX_LENGTH + " characters");
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            throw new ToolInputException("url: not a valid URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new ToolInputException("url: must start with http:// or https://");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new ToolInputException("url: has no host");
        }
        if (uri.getRawUserInfo() != null) {
            throw new ToolInputException("url: must not contain a user name or password");
        }
        if (!allowPrivateHosts) {
            rejectPrivate(uri.getHost());
        }
        return uri;
    }

    private void rejectPrivate(String host) {
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException e) {
            throw new ToolInputException("url: host " + host + " does not resolve");
        }
        for (InetAddress a : addresses) {
            if (isPrivate(a)) {
                throw new ToolInputException("url: " + host + " points at a local or private address");
            }
        }
    }

    static boolean isPrivate(InetAddress a) {
        if (a.isLoopbackAddress() || a.isAnyLocalAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet4Address) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            return first == 0 || (first == 100 && second >= 64 && second <= 127); // "this network", carrier-grade NAT
        }
        if (a instanceof Inet6Address) {
            return (b[0] & 0xfe) == 0xfc; // unique local fc00::/7
        }
        return false;
    }
}
