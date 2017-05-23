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
			
			
			Date indexTime = new Date();

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

	protected SolrInputDocument getSolrInputDocument(String shop, Product product, Date lastUpdate, Date from,
			Date to, List<Locale> locales, List<Currency> currencies, List<String> priceGroups) {
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

		for (Currency currency : currencies) {
			try {
				for (String priceGroup : priceGroups) {
					String key = new StringBuilder(currency.getCurrencyCode()).append("_").append(priceGroup)
							.append("_price").toString().toLowerCase();
					
					Price price = product.getPrice(currency, priceGroup, from);
					BigDecimal value = price.getValue();
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

		String assetSize = "m";
		List<Asset> assets = product.getAssets("listing", assetSize);
		for (int i = 0; i < assets.size(); i++) {
			Asset asset = assets.get(i);

			String pathCodeField = new StringBuilder("listing_").append(assetSize).append("_").append(i)
					.append("_asset_path").toString().toLowerCase();
			doc.addField(pathCodeField, asset.getPath());

			String typeCodeField = new StringBuilder("listing_").append(assetSize).append("_").append(i)
					.append("_asset_type").toString().toLowerCase();
			doc.addField(typeCodeField, asset.getType());
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
		
		/*
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

		if (product.get("colorpicker") != null) {
			for (String colorpicker : (List<String>) product.get("colorpicker")) {
				addI18NFields(doc, shop, "colorpicker", colorpicker, "color", "text", locales, true);			
			}			
		}

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
		*/

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
