/*
 * *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2020 by the members listed in the COPYING,        *
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
 * *********************************************************************** *
 */

package org.matsim.contrib.drt.optimizer.rebalancing.demandestimator;

import static org.junit.Assert.assertEquals;

import java.net.URL;
import java.util.function.ToIntFunction;

import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.contrib.drt.analysis.zonal.DrtZonalSystem;
import org.matsim.contrib.drt.analysis.zonal.DrtZonalSystemParams;
import org.matsim.contrib.drt.analysis.zonal.DrtZone;
import org.matsim.contrib.drt.optimizer.rebalancing.RebalancingParams;
import org.matsim.contrib.drt.optimizer.rebalancing.mincostflow.MinCostFlowRebalancingStrategyParams;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.DrtControlerCreator;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.fleet.DvrpVehicleSpecification;
import org.matsim.contrib.dvrp.fleet.FleetSpecification;
import org.matsim.contrib.dvrp.fleet.ImmutableDvrpVehicleSpecification;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeModule;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpModes;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.examples.ExamplesUtils;
import org.matsim.testcases.MatsimTestUtils;
import org.matsim.vis.otfvis.OTFVisConfigGroup;

public class ZonalDemandAggregatorWithoutServiceAreaTest {

	//TODO write test with service area !!
	// (with an service are, demand estimation zones are not spread over the entire network but restricted to the service are (plus a little surrounding))

	@Rule
	public MatsimTestUtils utils = new MatsimTestUtils();

	@Test
	public void EqualVehicleDensityZonalDemandAggregatorTest() {
		Controler controler = setupControler(
				MinCostFlowRebalancingStrategyParams.ZonalDemandAggregatorType.EqualVehicleDensity, "");
		controler.run();
		ZonalDemandAggregator aggregator = controler.getInjector()
				.getInstance(DvrpModes.key(ZonalDemandAggregator.class, "drt"));
		DrtZonalSystem zonalSystem = controler.getInjector().getInstance(DvrpModes.key(DrtZonalSystem.class, "drt"));
		for (double ii = 0; ii < 16 * 3600; ii += 1800) {
			ToIntFunction<DrtZone> demandFunction = aggregator.getExpectedDemandForTimeBin(
					ii + 60); //inside DRT, the demand is actually estimated for rebalancing time + 60 seconds..
			assertDemand(demandFunction, zonalSystem, "1", ii, 1);
			assertDemand(demandFunction, zonalSystem, "2", ii, 1);
			assertDemand(demandFunction, zonalSystem, "3", ii, 1);
			assertDemand(demandFunction, zonalSystem, "4", ii, 1);
			assertDemand(demandFunction, zonalSystem, "5", ii, 1);
			assertDemand(demandFunction, zonalSystem, "6", ii, 1);
			assertDemand(demandFunction, zonalSystem, "7", ii, 1);
			assertDemand(demandFunction, zonalSystem, "8", ii, 1);
		}
	}

	private void assertDemand(ToIntFunction<DrtZone> demandFunction, DrtZonalSystem zonalSystem, String zoneId,
			double time, int expectedValue) {
		DrtZone zone = zonalSystem.getZones().get(zoneId);
		assertEquals("wrong estimation of demand at time=" + (time + 60) + " in zone " + zoneId, expectedValue,
				demandFunction.applyAsInt(zone), MatsimTestUtils.EPSILON);
	}

