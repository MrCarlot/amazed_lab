package amazed.solver;

import amazed.maze.Maze;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <code>ForkJoinSolver</code> implements a solver for
 * <code>Maze</code> objects using a fork/join multi-thread
 * depth-first search.
 * <p>
 * Instances of <code>ForkJoinSolver</code> should be run by a
 * <code>ForkJoinPool</code> object.
 */

public class ForkJoinSolver extends SequentialSolver {

    // Shared by all instances/players
    private Set<Integer> allVisited;
    // Needed for not adding cells to allVisited twice
    private boolean newChild;
    // If true, stops all players from moving. Wrapped in object so that it can be shared.
    private AtomicBoolean goalFound;

    /**
     * Creates a solver that searches in <code>maze</code> from the
     * start node to a goal.
     *
     * @param maze   the maze to be searched
     */
    public ForkJoinSolver(Maze maze)
    {
        super(maze);
        allVisited = new ConcurrentSkipListSet<>();
        goalFound = new AtomicBoolean();
    }

    // Auxiliary constructor for children so allVisited and goalFound can be shared
    private ForkJoinSolver(Maze maze, Set<Integer> allVisited, AtomicBoolean goalFound)
    {
        super(maze);
        this.allVisited = allVisited;
        this.goalFound = goalFound;
    }

    /**
     * Creates a solver that searches in <code>maze</code> from the
     * start node to a goal, forking after a given number of allVisited
     * nodes.
     *
     * @param maze        the maze to be searched
     * @param forkAfter   the number of steps (allVisited nodes) after
     *                    which a parallel task is forked; if
     *                    <code>forkAfter &lt;= 0</code> the solver never
     *                    forks new tasks
     */
    public ForkJoinSolver(Maze maze, int forkAfter)
    {
        this(maze);
        this.forkAfter = forkAfter;
    }

    /**
     * Searches for and returns the path, as a list of node
     * identifiers, that goes from the start node to a goal node in
     * the maze. If such a path cannot be found (because there are no
     * goals, or all goals are unreacheable), the method returns
     * <code>null</code>.
     *
     * @return   the list of node identifiers from the start node to a
     *           goal node in the maze; <code>null</code> if such a path cannot
     *           be found.
     */
    @Override
    public List<Integer> compute() {
        return parallelSearch();
    }

    private List<Integer> parallelSearch() {
        // one player active on the maze at start
        final int player = maze.newPlayer(start);
        // start with start node
        frontier.push(start);
        // as long as not all nodes have been processed
        while (!frontier.empty() && !(goalFound.get())) {
            // get the new node to process
            final int current = frontier.pop();
            // if current node has a goal
            if (maze.hasGoal(current) ) {
                // move player to goal
                maze.move(player, current);
                // search finished: reconstruct and return path
                return pathFromTo(start, current); }

            /* if current node has not been visited yet,
             * OR if it is a newly created child (in which case adding it to allVisited has already been
             * handled in handleForks*/
            if ((allVisited.add(current) || newChild) && !(goalFound.get())) {
                newChild = false;
                // move player to current node
                maze.move(player, current);
                // The path created by appending current instance with its children
                List<Integer> path = handleForks(current);
                // If path != null it must be the goal path
                if (path != null) { return path; }
                // add neighbour to the nodes to be processed
                for (int neighbour: maze.neighbors(current)) {
                    frontier.push(neighbour);
                    // if neighbour has not been already visited,
                    // neighbour can be reached from current (i.e., current is nb's predecessor)
                    if (!allVisited.contains(neighbour)) //not thread safe but doesn't matter since players that get-
                        { predecessor.putIfAbsent(neighbour, current); } } } } //  -blocked off will eventually return null anyway
        // all nodes explored, no goal found
        return null; }

    //Forks children and evaluates if they found the goal
    private List<Integer> handleForks(int current) {
        List<ForkJoinSolver> solvers = new ArrayList<>();

        //Only forks at a crossing
        if (maze.neighbors(current).size() > 2) {
            for (int neighbour: maze.neighbors(current)) {
                if(allVisited.add(neighbour)){ //if possible add the neighbour to allVisited
                    ForkJoinSolver childSolver = new ForkJoinSolver(maze, allVisited, goalFound);
                    childSolver.start = neighbour; //child starts at the neighbour cell
                    childSolver.newChild = true; //set so that the child won't have to add its cell to allVisited again
                    childSolver.fork();
                    solvers.add(childSolver); } }

            for (ForkJoinSolver solver : solvers) {
                List<Integer> result = solver.join();
                // If goal found, appends child's path to parent's
                if (result != null) {
                    List<Integer> path = pathFromTo(start, current);
                    path.addAll(result);
                    goalFound.set(true);
                    return path; }} }
        return null;
    }
}