// Creator base class for Factory Method.
// The base workflow calls createDocument(), while subclasses decide which
// concrete Document is produced.
public abstract class DocumentCreator {

    public String export() {
        Document document = createDocument();
        return document.render();
    }

    protected abstract Document createDocument();
}
