package spacesettlers.clients;


public class SearchNode {
	Vertex vertex;
	Edge edge;
	
	public SearchNode(Vertex v, Edge e) {
		vertex = v;
		edge = e;
	}

	public Edge getEdge() {
		return edge;
	}

	public Vertex getVertex() {
		return vertex;
	}
	
	

}
