import visual.VisualInjection;

public class Playground {
    public static void main(String[] args) {
        VisualInjection app = VisualInjection.app();

        // An endpoint that accepts an XML invoice. No SQL anywhere.
        String ordinary = """
                <invoice>
                  <total>42</total>
                </invoice>""";

        // The same endpoint, with a document that brings its own DTD. The
        // entity is an instruction to the parser: go and open this file.
        String fileRead = """
                <?xml version="1.0"?>
                <!DOCTYPE invoice [
                  <!ENTITY secret SYSTEM "file:///etc/passwd">
                ]>
                <invoice>
                  <total>&secret;</total>
                </invoice>""";

        // Point the entity at a URL instead and the parser makes the request
        // for you, from inside your network, as a trusted host.
        String ssrf = """
                <?xml version="1.0"?>
                <!DOCTYPE invoice [
                  <!ENTITY probe SYSTEM "http://169.254.169.254/latest/meta-data/">
                ]>
                <invoice>
                  <total>&probe;</total>
                </invoice>""";

        // No external resource at all this time: the entities only reference
        // each other, and each level multiplies the one below it.
        String expansion = """
                <?xml version="1.0"?>
                <!DOCTYPE lolz [
                  <!ENTITY lol "lol">
                  <!ENTITY lol1 "&lol;&lol;&lol;">
                  <!ENTITY lol2 "&lol1;&lol1;&lol1;">
                ]>
                <lolz>&lol2;</lolz>""";

        app.parseXml(ordinary);
        app.parseXml(fileRead);
        app.parseXml(ssrf);
        app.parseXml(expansion);

        // One setting closes all three: no DTD, no entity to declare.
        app.secureXmlParser();
        app.parseXml(fileRead);

        app.report();
        System.out.println("Same bug, different parser -- and neither escaping nor binding is the fix.");
    }
}
