package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A teaching model of a red-black tree for integer keys. It is intentionally
 * smaller than java.util.TreeMap: it focuses on insertion, recoloring,
 * rotations, and search while emitting trace events for the learning UI.
 */
public class VisualRedBlackTree {

    private enum Color {
        RED, BLACK
    }

    private final String name;
    private Node root;
    private int size;

    public VisualRedBlackTree() {
        this("tree");
    }

    public VisualRedBlackTree(String name) {
        this.name = name;
        Trace.event("RBT_CREATED",
                "Created empty red-black tree '" + name + "'",
                "Создано пустое красно-чёрное дерево '" + name + "'",
                List.of(),
                state("created", null, null, List.of()));
    }

    public boolean insert(int key) {
        if (root == null) {
            root = new Node(key, Color.BLACK, null);
            size = 1;
            Trace.event("RBT_INSERT",
                    "Inserted " + key + " as the black root",
                    "Вставили " + key + " как чёрный корень",
                    List.of("node:" + key),
                    state("insert-root", key, "inserted", List.of(key)));
            return true;
        }

        Node parent = null;
        Node current = root;
        List<Integer> path = new ArrayList<>();
        while (current != null) {
            path.add(current.key);
            parent = current;
            if (key == current.key) {
                Trace.event("RBT_DUPLICATE",
                        "Key " + key + " is already present; the tree keeps one copy",
                        "Ключ " + key + " уже есть; дерево хранит один экземпляр",
                        highlightPath(path),
                        state("duplicate", key, "already present", path));
                return false;
            }
            current = key < current.key ? current.left : current.right;
        }

        Node inserted = new Node(key, Color.RED, parent);
        if (key < parent.key) {
            parent.left = inserted;
        } else {
            parent.right = inserted;
        }
        size++;
        path.add(key);

        Trace.event("RBT_INSERT",
                "Inserted " + key + " as a red leaf under " + parent.key,
                "Вставили " + key + " как красный лист под " + parent.key,
                highlightPath(path),
                state("insert", key, "inserted red leaf", path));

        fixAfterInsert(inserted);
        return true;
    }

    public boolean contains(int key) {
        Node current = root;
        List<Integer> path = new ArrayList<>();
        while (current != null) {
            path.add(current.key);
            if (key == current.key) {
                Trace.event("RBT_SEARCH",
                        "Search for " + key + " followed " + path + " and found the key",
                        "Поиск " + key + " прошёл по " + path + " и нашёл ключ",
                        highlightPath(path),
                        state("search", key, "found", path));
                return true;
            }
            current = key < current.key ? current.left : current.right;
        }

        Trace.event("RBT_SEARCH",
                "Search for " + key + " followed " + path + " and reached a null child",
                "Поиск " + key + " прошёл по " + path + " и дошёл до null-потомка",
                highlightPath(path),
                state("search", key, "missing", path));
        return false;
    }

    public List<Integer> values() {
        List<Integer> values = new ArrayList<>();
        inorder(root, values);
        return values;
    }

    public int size() {
        return size;
    }

    private void fixAfterInsert(Node node) {
        while (node != root && colorOf(parentOf(node)) == Color.RED) {
            Node parent = parentOf(node);
            Node grand = parentOf(parent);
            if (grand == null) {
                break;
            }

            if (parent == grand.left) {
                Node uncle = grand.right;
                if (colorOf(uncle) == Color.RED) {
                    parent.color = Color.BLACK;
                    uncle.color = Color.BLACK;
                    grand.color = Color.RED;
                    Trace.event("RBT_RECOLOR",
                            "Parent " + parent.key + " and uncle " + uncle.key
                                    + " were red, so both became black and grandparent "
                                    + grand.key + " became red",
                            "Родитель " + parent.key + " и дядя " + uncle.key
                                    + " были красными, поэтому оба стали чёрными, а дед "
                                    + grand.key + " стал красным",
                            List.of("node:" + parent.key, "node:" + uncle.key, "node:" + grand.key),
                            state("recolor", node.key, "red uncle", List.of(grand.key, parent.key, node.key)));
                    node = grand;
                } else {
                    if (node == parent.right) {
                        node = parent;
                        rotateLeft(node);
                        parent = parentOf(node);
                        grand = parentOf(parent);
                    }
                    if (parent != null && grand != null) {
                        parent.color = Color.BLACK;
                        grand.color = Color.RED;
                        rotateRight(grand);
                    }
                }
            } else {
                Node uncle = grand.left;
                if (colorOf(uncle) == Color.RED) {
                    parent.color = Color.BLACK;
                    uncle.color = Color.BLACK;
                    grand.color = Color.RED;
                    Trace.event("RBT_RECOLOR",
                            "Parent " + parent.key + " and uncle " + uncle.key
                                    + " were red, so both became black and grandparent "
                                    + grand.key + " became red",
                            "Родитель " + parent.key + " и дядя " + uncle.key
                                    + " были красными, поэтому оба стали чёрными, а дед "
                                    + grand.key + " стал красным",
                            List.of("node:" + parent.key, "node:" + uncle.key, "node:" + grand.key),
                            state("recolor", node.key, "red uncle", List.of(grand.key, parent.key, node.key)));
                    node = grand;
                } else {
                    if (node == parent.left) {
                        node = parent;
                        rotateRight(node);
                        parent = parentOf(node);
                        grand = parentOf(parent);
                    }
                    if (parent != null && grand != null) {
                        parent.color = Color.BLACK;
                        grand.color = Color.RED;
                        rotateLeft(grand);
                    }
                }
            }
        }

        if (root != null && root.color == Color.RED) {
            root.color = Color.BLACK;
            Trace.event("RBT_ROOT_BLACK",
                    "The root must always be black, so " + root.key + " became black",
                    "Корень всегда должен быть чёрным, поэтому " + root.key + " стал чёрным",
                    List.of("node:" + root.key),
                    state("root-black", root.key, "root forced black", List.of(root.key)));
        }
    }

