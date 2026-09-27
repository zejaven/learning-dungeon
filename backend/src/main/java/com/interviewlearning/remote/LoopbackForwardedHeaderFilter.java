package com.interviewlearning.remote;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * Spring's forwarded-header handling, applied only to requests whose TCP peer
 * is this machine.
 *
 * <p>Behind Tailscale Serve the browser talks https to {@code <host>.ts.net}
 * while the app sees plain http on 127.0.0.1; without X-Forwarded-Proto/Host
 * applied, Spring calls the phone's {@code Origin} cross-origin and refuses
 * every POST. That is why these headers must be honoured at all.
 *
 * <p>But the stock filter ({@code server.forward-headers-strategy: framework})
 * honours them from ANY caller, X-Forwarded-For included, and rewrites
 * {@code getRemoteAddr()} from it. With the server bound to the LAN a caller
 * could send {@code X-Forwarded-For: 127.0.0.1} and look local. So the stock
 * strategy is off ({@code none} in application.yml) and this one only acts for
 * a loopback peer — a proxy we run ourselves. A LAN caller's forwarding headers
 * are left on the request, unread: nothing else in the app consults them.
 *
 * <p>Ordered right after {@link RemoteAccessFilter}, which must see the request
 * before anything is rewritten.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class LoopbackForwardedHeaderFilter extends ForwardedHeaderFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !RemoteAccessFilter.isLoopback(request.getRemoteAddr()) || super.shouldNotFilter(request);
    }
}
