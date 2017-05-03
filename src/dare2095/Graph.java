package dare2095;

import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.Vector;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;

import spacesettlers.graphics.SpacewarGraphics;
import spacesettlers.simulator.Toroidal2DPhysics;

public class Graph {
	private Vector <Vertex> vertices;
	private Vector <Edge> edges;
	private Vector <Vertex>goals;
	private Vertex start;
	private static final int maxSearchSteps = 100;

	public Graph() {
		vertices = new Vector<Vertex>();
		edges = new Vector<Edge>();
		goals = new Vector<Vertex>();
	}

	public void reset() {
		vertices.clear();
		edges.clear();
		goals.clear();
	}

	/**
	 * Adds the vertex to the current list.  If it is a goal vertex, 
	 * adds it to the goal list as well.
	 * @param v
	 */
	public void addVertex(Vertex v) {
		vertices.add(v);

		if (v.isGoal()) {
			goals.add(v);
		}

		if (v.isStart()) {
			start = v;
		}
	}

	public void addEdge(Edge e) {
		edges.add(e);
	}

	public Vector<Vertex> getVertices() {
		return vertices;
	}

	/**
	 * Get the graphics objects for the whole graph (with solution colored)
	 * @return
	 */
	public Set<SpacewarGraphics> getAllGraphics() {
		Set<SpacewarGraphics> shadows = new HashSet<SpacewarGraphics>();

		for (int v = 0; v < vertices.size(); v++) {
			SpacewarGraphics shadow = ((Vertex)vertices.get(v)).getGraphic();
			shadows.add(shadow);
		}

		for (int e = 0; e < edges.size(); e++) {
			SpacewarGraphics shadow = ((Edge)edges.get(e)).getGraphic();
			shadows.add(shadow);
		}

		return shadows;
	}

	/**
	 * Only return the graphics objects for the solution path
	 * @return
	 */
	public Set<SpacewarGraphics> getSolutionPathGraphics() {
		Set<SpacewarGraphics> graphics = new HashSet<SpacewarGraphics>();

		for (Vertex vertex : vertices) {
			if (vertex.isSolution()) {
				SpacewarGraphics graphic = vertex.getGraphic();
				graphics.add(graphic);
			}
		}

		for (Edge edge : edges) {
			if (edge.isSolution) {
				SpacewarGraphics graphic = edge.getGraphic();
				graphics.add(graphic);
			}

		}

		return graphics;
	}

	
	private void resetGraphForSearch() {
		for (int v = 0; v < vertices.size(); v++) {
			vertices.get(v).setExpanded(false);
			vertices.get(v).setPathCost(0);
		}
	}

	/**
	 * find an optimal path from the start to the goal using astar
	 * @return
	 */
	public Vertex[] findAStarPath(Toroidal2DPhysics state) {
		double minDist, dist;

		// initialize the graph
		resetGraphForSearch();

		// setup the H distances.  G is already stored in the edges
		for (int v = 0; v < vertices.size(); v++) {
			minDist = Double.MAX_VALUE;
			Vertex vertex = vertices.get(v);
			for (int g = 0; g < goals.size(); g++) {
				dist = state.findShortestDistance(vertex.getPosition(), goals.get(g).getPosition());
				if (dist < minDist) {
					minDist = dist;
				}
			}
			vertex.setHeuristicCostToGoal(minDist);
		}

		// now search for a goal using AStar.  The priority queue uses the search tree nodes
		// because of Astar's ability to jump around in the search.  This is a clean way to
		// both know where you are in the search tree and to know what the next node's priority is
		PriorityQueue <DefaultMutableTreeNode>queue = 
			new PriorityQueue<DefaultMutableTreeNode>(edges.size(), new TreeNodeComparator());

		// start the search with the children of the start node.  
		DefaultMutableTreeNode searchTree = new DefaultMutableTreeNode(new SearchNode(start, null));
		start.setExpanded(true);
		visitSuccessors(queue, searchTree, start);

		// initilize the loop
		boolean goalFound = false;
		int steps = 0;
		DefaultMutableTreeNode goalNode = null;

		// search until we find a goal node, reach a maximum number of steps, or run out of 
		// nodes to search
		while (!goalFound && steps < maxSearchSteps && !queue.isEmpty()) {
			// get the next node from the queue
			DefaultMutableTreeNode currentNode = queue.poll();

			// get the vertex object
			SearchNode node = (SearchNode) currentNode.getUserObject();
			Vertex nextVertex = node.getVertex();
			while (nextVertex.isExpanded()) {
				if (queue.isEmpty()) {
					// we ran out of objects before finding the goal, return failure
					return null;
				}
				
				currentNode = queue.poll();

				// get the vertex object
				node = (SearchNode) currentNode.getUserObject();
				nextVertex = node.getVertex();
			}

			// if it is a goal, quit
			if (nextVertex.isGoal()) {
				goalFound = true;
				goalNode = currentNode;
				break;
			} 

			// otherwise, mark it as expanded and visit it's successors
			nextVertex.setExpanded(true);
			visitSuccessors(queue, currentNode, nextVertex);

			steps++;
		}

		// find the solution path for the agent to follow
		// and mark it as a solution (so it changes color)
		if (goalFound) {
			TreeNode[] path = goalNode.getPath();
			Vertex[] solutionPath = new Vertex[path.length];
			
			//System.out.println("Path is length " + path.length);

			for (int t = 0; t < path.length; t++) {
				TreeNode node = path[t];
				
				SearchNode searchNode = (SearchNode) ((DefaultMutableTreeNode) node).getUserObject();
				searchNode.getVertex().setSolution();
				solutionPath[t] = searchNode.getVertex();

				if (searchNode.getEdge() != null) {
					searchNode.getEdge().setSolution();
				} 
			}
			return solutionPath;

		} else {
			return null;
		}
	}

	/**
	 * Visits all successors of the listed vertex, adds all non-expanded ones 
	 * to the queue and saves them in the search tree
	 */
	private void visitSuccessors(PriorityQueue<DefaultMutableTreeNode> queue, 
			DefaultMutableTreeNode tree, Vertex vertex) {
		for (Edge edge : vertex.getEdges()) {
			Vertex child = edge.getVertex1();
			if (!child.isExpanded()) {
				child.setPathCost(vertex.getPathCost() + edge.getPathCost());
				child.updateF();

				DefaultMutableTreeNode node = new DefaultMutableTreeNode(new SearchNode(child, edge));
				tree.add(node);
				queue.add(node);
			}

			// edges are bi-directional
			child = edge.getVertex2();
			if (!child.isExpanded()) {
				child.setPathCost(vertex.getPathCost() + edge.getPathCost());
				child.updateF();

				DefaultMutableTreeNode node = new DefaultMutableTreeNode(new SearchNode(child, edge));
				tree.add(node);
				queue.add(node);
			}
		}
	}

	public Vertex getStart() {
		return start;
	}

}
