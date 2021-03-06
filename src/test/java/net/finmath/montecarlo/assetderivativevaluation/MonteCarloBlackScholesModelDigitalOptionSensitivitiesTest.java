/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christianfries.com.
 *
 * Created on 15.06.2016
 */

package net.finmath.montecarlo.assetderivativevaluation;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import net.finmath.exception.CalculationException;
import net.finmath.functions.AnalyticFormulas;
import net.finmath.montecarlo.BrownianMotion;
import net.finmath.montecarlo.RandomVariableFactory;
import net.finmath.montecarlo.assetderivativevaluation.products.DigitalOption;
import net.finmath.montecarlo.automaticdifferentiation.RandomVariableDifferentiableInterface;
import net.finmath.montecarlo.automaticdifferentiation.backward.RandomVariableDifferentiableAADFactory;
import net.finmath.montecarlo.model.AbstractModel;
import net.finmath.montecarlo.process.AbstractProcess;
import net.finmath.montecarlo.process.ProcessEulerScheme;
import net.finmath.stochastic.RandomVariableInterface;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationInterface;

/**
 * @author Christian Fries
 *
 */
public class MonteCarloBlackScholesModelDigitalOptionSensitivitiesTest {

	// Model properties
	private final double	modelInitialValue   = 1.0;
	private final double	modelRiskFreeRate   = 0.05;
	private final double	modelVolatility     = 0.30;

	// Process discretization properties
	private final int		numberOfPaths		= 1000000;
	private final int		numberOfTimeSteps	= 10;
	private final double	deltaT				= 0.5;
	
	private final int		seed				= 31415;

	// Product properties
	private final int		assetIndex = 0;
	private final double	optionMaturity = 2.0;
	private final double	optionStrike = 1.05;

	@Test
	public void testProductAADSensitivities() throws CalculationException {
		RandomVariableDifferentiableAADFactory randomVariableFactory = new RandomVariableDifferentiableAADFactory(new RandomVariableFactory());

		// Generate independent variables (quantities w.r.t. to which we like to differentiate)
		RandomVariableDifferentiableInterface initialValue	= randomVariableFactory.createRandomVariable(modelInitialValue);
		RandomVariableDifferentiableInterface riskFreeRate	= randomVariableFactory.createRandomVariable(modelRiskFreeRate);
		RandomVariableDifferentiableInterface volatility	= randomVariableFactory.createRandomVariable(modelVolatility);

		// Create a model
		AbstractModel model = new BlackScholesModel(initialValue, riskFreeRate, volatility, randomVariableFactory);

		// Create a time discretization
		TimeDiscretizationInterface timeDiscretization = new TimeDiscretization(0.0 /* initial */, numberOfTimeSteps, deltaT);

		// Create a corresponding MC process
		AbstractProcess process = new ProcessEulerScheme(new BrownianMotion(timeDiscretization, 1 /* numberOfFactors */, numberOfPaths, seed));

		// Using the process (Euler scheme), create an MC simulation of a Black-Scholes model
		AssetModelMonteCarloSimulationInterface monteCarloBlackScholesModel = new MonteCarloAssetModel(model, process);

		/*
		 * Value a call option (using the product implementation)
		 */
		DigitalOption digitalOption = new DigitalOption(optionMaturity, optionStrike);
		RandomVariableInterface value = (RandomVariableDifferentiableInterface) digitalOption.getValue(0.0, monteCarloBlackScholesModel);

		/*
		 * Calculate sensitivities using AAD
		 */
		Map<Long, RandomVariableInterface> derivative = ((RandomVariableDifferentiableInterface)value).getGradient();
		
		double valueMonteCarlo = value.getAverage();
		double deltaAAD = derivative.get(initialValue.getID()).getAverage();
		double rhoAAD = derivative.get(riskFreeRate.getID()).getAverage();
		double vegaAAD = derivative.get(volatility.getID()).getAverage();
		
		/*
		 * Calculate sensitivities using finite differences
		 */
		
		double eps = 1E-3;

		double epsDelta = eps;
		Map<String, Object> dataModifiedInitialValue = new HashMap<String, Object>();
		dataModifiedInitialValue.put("initialValue", modelInitialValue+eps);
		double deltaFiniteDifference = (digitalOption.getValue(monteCarloBlackScholesModel.getCloneWithModifiedData(dataModifiedInitialValue)) - valueMonteCarlo)/epsDelta;

		double epsRho = eps/100;
		Map<String, Object> dataModifiedRiskFreeRate = new HashMap<String, Object>();
		dataModifiedRiskFreeRate.put("riskFreeRate", modelRiskFreeRate+epsRho);
		double rhoFiniteDifference = (digitalOption.getValue(monteCarloBlackScholesModel.getCloneWithModifiedData(dataModifiedRiskFreeRate)) - valueMonteCarlo)/epsRho ;

		double epsVega = eps/10;
		Map<String, Object> dataModifiedVolatility = new HashMap<String, Object>();
		dataModifiedVolatility.put("volatility", modelVolatility+epsVega);
		double vegaFiniteDifference = (digitalOption.getValue(monteCarloBlackScholesModel.getCloneWithModifiedData(dataModifiedVolatility)) - valueMonteCarlo)/epsVega ;

		/*
		 * Calculate sensitivities using analytic formulas
		 */
		double valueAnalytic = AnalyticFormulas.blackScholesDigitalOptionValue(modelInitialValue, modelRiskFreeRate, modelVolatility, optionMaturity, optionStrike);
		double deltaAnalytic = AnalyticFormulas.blackScholesDigitalOptionDelta(modelInitialValue, modelRiskFreeRate, modelVolatility, optionMaturity, optionStrike);
		double rhoAnalytic	= AnalyticFormulas.blackScholesDigitalOptionRho(modelInitialValue, modelRiskFreeRate, modelVolatility, optionMaturity, optionStrike);
		double vegaAnalytic	= AnalyticFormulas.blackScholesDigitalOptionVega(modelInitialValue, modelRiskFreeRate, modelVolatility, optionMaturity, optionStrike);

		System.out.println("value using Monte-Carlo.......: " + valueMonteCarlo);
		System.out.println("value using analytic formula..: " + valueAnalytic);
		System.out.println("");
		
		System.out.println("delta using adj. auto diff....: " + deltaAAD);
		System.out.println("delta using finite differences: " + deltaFiniteDifference);
		System.out.println("delta using analytic formula..: " + deltaAnalytic);
		System.out.println("");

		System.out.println("rho using adj. auto diff....: " + rhoAAD);
		System.out.println("rho using finite differences: " + rhoFiniteDifference);
		System.out.println("rho using analytic formula..: " + rhoAnalytic);
		System.out.println("");

		System.out.println("vega using Madj. auto diff...: " + vegaAAD);
		System.out.println("vega using finite differences: " + vegaFiniteDifference);
		System.out.println("vega using analytic formula..: " + vegaAnalytic);
		System.out.println("");

//		Assert.assertEquals(valueAnalytic, value, 0.005);
	}
}
