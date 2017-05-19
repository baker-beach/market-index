package com.bakerbeach.market.index.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.LocaleUtils;

public class IndexContextImpl implements IndexContext {
	private String shopCode;
	private List<Locale> locales;
	private List<Currency> currencies;
	private List<String> priceGroups;
	private Map<String, String> solrUrls = new HashMap<String, String>();

	/* (non-Javadoc)
	 * @see com.bakerbeach.market.index.model.IndexContext#getShopCode()
	 */
	@Override
	public String getShopCode() {
		return shopCode;
	}

	public void setShopCode(String shopCode) {
		this.shopCode = shopCode;
	}

	/* (non-Javadoc)
	 * @see com.bakerbeach.market.index.model.IndexContext#getLocales()
	 */
	@Override
	public List<Locale> getLocales() {
		return locales;
	}

	public void setLocales(List<Locale> locales) {
		this.locales = locales;
	}

	public void setLocalesString(String localesStr) {
		this.locales = new ArrayList<Locale>();
		for (String str : localesStr.split(",")) {
			this.locales.add(LocaleUtils.toLocale(str));
		}
	}

	/* (non-Javadoc)
	 * @see com.bakerbeach.market.index.model.IndexContext#getCurrencies()
	 */
	@Override
	public List<Currency> getCurrencies() {
		return currencies;
	}

	/* (non-Javadoc)
	 * @see com.bakerbeach.market.index.model.IndexContext#setCurrencies(java.util.List)
	 */
	@Override
	public void setCurrencies(List<Currency> currencies) {
		this.currencies = currencies;
	}

	public void setCurrenciesString(String currenciesStr) {
		this.currencies = new ArrayList<Currency>();
		for (String str : currenciesStr.split(",")) {
			this.currencies.add(Currency.getInstance(str));
		}
	}

	/* (non-Javadoc)
	 * @see com.bakerbeach.market.index.model.IndexContext#getPriceGroups()
	 */
	@Override
	public List<String> getPriceGroups() {
		return priceGroups;
	}

	public void setPriceGroups(List<String> priceGroups) {
		this.priceGroups = priceGroups;
	}

	public void setPriceGroupsString(String priceGroupsStr) {
		setPriceGroups(Arrays.asList(priceGroupsStr.split(",")));
	}
	
	@Override
	public Map<String, String> getSolrUrls() {
		return solrUrls;
	}
	
	public void setSolrUrls(Map<String, String> solrUrls) {
		this.solrUrls = solrUrls;
	}

}
