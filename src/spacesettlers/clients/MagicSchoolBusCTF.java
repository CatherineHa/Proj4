package spacesettlers.clients;

import java.awt.Color;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.XStreamException;

import spacesettlers.actions.AbstractAction;
import spacesettlers.actions.DoNothingAction;
import spacesettlers.actions.MoveToObjectAction;
import spacesettlers.actions.PurchaseCosts;
import spacesettlers.actions.PurchaseTypes;
import spacesettlers.graphics.SpacewarGraphics;
import spacesettlers.graphics.StarGraphics;
import spacesettlers.objects.AbstractActionableObject;
import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Asteroid;
import spacesettlers.objects.Base;
import spacesettlers.objects.Beacon;
import spacesettlers.objects.Flag;
import spacesettlers.objects.Ship;
import spacesettlers.objects.powerups.SpaceSettlersPowerupEnum;
import spacesettlers.objects.resources.ResourcePile;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;

/**
 * A pacifist flag collector client that handles multiple agents in the team.  The heuristic works as follows:
 * 
 *   The nearest and healthy ship is assigned to go get the flag and bring it back.
 *   The other ships are assigned to resource collection.
 *   Resources are used to buy additional ships and bases (with the idea that bases are better to have near the 
 *   enemy flag locations).
 * 
 * (similar to PassiveHeuritsicAsteroidCollector).  
 *  
 * @author amy
 */
public class MagicSchoolBusCTF extends TeamClient {
	HashMap <UUID, Ship> asteroidToShipMap;
	HashMap <UUID, Boolean> aimingForBase;
	FollowPathAction followPathAction;
	HashMap <UUID, Graph> graphByShip;
	
	HashSet<Ship> flagCollectors;
	HashSet<Ship> resourceCollectors;
	
	//for how often you want to replan
	int updateInterval = 50;
	
	//indicates the current phase in the master plan
	int phase = 0;
	public static final int BUILDING_PHASE = 0;
	public static final int AGGRESSIVE_PHASE = 0;
	public static final int FINAL_JUSTICE_PHASE = 0;
	
	
	/**
	 * Assigns ships to asteroids and beacons, as described above
	 */
	public Map<UUID, AbstractAction> getMovementStart(Toroidal2DPhysics space,
			Set<AbstractActionableObject> actionableObjects) {
		HashMap<UUID, AbstractAction> actions = new HashMap<UUID, AbstractAction>();
		Ship flagShip;

		// loop through each ship and assign it to either get energy (if needed for health) or
		// resources (as long as it isn't the flagShip)
		for (AbstractObject actionable :  actionableObjects) {
			if (actionable instanceof Ship) {
				Ship ship = (Ship) actionable;
				
				AbstractAction action = new DoNothingAction();
				AbstractAction current = ship.getCurrentAction();
				
				//Phase 1: building phase, focus on buidling bases and ships
				if(phase >= 0) {
					//assign roles to the ships
					if(resourceCollectors.size() <2){
						resourceCollectors.add(ship);
					}else if(!flagCollectors.contains(ship) && !resourceCollectors.contains(ship) ){
						flagCollectors.add(ship);
					}
					
					
					if (flagCollectors.contains(ship)) {
						action = getFlagCollectorAction(space, actionableObjects, ship);
						
					} else {
						if (current == null || space.getCurrentTimestep() % updateInterval == 0) {
							action =  getAsteroidCollectorAction(space, ship);
						}else{
							action = current;
						}
//						action = getAsteroidCollectorAction(space, ship);
					}

					// save the action for this ship
					actions.put(ship.getId(), action);
				}else if(phase == AGGRESSIVE_PHASE){
					//todo
				}else if(phase == FINAL_JUSTICE_PHASE){
					//todo
				}
				
				
			} else {
				// bases do nothing
				actions.put(actionable.getId(), new DoNothingAction());
			}
		} 
		return actions;
	}

