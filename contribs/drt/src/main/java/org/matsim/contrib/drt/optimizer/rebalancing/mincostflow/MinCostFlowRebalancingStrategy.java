/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2018 by the members listed in the COPYING,        *
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

package org.matsim.contrib.drt.optimizer.rebalancing.mincostflow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.drt.analysis.zonal.DrtZonalSystem;
import org.matsim.contrib.drt.analysis.zonal.DrtZone;
import org.matsim.contrib.drt.optimizer.rebalancing.RebalancingParams;
import org.matsim.contrib.drt.optimizer.rebalancing.RebalancingStrategy;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.fleet.Fleet;
import org.matsim.contrib.dvrp.schedule.Schedule;
import org.matsim.contrib.dvrp.schedule.Schedules;
import org.matsim.contrib.dvrp.schedule.StayTask;
import org.matsim.contrib.dvrp.schedule.Task.TaskStatus;

/**
 * @author michalm
 */
public class MinCostFlowRebalancingStrategy implements RebalancingStrategy {
	public interface RebalancingTargetCalculator {
		int estimate(DrtZone zone, double time);
	}

	private final RebalancingTargetCalculator rebalancingTargetCalculator;
	private final DrtZonalSystem zonalSystem;
	private final Fleet fleet;
	private final MinCostRelocationCalculator minCostRelocationCalculator;
	private final RebalancingParams params;

	public MinCostFlowRebalancingStrategy(RebalancingTargetCalculator rebalancingTargetCalculator,
			DrtZonalSystem zonalSystem, Fleet fleet, MinCostRelocationCalculator minCostRelocationCalculator,
			RebalancingParams params) {
		this.rebalancingTargetCalculator = rebalancingTargetCalculator;
		this.zonalSystem = zonalSystem;
		this.fleet = fleet;
		this.minCostRelocationCalculator = minCostRelocationCalculator;
		this.params = params;
	}

	@Override
	public List<Relocation> calcRelocations(Stream<? extends DvrpVehicle> rebalancableVehicles, double time) {
		Map<DrtZone, List<DvrpVehicle>> rebalancableVehiclesPerZone = groupRebalancableVehicles(rebalancableVehicles,
				time);
		if (rebalancableVehiclesPerZone.isEmpty()) {
			return Collections.emptyList();
		}
		Map<DrtZone, List<DvrpVehicle>> soonIdleVehiclesPerZone = groupSoonIdleVehicles(time);
		return calculateMinCostRelocations(time, rebalancableVehiclesPerZone, soonIdleVehiclesPerZone);
	}

	private Map<DrtZone, List<DvrpVehicle>> groupRebalancableVehicles(
			Stream<? extends DvrpVehicle> rebalancableVehicles, double time) {
		Map<DrtZone, List<DvrpVehicle>> rebalancableVehiclesPerZone = new HashMap<>();
		rebalancableVehicles.filter(v -> v.getServiceEndTime() > time + params.getMinServiceTime()).forEach(v -> {
			Link link = ((StayTask)v.getSchedule().getCurrentTask()).getLink();
			DrtZone zone = zonalSystem.getZoneForLinkId(link.getId());
			if (zone != null) {
				// zonePerVehicle.put(v.getId(), zone);
				rebalancableVehiclesPerZone.computeIfAbsent(zone, z -> new ArrayList<>()).add(v);
			}
		});
		return rebalancableVehiclesPerZone;
	}

	// also include vehicles being right now relocated or recharged
	private Map<DrtZone, List<DvrpVehicle>> groupSoonIdleVehicles(double time) {
		Map<DrtZone, List<DvrpVehicle>> soonIdleVehiclesPerZone = new HashMap<>();
		for (DvrpVehicle v : fleet.getVehicles().values()) {
			Schedule s = v.getSchedule();
			StayTask stayTask = (StayTask)Schedules.getLastTask(s);
			if (stayTask.getStatus() == TaskStatus.PLANNED
					&& stayTask.getBeginTime() < time + params.getMaxTimeBeforeIdle()
					&& v.getServiceEndTime() > time + params.getMinServiceTime()) {
				DrtZone zone = zonalSystem.getZoneForLinkId(stayTask.getLink().getId());
				if (zone != null) {
					soonIdleVehiclesPerZone.computeIfAbsent(zone, z -> new ArrayList<>()).add(v);
				}
			}
		}
		return soonIdleVehiclesPerZone;
	}

	private List<Relocation> calculateMinCostRelocations(double time,
			Map<DrtZone, List<DvrpVehicle>> rebalancableVehiclesPerZone,
			Map<DrtZone, List<DvrpVehicle>> soonIdleVehiclesPerZone) {
		List<Pair<DrtZone, Integer>> supply = new ArrayList<>();
		List<Pair<DrtZone, Integer>> demand = new ArrayList<>();

		for (DrtZone z : zonalSystem.getZones().values()) {
			int rebalancable = rebalancableVehiclesPerZone.getOrDefault(z, Collections.emptyList()).size();
			int soonIdle = soonIdleVehiclesPerZone.getOrDefault(z, Collections.emptyList()).size();
			int target = rebalancingTargetCalculator.estimate(z, time);

			int delta = Math.min(rebalancable + soonIdle - target, rebalancable);
			if (delta < 0) {
				demand.add(Pair.of(z, -delta));
			} else if (delta > 0) {
				supply.add(Pair.of(z, delta));
			}
		}

		return minCostRelocationCalculator.calcRelocations(supply, demand, rebalancableVehiclesPerZone);
	}
}
