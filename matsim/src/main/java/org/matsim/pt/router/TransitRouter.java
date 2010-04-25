/* *********************************************************************** *
 * project: org.matsim.*
 * TranitRouter.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.pt.router;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.population.LegImpl;
import org.matsim.core.population.routes.GenericRouteImpl;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.router.MultiNodeDijkstra.InitialNode;
import org.matsim.pt.router.TransitRouterNetwork.TransitRouterNetworkLink;
import org.matsim.pt.router.TransitRouterNetwork.TransitRouterNetworkNode;
import org.matsim.pt.routes.ExperimentalTransitRoute;
import org.matsim.transitSchedule.api.TransitLine;
import org.matsim.transitSchedule.api.TransitRoute;
import org.matsim.transitSchedule.api.TransitRouteStop;
import org.matsim.transitSchedule.api.TransitSchedule;
import org.matsim.transitSchedule.api.TransitStopFacility;

public class TransitRouter {

	private final TransitSchedule schedule;
	private final TransitRouterNetwork transitNetwork;
	private final Map<TransitRouterNetworkLink, Tuple<TransitLine, TransitRoute>> linkMappings;
	private final Map<TransitRouterNetworkNode, TransitStopFacility> nodeMappings;

	private final MultiNodeDijkstra dijkstra;
	private final TransitRouterConfig config;
	private final TransitRouterNetworkTravelTimeCost ttCalculator;

	public TransitRouter(final TransitSchedule schedule) {
		this(schedule, new TransitRouterConfig());
	}

	public TransitRouter(final TransitSchedule schedule, final TransitRouterConfig config) {
		this( schedule, config, new TransitRouterNetworkTravelTimeCost(config) ) ;
	}

	// I added a constructor so that Manuel can pass his own TravelCost Object.  Given the calling 
	// syntax for MultiNodeDijkstra, might make more sense to pass this in a more segmented way. 
	// kai, apr'10
	
	public TransitRouter(final TransitSchedule schedule, final TransitRouterConfig config, 
			TransitRouterNetworkTravelTimeCost ttCalculator ) {
		this.schedule = schedule;
		this.config = config;
		this.linkMappings = new HashMap<TransitRouterNetworkLink, Tuple<TransitLine, TransitRoute>>();
		this.nodeMappings = new HashMap<TransitRouterNetworkNode, TransitStopFacility>();
		this.transitNetwork = buildNetwork();
		this.ttCalculator = ttCalculator ;
		this.dijkstra = new MultiNodeDijkstra(this.transitNetwork, this.ttCalculator, this.ttCalculator);
	}

	public List<Leg> calcRoute(final Coord fromCoord, final Coord toCoord, final double departureTime) {
		// find possible start stops
		Collection<TransitRouterNetworkNode> fromNodes = this.transitNetwork.getNearestNodes(fromCoord, this.config.searchRadius);
		if (fromNodes.size() < 2) {
			// also enlarge search area if only one stop found, maybe a second one is near the border of the search area
			TransitRouterNetworkNode nearestNode = this.transitNetwork.getNearestNode(fromCoord);
			double distance = CoordUtils.calcDistance(fromCoord, nearestNode.stop.getStopFacility().getCoord());
			fromNodes = this.transitNetwork.getNearestNodes(fromCoord, distance + this.config.extensionRadius);
		}
		Map<Node, InitialNode> wrappedFromNodes = new LinkedHashMap<Node, InitialNode>();
		for (TransitRouterNetworkNode node : fromNodes) {
			double distance = CoordUtils.calcDistance(fromCoord, node.stop.getStopFacility().getCoord());
			double initialTime = distance / this.config.beelineWalkSpeed;
			double initialCost = - (initialTime * this.config.marginalUtilityOfTravelTimeWalk);
			wrappedFromNodes.put(node, new InitialNode(initialCost, initialTime + departureTime));
		}

		// find possible end stops
		Collection<TransitRouterNetworkNode> toNodes = this.transitNetwork.getNearestNodes(toCoord, this.config.searchRadius);
		if (toNodes.size() < 2) {
			// also enlarge search area if only one stop found, maybe a second one is near the border of the search area
			TransitRouterNetworkNode nearestNode = this.transitNetwork.getNearestNode(toCoord);
			double distance = CoordUtils.calcDistance(toCoord, nearestNode.stop.getStopFacility().getCoord());
			toNodes = this.transitNetwork.getNearestNodes(toCoord, distance + this.config.extensionRadius);
		}
		Map<Node, InitialNode> wrappedToNodes = new LinkedHashMap<Node, InitialNode>();
		for (TransitRouterNetworkNode node : toNodes) {
			double distance = CoordUtils.calcDistance(toCoord, node.stop.getStopFacility().getCoord());
			double initialTime = distance / this.config.beelineWalkSpeed;
			double initialCost = - (initialTime * this.config.marginalUtilityOfTravelTimeWalk);
			wrappedToNodes.put(node, new InitialNode(initialCost, initialTime + departureTime));
		}

		// find routes between start and end stops
		Path p = this.dijkstra.calcLeastCostPath(wrappedFromNodes, wrappedToNodes);

		if (p == null) {
			return null;
		}

		double directWalkCost = CoordUtils.calcDistance(fromCoord, toCoord) / this.config.beelineWalkSpeed * ( 0 - this.config.marginalUtilityOfTravelTimeWalk);
		double pathCost = p.travelCost + wrappedFromNodes.get(p.nodes.get(0)).initialCost + wrappedToNodes.get(p.nodes.get(p.nodes.size() - 1)).initialCost;
		if (directWalkCost < pathCost) {
			List<Leg> legs = new ArrayList<Leg>();
			Leg leg = new LegImpl(TransportMode.transit_walk);
			double walkTime = CoordUtils.calcDistance(fromCoord, toCoord) / this.config.beelineWalkSpeed;
			Route walkRoute = new GenericRouteImpl(null, null);
			leg.setRoute(walkRoute);
			leg.setTravelTime(walkTime);
			legs.add(leg);
			return legs;
		}
		
		return convert( departureTime, p, fromCoord, toCoord ) ;
	}
	
	protected List<Leg> convert( double departureTime, Path p, Coord fromCoord, Coord toCoord ) {
		// yy there could be a better name for this method.  kai, apr'10

		// now convert the path back into a series of legs with correct routes
		double time = departureTime;
		List<Leg> legs = new ArrayList<Leg>();
		Leg leg = null;

		TransitLine line = null;
		TransitRoute route = null;
		TransitStopFacility accessStop = null;
		TransitRouteStop transitRouteStart = null;
		Link prevLink = null;
		int transitLegCnt = 0;
		for (Link link : p.links) {
			Tuple<TransitLine, TransitRoute> lineData = this.linkMappings.get(link);
			//			TransitStopFacility nodeData = this.nodeMappings.get(((TransitRouterNetworkWrapper.NodeWrapper)link.getToNode()).node);
			if (lineData == null) {
				TransitStopFacility egressStop = this.nodeMappings.get(link.getFromNode());
				// it must be one of the "transfer" links. finish the pt leg, if there was one before...
				if (route != null) {
					leg = new LegImpl(TransportMode.pt);
					ExperimentalTransitRoute ptRoute = new ExperimentalTransitRoute(accessStop, line, route, egressStop);
					leg.setRoute(ptRoute);
					double arrivalOffset = (((TransitRouterNetworkLink) link).getFromNode().stop.getArrivalOffset() != Time.UNDEFINED_TIME) ? ((TransitRouterNetworkLink) link).fromNode.stop.getArrivalOffset() : ((TransitRouterNetworkLink) link).fromNode.stop.getDepartureOffset();
					double arrivalTime = this.ttCalculator.getNextDepartureTime(route, transitRouteStart, time) + (arrivalOffset - transitRouteStart.getDepartureOffset());
					leg.setTravelTime(arrivalTime - time);
					time = arrivalTime;
					legs.add(leg);
					transitLegCnt++;
					accessStop = egressStop;
				}
				line = null;
				route = null;
				transitRouteStart = null;
			} else {
				if (lineData.getSecond() != route) {
					// the line changed
					TransitStopFacility egressStop = this.nodeMappings.get(((TransitRouterNetworkLink)link).getFromNode());
					if (route == null) {
						// previously, the agent was on a transfer, add the walk leg
						transitRouteStart = ((TransitRouterNetworkLink) link).getFromNode().stop;
						if (accessStop != egressStop) {
							if (accessStop != null) {
								leg = new LegImpl(TransportMode.transit_walk);
								double walkTime = CoordUtils.calcDistance(accessStop.getCoord(), egressStop.getCoord()) / this.config.beelineWalkSpeed;
								Route walkRoute = new GenericRouteImpl(accessStop.getLinkId(), egressStop.getLinkId());
								leg.setRoute(walkRoute);
								leg.setTravelTime(walkTime);
								time += walkTime;
								legs.add(leg);
							} else { // accessStop == null, so it must be the first walk-leg
								leg = new LegImpl(TransportMode.transit_walk);
								double walkTime = CoordUtils.calcDistance(fromCoord, egressStop.getCoord()) / this.config.beelineWalkSpeed;
								leg.setTravelTime(walkTime);
								time += walkTime;
								legs.add(leg);
							}
						}
					}
					line = lineData.getFirst();
					route = lineData.getSecond();
					accessStop = egressStop;
				}
			}
			prevLink = link;
		}
		if (route != null) {
			// the last part of the path was with a transit route, so add the pt-leg and final walk-leg
			leg = new LegImpl(TransportMode.pt);
			TransitStopFacility egressStop = this.nodeMappings.get(((TransitRouterNetworkLink) prevLink).getToNode());
			ExperimentalTransitRoute ptRoute = new ExperimentalTransitRoute(accessStop, line, route, egressStop);
			leg.setRoute(ptRoute);
			double arrivalOffset = (((TransitRouterNetworkLink) prevLink).toNode.stop.getArrivalOffset() != Time.UNDEFINED_TIME) ? 
					((TransitRouterNetworkLink) prevLink).toNode.stop.getArrivalOffset() 
					: ((TransitRouterNetworkLink) prevLink).toNode.stop.getDepartureOffset();
			double arrivalTime = this.ttCalculator.getNextDepartureTime(route, transitRouteStart, time) + (arrivalOffset - transitRouteStart.getDepartureOffset());
			leg.setTravelTime(arrivalTime - time);

			legs.add(leg);
			transitLegCnt++;
			accessStop = egressStop;
		}
		if (prevLink != null) {
			leg = new LegImpl(TransportMode.transit_walk);
			double walkTime;
			if (accessStop == null) {
				walkTime = CoordUtils.calcDistance(fromCoord, toCoord) / this.config.beelineWalkSpeed;
			} else {
				walkTime = CoordUtils.calcDistance(accessStop.getCoord(), toCoord) / this.config.beelineWalkSpeed;
			}
			leg.setTravelTime(walkTime);
			legs.add(leg);
		}
		if (transitLegCnt == 0) {
			// it seems, the agent only walked
			legs.clear();
			leg = new LegImpl(TransportMode.transit_walk);
			double walkTime = CoordUtils.calcDistance(fromCoord, toCoord) / this.config.beelineWalkSpeed;
			leg.setTravelTime(walkTime);
			legs.add(leg);
		}
		return legs;
	}

	protected TransitRouterNetwork buildNetwork() {
		final TransitRouterNetwork network = new TransitRouterNetwork();

		// build nodes and links connecting the nodes according to the transit routes
		for (TransitLine line : this.schedule.getTransitLines().values()) {
			for (TransitRoute route : line.getRoutes().values()) {
				TransitRouterNetworkNode prevNode = null;
				for (TransitRouteStop stop : route.getStops()) {
					TransitRouterNetworkNode node = network.createNode(stop, route, line);
					this.nodeMappings.put(node, stop.getStopFacility());
					if (prevNode != null) {
						TransitRouterNetworkLink link = network.createLink(prevNode, node, route, line);
						this.linkMappings.put(link, new Tuple<TransitLine, TransitRoute>(line, route));
					}
					prevNode = node;
				}
			}
		}
		network.finishInit(); // not nice to call "finishInit" here before we added all links...
		// in my view, it would be possible to completely do without finishInit: do the 
		// additions to the central data structures as items come in, not near the end.  I would
		// prefer that because nobody could forget the "finishInit".  kai, apr'10

		List<Tuple<TransitRouterNetworkNode, TransitRouterNetworkNode>> toBeAdded = new LinkedList<Tuple<TransitRouterNetworkNode, TransitRouterNetworkNode>>();
		// connect all stops with walking links if they're located less than beelineWalkConnectionDistance from each other
		for (TransitRouterNetworkNode node : network.getNodes().values()) {
			if (node.getInLinks().size() > 0) { // only add links from this node to other nodes if agents actually can arrive here
				for (TransitRouterNetworkNode node2 : network.getNearestNodes(node.stop.getStopFacility().getCoord(), this.config.beelineWalkConnectionDistance)) {
					if ((node != node2) && (node2.getOutLinks().size() > 0)) { // only add links to other nodes when agents can depart there
						if ((node.line != node2.line) || (node.stop.getStopFacility() != node2.stop.getStopFacility())) {
							// do not yet add them to the network, as this would change in/out-links
							toBeAdded.add(new Tuple<TransitRouterNetworkNode, TransitRouterNetworkNode>(node, node2));
						}
					}
				}
			}
		}
		for (Tuple<TransitRouterNetworkNode, TransitRouterNetworkNode> tuple : toBeAdded) {
			network.createLink(tuple.getFirst(), tuple.getSecond(), null, null);
		}

		System.out.println("transit router network statistics:");
		System.out.println(" # nodes: " + network.getNodes().size());
		System.out.println(" # links: " + network.getLinks().size());

		return network;
	}

	public TransitRouterNetwork getTransitRouterNetwork() {
		return this.transitNetwork;
	}

	protected TransitSchedule getSchedule() {
		return schedule;
	}

	protected TransitRouterNetwork getTransitNetwork() {
		return transitNetwork;
	}

	protected Map<TransitRouterNetworkLink, Tuple<TransitLine, TransitRoute>> getLinkMappings() {
		return linkMappings;
	}

	protected Map<TransitRouterNetworkNode, TransitStopFacility> getNodeMappings() {
		return nodeMappings;
	}

	protected MultiNodeDijkstra getDijkstra() {
		return dijkstra;
	}

	protected TransitRouterConfig getConfig() {
		return config;
	}

	protected TransitRouterNetworkTravelTimeCost getTtCalculator() {
		return ttCalculator;
	}

}