	@Test
	public void EqualVehicleDensityZonalDemandAggregatorFleetModificationTest() {
		Controler controler = setupControler(
				MinCostFlowRebalancingStrategyParams.ZonalDemandAggregatorType.EqualVehicleDensity, "");
		// double number of vehicles after 0th iteration -> estimation of demand should double too
		controler.addOverridingModule(new AbstractDvrpModeModule("drt") {
			@Override
			public void install() {
				bindModal(FleetModifier.class).toProvider(
						modalProvider(getter -> new FleetModifier(getter.getModal(FleetSpecification.class), 8)))
						.asEagerSingleton();
				addControlerListenerBinding().to(modalKey(FleetModifier.class));
			}
		});
		controler.run();
		ZonalDemandAggregator aggregator = controler.getInjector()
				.getInstance(DvrpModes.key(ZonalDemandAggregator.class, "drt"));
		DrtZonalSystem zonalSystem = controler.getInjector().getInstance(DvrpModes.key(DrtZonalSystem.class, "drt"));
		for (double ii = 0; ii < 16 * 3600; ii += 1800) {
			ToIntFunction<DrtZone> demandFunction = aggregator.getExpectedDemandForTimeBin(
					ii + 60); //inside DRT, the demand is actually estimated for rebalancing time + 60 seconds..
			assertDemand(demandFunction, zonalSystem, "1", ii, 2);
			assertDemand(demandFunction, zonalSystem, "2", ii, 2);
			assertDemand(demandFunction, zonalSystem, "3", ii, 2);
			assertDemand(demandFunction, zonalSystem, "4", ii, 2);
			assertDemand(demandFunction, zonalSystem, "5", ii, 2);
			assertDemand(demandFunction, zonalSystem, "6", ii, 2);
			assertDemand(demandFunction, zonalSystem, "7", ii, 2);
			assertDemand(demandFunction, zonalSystem, "8", ii, 2);
		}
	}

	@Test
	public void PreviousIterationZonalDemandAggregatorTest() {
		Controler controler = setupControler(
				MinCostFlowRebalancingStrategyParams.ZonalDemandAggregatorType.PreviousIteration, "");
		controler.run();
		ZonalDemandAggregator aggregator = controler.getInjector()
				.getInstance(DvrpModes.key(ZonalDemandAggregator.class, "drt"));
		DrtZonalSystem zonalSystem = controler.getInjector().getInstance(DvrpModes.key(DrtZonalSystem.class, "drt"));
		for (double ii = 1800; ii < 16 * 3600; ii += 1800) {
			ToIntFunction<DrtZone> demandFunction = aggregator.getExpectedDemandForTimeBin(
					ii + 60); //inside DRT, the demand is actually estimated for rebalancing time + 60 seconds..
			assertDemand(demandFunction, zonalSystem, "1", ii, 0);
			assertDemand(demandFunction, zonalSystem, "2", ii, 0);
			assertDemand(demandFunction, zonalSystem, "3", ii, 0);
			assertDemand(demandFunction, zonalSystem, "4", ii, 0);
			assertDemand(demandFunction, zonalSystem, "5", ii, 0);
			assertDemand(demandFunction, zonalSystem, "6", ii, 0);
			assertDemand(demandFunction, zonalSystem, "7", ii, 0);
			assertDemand(demandFunction, zonalSystem, "8", ii, 3);
		}
	}

	@Test
	public void PreviousIterationZonalDemandAggregatorWithSpeedUpModeTest() {
		Controler controler = setupControler(
				MinCostFlowRebalancingStrategyParams.ZonalDemandAggregatorType.PreviousIteration, "drt_teleportation");
		controler.run();
		ZonalDemandAggregator aggregator = controler.getInjector()
				.getInstance(DvrpModes.key(ZonalDemandAggregator.class, "drt"));
		DrtZonalSystem zonalSystem = controler.getInjector().getInstance(DvrpModes.key(DrtZonalSystem.class, "drt"));
		for (double ii = 1800; ii < 16 * 3600; ii += 1800) {
			ToIntFunction<DrtZone> demandFunction = aggregator.getExpectedDemandForTimeBin(
					ii + 60); //inside DRT, the demand is actually estimated for rebalancing time + 60 seconds..
			assertDemand(demandFunction, zonalSystem, "1", ii, 0);
			assertDemand(demandFunction, zonalSystem, "2", ii, 0);
			assertDemand(demandFunction, zonalSystem, "3", ii, 0);
			assertDemand(demandFunction, zonalSystem, "4", ii, 3);
			assertDemand(demandFunction, zonalSystem, "5", ii, 0);
			assertDemand(demandFunction, zonalSystem, "6", ii, 0);
			assertDemand(demandFunction, zonalSystem, "7", ii, 0);
			assertDemand(demandFunction, zonalSystem, "8", ii, 3);
		}
	}