	/**
	 * Get the flag carrier (if there is one).  Return null if there isn't a current flag carrier
	 * 
	 * @param space
	 * @param actionableObjects
	 * @return
	 */
	private Ship getFlagCarrier(Toroidal2DPhysics space,
			Set<AbstractActionableObject> actionableObjects) {
		for (AbstractObject actionable :  actionableObjects) {
			if (actionable instanceof Ship) {
				Ship ship = (Ship) actionable;
				
				if (ship.isCarryingFlag()) {
					return ship;
				}
			}
		}
		return null;
	}
	
	/**
	 * Finds and returns the enemy flag
	 * @param space
	 * @return
	 */
	private Flag getEnemyFlag(Toroidal2DPhysics space) {
		Flag enemyFlag = null;
		for (Flag flag : space.getFlags()) {
			if (flag.getTeamName().equalsIgnoreCase(getTeamName())) {
				continue;
			} else {
				enemyFlag = flag;
			}
		}
		return enemyFlag;
	}
	
	
	/**
	 * Finds the ship with the highest health and nearest the flag
	 * 
	 * @param space
	 * @param actionableObjects
	 * @return
	 */
	private Ship findHealthiestShipNearFlag(Toroidal2DPhysics space,
			Set<AbstractActionableObject> actionableObjects) {
		double minDistance = Double.MAX_VALUE;
		double maxHealth = Double.MIN_VALUE;
		int minHealth = 2000;
		Ship bestShip = null;

		// first find the enemy flag
		Flag enemyFlag = getEnemyFlag(space);

		// now find the healthiest ship that has at least the required minimum energy 
		// if no ships meet that criteria, return null
		for (AbstractObject actionable :  actionableObjects) {
			if (actionable instanceof Ship) {
				Ship ship = (Ship) actionable;
				
				double dist = space.findShortestDistance(ship.getPosition(), enemyFlag.getPosition());
				if (dist < minDistance && ship.getEnergy() > minHealth) {
					if (ship.getEnergy() > maxHealth) {
						minDistance = dist;
						maxHealth = ship.getEnergy();
						bestShip = ship;
					}
				}
			}
		}
		
		return bestShip;
		
	}
	
	
	/**
	 * Gets the action for the asteroid collecting ship
	 * @param space
	 * @param ship
	 * @return
	 */
	private AbstractAction getAsteroidCollectorAction(Toroidal2DPhysics space,
			Ship ship) {
		AbstractAction current = ship.getCurrentAction();
		Position currentPosition = ship.getPosition();

		// aim for a beacon if there isn't enough energy
		if (ship.getEnergy() < 2000) {
			Beacon beacon = pickNearestBeacon(space, ship);
			AbstractAction newAction = null;
			// if there is no beacon, then just skip a turn
			if (beacon == null) {
				newAction = new DoNothingAction();
			} else {
				newAction = getAStarPathToGoal(space, ship, beacon.getPosition());
			}
			aimingForBase.put(ship.getId(), false);
			return newAction;
		}

		// if the ship has enough resourcesAvailable, take it back to base
		if (ship.getResources().getTotal() > 500) {
			Base base = findNearestBase(space, ship);
			AbstractAction newAction = getAStarPathToGoal(space, ship, base.getPosition());
			aimingForBase.put(ship.getId(), true);
			return newAction;
		}

		// did we bounce off the base?
		if (ship.getResources().getTotal() == 0 && ship.getEnergy() > 2000 && aimingForBase.containsKey(ship.getId()) && aimingForBase.get(ship.getId())) {
			current = null;
			aimingForBase.put(ship.getId(), false);
		}

		// otherwise aim for the asteroid
		if (current == null || current.isMovementFinished(space)) {
			aimingForBase.put(ship.getId(), false);
			Asteroid asteroid = pickHighestValueNearestFreeAsteroid(space, ship);

			AbstractAction newAction = null;

			if (asteroid != null) {
				asteroidToShipMap.put(asteroid.getId(), ship);
//				newAction = new MoveToObjectAction(space, currentPosition, asteroid, 
//						asteroid.getPosition().getTranslationalVelocity());
				newAction = getAStarPathToGoal(space, ship, asteroid.getPosition()) ;
			}
			
			return newAction;
		} 
		
		return ship.getCurrentAction();
	}


