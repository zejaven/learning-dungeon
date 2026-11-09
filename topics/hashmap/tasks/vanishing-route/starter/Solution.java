import java.util.List;

/** The key of a route cache: the ordered stops of a delivery route. */
final class RouteKey {
    private final List<String> stops;

    RouteKey(List<String> stops) {
        this.stops = stops;
    }

    List<String> stops() {
        return stops;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RouteKey other)) return false;
        return stops.equals(other.stops);
    }

    @Override
    public int hashCode() {
        return stops.hashCode();
    }
}