	@Test
	public void ActivityLocationBasedZonalDemandAggregatorTest() {
		Controler controler = setupControler(
				MinCostFlowRebalancingStrategyParams.ZonalDemandAggregatorType.TimeDependentActivityBased, "");
		controler.run();
		ZonalDemandAggregator aggregator = controler.getInjector()
				.getInstance(DvrpModes.key(ZonalDemandAggregator.class, "drt"));
		DrtZonalSystem zonalSystem = controler.getInjector().getInstance(DvrpModes.key(DrtZonalSystem.class, "drt"));
		for (double ii = 1800; ii < 16 * 3600; ii += 1800) {
			ToIntFunction<DrtZone> demandFunction = aggregator.getExpectedDemandForTimeBin(
					ii + 60); //inside DRT, the demand is actually estimated for rebalancing time + 60 seconds..
			assertDemand(demandFunction, zonalSystem, "1", ii, 0);
			assertDemand(demandFunction, zonalSystem, "2", ii, 3);
			assertDemand(demandFunction, zonalSystem, "3", ii, 0);
			assertDemand(demandFunction, zonalSystem, "4", ii, 3);
			assertDemand(demandFunction, zonalSystem, "5", ii, 0);
			assertDemand(demandFunction, zonalSystem, "6", ii, 0);
			assertDemand(demandFunction, zonalSystem, "7", ii, 0);
			assertDemand(demandFunction, zonalSystem, "8", ii, 3);
		}
	}

	@Test
	public void FleetSizeWeightedByPopulationShareDemandAggregatorTest() {
		Controler controler = setupControler(
				MinCostFlowRebalancingStrategyParams.ZonalDemandAggregatorType.FleetSizeWeightedByPopulationShare, "");
		controler.run();
		ZonalDemandAggregator aggregator = controler.getInjector()
				.getInstance(DvrpModes.key(ZonalDemandAggregator.class, "drt"));
		DrtZonalSystem zonalSystem = controler.getInjector().getInstance(DvrpModes.key(DrtZonalSystem.class, "drt"));
		for (double ii = 0; ii < 16 * 3600; ii += 1800) {
			ToIntFunction<DrtZone> demandFunction = aggregator.getExpectedDemandForTimeBin(
					ii + 60); //inside DRT, the demand is actually estimated for rebalancing time + 60 seconds..
			assertDemand(demandFunction, zonalSystem, "1", ii, 0);
			assertDemand(demandFunction, zonalSystem, "2", ii, 2);
			assertDemand(demandFunction, zonalSystem, "3", ii, 0);
			assertDemand(demandFunction, zonalSystem, "4", ii, 2);
			assertDemand(demandFunction, zonalSystem, "5", ii, 0);
			assertDemand(demandFunction, zonalSystem, "6", ii, 0);
			assertDemand(demandFunction, zonalSystem, "7", ii, 0);
			assertDemand(demandFunction, zonalSystem, "8", ii, 2);
		}
	}

	@Test
	public void FleetSizeWeightedByPopulationShareDemandAggregatorFleetModificationTest() {
		Controler controler = setupControler(
				MinCostFlowRebalancingStrategyParams.ZonalDemandAggregatorType.FleetSizeWeightedByPopulationShare, "");
		// double number of vehicles after 0th iteration -> estimation of demand should double too (besides rounding issues)
		controler.addOverridingModule(new AbstractDvrpModeModule("drt") {
			@Override
			public void install() {
				bindModal(FleetModifier.class).toProvider(
						modalProvider(getter -> new FleetModifier(getter.getModal(FleetSpecification.class), 8)))
						.asEagerSingleton();
				addControlerListenerBinding().to(modalKey(FleetModifier.class));
			}
		});
		controler.run();
		ZonalDemandAggregator aggregator = controler.getInjector()
				.getInstance(DvrpModes.key(ZonalDemandAggregator.class, "drt"));
		DrtZonalSystem zonalSystem = controler.getInjector().getInstance(DvrpModes.key(DrtZonalSystem.class, "drt"));
		for (double ii = 0; ii < 16 * 3600; ii += 1800) {
			ToIntFunction<DrtZone> demandFunction = aggregator.getExpectedDemandForTimeBin(
					ii + 60); //inside DRT, the demand is actually estimated for rebalancing time + 60 seconds..
			assertDemand(demandFunction, zonalSystem, "1", ii, 0);
			assertDemand(demandFunction, zonalSystem, "2", ii, 5);
			assertDemand(demandFunction, zonalSystem, "3", ii, 0);
			assertDemand(demandFunction, zonalSystem, "4", ii, 5);
			assertDemand(demandFunction, zonalSystem, "5", ii, 0);
			assertDemand(demandFunction, zonalSystem, "6", ii, 0);
			assertDemand(demandFunction, zonalSystem, "7", ii, 0);
			assertDemand(demandFunction, zonalSystem, "8", ii, 5);
		}
	}