	/**
	 * Find the base for this team nearest to this ship
	 * 
	 * @param space
	 * @param ship
	 * @return
	 */
	private Base findNearestBase(Toroidal2DPhysics space, Ship ship) {
		double minDistance = Double.MAX_VALUE;
		Base nearestBase = null;

		for (Base base : space.getBases()) {
			if (base.getTeamName().equalsIgnoreCase(ship.getTeamName())) {
				double dist = space.findShortestDistance(ship.getPosition(), base.getPosition());
				if (dist < minDistance) {
					minDistance = dist;
					nearestBase = base;
				}
			}
		}
		return nearestBase;
	}

	/**
	 * Returns the asteroid of highest value that isn't already being chased by this team
	 * 
	 * @return
	 */
	private Asteroid pickHighestValueNearestFreeAsteroid(Toroidal2DPhysics space, Ship ship) {
		Set<Asteroid> asteroids = space.getAsteroids();
		int bestMoney = Integer.MIN_VALUE;
		Asteroid bestAsteroid = null;
		double minDistance = Double.MAX_VALUE;

		for (Asteroid asteroid : asteroids) {
			if (!asteroidToShipMap.containsKey(asteroid.getId())) {
				if (asteroid.isMineable() && asteroid.getResources().getTotal() > bestMoney) {
					double dist = space.findShortestDistance(asteroid.getPosition(), ship.getPosition());
					if (dist < minDistance) {
						bestMoney = asteroid.getResources().getTotal();
						//System.out.println("Considering asteroid " + asteroid.getId() + " as a best one");
						bestAsteroid = asteroid;
						minDistance = dist;
					}
				}
			}
		}
		//System.out.println("Best asteroid has " + bestMoney);
		return bestAsteroid;
	}


	/**
	 * Find the nearest beacon to this ship
	 * @param space
	 * @param ship
	 * @return
	 */
	private Beacon pickNearestBeacon(Toroidal2DPhysics space, Ship ship) {
		// get the current beacons
		Set<Beacon> beacons = space.getBeacons();

		Beacon closestBeacon = null;
		double bestDistance = Double.POSITIVE_INFINITY;

		for (Beacon beacon : beacons) {
			double dist = space.findShortestDistance(ship.getPosition(), beacon.getPosition());
			if (dist < bestDistance) {
				bestDistance = dist;
				closestBeacon = beacon;
			}
		}

		return closestBeacon;
	}



	@Override
	public void getMovementEnd(Toroidal2DPhysics space, Set<AbstractActionableObject> actionableObjects) {
		ArrayList<Asteroid> finishedAsteroids = new ArrayList<Asteroid>();

		for (UUID asteroidId : asteroidToShipMap.keySet()) {
			Asteroid asteroid = (Asteroid) space.getObjectById(asteroidId);
			if (asteroid == null || !asteroid.isAlive() || asteroid.isMoveable()) {
 				finishedAsteroids.add(asteroid);
				//System.out.println("Removing asteroid from map");
			}
		}

		for (Asteroid asteroid : finishedAsteroids) {
			asteroidToShipMap.remove(asteroid.getId());
		}


	}

	/**
	 * Demonstrates one way to read in knowledge from a file
	 */
	@Override
	public void initialize(Toroidal2DPhysics space) {
		asteroidToShipMap = new HashMap<UUID, Ship>();
		aimingForBase = new HashMap<UUID, Boolean>();
		graphByShip = new HashMap<UUID, Graph>();
		flagCollectors = new HashSet<Ship>();
		resourceCollectors = new HashSet<Ship>();
		
		//set first phase to building phase
		phase = BUILDING_PHASE;
		
	}

	/**
	 * Demonstrates saving out to the xstream file
	 * You can save out other ways too.  This is a human-readable way to examine
	 * the knowledge you have learned.
	 */
	@Override
	public void shutDown(Toroidal2DPhysics space) {
	}

