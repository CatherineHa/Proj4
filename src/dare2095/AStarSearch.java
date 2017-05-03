package dare2095;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Asteroid;
import spacesettlers.objects.Base;
import spacesettlers.objects.Beacon;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

/**
 * AStar search (outside of an agent) from a start to a goal
 * 
 * @author amy
 *
 */
public class AStarSearch  {

	/**
	 * Fudge factor keeps you from going too close to obstacles
	 */
	private static final int fudge_factor = 10;
	private static final int numGraphNodes = 200;
	private static final double maxNodeDistance = 200;
	

	/**
	 * Ensures that the specified point is not inside an obstacle
	 * 
	 * @param x
	 *                x-location
	 * @param y
	 *                y-location
	 * @param state
	 *                state object used to find the other objects
	 * @return true if it is in free space and false otherwise
	 */
	public static boolean inFreeSpace(Position pos, Toroidal2DPhysics state) {
		// loop through the obstacles
		for (Asteroid asteroid : state.getAsteroids()) {
			double dist = state.findShortestDistance(pos, asteroid.getPosition());

			if (dist < (asteroid.getRadius() + fudge_factor)) {
				return false;
			}
		}

		return true;
	}
	
	/**
	 * Is the space free from position1 to position2 alone a line?
	 * @param position1
	 * @param position2
	 * @param state
	 * @return
	 */

	public static boolean isFreeLine(Position position1, Position position2, Toroidal2DPhysics state) {
		// get a vector from location 1 to location 2
		Vector2D line = state.findShortestDistanceVector(position1, position2);

		for (Asteroid asteroid : state.getAsteroids()) {
			// now find the distance to the obstacle (vector pointing from location 1 to the obstacle)
			Vector2D obstacleVec = state.findShortestDistanceVector(position1, asteroid.getPosition());

			// project the obstacle down to the line
			Vector2D projectedLoc = line.vectorProject(obstacleVec);

			double innerProduct = projectedLoc.dot(line);
			if (innerProduct >= 0) {
				if (Math.abs(projectedLoc.getXValue()) <= Math.abs(line.getXValue()) &&
						Math.abs(projectedLoc.getYValue()) <= Math.abs(line.getYValue())) {
					// now find the vector from the obstacle to the line
					Vector2D worldCoordsProjection = projectedLoc.add(new Vector2D(position1));
					double obstacleToLineDist = state.findShortestDistance(new Position(worldCoordsProjection), asteroid.getPosition());

					// if it the distance is less than the obstacle radius, they intersect
					if (obstacleToLineDist < (asteroid.getRadius() + fudge_factor)) {
						return false;
					}
				}
			}
		}
		return true;
	}


	/**
	 * creates a graph of random locations plus the starting position and each of the beacons
	 * 
	 */
	public static Graph createGraphToGoalWithBeacons(Toroidal2DPhysics state, Ship myShip, 
			Position goalPosition, Random random) {
		// where am I?
		Position startPos = myShip.getPosition();
		
		// make the graph with random nodes across the screen plus a goal node at each beacon
		Graph graph = new Graph();
		
		// add the start
		Vertex startVertex = new Vertex(startPos);
		startVertex.setStart();
		graph.addVertex(startVertex);
		
		// add the beacons
		Set<Beacon> beacons = state.getBeacons();
		for (Beacon beacon : beacons) {
			Vertex vertex = new Vertex(beacon.getPosition());
			//vertex.setGoal();
			graph.addVertex(vertex);
		}
		
		// add the goal
		Vertex goal = new Vertex(goalPosition);
		goal.setGoal();
		graph.addVertex(goal);

		Position position;
		// add a random set of vertices that are not inside obstacles
		for (int v = 0; v < numGraphNodes; v++) {
			double newX = random.nextFloat() * state.getWidth();
			double newY = random.nextFloat() * state.getHeight();
			position = new Position(newX, newY);
			
			if (inFreeSpace(position, state)) {
				graph.addVertex(new Vertex(position));
			}
		}
		
		// now connect all vertices that are within a specified radius and don't go through an obstacle
		Set<AbstractObject> obstaclesForGraph = new HashSet<AbstractObject>();

		// don't add an asteroid if it is the goal!
		for (Asteroid asteroid : state.getAsteroids()) {
			if (!asteroid.getPosition().equals(goalPosition)) {
				obstaclesForGraph.add(asteroid);
			}
		}
		
		// avoid the other team's bases
		for (Base base : state.getBases()) {
			if (!base.getTeamName().equalsIgnoreCase(myShip.getTeamName())) {
				obstaclesForGraph.add(base);
			}
		}
		
		for (Vertex vertex1 : graph.getVertices()) {
			for (Vertex vertex2 : graph.getVertices()) {
				double distance = state.findShortestDistance(vertex1.getPosition(), vertex2.getPosition());
				if (distance > 0 && distance < maxNodeDistance && 
						state.isPathClearOfObstructions(vertex1.getPosition(), vertex2.getPosition(), 
								obstaclesForGraph, fudge_factor)) {
					Vector2D lineVec = state.findShortestDistanceVector(vertex1.getPosition(), vertex2.getPosition());
					Edge edge = new Edge(vertex1, vertex2, lineVec);
					vertex1.addEdge(edge);
					vertex2.addEdge(edge);
					graph.addEdge(edge);
				}
			}
		}
		
		return graph;
	}

}
