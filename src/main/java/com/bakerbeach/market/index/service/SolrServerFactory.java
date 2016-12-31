package com.bakerbeach.market.index.service;

import java.util.HashMap;
import java.util.Map;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer;

public class SolrServerFactory {
	private static Map<String, String> urls;
	private static Map<String, SolrServer> servers = new HashMap<String, SolrServer>();

	public SolrServerFactory(Map<String, String> urls) {
		SolrServerFactory.urls = urls;
	}

	public static SolrServer getServer(String key) {
		SolrServer server = servers.get(key);
		if (server != null) {
			return server;
		} else {			
			server = new HttpSolrServer(getUrl(key));
			servers.put(key, server);
			return server;
		}		
	}

	private static String getUrl(String key) {
		return urls.get(key);
	}

}
