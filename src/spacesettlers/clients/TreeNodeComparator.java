package spacesettlers.clients;

import java.util.Comparator;
import javax.swing.tree.DefaultMutableTreeNode;


public class TreeNodeComparator implements Comparator {
    public int compare(Object o1, Object o2) {
        SearchNode node1 = (SearchNode) ((DefaultMutableTreeNode) o1).getUserObject();
        SearchNode node2 = (SearchNode) ((DefaultMutableTreeNode) o2).getUserObject();
        Vertex v1 = node1.getVertex();
        Vertex v2 = node2.getVertex();

        if (v1.getF() < v2.getF()) {
            return -1;
        } else if (v1.getF() == v2.getF()) {
            return 0;
        } else {
            return 1;
        }
    }
}