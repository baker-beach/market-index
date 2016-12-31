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
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.common.SolrInputDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bakerbeach.market.catalog.model.RawGroupTag;
import com.bakerbeach.market.catalog.model.RawProduct;
import com.bakerbeach.market.core.api.model.Asset;
import com.bakerbeach.market.core.api.model.AssetGroup;
import com.bakerbeach.market.core.api.model.Assets;
import com.bakerbeach.market.core.api.model.ScaledPrice;
import com.bakerbeach.market.core.api.model.Type;
import com.bakerbeach.market.index.utils.PriceUtils;
import com.bakerbeach.market.inventory.api.model.InventoryStatus;
import com.bakerbeach.market.inventory.api.service.InventoryService;
import com.bakerbeach.market.translation.api.service.TranslationService;

public class IndexServiceImpl implements IndexService {
	protected static final Logger log = LoggerFactory.getLogger(IndexServiceImpl.class);

	private InventoryService inventoryService;
	private TranslationService translationService;

	@Override
	public void index(List<RawProduct> products, String code, String status, Date lastUpdate, List<Locale> locales,
			List<Currency> currencies, List<String> priceGroups) {
		for (RawProduct product : products) {
			index(product, code, status, lastUpdate, locales, currencies, priceGroups);
		}
	}

	@Override
	public void index(RawProduct product, String code, String status, Date lastUpdate, List<Locale> locales,
			List<Currency> currencies, List<String> priceGroups) {
		try {
			String solrCollectionCode = code.concat("_").concat(status).toLowerCase();
			SolrServer solr = SolrServerFactory.getServer(solrCollectionCode);

			// delete existing entries
			try {
				String q = new StringBuilder("gtin:").append(product.getGtin()).toString();
				solr.deleteByQuery(q);
			} catch (Exception e) {
				log.error(ExceptionUtils.getStackTrace(e));
			}

			if (!product.isIndex()) {
				log.info(String.format("indexed is false for gtin=%s", product.getGtin()));
				return;
			}

			Date indexTime = product.getStartDate();

			// get all relevant time spans
			List<Date> dates = new ArrayList<Date>();
			dates.add(indexTime);
			dates.add(getDefaultTo().getTime());

			List<ScaledPrice> prices = product.getPrices();
			for (ScaledPrice price : prices) {
				Date start = price.getStart();
				if (start != null && start.after(indexTime)) {
					dates.add(start);
				}
			}
			Collections.sort(dates);

			// create solr docs for each time span
			List<SolrInputDocument> docs = new ArrayList<SolrInputDocument>();
			Iterator<Date> iterator = dates.iterator();
			for (Date to = iterator.next(); iterator.hasNext();) {
				Date from = to;
				to = iterator.next();
				try {
					SolrInputDocument doc = getSolrInputDocument(code, product, lastUpdate, from, to, locales,
							currencies, priceGroups);
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

	protected SolrInputDocument getSolrInputDocument(String shop, RawProduct product, Date lastUpdate, Date from, Date to,
			List<Locale> locales, List<Currency> currencies, List<String> groups) {
		SolrInputDocument doc = new SolrInputDocument();

		String gtin = product.getGtin();
		List<String> categories = product.getCategories();
		String brand = product.getBrand();
		String size = product.getSize();
		String color = product.getColor();
		String diet = product.getDiet();
		Type type = product.getType();
		RawGroupTag primaryGroup = product.getPrimaryGroup();
		RawGroupTag secondaryGroup = product.getSecondaryGroup();

		String id = getId(doc, product.getGtin(), primaryGroup.getCode(), from);
		doc.addField("id", id);
		doc.addField("gtin", gtin);
		doc.addField("type", type);
		doc.addField("last_update", lastUpdate);
		if (primaryGroup != null) {
			doc.addField("primary_group", primaryGroup.getCode());
			if (primaryGroup.getDim1() != null) {
				doc.addField("primary_group_dim_1", primaryGroup.getDim1());
			}
			if (primaryGroup.getDim2() != null) {
				doc.addField("primary_group_dim_2", primaryGroup.getDim2());
			}
			if (primaryGroup.getSort() != null) {
				doc.addField("primary_group_sort", secondaryGroup.getSort());
			} else {
				doc.setField("primary_group_sort", product.getSort());
			}
		}
		if (secondaryGroup != null) {
			doc.addField("secondary_group", secondaryGroup.getCode());
			if (secondaryGroup.getDim1() != null) {
				doc.addField("secondary_group_dim_1", secondaryGroup.getDim1());
			}
			if (secondaryGroup.getDim2() != null) {
				doc.addField("secondary_group_dim_2", secondaryGroup.getDim2());
			}
			if (secondaryGroup.getSort() != null) {
				doc.addField("secondary_group_sort", secondaryGroup.getSort());
			} else {
				doc.setField("secondary_group_sort", product.getSort());
			}
		}
		doc.addField("active_from", from);
		doc.addField("active_to", to);
		addI18NFields(doc, shop, "brand", brand, "brand", "text", locales, false);
		addI18NFields(doc, shop, "size", size, "size", "text", locales, false);
		addI18NFields(doc, shop, "color", color, "color", "text", locales, false);
		addI18NFields(doc, shop, "diet", diet, "diet", "text", locales, false);
		addI18NFields(doc, shop, "colorpicker", (String) product.get("colorpicker"), "color", "text", locales, false);

		if (product.getVariant1() != null) {
			addI18NFields(doc, shop, "variant_1", product.getVariant1(), "variant_1", "text", locales, false);
		}
		if (product.getVariant1Sort() != null) {
			doc.addField("variant_1_sort", product.getVariant1Sort());
		}
		if (product.getVariant2() != null) {
			addI18NFields(doc, shop, "variant_2", product.getVariant2(), "variant_2", "text", locales, false);
		}
		if (product.getVariant2Sort() != null) {
			doc.addField("variant_2_sort", product.getVariant2Sort());
		}

		// doc.addField("primary_group_sort", product.getSort());
		// doc.addField("secondary_group_sort", product.getSort());

		addI18NFields(doc, shop, "name", gtin, "product.code", "text", locales, false);

		// doc.setField("url",
		// "/product/"+doc.getField("name_en").getFirstValue().toString().toLowerCase().replace("
		// ", "-")+"-"+ primaryGroup.getCode() +".html");

		for (String category : categories) {
			addI18NFields(doc, shop, "category", category, "category", "text", locales, true);
		}

		for (Currency currency : currencies) {
			try {
				for (String group : groups) {
					String key = new StringBuilder(currency.getCurrencyCode()).append("_").append(group)
							.append("_std_price").toString().toLowerCase();
					BigDecimal value = PriceUtils.getStdPrice(product.getPrices(), currency, group, null);
					doc.addField(key, value);
				}
			} catch (Exception e) {
				log.warn(String.format("missing price information for gtin=%s and currency=%s", currency.toString(),
						product.getGtin()));
			}
		}

		for (Currency currency : currencies) {
			try {
				for (String group : groups) {
					String key = new StringBuilder(currency.getCurrencyCode()).append("_").append(group)
							.append("_price").toString().toLowerCase();
					BigDecimal value = PriceUtils.getPrice(product.getPrices(), currency, group, from);
					doc.addField(key, value);
				}
			} catch (Exception e) {
				log.warn(String.format("missing price information for gtin=%s and currency=%s", currency.toString(),
						product.getGtin()));
			}
		}

		for (String key : product.getLogos().keySet()) {
			String codeField = new StringBuilder("logos_").append(key).append("_codes").toString().toLowerCase();
			doc.addField(codeField, product.getLogos().get(key));
		}

		for (String key : product.getTags().keySet()) {
			String codeField = new StringBuilder("tags_").append(key).append("_codes").toString().toLowerCase();
			doc.addField(codeField, product.getTags().get(key));
		}

		// TODO: more generic
		Assets assets = product.getAssets();
		if (assets != null) {
			List<Asset> smallListingAssets = assets.get("listing", AssetGroup.SIZE_MEDIUM);
			for (int i = 0; i < smallListingAssets.size(); i++) {
				Asset asset = smallListingAssets.get(i);
				String pathCodeField = new StringBuilder("listing_").append(AssetGroup.SIZE_MEDIUM).append("_")
						.append(i).append("_asset_path").toString().toLowerCase();
				doc.addField(pathCodeField, asset.getPath());

				String typeCodeField = new StringBuilder("listing_").append(AssetGroup.SIZE_MEDIUM).append("_")
						.append(i).append("_asset_type").toString().toLowerCase();
				doc.addField(typeCodeField, asset.getType());

				// TODO: alt text per language
			}
		}

		try {
			InventoryStatus status = inventoryService.getInventoryStatus(gtin);

			for (String group : groups) {
				// TODO: get buffer per price group and gtin

				Integer stock = status.getStock();
				Integer outOfStockLimit = status.getOutOfStockLimit();
				Integer moq = stock - outOfStockLimit;

				String moqFieldName = new StringBuilder(group).append("_moq").toString().toLowerCase();
				doc.addField(moqFieldName, moq);

				String visibleFieldName = new StringBuilder(group).append("_available").toString().toLowerCase();
				doc.addField(visibleFieldName, (moq > 0) ? 1 : 0);
			}
		} catch (Exception e) {
			log.error(String.format("error reading inventory status for gtin %s", gtin));

			for (String group : groups) {
				String moqFieldName = new StringBuilder(group).append("_moq").toString().toLowerCase();
				doc.addField(moqFieldName, 0);

				String visibleFieldName = new StringBuilder(group).append("_available").toString().toLowerCase();
				doc.addField(visibleFieldName, 0);
			}
		}

		return doc;
	}

	// private void addI18NFields(SolrInputDocument doc, String shop, String
	// field, String code, List<Locale> locales,
	// Boolean multi) {
	// addI18NFields(doc, shop, field, code, ""field, "text", locales, multi);
	// }

	private void addI18NFields(SolrInputDocument doc, String shop, String field, String code, String translationTag,
			String translationType, List<Locale> locales, Boolean multi) {
		String codeField = getCodeField(field, multi);
		doc.addField(codeField, code);

		for (Locale locale : locales) {
			String langField = getLocaleField(field, locale, multi);
			String msg = translationService.getMessage(translationTag, translationType, code, null, code, locale);
			doc.addField(langField, msg);
		}
	}

	private String getCodeField(String field, Boolean multi) {
		StringBuilder sb = new StringBuilder(field);
		if (multi)
			sb.append("_codes");
		else
			sb.append("_code");

		return sb.toString();
	}

	private String getLocaleField(String field, Locale locale, Boolean multi) {
		StringBuilder sb = new StringBuilder(field);
		sb.append("_").append(locale.getLanguage());
		if (multi)
			sb.append("s");

		return sb.toString();
	}

	private String getId(SolrInputDocument doc, String gtin, String primaryGroup, Date from) {
		StringBuilder id = new StringBuilder(gtin);
		id.append("-").append(primaryGroup);
		id.append("-").append(from);

		return id.toString();
	}

	private Calendar getDefaultTo() {
		Calendar cal = new GregorianCalendar(Locale.GERMANY);
		cal.clear();
		cal.set(2020, 0, 1);

		return cal;
	}

	public InventoryService getInventoryService() {
		return inventoryService;
	}

	public void setInventoryService(InventoryService inventoryService) {
		this.inventoryService = inventoryService;
	}

	public TranslationService getTranslationService() {
		return translationService;
	}

	public void setTranslationService(TranslationService translationService) {
		this.translationService = translationService;
	}

}
