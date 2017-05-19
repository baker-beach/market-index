package com.bakerbeach.market.index.service;

import java.util.HashMap;
import java.util.Map;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer;

public class SolrServerCache {
	private static Map<String, SolrServer> servers = new HashMap<String, SolrServer>();

	public static SolrServer getServer(String url) {
		SolrServer server = servers.get(url);
		if (server != null) {
			return server;
		} else {
			server = new HttpSolrServer(url);
			servers.put(url, server);
			return server;
		}
	}

}
