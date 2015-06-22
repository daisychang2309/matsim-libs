/* *********************************************************************** *
 * project: org.matsim.*
 * TestControler.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2011 by the members listed in the COPYING,        *
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

/**
 * 
 */
package playground.ikaddoura;


import java.io.IOException;

import org.apache.log4j.Logger;
import org.matsim.contrib.otfvis.OTFVisModule;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.ScenarioImpl;
import org.matsim.vis.otfvis.OTFFileWriterFactory;

import playground.vsp.congestion.controler.MarginalCongestionPricingContolerListener;
import playground.vsp.congestion.handlers.CongestionHandlerImplV3;
import playground.vsp.congestion.handlers.TollHandler;
import playground.vsp.congestion.routing.TollDisutilityCalculatorFactory;

/**
 * @author ikaddoura
 *
 */
public class CongestionPricingControlerEqualVTTS {

	private static final Logger log = Logger.getLogger(CongestionPricingControlerEqualVTTS.class);

	static String configFile;

	public static void main(String[] args) throws IOException {
		log.info("Starting simulation run with equal VTTS...");

		if (args.length > 0) {

			configFile = args[0];		
			log.info("config file: "+ configFile);

		} else {
			configFile = "../../runs-svn/internalizationCar/input/config_internalizationCar.xml";
		}

		CongestionPricingControlerEqualVTTS main = new CongestionPricingControlerEqualVTTS();
		main.run();
	}

	private void run() {

		Controler controler = new Controler(configFile);

		TollHandler tollHandler = new TollHandler(controler.getScenario());
		final TollDisutilityCalculatorFactory tollDisutilityCalculatorFactory = new TollDisutilityCalculatorFactory(tollHandler);
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				bindTravelDisutilityFactory().toInstance(tollDisutilityCalculatorFactory);
			}
		});

		controler.addControlerListener(new MarginalCongestionPricingContolerListener(controler.getScenario(), tollHandler, new CongestionHandlerImplV3(controler.getEvents(), (ScenarioImpl) controler.getScenario())));

		controler.addOverridingModule(new OTFVisModule());
		controler.getConfig().controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		controler.run();

	}
}

