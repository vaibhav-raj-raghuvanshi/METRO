import java.util.*;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/* ================= EXCEPTIONS ================= */

class NoPathException extends RuntimeException {
    public NoPathException(String msg) {
        super(msg);
    }
}

/* ================= MODELS ================= */

class Station {
    int id;
    String name;
    String line;
    boolean isInterchange;
    String nearbyPlace;
    int walkingTime; // minutes

    public Station(int id, String name, String line,
                   boolean isInterchange,
                   String nearbyPlace,
                   int walkingTime) {
        this.id = id;
        this.name = name;
        this.line = line;
        this.isInterchange = isInterchange;
        this.nearbyPlace = nearbyPlace;
        this.walkingTime = walkingTime;
    }
}

class Edge {
    int to;
    int distance;
    int transferTime;

    public Edge(int to, int distance, int transferTime) {
        this.to = to;
        this.distance = distance;
        this.transferTime = transferTime;
    }
}

class User {
    String id;
    String username;
    String passwordHash;

    public User(String username, String password) {
        this.id = UUID.randomUUID().toString();
        this.username = username;
        this.passwordHash = PasswordUtil.hash(password);
    }
}

class Booking {
    String id;
    String userId;
    List<Integer> path;
    int totalDistance;
    int totalTime;
    String qrString;

    public Booking(String userId, List<Integer> path,
                   int totalDistance, int totalTime) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.path = path;
        this.totalDistance = totalDistance;
        this.totalTime = totalTime;
        this.qrString = QRService.generateQR(id);
    }
}

/* ================= UTIL ================= */

class PasswordUtil {
    public static String hash(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(md.digest(password.getBytes()));
        } catch (Exception e) {
            throw new RuntimeException("Hash failed");
        }
    }
}

class QRService {
    private static final String SECRET = "MoveInSyncSecret";

    public static String generateQR(String bookingId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec key =
                    new SecretKeySpec(SECRET.getBytes(), "HmacSHA256");

            mac.init(key);
            byte[] hash = mac.doFinal(bookingId.getBytes());

            return bookingId + "." +
                    Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("QR failed");
        }
    }
}

/* ================= METRO GRAPH ================= */

class MetroGraphService {

    Map<Integer, Station> stations = new HashMap<>();
    Map<Integer, List<Edge>> graph = new HashMap<>();

    public MetroGraphService() {
        loadStations();
        buildGraph();
    }

    private void loadStations() {

        stations.put(1, new Station(1, "SEC-51", "Blue", false, "Mall Area", 5));
        stations.put(2, new Station(2, "SEC-50", "Blue", false, "Residential Hub", 3));
        stations.put(3, new Station(3, "SEC-101", "Blue", true, "Hospital", 4));
        stations.put(4, new Station(4, "SEC-81", "Red", false, "Market", 2));
        stations.put(5, new Station(5, "PARI CHOWK", "Red", true, "University", 6));
    }

    private void buildGraph() {
        addEdge(1, 2, 5, 0);
        addEdge(2, 3, 4, 0);
        addEdge(3, 4, 6, 3); // transfer at interchange
        addEdge(4, 5, 5, 0);
    }

    private void addEdge(int from, int to, int distance, int transferTime) {
        graph.computeIfAbsent(from, k -> new ArrayList<>())
                .add(new Edge(to, distance, transferTime));

        graph.computeIfAbsent(to, k -> new ArrayList<>())
                .add(new Edge(from, distance, transferTime));
    }

    public void displayMetroSheet() {
        System.out.println("===== METRO INFORMATION SHEET =====");
        for (Station s : stations.values()) {
            System.out.println("ID: " + s.id +
                    " | Name: " + s.name +
                    " | Line: " + s.line +
                    " | Interchange: " + s.isInterchange +
                    " | Nearby: " + s.nearbyPlace +
                    " | Walking Time: " + s.walkingTime + " mins");
        }
    }

    public Integer findStationByName(String name) {
        for (Station s : stations.values()) {
            if (s.name.equalsIgnoreCase(name)) {
                return s.id;
            }
        }
        return null;
    }
}

/* ================= PATH SERVICE ================= */

class PathService {

