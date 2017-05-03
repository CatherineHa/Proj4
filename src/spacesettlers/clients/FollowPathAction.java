package spacesettlers.clients;


import spacesettlers.actions.AbstractAction;
import spacesettlers.actions.DoNothingAction;
import spacesettlers.actions.MoveAction;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;

/**
 * This action takes a path as input and outputs the primitive commands 
 * necessary to follow the path.  Path following is accomplished using pd-control.
 * @author amy
 *
 */
public class FollowPathAction {
	Vertex[] path;
	int currentVertex;
	boolean finishedShortAction;
	AbstractAction lastCommand;

	

	public FollowPathAction() {
		path = null;
		currentVertex = -1;
		lastCommand = null;
	}

	public FollowPathAction (Vertex[] newPath) {
		path = newPath;
		currentVertex = 0;
	}

	public void followNewPath(Vertex[] newPath) {
		path = newPath;
		currentVertex = 0;
	}

	/**
	 * 
	 * @param state
	 * @param ship
	 * @return
	 */
	public AbstractAction followPath(Toroidal2DPhysics state, Ship ship) {
		//System.out.println("Following path at current action " + currentVertex);

		// safety case:  break if we have a null path
		if (path == null || currentVertex < 0) {
			DoNothingAction doNothing = new DoNothingAction();
			lastCommand = doNothing;
			return lastCommand;
		}
		
		if (lastCommand == null || lastCommand.isMovementFinished(state)) {
			currentVertex++;
			// force a replan every time a vertex is reached

			if (currentVertex >= path.length) {
				//System.out.println("Done!");
				DoNothingAction doNothing = new DoNothingAction();
				lastCommand = doNothing;
			} 
			else if(currentVertex == path.length -1 || 
					state.findShortestDistance(ship.getPosition(),path[currentVertex].getPosition()) < 100){
				MoveActionSlow command = new MoveActionSlow(state, ship.getPosition(), path[currentVertex].getPosition());
				//AbstractAction command = new MyFasterMoveAction(state, ship.getPosition(), path[currentVertex].getPosition());
				lastCommand = command;

			}
			else {
//				MoveAction command = new MoveAction(state, ship.getPosition(), path[currentVertex].getPosition());
				MoveActionD command = new MoveActionD(state, ship.getPosition(), path[currentVertex].getPosition(), null);
				//AbstractAction command = new MyFasterMoveAction(state, ship.getPosition(), path[currentVertex].getPosition());
				lastCommand = command;
			}
		}

		//System.out.println("Current command " + command);
		return lastCommand;
	}



}
