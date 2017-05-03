package dare2095;

import java.awt.Color;
import java.util.Vector;

import spacesettlers.graphics.CircleGraphics;
import spacesettlers.graphics.SpacewarGraphics;
import spacesettlers.utilities.Position;

public class Vertex {
	private Position location;
	private CircleGraphics graphic;
	private boolean isStart;
	private boolean isGoal;
	private boolean isSolution;
	private double heuristicCostToGoal, pathCost, F;
	private boolean expanded;
	private Vector <Edge> edges;
	
	public Vertex(Position location) {
		this.location = location;
		graphic = new CircleGraphics(2, Color.WHITE, location);
		expanded = false;
		isGoal = false;
		isStart = false;
		isSolution = false;
		edges = new Vector<Edge>();
	}
	
	public boolean isConnectedTo(Vertex v) {
		for (Edge edge : getEdges()) {
			if (edge.getVertex1() == v) {
				return true;
			}
			if (edge.getVertex2() == v) {
				return true;
			}
		} 
		
		return false;
	}
	
	public SpacewarGraphics getGraphic() {
		return graphic;
	}
	
	public Position getPosition() {
		return location;
	}
	
	public void setStart() {
		isStart = true;
		graphic.setColor(Color.YELLOW);
	}
	
	public void setGoal() {
		isGoal = true;
		graphic.setColor(Color.BLUE);
	}
	
	public void setSolution() {
		isSolution = true;
		graphic.setColor(Color.YELLOW);
	}

	public boolean isSolution() {
		return isSolution;
	}

	public boolean isGoal() {
		return isGoal;
	}

	public boolean isStart() {
		return isStart;
	}

	public boolean isExpanded() {
		return expanded;
	}

	public void setExpanded(boolean expanded) {
		this.expanded = expanded;
	}

	public Edge[] getEdges() {
		return (Edge[]) edges.toArray(new Edge[edges.size()]);
	}
	
	public void addEdge(Edge edge) {
		edges.add(edge);
	}

	public double getHeuristicCostToGoal() {
		return heuristicCostToGoal;
	}

	public void setHeuristicCostToGoal(double heuristicCostToGoal) {
		this.heuristicCostToGoal = heuristicCostToGoal;
	}

	public double getPathCost() {
		return pathCost;
	}

	public void setPathCost(double pathCost) {
		this.pathCost = pathCost;
	}
	
	public void updateF() {
		F = pathCost + heuristicCostToGoal;
	}
	
	public double getF() {
		return F;
	}
}