    public Booking findShortestPath(int source,
                                    int destination,
                                    MetroGraphService metro,
                                    String userId) {

        Map<Integer, List<Edge>> graph = metro.graph;

        PriorityQueue<int[]> pq =
                new PriorityQueue<>(Comparator.comparingInt(a -> a[1]));

        Map<Integer, Integer> dist = new HashMap<>();
        Map<Integer, Integer> time = new HashMap<>();
        Map<Integer, Integer> parent = new HashMap<>();

        pq.add(new int[]{source, 0});
        dist.put(source, 0);
        time.put(source, 0);

        while (!pq.isEmpty()) {

            int[] curr = pq.poll();
            int node = curr[0];
            int cost = curr[1];

            if (node == destination) break;

            for (Edge edge : graph.getOrDefault(node, new ArrayList<>())) {

                int newDist = dist.get(node) + edge.distance;
                int newTime = time.get(node) + edge.distance + edge.transferTime;

                if (!dist.containsKey(edge.to) ||
                        newDist < dist.get(edge.to)) {

                    dist.put(edge.to, newDist);
                    time.put(edge.to, newTime);
                    parent.put(edge.to, node);
                    pq.add(new int[]{edge.to, newDist});
                }
            }
        }

        if (!dist.containsKey(destination)) {
            throw new NoPathException("No route found");
        }

        List<Integer> path = new ArrayList<>();
        Integer curr = destination;

        while (curr != null) {
            path.add(curr);
            curr = parent.get(curr);
        }

        Collections.reverse(path);

        return new Booking(userId, path,
                dist.get(destination),
                time.get(destination));
    }
}

/* ================= MAIN ================= */

public class Main {

    static Map<String, User> users = new HashMap<>();

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);
        MetroGraphService metro = new MetroGraphService();
        PathService pathService = new PathService();

        System.out.println("=== METRO BOOKING SYSTEM ===");
        System.out.println("1. Register");
        System.out.println("2. Login");

        int choice = sc.nextInt();
        sc.nextLine();

        if (choice == 1) {
            System.out.print("Username: ");
            String u = sc.nextLine();
            System.out.print("Password: ");
            String p = sc.nextLine();
            users.put(u, new User(u, p));
            System.out.println("Registered Successfully.");
        }

        System.out.print("Login Username: ");
        String username = sc.nextLine();
        System.out.print("Password: ");
        String password = sc.nextLine();

        if (!users.containsKey(username) ||
                !users.get(username).passwordHash
                        .equals(PasswordUtil.hash(password))) {

            System.out.println("Invalid credentials.");
            return;
        }

        User currentUser = users.get(username);

        while (true) {

            System.out.println("\n1. View Metro Sheet");
            System.out.println("2. Search Route (By ID)");
            System.out.println("3. Search Route (By Name)");
            System.out.println("4. Exit");

            int option = sc.nextInt();
            sc.nextLine();

            if (option == 1) {
                metro.displayMetroSheet();
            }

            else if (option == 2) {
                System.out.print("Source ID: ");
                int s = sc.nextInt();
                System.out.print("Destination ID: ");
                int d = sc.nextInt();

                try {
                    Booking booking =
                            pathService.findShortestPath(s, d, metro, currentUser.id);

                    System.out.println("Path: " + booking.path);
                    System.out.println("Total Distance: " + booking.totalDistance + " km");
                    System.out.println("Total Time: " + booking.totalTime + " mins");
                    System.out.println("QR: " + booking.qrString);

                } catch (NoPathException e) {
                    System.out.println(e.getMessage());
                }
            }

            else if (option == 3) {
                System.out.print("Source Name: ");
                String sName = sc.nextLine();
                System.out.print("Destination Name: ");
                String dName = sc.nextLine();

                Integer s = metro.findStationByName(sName);
                Integer d = metro.findStationByName(dName);

                if (s == null || d == null) {
                    System.out.println("Invalid station name.");
                    continue;
                }

                try {
                    Booking booking =
                            pathService.findShortestPath(s, d, metro, currentUser.id);

                    System.out.println("Path: " + booking.path);
                    System.out.println("Total Distance: " + booking.totalDistance + " km");
                    System.out.println("Total Time: " + booking.totalTime + " mins");
                    System.out.println("QR: " + booking.qrString);

                } catch (NoPathException e) {
                    System.out.println(e.getMessage());
                }
            }

            else {
                System.out.println("Thank you for using Metro Service.");
                break;
            }
        }
    }
}