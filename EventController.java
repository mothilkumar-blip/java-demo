package com.example.demo;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestController
public class EventController {

    private final Map<Integer, Event> events = new LinkedHashMap<>();
    private int nextId = 1;

    private static final Set<String> CATEGORIES = Set.of(
        "Music", "Tech", "Sports", "Workshop", "Festival"
    );

    @GetMapping("/")
    public Map<String, String> home() {
        return Map.of("message", "Event Tickets API is running");
    }

    private Map<String, String> error(String message) {
        return Map.of("error", message);
    }

    private ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.badRequest().body(error(message));
    }

    private Integer parseId(String id) {
        try {
            int value = Integer.parseInt(id);
            return value > 0 ? value : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private String validate(Event e) {
        if (e.getTitle() == null || e.getTitle().length() < 3
                || e.getTitle().length() > 60)
            return "title must be between 3 and 60 characters";

        if (e.getVenue() == null || e.getVenue().length() < 2
                || e.getVenue().length() > 40)
            return "venue must be between 2 and 40 characters";

        if (e.getCategory() == null || !CATEGORIES.contains(e.getCategory()))
            return "Invalid category";

        if (e.getTicketPrice() == null
                || !Double.isFinite(e.getTicketPrice())
                || e.getTicketPrice() <= 0
                || e.getTicketPrice() > 50000)
            return "ticketPrice must be greater than 0 and at most 50000";

        if (e.getSeatsAvailable() == null
                || e.getSeatsAvailable() < 0
                || e.getSeatsAvailable() > 100000)
            return "seatsAvailable must be between 0 and 100000";

        if (e.getIsOnline() == null)
            return "isOnline must be a boolean";

        return null;
    }

    private String applyFields(
            Event e, Map<String, Object> body, boolean partial) {

        if (body == null) return "Request body must be a JSON object";

        try {
            if (!partial || body.containsKey("title")) {
                Object v = body.get("title");
                if (!(v instanceof String)) return "title must be a string";
                e.setTitle((String) v);
            }

            if (!partial || body.containsKey("venue")) {
                Object v = body.get("venue");
                if (!(v instanceof String)) return "venue must be a string";
                e.setVenue((String) v);
            }

            if (!partial || body.containsKey("category")) {
                Object v = body.get("category");
                if (!(v instanceof String)) return "category must be a string";
                e.setCategory((String) v);
            }

            if (!partial || body.containsKey("ticketPrice")) {
                Object v = body.get("ticketPrice");
                if (!(v instanceof Number))
                    return "ticketPrice must be a number";
                e.setTicketPrice(((Number) v).doubleValue());
            }

            if (!partial || body.containsKey("seatsAvailable")) {
                Object v = body.get("seatsAvailable");
                if (!(v instanceof Number))
                    return "seatsAvailable must be an integer";

                double d = ((Number) v).doubleValue();

                if (!Double.isFinite(d) || d != Math.floor(d)
                        || d < Integer.MIN_VALUE || d > Integer.MAX_VALUE)
                    return "seatsAvailable must be an integer";

                e.setSeatsAvailable((int) d);
            }

            if (!partial || body.containsKey("isOnline")) {
                Object v = body.get("isOnline");
                if (!(v instanceof Boolean))
                    return "isOnline must be a boolean";
                e.setIsOnline((Boolean) v);
            }

            return validate(e);
        } catch (Exception ex) {
            return "Invalid event data";
        }
    }

    private Event copy(Event original) {
        Event e = new Event();
        e.setId(original.getId());
        e.setTitle(original.getTitle());
        e.setVenue(original.getVenue());
        e.setCategory(original.getCategory());
        e.setTicketPrice(original.getTicketPrice());
        e.setSeatsAvailable(original.getSeatsAvailable());
        e.setIsOnline(original.getIsOnline());
        return e;
    }

    @PostMapping("/api/events")
    public ResponseEntity<?> create(
            @RequestBody Map<String, Object> body) {

        Event event = new Event();
        String problem = applyFields(event, body, false);

        if (problem != null) return badRequest(problem);

        event.setId(nextId++);
        events.put(event.getId(), event);

        return ResponseEntity.status(HttpStatus.CREATED).body(event);
    }

    @GetMapping("/api/events/{id}")
    public ResponseEntity<?> getOne(@PathVariable String id) {
        Integer key = parseId(id);

        if (key == null || !events.containsKey(key))
            return ResponseEntity.status(404)
                    .body(error("Event not found"));

        return ResponseEntity.ok(events.get(key));
    }

    @PutMapping("/api/events/{id}")
    public ResponseEntity<?> replace(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {

        Integer key = parseId(id);

        if (key == null || !events.containsKey(key))
            return ResponseEntity.status(404)
                    .body(error("Event not found"));

        Event updated = new Event();
        String problem = applyFields(updated, body, false);

        if (problem != null) return badRequest(problem);

        updated.setId(key);
        events.put(key, updated);

        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/api/events/{id}")
    public ResponseEntity<?> update(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {

        Integer key = parseId(id);

        if (key == null || !events.containsKey(key))
            return ResponseEntity.status(404)
                    .body(error("Event not found"));

        if (body == null || body.isEmpty())
            return badRequest("Request body must not be empty");

        Event updated = copy(events.get(key));
        String problem = applyFields(updated, body, true);

        if (problem != null) return badRequest(problem);

        updated.setId(key);
        events.put(key, updated);

        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/api/events/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        Integer key = parseId(id);

        if (key == null || events.remove(key) == null)
            return ResponseEntity.status(404)
                    .body(error("Event not found"));

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/events")
    public ResponseEntity<?> list(
            @RequestParam Map<String, String> params) {

        int page = 1;
        int limit = 10;

        try {
            if (params.containsKey("page"))
                page = Integer.parseInt(params.get("page"));

            if (params.containsKey("limit"))
                limit = Integer.parseInt(params.get("limit"));
        } catch (Exception ex) {
            return badRequest("page and limit must be integers");
        }

        if (page < 1)
            return badRequest("page must be at least 1");

        if (limit < 1 || limit > 50)
            return badRequest("limit must be between 1 and 50");

        String category = params.get("category");
        String online = params.get("isOnline");
        String search = params.getOrDefault("search", "")
                .toLowerCase(Locale.ROOT);

        Double minPrice;
        Double maxPrice;

        try {
            minPrice = params.containsKey("minTicketPrice")
                    ? Double.valueOf(params.get("minTicketPrice")) : null;

            maxPrice = params.containsKey("maxTicketPrice")
                    ? Double.valueOf(params.get("maxTicketPrice")) : null;
        } catch (Exception ex) {
            return badRequest("Invalid ticket price filter");
        }

        if (category != null && !CATEGORIES.contains(category))
            return badRequest("Invalid category filter");

        if (online != null && !online.equals("true")
                && !online.equals("false"))
            return badRequest("isOnline must be true or false");

        if (minPrice != null
                && (!Double.isFinite(minPrice) || minPrice < 0))
            return badRequest("Invalid minimum ticket price");

        if (maxPrice != null
                && (!Double.isFinite(maxPrice) || maxPrice < 0))
            return badRequest("Invalid maximum ticket price");

        if (minPrice != null && maxPrice != null
                && minPrice > maxPrice)
            return badRequest("Minimum ticket price cannot exceed maximum");

        List<Event> result = new ArrayList<>(events.values());

        if (category != null)
            result.removeIf(e -> !category.equals(e.getCategory()));

        if (online != null) {
            boolean flag = Boolean.parseBoolean(online);
            result.removeIf(e ->
                    !Objects.equals(e.getIsOnline(), flag));
        }

        if (minPrice != null)
            result.removeIf(e -> e.getTicketPrice() < minPrice);

        if (maxPrice != null)
            result.removeIf(e -> e.getTicketPrice() > maxPrice);

        if (!search.isEmpty()) {
            result.removeIf(e ->
                !e.getTitle().toLowerCase(Locale.ROOT).contains(search)
                && !e.getVenue().toLowerCase(Locale.ROOT).contains(search)
            );
        }

        String sort = params.get("sort");
        String order = params.getOrDefault("order", "asc");

        if (!order.equals("asc") && !order.equals("desc"))
            return badRequest("order must be asc or desc");

        if (sort != null) {
            Comparator<Event> comparator;

            switch (sort) {
                case "title":
                    comparator = Comparator.comparing(
                            Event::getTitle,
                            String.CASE_INSENSITIVE_ORDER);
                    break;

                case "ticketPrice":
                    comparator = Comparator.comparing(
                            Event::getTicketPrice);
                    break;

                case "seatsAvailable":
                    comparator = Comparator.comparing(
                            Event::getSeatsAvailable);
                    break;

                default:
                    return badRequest("Invalid sort field");
            }

            if (order.equals("desc"))
                comparator = comparator.reversed();

            result.sort(comparator);
        }

        int total = result.size();
        int totalPages = (int) Math.ceil(total / (double) limit);
        int start = (int) Math.min((long) (page - 1) * limit,
                                   Integer.MAX_VALUE);

        List<Event> data;

        if (start >= total) {
            data = Collections.emptyList();
        } else {
            data = result.subList(start, Math.min(start + limit, total));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("page", page);
        response.put("limit", limit);
        response.put("total", total);
        response.put("totalPages", totalPages);

        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> invalidJson(
            HttpMessageNotReadableException ex) {

        return ResponseEntity.badRequest()
                .body(error("Invalid JSON request body"));
    }
}