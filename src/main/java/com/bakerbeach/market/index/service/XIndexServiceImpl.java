package com.bakerbeach.market.index.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Currency;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.common.SolrInputDocument;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.bakerbeach.market.index.model.IndexContext;
import com.bakerbeach.market.inventory.api.model.InventoryStatus;
import com.bakerbeach.market.inventory.api.service.InventoryService;
import com.bakerbeach.market.translation.api.service.TranslationService;
import com.bakerbeach.market.xcatalog.model.Asset;
import com.bakerbeach.market.xcatalog.model.Price;
import com.bakerbeach.market.xcatalog.model.Product;
import com.bakerbeach.market.xcatalog.model.Product.Status;

public class XIndexServiceImpl implements XIndexService {
	protected static final Logger log = LoggerFactory.getLogger(XIndexServiceImpl.class);

	@Autowired
	private TranslationService translationService;

	@Autowired(required = false)
	private InventoryService inventoryService;

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

			// delete existing entries
			try {
				String q = new StringBuilder("code:").append(product.getCode()).toString();
				solr.deleteByQuery(q, 10000);
			} catch (Exception e) {
				log.error(ExceptionUtils.getStackTrace(e));
			}
			
			if (product.isIndexed() != null && !product.isIndexed()) {
				return;
			}
			
			// get all relevant time spans
			List<Date> dates = new ArrayList<Date>();
			dates.add(getDefaultTo().getTime());
			product.getPrices().forEach(price -> {
				try {
					dates.add(price.getStart());
				} catch (Exception e) {
				}
			});
			Collections.sort(dates);

			List<SolrInputDocument> docs = new ArrayList<SolrInputDocument>();
			Iterator<Date> iterator = dates.iterator();
			for (Date to = iterator.next(); iterator.hasNext();) {
				Date from = to;
				to = iterator.next();

				try {
					SolrInputDocument doc = getSolrInputDocument(context.getShopCode(), product, lastUpdate, from, to,
							context.getLocales(), context.getCurrencies(), context.getPriceGroups());
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

	protected SolrInputDocument getSolrInputDocument(String shop, Product product, Date lastUpdate, Date from, Date to,
			List<Locale> locales, List<Currency> currencies, List<String> priceGroups) {
		SolrInputDocument doc = new SolrInputDocument();

		doc.addField("last_update", lastUpdate);
		doc.addField("active_from", from);
		doc.addField("active_to", to);

		String code = product.getCode();
		doc.addField("code", code);

		String id = new StringBuilder(code).append("-").append(from).toString();
		doc.addField("id", id);

		String type = product.getType().name();
		doc.addField("type", type);

		String unit = product.getUnit().name();
		doc.setField("unit_code", unit);

		String primaryGroup = product.getPrimaryGroup();
		doc.addField("primary_group", primaryGroup);

		String secondaryGroup = product.getSecondaryGroup();
		doc.addField("secondary_group", secondaryGroup);

		String brand = product.getBrand();
		addI18NFields(doc, shop, "brand", brand, "index.brand", "text", locales, false);

		String name = product.getName();
		addI18NFields(doc, shop, "name", name, "index.name", "text", locales, false);

		List<String> categories = product.getCategories();
		for (String category : categories) {
			addI18NFields(doc, shop, "category", category, "category", "text", locales, true);
		}

		for (Price price : product.getPrices()) {
			String key = new StringBuilder(price.getCurrency().getCurrencyCode()).append("_").append(price.getGroup())
					.append("_").append(price.getTag()).append("_price").toString().toLowerCase();
			BigDecimal value = price.getValue();
			doc.addField(key, value);
		}

		BigDecimal basePrice1Divisor = product.getBasePrice1Divisor();
		String basePrice1Unit = product.getBasePrice1Unit();
		if (basePrice1Divisor != null && StringUtils.isNotBlank(basePrice1Unit)) {
			doc.setField("base_price_1_divisor", basePrice1Divisor);
			doc.setField("base_price_1_unit_code", basePrice1Unit);
		}

		BigDecimal basePrice2Divisor = product.getBasePrice2Divisor();
		String basePrice2Unit = product.getBasePrice2Unit();
		if (basePrice2Divisor != null && StringUtils.isNotBlank(basePrice2Unit)) {
			doc.setField("base_price_2_divisor", basePrice2Divisor);
			doc.setField("base_price_2_unit_code", basePrice2Unit);
		}

		for (String key : product.getLogos().keySet()) {
			String codeField = new StringBuilder("logos_").append(key).append("_codes").toString().toLowerCase();
			doc.addField(codeField, product.getLogos().get(key));
		}

		for (String key : product.getTags().keySet()) {
			String codeField = new StringBuilder("tags_").append(key).append("_codes").toString().toLowerCase();
			doc.addField(codeField, product.getTags().get(key));
		}

		try {
			Map<String, List<Map<String, Asset>>> assets = product.getAssets();

			ObjectMapper mapper = new ObjectMapper();
			doc.setField("assets", mapper.writeValueAsString(assets));

		} catch (Exception e) {
			log.error(String.format("error while writing assets on %s", code));
		}

		if (inventoryService != null) {
			try {
				InventoryStatus status = inventoryService.getInventoryStatus(code);

				for (String priceGroup : priceGroups) {
					// TODO: get buffer per price group and gtin

					Integer stock = status.getStock();
					Integer outOfStockLimit = status.getOutOfStockLimit();
					Integer moq = stock - outOfStockLimit;

					String moqFieldName = new StringBuilder(priceGroup).append("_moq").toString().toLowerCase();
					doc.addField(moqFieldName, moq);

					String visibleFieldName = new StringBuilder(priceGroup).append("_available").toString()
							.toLowerCase();
					doc.addField(visibleFieldName, (moq > 0) ? 1 : 0);
				}
			} catch (Exception e) {
				log.error(String.format("inventory error on %s", code));

				for (String priceGroup : priceGroups) {
					String moqFieldName = new StringBuilder(priceGroup).append("_moq").toString().toLowerCase();
					doc.addField(moqFieldName, 0);

					String visibleFieldName = new StringBuilder(priceGroup).append("_available").toString()
							.toLowerCase();
					doc.addField(visibleFieldName, 0);
				}
			}
		}

		return doc;
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

	protected void addI18NFields(SolrInputDocument doc, String shop, String field, String code, String translationTag,
			String translationType, List<Locale> locales, Boolean multi) {
		String codeField = getCodeField(field, multi);
		doc.addField(codeField, code);

		for (Locale locale : locales) {
			String langField = getLocaleField(field, locale, multi);
			String msg = translationService.getMessage(translationTag, translationType, code, null, code, locale);
			doc.addField(langField, msg);
		}
	}

	protected String getCodeField(String field, Boolean multi) {
		StringBuilder sb = new StringBuilder(field);
		if (multi)
			sb.append("_codes");
		else
			sb.append("_code");

		return sb.toString();
	}

	protected String getLocaleField(String field, Locale locale, Boolean multi) {
		StringBuilder sb = new StringBuilder(field);
		sb.append("_").append(locale.getLanguage());
		if (multi)
			sb.append("s");

		return sb.toString();
	}

	private Calendar getDefaultTo() {
		Calendar cal = new GregorianCalendar(Locale.GERMANY);
		cal.clear();
		cal.set(2020, 0, 1);

		return cal;
	}

}