	private Controler setupControler(MinCostFlowRebalancingStrategyParams.ZonalDemandAggregatorType aggregatorType,
			String drtSpeedUpModeForRebalancingConfiguration) {
		URL configUrl = IOUtils.extendUrl(ExamplesUtils.getTestScenarioURL("dvrp-grid"),
				"eight_shared_taxi_config.xml");
		Config config = ConfigUtils.loadConfig(configUrl, new MultiModeDrtConfigGroup(), new DvrpConfigGroup(),
				new OTFVisConfigGroup());

		DrtConfigGroup drtCfg = DrtConfigGroup.getSingleModeDrtConfig(config);
		drtCfg.setDrtSpeedUpMode(drtSpeedUpModeForRebalancingConfiguration);

		MinCostFlowRebalancingStrategyParams rebalancingStrategyParams = new MinCostFlowRebalancingStrategyParams();
		rebalancingStrategyParams.setTargetAlpha(1);
		rebalancingStrategyParams.setTargetBeta(0);
		rebalancingStrategyParams.setZonalDemandAggregatorType(aggregatorType);

		RebalancingParams rebalancingParams = new RebalancingParams();
		rebalancingParams.addParameterSet(rebalancingStrategyParams);
		drtCfg.addParameterSet(rebalancingParams);

		DrtZonalSystemParams zonalSystemParams = new DrtZonalSystemParams();
		zonalSystemParams.setZonesGeneration(DrtZonalSystemParams.ZoneGeneration.GridFromNetwork);
		zonalSystemParams.setCellSize(500.);
		drtCfg.addParameterSet(zonalSystemParams);

		drtCfg.setChangeStartLinkToLastLinkInSchedule(false); //do not take result from last iteration...

		config.controler().setLastIteration(1);
		config.qsim().setStartTime(0.);
		config.controler()
				.setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.controler().setOutputDirectory(utils.getOutputDirectory());

		PlansCalcRouteConfigGroup.ModeRoutingParams pseudoDrtSpeedUpModeRoutingParams = new PlansCalcRouteConfigGroup.ModeRoutingParams(
				"drt_teleportation");
		pseudoDrtSpeedUpModeRoutingParams.setBeelineDistanceFactor(1.3);
		pseudoDrtSpeedUpModeRoutingParams.setTeleportedModeSpeed(8.0);
		config.plansCalcRoute().addModeRoutingParams(pseudoDrtSpeedUpModeRoutingParams);

		// if adding a new mode (drtSpeedUpMode), some default modes are deleted, so re-insert them...
		PlansCalcRouteConfigGroup.ModeRoutingParams walkRoutingParams = new PlansCalcRouteConfigGroup.ModeRoutingParams(
				TransportMode.walk);
		walkRoutingParams.setBeelineDistanceFactor(1.3);
		walkRoutingParams.setTeleportedModeSpeed(3.0 / 3.6);
		config.plansCalcRoute().addModeRoutingParams(walkRoutingParams);

		PlanCalcScoreConfigGroup.ModeParams pseudoDrtSpeedUpModeScoreParams = new PlanCalcScoreConfigGroup.ModeParams(
				"drt_teleportation");
		config.planCalcScore().addModeParams(pseudoDrtSpeedUpModeScoreParams);

		//this is the wrong way around (create controler before manipulating scenario...
		Controler controler = DrtControlerCreator.createControler(config, false);
		setupPopulation(controler.getScenario().getPopulation());
		return controler;
	}

