package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualInjectionTest {

    private static final String TAUTOLOGY = "' OR '1'='1";
    private static final String COMMENT_OUT = "admin'--";
    private static final String UNION = "' UNION SELECT id, number, holder FROM cards --";
    private static final String STACKED = "'; DROP TABLE users; --";

    private static final String PLAIN_XML = """
            <invoice>
              <total>42</total>
            </invoice>""";

    private static final String XXE_FILE_XML = """
            <?xml version="1.0"?>
            <!DOCTYPE invoice [
              <!ENTITY secret SYSTEM "file:///etc/passwd">
            ]>
            <invoice>
              <total>&secret;</total>
            </invoice>""";

    private static final String XXE_SSRF_XML = """
            <?xml version="1.0"?>
            <!DOCTYPE invoice [
              <!ENTITY probe SYSTEM "http://169.254.169.254/latest/meta-data/">
            ]>
            <invoice>
              <total>&probe;</total>
            </invoice>""";

    private static final String EXPANSION_XML = """
            <?xml version="1.0"?>
            <!DOCTYPE lolz [
              <!ENTITY lol "lol">
              <!ENTITY lol1 "&lol;&lol;&lol;">
              <!ENTITY lol2 "&lol1;&lol1;&lol1;">
            ]>
            <lolz>&lol2;</lolz>""";

    private String captureTrace(Runnable body) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            body.run();
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void creatingTheAppEmitsTheSetupEvent() {
        String out = captureTrace(VisualInjection::app);
        assertTrue(out.contains("INJECTION_SETUP"), "expected a setup event, got:\n" + out);
    }

    @Test
    void anOrdinaryValueInAConcatenatedQueryIsNotReportedAsAnInjection() {
        String out = captureTrace(() -> {
            VisualInjection app = VisualInjection.app();
            app.findByNameConcatenated("alice");
            app.report();
        });
        assertTrue(out.contains("SQL_BUILT"), "the statement must be built, got:\n" + out);
        assertTrue(out.contains("STATEMENT_EXECUTED"), "it must run, got:\n" + out);
        assertFalse(out.contains("SQL_INJECTED"), "nothing was injected, got:\n" + out);
        assertTrue(out.contains("became part of the grammar 0"), "no injections, got:\n" + out);
    }

    @Test
    void aQuoteInAConcatenatedQueryBecomesGrammarAndReturnsEveryRow() {
        String out = captureTrace(() -> {
            VisualInjection app = VisualInjection.app();
            app.findByNameConcatenated(TAUTOLOGY);
            app.report();
        });
        assertTrue(out.contains("SQL_INJECTED"), "the value must break out, got:\n" + out);
        assertTrue(out.contains("{\"text\":\"OR\",\"kind\":\"keyword\",\"fromInput\":true,\"danger\":true}"),
                "OR must be tokenized as a dangerous keyword from the input, got:\n" + out);
        assertTrue(out.contains("extra-rows"), "every active row comes back, got:\n" + out);
        assertTrue(out.contains("became part of the grammar 1"), "one injection, got:\n" + out);
    }

    @Test
    void aCommentMarkerDeletesTheActiveCheckAndLetsALockedAccountThrough() {
        String out = captureTrace(() -> {
            VisualInjection app = VisualInjection.app();
            app.findByNameConcatenated(COMMENT_OUT);
            app.report();
        });
        assertTrue(out.contains("SQL_INJECTED"), "the comment must break out, got:\n" + out);
        assertTrue(out.contains("auth-bypass"), "the guard must be gone, got:\n" + out);
        assertTrue(out.contains("[\"4\",\"admin\",\"admin\"]"),
                "the locked account must be returned, got:\n" + out);
    }

    @Test
    void aUnionReturnsRowsFromATableTheEndpointNeverMentions() {
        String out = captureTrace(() -> {
            VisualInjection app = VisualInjection.app();
            app.findByNameConcatenated(UNION);
            app.report();
        });
        assertTrue(out.contains("data-theft"), "card rows must come back, got:\n" + out);
        assertTrue(out.contains("4111-1111-1111-1111"), "the card number must leak, got:\n" + out);
    }

    @Test
    void aSemicolonStartsASecondStatement() {
        String out = captureTrace(() -> {
            VisualInjection app = VisualInjection.app();
            app.findByNameConcatenated(STACKED);
            app.report();
        });
        assertTrue(out.contains("SQL_INJECTED"), "the value must break out, got:\n" + out);
        assertTrue(out.contains("schema-change"), "a second command must run, got:\n" + out);
    }

    @Test
    void aBoundParameterNeverEntersTheStatementText() {
        String out = captureTrace(() -> {
            VisualInjection app = VisualInjection.app();
            app.findByNameBound(TAUTOLOGY);
            app.report();
        });
        assertTrue(out.contains("STATEMENT_PREPARED"), "the text must be parsed first, got:\n" + out);
        assertTrue(out.contains("PARAMETER_BOUND"), "the value must be bound, got:\n" + out);
        assertTrue(out.contains("VALUE_STAYED_DATA"), "the payload must stay data, got:\n" + out);
        assertFalse(out.contains("SQL_INJECTED"), "nothing may be injected, got:\n" + out);
        assertTrue(out.contains("out-of-band"), "the value travels beside the query, got:\n" + out);
    }

    @Test
    void escapingHoldsInsideAStringLiteralAndDoesNothingForANumericColumn() {
        String quoted = captureTrace(() -> {
            VisualInjection app = VisualInjection.app().escapeQuotes();
            app.findByNameConcatenated(TAUTOLOGY);
            app.report();
        });
        assertTrue(quoted.contains("ESCAPING_ATTEMPTED"), "the escaper must run, got:\n" + quoted);
        assertTrue(quoted.contains("VALUE_STAYED_DATA"), "the payload must stay data, got:\n" + quoted);
        assertFalse(quoted.contains("SQL_INJECTED"), "nothing may break out, got:\n" + quoted);

        String numeric = captureTrace(() -> {
            VisualInjection app = VisualInjection.app().escapeQuotes();
            app.findByIdConcatenated("3 OR 1=1");
            app.report();
        });
        assertTrue(numeric.contains("ESCAPING_BYPASSED"), "no quote to escape, got:\n" + numeric);
        assertTrue(numeric.contains("SQL_INJECTED"), "it must still break out, got:\n" + numeric);
    }

    @Test
    void prepareStatementOverAConcatenatedStringProtectsNothing() {
        String out = captureTrace(() -> {
            VisualInjection app = VisualInjection.app();
            app.preparedButConcatenated(TAUTOLOGY);
            app.report();
        });
        assertTrue(out.contains("PREPARED_IN_NAME_ONLY"), "the trap must be named, got:\n" + out);
        assertTrue(out.contains("SQL_INJECTED"), "it must still break out, got:\n" + out);
        assertTrue(out.contains("in-band"), "the value is inside the text, got:\n" + out);
    }

    @Test
    void anIdentifierCannotBeBoundAndAnAllowlistIsWhatFixesIt() {
        String concatenated = captureTrace(() -> {
            VisualInjection app = VisualInjection.app();
            app.sortByColumnConcatenated("name");
            app.sortByColumnConcatenated("name; DROP TABLE users --");
            app.report();
        });
        assertTrue(concatenated.contains("IDENTIFIER_INTERPOLATED"),
                "the identifier case must be named, got:\n" + concatenated);
        assertTrue(concatenated.contains("SQL_INJECTED"),
                "the crafted column must break out, got:\n" + concatenated);
        assertTrue(concatenated.contains("became part of the grammar 1"),
                "the plain column name must NOT count as an injection, got:\n" + concatenated);

        String allowlisted = captureTrace(() -> {
            VisualInjection app = VisualInjection.app();
            app.sortByColumnAllowlisted("name; DROP TABLE users --");
            app.report();
        });
        assertTrue(allowlisted.contains("ALLOWLIST_CHECKED"), "the check must run, got:\n" + allowlisted);
        assertTrue(allowlisted.contains("VALUE_STAYED_DATA"), "it must stay safe, got:\n" + allowlisted);
        assertFalse(allowlisted.contains("SQL_INJECTED"), "nothing may break out, got:\n" + allowlisted);
    }

    @Test
    void aValueBoundOnWriteIsStillDangerousWhenConcatenatedOnRead() {
        String out = captureTrace(() -> {
            VisualInjection app = VisualInjection.app();
            app.saveProfileBound(TAUTOLOGY);
            app.auditSavedProfile();
            app.report();
        });
        assertTrue(out.contains("PARAMETER_BOUND"), "the write must be bound, got:\n" + out);
        assertTrue(out.contains("SECOND_ORDER_INJECTION"), "the read must be flagged, got:\n" + out);
        assertTrue(out.contains("SQL_INJECTED"), "the later query must break, got:\n" + out);
    }

    @Test
    void aBindParameterDoesNotHelpWhenTheDatabaseConcatenatesItAgain() {
        String out = captureTrace(() -> {
            VisualInjection app = VisualInjection.app();
            app.callReportProcedure("user' OR '1'='1");
            app.report();
        });
        assertTrue(out.contains("PARAMETER_BOUND"), "the call must bind, got:\n" + out);
        assertTrue(out.contains("DYNAMIC_SQL_IN_DATABASE"), "the procedure must concatenate, got:\n" + out);
        assertTrue(out.contains("SQL_INJECTED"), "it must break out inside the database, got:\n" + out);
    }

    @Test
    void anExternalEntityMakesTheParserReadAFile() {
        String out = captureTrace(() -> {
            VisualInjection app = VisualInjection.app();
            app.parseXml(XXE_FILE_XML);
            app.report();
        });
        assertTrue(out.contains("DTD_DECLARED"), "the DTD must be accepted, got:\n" + out);
        assertTrue(out.contains("ENTITY_RESOLVED"), "the entity must be resolved, got:\n" + out);
        assertTrue(out.contains("XXE_FILE_DISCLOSED"), "the file must leak, got:\n" + out);
        assertTrue(out.contains(VisualInjection.SECRET_FILE_CONTENT),
                "the contents must come back, got:\n" + out);
    }

    @Test
    void anExternalEntityOverHttpIsServerSideRequestForgery() {
        String out = captureTrace(() -> {
            VisualInjection app = VisualInjection.app();
            app.parseXml(XXE_SSRF_XML);
            app.report();
        });
        assertTrue(out.contains("XXE_SSRF"), "the parser must make the request, got:\n" + out);
        assertTrue(out.contains(VisualInjection.INTERNAL_RESPONSE),
                "the internal answer must come back, got:\n" + out);
    }

    @Test
    void nestedEntitiesExhaustMemoryWithoutTouchingAnyResource() {
        String out = captureTrace(() -> {
            VisualInjection app = VisualInjection.app();
            app.parseXml(EXPANSION_XML);
            app.report();
        });
        assertTrue(out.contains("ENTITY_EXPANSION"), "expansion must be reported, got:\n" + out);
        assertTrue(out.contains("dos"), "the impact is denial of service, got:\n" + out);
        assertFalse(out.contains("ENTITY_RESOLVED"), "nothing external is fetched, got:\n" + out);
    }

    @Test
    void disallowingDoctypesEndsAllOfThemAtOnce() {
        String out = captureTrace(() -> {
            VisualInjection app = VisualInjection.app().secureXmlParser();
            app.parseXml(XXE_FILE_XML);
            app.parseXml(EXPANSION_XML);
            app.report();
        });
        assertTrue(out.contains("XXE_BLOCKED"), "the document must be refused, got:\n" + out);
        assertFalse(out.contains("XXE_FILE_DISCLOSED"), "nothing may leak, got:\n" + out);
        assertFalse(out.contains("ENTITY_EXPANSION"), "nothing may expand, got:\n" + out);
        assertTrue(out.contains("disallow-doctype-decl"), "the setting must be named, got:\n" + out);
    }

    @Test
    void anOrdinaryDocumentWithNoDtdIsJustData() {
        String out = captureTrace(() -> {
            VisualInjection app = VisualInjection.app();
            app.parseXml(PLAIN_XML);
            app.report();
        });
        assertTrue(out.contains("XML_PARSED"), "it must parse normally, got:\n" + out);
        assertFalse(out.contains("ENTITY_RESOLVED"), "nothing is resolved, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualInjection app = VisualInjection.app();
            app.findByNameConcatenated(TAUTOLOGY);
            app.findByIdConcatenated("3");
            app.findByNameBound(TAUTOLOGY);
            app.preparedButConcatenated(COMMENT_OUT);
            app.sortByColumnConcatenated("name");
            app.sortByColumnAllowlisted("role");
            app.saveProfileBound(UNION);
            app.auditSavedProfile();
            app.callReportProcedure("admin");
            app.parseXml(XXE_FILE_XML);
            app.parseXml(PLAIN_XML);
            app.escapeQuotes();
            app.secureXmlParser();
            app.parseXml(XXE_SSRF_XML);
            app.report();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
