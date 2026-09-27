package com.interviewlearning.remote;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gate and the forwarded-header filter together, in their registered
 * order: what a LAN device and a Tailscale Serve client actually get when the
 * server is bound to 0.0.0.0 and proxied at the same time.
 */
class RemoteAccessFilterChainTest {

    private static final String TOKEN = "0123456789abcdef0123";

    /** What the application code behind both filters saw, or null if it was never reached. */
    private HttpServletRequest reached;

    private MockHttpServletResponse run(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse res) {
                reached = req;
            }
        }, new RemoteAccessFilter("proxied", TOKEN, false), new LoopbackForwardedHeaderFilter());
        chain.doFilter(request, response);
        return response;
    }

    private static MockHttpServletRequest request(String peer, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRemoteAddr(peer);
        return request;
    }

    @Test
    void aLanDeviceForgingTheProxyHeadersIsStillAskedForTheToken() throws Exception {
        MockHttpServletRequest request = request("192.168.88.5", "/api/run");
        request.addHeader("X-Forwarded-For", "127.0.0.1");
        request.addHeader("Forwarded", "for=127.0.0.1");

        assertEquals(401, run(request).getStatus());
        assertNull(reached);
    }

    @Test
    void aLanDevicesForwardingHeadersAreNotApplied() throws Exception {
        MockHttpServletRequest request = request("192.168.88.5", "/api/topics");
        request.setCookies(new Cookie(RemoteAccessFilter.TOKEN_COOKIE, TOKEN));
        request.addHeader("X-Forwarded-For", "127.0.0.1");
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Host", "evil.example");

        assertEquals(200, run(request).getStatus());
        assertEquals("192.168.88.5", reached.getRemoteAddr());
        assertFalse(reached.isSecure());
        assertEquals("localhost", reached.getServerName());
    }

    @Test
    void aTailscaleClientNeedsTheTokenAndKeepsItsHttpsView() throws Exception {
        MockHttpServletRequest anonymous = serve(request("127.0.0.1", "/api/topics"));
        assertEquals(401, run(anonymous).getStatus());

        MockHttpServletRequest bootstrap = serve(request("127.0.0.1", "/"));
        bootstrap.setQueryString("token=" + TOKEN);
        MockHttpServletResponse response = run(bootstrap);
        assertEquals(302, response.getStatus());
        // Relative: an absolute one would be built from the loopback hop.
        assertEquals("/", response.getHeader("Location"));
        // The hop to the app is plain http, but the phone is on https.
        assertTrue(response.getCookie(RemoteAccessFilter.TOKEN_COOKIE).getSecure());

        MockHttpServletRequest withCookie = serve(request("127.0.0.1", "/api/topics"));
        withCookie.setCookies(new Cookie(RemoteAccessFilter.TOKEN_COOKIE, TOKEN));
        assertEquals(200, run(withCookie).getStatus());
        // What keeps the phone's POSTs same-origin for Spring's CORS check.
        assertTrue(reached.isSecure());
        assertEquals("pc.tailnet.ts.net", reached.getServerName());
    }

    @Test
    void aBrowserOnThisPcIsNotGated() throws Exception {
        assertEquals(200, run(request("127.0.0.1", "/api/run")).getStatus());
    }

    private static MockHttpServletRequest serve(MockHttpServletRequest request) {
        request.addHeader("X-Forwarded-For", "100.101.102.103");
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Host", "pc.tailnet.ts.net");
        return request;
    }
}