	/**
	 * we have eight zones, 2 rows 4 columns.
	 * order of zones:
	 * 2	4	6	8
	 * 1	3	5	7
	 * <p>
	 * 1) in the left column, there are half of the people, performing dummy - > car -> dummy
	 * That should lead to half of the drt vehicles rebalanced to the left column when using TimeDependentActivityBasedZonalDemandAggregator.
	 * 2) in the right column, the other half of the people perform dummy -> drt -> dummy from top row to bottom row.
	 * That should lead to all drt vehicles rebalanced to the right column when using PreviousIterationZonalDRTDemandAggregator.
	 * 3) in the center, there is nothing happening.
	 * But, when using EqualVehicleDensityZonalDemandAggregator, one vehicle should get sent to every zone..
	 */
	private void setupPopulation(Population population) {
		//delete what's there
		population.getPersons().clear();

		PopulationFactory factory = population.getFactory();

		Id<Link> left1 = Id.createLinkId(344);
		Id<Link> left2 = Id.createLinkId(112);

		for (int i = 1; i < 100; i++) {
			Person person = factory.createPerson(Id.createPersonId("leftColumn_" + i));

			Plan plan = factory.createPlan();
			Activity dummy1 = factory.createActivityFromLinkId("dummy", left1);
			dummy1.setEndTime(i * 10 * 60);
			plan.addActivity(dummy1);

			plan.addLeg(factory.createLeg(TransportMode.car));
			plan.addActivity(factory.createActivityFromLinkId("dummy", left2));

			person.addPlan(plan);
			population.addPerson(person);
		}

		Id<Link> right1 = Id.createLinkId(151);
		Id<Link> right2 = Id.createLinkId(319);

		for (int i = 1; i < 100; i++) {
			Person person = factory.createPerson(Id.createPersonId("rightColumn_" + i));

			Plan plan = factory.createPlan();
			Activity dummy1 = factory.createActivityFromLinkId("dummy", right1);
			dummy1.setEndTime(i * 10 * 60);
			plan.addActivity(dummy1);

			plan.addLeg(factory.createLeg(TransportMode.drt));
			plan.addActivity(factory.createActivityFromLinkId("dummy", right2));

			person.addPlan(plan);
			population.addPerson(person);
		}

		Id<Link> center1 = Id.createLinkId(147);
		Id<Link> center2 = Id.createLinkId(315);

		for (int i = 1; i < 100; i++) {
			Person person = factory.createPerson(Id.createPersonId("centerColumn_" + i));

			Plan plan = factory.createPlan();
			Activity dummy1 = factory.createActivityFromLinkId("dummy", center1);
			dummy1.setEndTime(i * 10 * 60);
			plan.addActivity(dummy1);

			plan.addLeg(factory.createLeg("drt_teleportation"));
			plan.addActivity(factory.createActivityFromLinkId("dummy", center2));

			person.addPlan(plan);
			population.addPerson(person);
		}
	}

	private class FleetModifier implements IterationEndsListener {
		private FleetSpecification fleetSpecification;
		private int numberOfVehiclesToAdd;

		FleetModifier(FleetSpecification fleetSpecification, int numberOfVehiclesToAdd) {
			this.fleetSpecification = fleetSpecification;
			this.numberOfVehiclesToAdd = numberOfVehiclesToAdd;
		}

		@Override
		public void notifyIterationEnds(IterationEndsEvent event) {
			if (event.getIteration() == 0) {
				// find any vehicle id to clone later
				Id<DvrpVehicle> vehicleIdToBeCopied = fleetSpecification.getVehicleSpecifications()
						.keySet()
						.iterator()
						.next();
				DvrpVehicleSpecification dvrpVehicleSpecficationToBeCloned = fleetSpecification.getVehicleSpecifications()
						.get(vehicleIdToBeCopied);

				for (int vehicleCounter = 1; vehicleCounter <= numberOfVehiclesToAdd; vehicleCounter++) {
					Id<DvrpVehicle> id = Id.create(
							"optDrt_" + vehicleCounter + "_cloneOf_" + dvrpVehicleSpecficationToBeCloned.getId(),
							DvrpVehicle.class);
					DvrpVehicleSpecification newSpecification = ImmutableDvrpVehicleSpecification.newBuilder()
							.id(id)
							.serviceBeginTime(dvrpVehicleSpecficationToBeCloned.getServiceBeginTime())
							.serviceEndTime(dvrpVehicleSpecficationToBeCloned.getServiceEndTime())
							.startLinkId(dvrpVehicleSpecficationToBeCloned.getStartLinkId())
							.capacity(dvrpVehicleSpecficationToBeCloned.getCapacity())
							.build();

					fleetSpecification.addVehicleSpecification(newSpecification);
				}
			}
		}
	}

}
