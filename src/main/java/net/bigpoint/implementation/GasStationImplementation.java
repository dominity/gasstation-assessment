package net.bigpoint.implementation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import net.bigpoint.assessment.gasstation.GasPump;
import net.bigpoint.assessment.gasstation.GasStation;
import net.bigpoint.assessment.gasstation.GasType;
import net.bigpoint.assessment.gasstation.exceptions.GasTooExpensiveException;
import net.bigpoint.assessment.gasstation.exceptions.NotEnoughGasException;

/**
 * @author dominity
 *
 * <p>
 * Thread-safe implementation of {@link GasStation}.
 * </p>
 */
public final class GasStationImplementation implements GasStation {
	
	// counter for NotEnoughGasException thrown
	private int notEnoughCounter = 0;
	
	// counter for GasTooExpensiveException thrown
	private int tooExpensiveCounter = 0;
	
	// counter for successful sales
	private int successCounter = 0;
	
	// amount of revenue
	private double revenue = 0;
	
	// collection of gas pumps handles by this gas station
	private final Collection<GasPump> gasPumps = new ArrayList<GasPump>();
	
	// gas pumps cached by GasType for easier access
	private final Map<GasType, Collection<GasPump>> gasPumpsMap = new HashMap<GasType, Collection<GasPump>>();
	
	// price by gas type
	private final Map<GasType, Double> gasPrices = new HashMap<GasType, Double>();
	
	// objects that is used as monitor to lock actions related to price handling	
	private final Object priceMonitor = new Object();
	
	// objects that is used as monitor to lock actions related to gas pump handling
	private final Object gasPumpMonitor = new Object();
	
	@Override
	public void addGasPump( GasPump gasPump ) {
		if ( gasPump == null ) {
			throw new NullPointerException( "Passed gas pump is null." );
		}
		
		if ( gasPump.getGasType() == null ) {
			throw new NullPointerException( "Passed gas pump has no gas type." );
		}
		
		synchronized ( gasPumpMonitor ) {
			gasPumps.add( gasPump );
		
			cacheGasPump( gasPump );
		}
	}

	@Override
	public Collection<GasPump> getGasPumps() {
		synchronized ( gasPumpMonitor ) {
			return new ArrayList<GasPump>( gasPumps );
		}
	}

	@Override
	public double buyGas( GasType type, double amountInLiters, double maxPricePerLiter )
			throws NotEnoughGasException, GasTooExpensiveException {
		if ( type == null ) {
			throw new NullPointerException( "Gas type is null." );
		}
		
		if ( amountInLiters <= 0 ) {
			throw new IllegalArgumentException( "Amount can't be nagative or null." );
		}

		if ( maxPricePerLiter <= 0 ) {
			throw new IllegalArgumentException( "Price can't be nagative or null." );
		}
		
		final double price;
		synchronized ( priceMonitor ) {
			price = getPrice( type );
		}
		
		final GasPump gasPump; 
		synchronized ( gasPumpMonitor ) {
			checkPrice( price, maxPricePerLiter );
			gasPump = findGasPump( type, amountInLiters );
		}
		
		pumpGas( gasPump, amountInLiters );
		
		double amount = price * amountInLiters;
		
		synchronized ( gasPumpMonitor ) {
			cacheGasPump( gasPump );
			notifySuccessfulTransaction( amount );
		}
		
		return amount;
	}

	@Override
	public double getPrice( GasType type ) {
		if ( type == null ) {
			throw new NullPointerException( "Gas type is null." );
		}
		
		synchronized ( priceMonitor ) {
			Double price = gasPrices.get( type );
			return price == null ? 0 : price.doubleValue();
		}
	}

	@Override
	public void setPrice( GasType type, double price ) {
		if ( type == null ) {
			throw new NullPointerException( "Gas type is null." );
		}
		
		if ( price <= 0 ) {
			throw new IllegalArgumentException( "Price can't be nagative or null." );
		}
		
		synchronized ( priceMonitor ) {
			gasPrices.put( type, new Double( price ) );
		}
	}

	@Override
	public int getNumberOfCancellationsNoGas() {
		synchronized ( gasPumpMonitor ) {
			return notEnoughCounter;
		}
	}

	@Override
	public int getNumberOfCancellationsTooExpensive() {
		synchronized ( gasPumpMonitor ) {
			return tooExpensiveCounter;
		}
	}

	@Override
	public int getNumberOfSales() {
		synchronized ( gasPumpMonitor ) {
			return successCounter;
		}
	}

	@Override
	public double getRevenue() {
		synchronized ( gasPumpMonitor ) {
			return revenue;
		}
	}

	// adds gas pump to map for easier access
	private void cacheGasPump( GasPump gasPump ) {
		Collection<GasPump> gasPumps = gasPumpsMap.get( gasPump.getGasType() );
		if ( gasPumps == null ) {
			gasPumps = new ArrayList<GasPump>();
			gasPumpsMap.put( gasPump.getGasType(), gasPumps );
		}
		
		gasPumps.add( gasPump );
	}

	// finds gas pump with requested gas type that can provide requested amount 
	// of gas  
	private GasPump findGasPump( GasType type, double amountInLiters ) 
			throws NotEnoughGasException {
		Collection<GasPump> gasPumps = gasPumpsMap.get( type );
		if ( gasPumps == null ) {
			notifyNotEnoughGas();
			throw new NotEnoughGasException();
		}
		
		for ( GasPump gasPump : gasPumps ) {
			if ( gasPump.getRemainingAmount() >= amountInLiters ) {
				gasPumps.remove( gasPump );
				return gasPump;
			}
		}
		
		notifyNotEnoughGas();
		throw new NotEnoughGasException();
	}
	
	// checks whether requested price is appropriated
	private void checkPrice( double currentPrice, double maxPricePerLiter ) throws GasTooExpensiveException {
		if ( currentPrice > maxPricePerLiter ) {
			notifyTooExpensive();
			throw new GasTooExpensiveException();
		}
	}
	
	// pumps requested amount of gas from selected gas pump
	private void pumpGas( GasPump gasPump, double amountInLiters ) {
		gasPump.pumpGas( amountInLiters );
	}
	
	private void notifyNotEnoughGas() {
		notEnoughCounter ++;
	}

	private void notifyTooExpensive() {
		tooExpensiveCounter ++;
	}
	
	private void notifySuccessfulTransaction( double amount ) {
		revenue += amount;
		successCounter ++;
	}
	
}
