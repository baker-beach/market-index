package com.bakerbeach.market.index.service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Currency;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.common.SolrInputDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bakerbeach.market.index.model.IndexContext;
import com.bakerbeach.market.xcatalog.model.Price;
import com.bakerbeach.market.xcatalog.model.Product;
import com.bakerbeach.market.xcatalog.model.Product.Status;

public class XIndexServiceImpl implements XIndexService {
	protected static final Logger log = LoggerFactory.getLogger(XIndexServiceImpl.class);

	@Override
	public void index(List<Product> products, Status status, Date lastUpdate, IndexContext context) {
		for (Product product : products) {
			index(product, status, lastUpdate, context);
		}
	}
	
	public void index(Product product, Product.Status status, Date lastUpdate, IndexContext context) {		
		try {
			String url = context.getSolrUrls().get(status.name());
			SolrServer solr = SolrServerCache.getServer(url);
			
			
			Date indexTime = new Date();

			// get all relevant time spans
			List<Date> dates = new ArrayList<Date>();
			dates.add(indexTime);
			dates.add(getDefaultTo().getTime());

			List<Price> prices = product.getPrices();
			for (Price price : prices) {
				Date start = price.getStart();
				if (start != null && start.after(indexTime)) {
					dates.add(start);
				}
				Collections.sort(dates);
			}
			
			List<SolrInputDocument> docs = new ArrayList<SolrInputDocument>();
			Iterator<Date> iterator = dates.iterator();
			for (Date to = iterator.next(); iterator.hasNext();) {
				Date from = to;
				to = iterator.next();
				
				try {
					SolrInputDocument doc = getSolrInputDocument(context.getShopCode(), product, lastUpdate, from, to, context.getLocales(),
							context.getCurrencies(), context.getPriceGroups());
					docs.add(doc);
				} catch (Exception e) {
					log.error(ExceptionUtils.getStackTrace(e));
				}
			}

			if (!docs.isEmpty()) {
				solr.add(docs, 10000);
				log.info(docs.toString());
			}
			
		} catch (Exception e) {
			log.error(ExceptionUtils.getStackTrace(e));
		}
	}

	private SolrInputDocument getSolrInputDocument(String shopCode, Product product, Date lastUpdate, Date from,
			Date to, List<Locale> locales, List<Currency> currencies, List<String> priceGroups) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void index(List<Product> products, String code, Status status, Date lastUpdate, List<Locale> locales,
			List<Currency> currencies, List<String> priceGroups) {
		try {
			String solrCollectionCode = code.concat("_").concat(status.name()).toLowerCase();
			SolrServer solr = SolrServerCache.getServer(solrCollectionCode);

			System.out.println("test");

		} catch (Exception e) {
			log.error(ExceptionUtils.getStackTrace(e));
		}
	}

	@Override
	public void delete(List<String> codes, String solrCode, String status) {
		// TODO Auto-generated method stub

	}

	@Override
	public void delete(String codes, String solrCode, String status) {
		// TODO Auto-generated method stub

	}

	private Calendar getDefaultTo() {
		Calendar cal = new GregorianCalendar(Locale.GERMANY);
		cal.clear();
		cal.set(2020, 0, 1);

		return cal;
	}


}
