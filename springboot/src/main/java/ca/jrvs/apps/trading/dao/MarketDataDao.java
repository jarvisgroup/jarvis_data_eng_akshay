package ca.jrvs.apps.trading.dao;

import ca.jrvs.apps.trading.model.config.MarketDataConfig;
import ca.jrvs.apps.trading.model.domain.IexQuote;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;


@Repository
public class MarketDataDao implements CrudRepository<IexQuote, String> {

  private static final String IEX_BATCH_PATH = "/stock/market/batch?symbols=%s&types=quote&token=";
  private String IEX_BATCH_URL;

  private Logger logger = LoggerFactory.getLogger(MarketDataDao.class);
  private HttpClientConnectionManager httpClientConnectionManager;

  /**
   * @param httpClientConnectionManager
   * @param marketDataConfig
   */
  @Autowired
  public MarketDataDao(HttpClientConnectionManager httpClientConnectionManager,
      MarketDataConfig marketDataConfig) {
    this.httpClientConnectionManager = httpClientConnectionManager;
    IEX_BATCH_URL = marketDataConfig.getHost() + IEX_BATCH_PATH + marketDataConfig.getToken();
  }

  @Override
  public <S extends IexQuote> S save(S s) {
    return null;
  }

  @Override
  public <S extends IexQuote> Iterable<S> saveAll(Iterable<S> iterable) {
    return null;
  }

  @Override
  public Optional<IexQuote> findById(String ticker) {
    Optional<IexQuote> iexQuote;

    List<IexQuote> quotes = (List<IexQuote>) findAllById(Collections.singletonList(ticker));

    if (quotes.size() == 0) {
      return Optional.empty();
    } else if (quotes.size() == 1) {
      iexQuote = Optional.of(quotes.get(0));
    } else {
      throw new DataRetrievalFailureException("Unexpected number of quotes");
    }
    return iexQuote;

  }

  @Override
  public Iterable<IexQuote> findAllById(Iterable<String> tickers) {
    int tickerCount = 0;
    for (String ticker : tickers) {
      tickerCount++;
    }
    if (tickerCount == 0) {
      throw new IllegalArgumentException("Please specify at least one ticker");
    }
    List<IexQuote> quotes = new ArrayList<>();
    String stringTickers = String.join(",", tickers);
    String url = String.format(IEX_BATCH_URL, stringTickers);
    Optional<String> response = executeHttpGet(url);
    JSONObject jsonObject = new JSONObject(response.get());
    ObjectMapper mapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    IexQuote quote;
    for (String ticker : tickers) {

      try {
        String stringQuote = jsonObject.getJSONObject(ticker).getJSONObject("quote").toString();
        quote = mapper.readValue(stringQuote, IexQuote.class);

      } catch (IOException e) {
        throw new RuntimeException("Cannot convert JSON to Iexquote object.");
      } catch (JSONException e) {
        throw new IllegalArgumentException("Ticker not found");
      }
      quotes.add(quote);
    }

    return quotes;
  }


  @Override
  public boolean existsById(String s) {
    return false;
  }

  @Override
  public Iterable<IexQuote> findAll() {
    return null;
  }


  @Override
  public long count() {
    return 0;
  }

  @Override
  public void deleteById(String s) {

  }

  @Override
  public void delete(IexQuote iexQuote) {

  }

  @Override
  public void deleteAll(Iterable<? extends IexQuote> iterable) {

  }

  @Override
  public void deleteAll() {

  }

  /**
   * Execute a get and return http entity/body as a string
   *
   * @param url of resource
   * @return http response body
   * @throws DataRetrievalFailureException if fail
   */
  private Optional<String> executeHttpGet(String url)
      throws DataRetrievalFailureException {
    HttpClient httpClient = getHttpClient();
    HttpGet httpGet = new HttpGet(url);

    try {
      HttpResponse response = httpClient.execute(httpGet);
      int responseStatus = response.getStatusLine().getStatusCode();
      if (responseStatus == 200) {
        return Optional.of(EntityUtils.toString(response.getEntity()));
      } else if (responseStatus == 404) {
        return Optional.empty();
      } else {
        throw new DataRetrievalFailureException("Response Code: " + responseStatus);
      }
    } catch (IOException e) {
      throw new DataRetrievalFailureException("Invalid URI; couldn't execute httpGet request");
    }

  }

  /**
   * Borrow a Http client form httpClientConnectionManager
   *
   * @return httpClient
   */
  private CloseableHttpClient getHttpClient() {
    return HttpClients.custom()
        .setConnectionManager(httpClientConnectionManager)
        .setConnectionManagerShared(true)
        .build();
  }
}