    private void rotateLeft(Node pivot) {
        Node child = pivot.right;
        if (child == null) {
            return;
        }

        pivot.right = child.left;
        if (child.left != null) {
            child.left.parent = pivot;
        }
        child.parent = pivot.parent;
        if (pivot.parent == null) {
            root = child;
        } else if (pivot == pivot.parent.left) {
            pivot.parent.left = child;
        } else {
            pivot.parent.right = child;
        }
        child.left = pivot;
        pivot.parent = child;

        Trace.event("RBT_ROTATE_LEFT",
                "Left rotation around " + pivot.key + " lifted " + child.key
                        + " and moved " + pivot.key + " to the left",
                "Левый поворот вокруг " + pivot.key + " поднял " + child.key
                        + " и переместил " + pivot.key + " влево",
                List.of("node:" + pivot.key, "node:" + child.key),
                state("rotate-left", child.key, "rotated left", List.of(child.key, pivot.key)));
    }

    private void rotateRight(Node pivot) {
        Node child = pivot.left;
        if (child == null) {
            return;
        }

        pivot.left = child.right;
        if (child.right != null) {
            child.right.parent = pivot;
        }
        child.parent = pivot.parent;
        if (pivot.parent == null) {
            root = child;
        } else if (pivot == pivot.parent.right) {
            pivot.parent.right = child;
        } else {
            pivot.parent.left = child;
        }
        child.right = pivot;
        pivot.parent = child;

        Trace.event("RBT_ROTATE_RIGHT",
                "Right rotation around " + pivot.key + " lifted " + child.key
                        + " and moved " + pivot.key + " to the right",
                "Правый поворот вокруг " + pivot.key + " поднял " + child.key
                        + " и переместил " + pivot.key + " вправо",
                List.of("node:" + pivot.key, "node:" + child.key),
                state("rotate-right", child.key, "rotated right", List.of(child.key, pivot.key)));
    }

    private Object state(String kind, Integer key, String result, List<Integer> path) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("size", size);
        s.put("height", height(root));
        s.put("blackHeight", root == null ? 0 : blackHeight(root));
        s.put("root", nodeState(root));

        List<Object> ordered = new ArrayList<>();
        inorderState(root, ordered);
        s.put("inOrder", ordered);

        Map<String, Object> lastOp = new LinkedHashMap<>();
        lastOp.put("kind", kind);
        lastOp.put("key", key);
        lastOp.put("result", result);
        lastOp.put("path", new ArrayList<>(path));
        s.put("lastOp", lastOp);
        return s;
    }

    private Object nodeState(Node node) {
        if (node == null) {
            return null;
        }
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", String.valueOf(node.key));
        n.put("key", node.key);
        n.put("color", node.color == Color.RED ? "R" : "B");
        n.put("left", nodeState(node.left));
        n.put("right", nodeState(node.right));
        return n;
    }

    private void inorderState(Node node, List<Object> ordered) {
        if (node == null) {
            return;
        }
        inorderState(node.left, ordered);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("index", ordered.size());
        item.put("key", node.key);
        item.put("color", node.color == Color.RED ? "R" : "B");
        ordered.add(item);
        inorderState(node.right, ordered);
    }

    private void inorder(Node node, List<Integer> values) {
        if (node == null) {
            return;
        }
        inorder(node.left, values);
        values.add(node.key);
        inorder(node.right, values);
    }

    private int height(Node node) {
        if (node == null) {
            return 0;
        }
        return 1 + Math.max(height(node.left), height(node.right));
    }

    private int blackHeight(Node node) {
        if (node == null) {
            return 1;
        }
        int childBlackHeight = blackHeight(node.left);
        return childBlackHeight + (node.color == Color.BLACK ? 1 : 0);
    }

    private List<String> highlightPath(List<Integer> path) {
        List<String> highlight = new ArrayList<>();
        for (Integer key : path) {
            highlight.add("path:" + key);
        }
        return highlight;
    }

    private Color colorOf(Node node) {
        return node == null ? Color.BLACK : node.color;
    }

    private Node parentOf(Node node) {
        return node == null ? null : node.parent;
    }

    private static final class Node {
        final int key;
        Color color;
        Node left;
        Node right;
        Node parent;

        Node(int key, Color color, Node parent) {
            this.key = key;
            this.color = color;
            this.parent = parent;
        }
    }
}