	@Override
	public Set<SpacewarGraphics> getGraphics() {
		HashSet<SpacewarGraphics> graphics = new HashSet<SpacewarGraphics>();
		if (graphByShip != null) {
			for (Graph graph : graphByShip.values()) {
				// uncomment to see the full graph
				//graphics.addAll(graph.getAllGraphics());
				graphics.addAll(graph.getSolutionPathGraphics());
			}
		}
		
		Position points[] = new Position[30];
		
		//edges
		points[0] = new Position(1400, 100);
		points[1] = new Position(1400, 425);
		points[2] = new Position(1400, 650);
		points[3] = new Position(1400, 950);
		points[4] = new Position(200, 100);
		points[5] = new Position(200, 425);
		points[6] = new Position(200, 650);
		points[7] = new Position(200, 950);
		
		//mid 
		points[8] = new Position(800, 150);
		points[9] = new Position(800, 950);
		
		
		//bunkers
		points[10] = new Position(1360, 800);
		points[11] = new Position(1360, 250);
		points[12] = new Position(1000, 800);
		points[13] = new Position(1000, 250);
		points[14] = new Position(240, 800);
		points[15] = new Position(240, 250);
		points[16] = new Position(600, 800);
		points[17] = new Position(600, 250);
		
		//equator
		points[18] = new Position(1200, 550);
		points[19] = new Position(400, 550);
		
		
		for(Position p : points){
			if(p != null){
				graphics.add(new StarGraphics(3, super.teamColor, p));	
			}
			
		}
		
		
		HashSet<SpacewarGraphics> newGraphicsClone = (HashSet<SpacewarGraphics>) graphics.clone();
		graphics.clear();
		return newGraphicsClone;
	}

	@Override
	/**
	 * If there is enough resourcesAvailable, buy a base.  Place it by finding a ship that is sufficiently
	 * far away from the existing bases
	 */
	public Map<UUID, PurchaseTypes> getTeamPurchases(Toroidal2DPhysics space,
			Set<AbstractActionableObject> actionableObjects, 
			ResourcePile resourcesAvailable, 
			PurchaseCosts purchaseCosts) {

		HashMap<UUID, PurchaseTypes> purchases = new HashMap<UUID, PurchaseTypes>();
		double BASE_BUYING_DISTANCE = 200;
		boolean bought_base = false;
		int numBases, numShips;

		// count the number of ships for the base/ship buying algorithm
		numShips = 0;
		for (AbstractActionableObject actionableObject : actionableObjects) {
			if (actionableObject instanceof Ship) {
				numShips++;
			}
		}
		
		// now see if we can afford a base or a ship.  We want a base but we also really want a 3rd ship
		// try to balance
		if (purchaseCosts.canAfford(PurchaseTypes.BASE, resourcesAvailable)) {
			for (AbstractActionableObject actionableObject : actionableObjects) {
				if (actionableObject instanceof Ship) {
					Ship ship = (Ship) actionableObject;
					Set<Base> bases = space.getBases();

					// how far away is this ship to a base of my team?
					boolean buyBase = true;
					numBases = 0;
					for (Base base : bases) {
						if (base.getTeamName().equalsIgnoreCase(getTeamName())) {
							numBases++;
							double distance = space.findShortestDistance(ship.getPosition(), base.getPosition());
							if (distance < BASE_BUYING_DISTANCE) {
								buyBase = false;
							}
						}
					}
					if (buyBase && numBases < numShips) {
						purchases.put(ship.getId(), PurchaseTypes.BASE);
						bought_base = true;
						System.out.println("Magic School Bus is buying a base!");
						break;
					}
				}
			}		
		} 
		
		// can I buy a ship?
		if (purchaseCosts.canAfford(PurchaseTypes.SHIP, resourcesAvailable) && bought_base == false) {
			for (AbstractActionableObject actionableObject : actionableObjects) {
				if (actionableObject instanceof Base) {
					Base base = (Base) actionableObject;
					
					purchases.put(base.getId(), PurchaseTypes.SHIP);
					System.out.println("Magic School Bus is buying a ship!");
					break;
				}

			}

		}


		return purchases;
	}

