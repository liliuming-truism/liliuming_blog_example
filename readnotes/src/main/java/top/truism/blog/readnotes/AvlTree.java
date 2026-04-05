package top.truism.blog.readnotes;

public class AvlTree<K extends Comparable<K>, V> {

    private static final class Node<K, V> {
        K key;
        V val;
        Node<K, V> left, right;
        int height; // 空节点高度记为 0；叶子高度为 1

        Node(K key, V val) {
            this.key = key;
            this.val = val;
            this.height = 1;
        }
    }

    private Node<K, V> root;

    // 对外接口
    public void put(K key, V val) {
        root = insert(root, key, val);
    }

    public void remove(K key) {
        root = delete(root, key);
    }

    public V get(K key) {
        Node<K, V> n = root;
        while (n != null) {
            int c = key.compareTo(n.key);
            if (c == 0)
                return n.val;
            n = (c < 0) ? n.left : n.right;
        }
        return null;
    }

    // ============ 基础工具 ============

    private int height(Node<K, V> n) {
        return n == null ? 0 : n.height;
    }

    private int bf(Node<K, V> n) {
        return (n == null) ? 0 : height(n.left) - height(n.right);
    }

    private void fixHeight(Node<K, V> n) {
        n.height = Math.max(height(n.left), height(n.right)) + 1;
    }

    // ============ 旋转 ============

    // 右旋（用于 LL 或 LR 的第二步）
    //    y                x
    //   / \              / \
    //  x   T3   --->    T1  y
    // / \                  / \
    //T1 T2                T2 T3
    private Node<K, V> rotateRight(Node<K, V> y) {
        Node<K, V> x = y.left;
        Node<K, V> T2 = x.right;

        x.right = y;
        y.left = T2;

        fixHeight(y);
        fixHeight(x);
        return x;
    }

    // 左旋（用于 RR 或 RL 的第二步）
    //  x                   y
    // / \                 / \
    //T1  y     --->      x  T3
    //   / \             / \
    //  T2 T3           T1 T2
    private Node<K, V> rotateLeft(Node<K, V> x) {
        Node<K, V> y = x.right;
        Node<K, V> T2 = y.left;

        y.left = x;
        x.right = T2;

        fixHeight(x);
        fixHeight(y);
        return y;
    }

    // 针对当前节点 n 做再平衡，返回新的子树根
    private Node<K, V> rebalance(Node<K, V> n) {
        fixHeight(n);
        int balance = bf(n);

        // LL 或 LR
        if (balance > 1) {
            if (bf(n.left) < 0) { // LR
                n.left = rotateLeft(n.left);
            }
            return rotateRight(n); // LL 或 LR 第二步
        }

        // RR 或 RL
        if (balance < -1) {
            if (bf(n.right) > 0) { // RL
                n.right = rotateRight(n.right);
            }
            return rotateLeft(n); // RR 或 RL 第二步
        }
        return n; // 已平衡
    }

    // ============ 插入并再平衡 ============

    private Node<K, V> insert(Node<K, V> node, K key, V val) {
        if (node == null)
            return new Node<>(key, val);

        int c = key.compareTo(node.key);
        if (c < 0)
            node.left = insert(node.left, key, val);
        else if (c > 0)
            node.right = insert(node.right, key, val);
        else { // key 已存在：更新值（也可选择抛异常）
            node.val = val;
            return node;
        }

        // 自底向上修复高度并再平衡
        return rebalance(node);
    }

    // ============ 删除并再平衡 ============

    private Node<K, V> minNode(Node<K, V> n) {
        while (n.left != null)
            n = n.left;
        return n;
    }

    private Node<K, V> delete(Node<K, V> node, K key) {
        if (node == null)
            return null;

        int c = key.compareTo(node.key);
        if (c < 0) {
            node.left = delete(node.left, key);
        } else if (c > 0) {
            node.right = delete(node.right, key);
        } else {
            // 命中：三种情况
            if (node.left == null || node.right == null) {
                node = (node.left != null) ? node.left : node.right; // 含 0 或 1 个子节点
            } else {
                // 两个子节点：用右子树最小节点替换，再删除那个最小节点
                Node<K, V> succ = minNode(node.right);
                node.key = succ.key;
                node.val = succ.val;
                node.right = delete(node.right, succ.key);
            }
        }

        if (node == null)
            return null; // 删除后子树为空

        // 自底向上再平衡（删除场景尤其需要）
        return rebalance(node);
    }

    // ============ 校验工具 ============

    public boolean isBalanced() {
        return isBalanced(root).balanced;
    }

    private static final class Check {
        final boolean balanced;
        final int height;

        Check(boolean b, int h) {
            balanced = b;
            height = h;
        }
    }

    private Check isBalanced(Node<?, ?> n) {
        if (n == null)
            return new Check(true, 0);
        Check L = isBalanced(n.left);
        Check R = isBalanced(n.right);
        boolean ok = L.balanced && R.balanced && Math.abs(L.height - R.height) <= 1;
        return new Check(ok, Math.max(L.height, R.height) + 1);
    }

    // 简单示例
    public static void main(String[] args) {
        AvlTree<Integer, String> avl = new AvlTree<>();
        int[] keys = {10, 20, 30, 40, 50, 25};
        for (int k : keys)
            avl.put(k, "v" + k);

        System.out.println("Balanced after inserts: " + avl.isBalanced()); // true

        avl.remove(50);
        avl.remove(40);
        System.out.println("Balanced after deletes: " + avl.isBalanced()); // true

        System.out.println("Get 25 = " + avl.get(25));
    }
}

