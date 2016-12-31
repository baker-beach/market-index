package com.bakerbeach.market.index.utils;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Date;
import java.util.List;

import com.bakerbeach.market.core.api.model.ScaledPrice;

public class PriceUtils {
	Currency defaultCurrency = Currency.getInstance("EUR");

	public static BigDecimal getStdPrice(List<ScaledPrice> prices, Currency currency, String group, Date date) {
		ScaledPrice price = null;
		ScaledPrice defaultPrice = null;

		for (ScaledPrice p : prices) {
			if (currency.equals(p.getCurrency())) {
				if (group.equals(p.getGroup())) {
					price = p;
				} else if ("default".equals(p.getGroup())) {
					defaultPrice = p;
				}
			}
		}
		
		return (price != null) ? price.getValue() : defaultPrice.getValue();
	}

	public static BigDecimal getPrice(List<ScaledPrice> prices, Currency currency, String group, Date date) {
		ScaledPrice price = null;
		ScaledPrice defaultPrice = null;

		for (ScaledPrice p : prices) {
			Date start = p.getStart();
			if (!start.after(date)) {
				if (currency.equals(p.getCurrency())) {
					if (group.equals(p.getGroup())) {
						if (price == null) {
							price = p;
						} else if (price.getStart().before(p.getStart())) {
							price = p;
						}
					} else if ("default".equals(p.getGroup())) {
						if (defaultPrice == null) {
							defaultPrice = p;
						} else if (defaultPrice.getStart().before(p.getStart())) {
							defaultPrice = p;
						}
					}
				}
			}
		}

		return (price != null) ? price.getValue() : (defaultPrice != null) ? defaultPrice.getValue() : null;
	}

}