	/**
	 * The pacifist flag collector doesn't use power ups 
	 * @param space
	 * @param actionableObjects
	 * @return
	 */
	@Override
	public Map<UUID, SpaceSettlersPowerupEnum> getPowerups(Toroidal2DPhysics space,
			Set<AbstractActionableObject> actionableObjects) {
		HashMap<UUID, SpaceSettlersPowerupEnum> powerUps = new HashMap<UUID, SpaceSettlersPowerupEnum>();

		
		return powerUps;
	}
	
	/**
	 * Follow an aStar path to the goal
	 * @param space
	 * @param ship
	 * @param goalPosition
	 * @return
	 */
	private AbstractAction getAStarPathToGoal(Toroidal2DPhysics space, Ship ship, Position goalPosition) {
		AbstractAction newAction;
		
		Graph graph = AStarSearch.createGraphToGoalWithBeacons(space, ship, goalPosition, new Random());
		Vertex[] path = graph.findAStarPath(space);
		followPathAction = new FollowPathAction(path);
		//followPathAction.followNewPath(path);
		newAction = followPathAction.followPath(space, ship);
		graphByShip.put(ship.getId(), graph);
		return newAction;
	}

	/*
	 * This method encapsulates the descion making process for a flag collector and makes sure
	 * it follows the specified plan
	 */
	private AbstractAction getFlagCollectorAction(Toroidal2DPhysics space, Set<AbstractActionableObject> actionableObjects, Ship ship){
		
		AbstractAction action;
		AbstractAction current = ship.getCurrentAction();
		
		if (ship.isCarryingFlag()) { //if ship is carrying flag, return to nearest base
			
			//A*
			//check if current action is null
			
			if (current == null || space.getCurrentTimestep() % updateInterval == 0) {
				//System.out.println("We have a flag carrier!");
				Base base = findNearestBase(space, ship);
				//System.out.println("Flag ship before computing action: " + flagShip);
				action = getAStarPathToGoal(space, ship, base.getPosition());
				//System.out.println("Aiming for base with action " + action);
				aimingForBase.put(ship.getId(), true);
				//System.out.println("Flag ship after computing action: " + flagShip);
				return action;
			}
						
			
		} else { //if you dont currently have the flag, either go after the flag or wait for the next one, or gte energy
			
			
			if(ship.getEnergy() < 2000) { //low on energy, get a beacon
				Beacon beacon = pickNearestBeacon(space, ship);
				// if there is no beacon, then just skip a turn
				if (beacon == null) {
					action = new DoNothingAction();
					return action;
				} else if(current == null || space.getCurrentTimestep() % updateInterval == 0) {
					action = getAStarPathToGoal(space, ship, beacon.getPosition());
					return action;
				}
				aimingForBase.put(ship.getId(), false);

			}else if(getFlagCarrier(space, actionableObjects) != null && phase == BUILDING_PHASE){ //enemy flag is already grabbed by another ship, wait
				Position holdingSpot = new Position(400, 550);
				
				if(space.findShortestDistance(ship.getPosition(), holdingSpot) < 40){ //if in a holding position
					action = new DoNothingAction();
					return action;
				}
				else if(current == null || space.getCurrentTimestep() % updateInterval == 0){ //if not at a holding position, move there
					action = getAStarPathToGoal(space, ship, holdingSpot);
					return action;
				}
			}else{// enemy flag available, go get it
//				action = new MoveToObjectAction(space, ship.getPosition(), enemyFlag,
//				enemyFlag.getPosition().getTranslationalVelocity());
				if (current == null || space.getCurrentTimestep() % updateInterval == 0) {
					Flag enemyFlag = getEnemyFlag(space);
					action = getAStarPathToGoal(space, ship, enemyFlag.getPosition());
					return action;
				}
			}
			
		}
		action = current;
		return action;
	}
	
	/*
	 * This method encapulates the decision and plan making logic of a resource collector ship
	 */
	private AbstractAction getResourceCollectorAction(Toroidal2DPhysics space, Ship ship){
		
		AbstractAction action;
		AbstractAction current = ship.getCurrentAction();
		
		//go after resources except for targets of oppurtunity
		
		
		if (current == null || space.getCurrentTimestep() % updateInterval == 0) {
			action =  getAsteroidCollectorAction(space, ship);
		}else{
			action = current;
		}
		
		return action;
	}

	

}

