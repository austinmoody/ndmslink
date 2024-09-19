package com.lantanagroup.link;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.lantanagroup.link.model.ApiInfoModel;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.regex.Pattern;

public class Helper {
  public static final String SIMPLE_DATE_MILLIS_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
  public static final String SIMPLE_DATE_SECONDS_FORMAT = "yyyy-MM-dd'T'HH:mm:ssXXX";

  public static ApiInfoModel getVersionInfo(){
    try {
      ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
      URL buildFile = Helper.class.getClassLoader().getResource("build.yml");

      if (buildFile == null) return new ApiInfoModel("dev", "0.9.0");

      return mapper.readValue(buildFile, ApiInfoModel.class);
    } catch (IOException ex) {
      return new ApiInfoModel("dev", "0.9.0");
    }
  }

  public static String getFhirDate(LocalDateTime localDateTime) {
    Instant instant = localDateTime.atZone(ZoneId.systemDefault()).toInstant();
    Date date = Date.from(instant);
    return Helper.getFhirDate(date);
  }

  public static String getFhirDate(Date date) {
    return new SimpleDateFormat(SIMPLE_DATE_MILLIS_FORMAT).format(date);
  }

  public static Date parseFhirDate(String dateStr) throws ParseException {
    SimpleDateFormat formatterMillis = new SimpleDateFormat(SIMPLE_DATE_MILLIS_FORMAT);
    SimpleDateFormat formatterSec = new SimpleDateFormat(SIMPLE_DATE_SECONDS_FORMAT);
    Date dateReturned;
    try {
      dateReturned = formatterMillis.parse(dateStr);
    } catch (Exception ex) {
      dateReturned = formatterSec.parse(dateStr);
    }
    return dateReturned;
  }

  public static Date parseDate(String dateString) throws ParseException {
    SimpleDateFormat sm = new SimpleDateFormat("yyyy-MM-dd");
    return sm.parse(dateString);
  }

  public static Date addDays(Date date, int numberOfDays) {
    Calendar c = Calendar.getInstance();
    c.setTime(date);
    c.add(Calendar.DATE, numberOfDays);  // number of days to add
    return c.getTime();
  }

  public static Date getStartOfDay(Date date) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTime(date);
    calendar.set(Calendar.HOUR_OF_DAY, 0);
    calendar.set(Calendar.MINUTE, 0);
    calendar.set(Calendar.SECOND, 0);
    calendar.set(Calendar.MILLISECOND, 0);
    return calendar.getTime();
  }

  public static Date getEndOfDay(Date date, int millisecond) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTime(date);
    calendar.set(Calendar.HOUR_OF_DAY, 23);
    calendar.set(Calendar.MINUTE, 59);
    calendar.set(Calendar.SECOND, 59);
    calendar.set(Calendar.MILLISECOND, millisecond);
    return calendar.getTime();
  }

  public static Date getEndOfDay(Date date) {
    return getEndOfDay(date, 0);
  }

  public static Date getStartOfMonth(Date date) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTime(date);
    calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMinimum(Calendar.DAY_OF_MONTH));
    calendar.set(Calendar.HOUR_OF_DAY, 0);
    calendar.set(Calendar.MINUTE, 0);
    calendar.set(Calendar.SECOND, 0);
    calendar.set(Calendar.MILLISECOND, 0);
    return calendar.getTime();
  }

  public static Date getEndOfMonth(Date date, int millisecond) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTime(date);
    calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
    calendar.set(Calendar.HOUR_OF_DAY, 23);
    calendar.set(Calendar.MINUTE, 59);
    calendar.set(Calendar.SECOND, 59);
    calendar.set(Calendar.MILLISECOND, millisecond);
    return calendar.getTime();
  }

  public static String URLEncode(String url) {
    try {
      return URLEncoder.encode(url, "UTF-8").replace("+", "%20");
    } catch (Exception ex) {
      return url;
    }
  }

  public static boolean validateLoggerValue(String logValue) {
    //check for empty/null input
    if (StringUtils.isEmpty(logValue)) {
      return false;
    }

    String allowedLogCharacters = "^[\\w,\\s\\.-]+$";
    return Pattern.matches(allowedLogCharacters, logValue);
  }

  public static boolean validateHeaderValue(String headerValue) {
    //check for empty/null input
    if (StringUtils.isEmpty(headerValue)) {
      return false;
    }

    String allowedHeaderCharacters = "^[\\w,\\h\\.-]+$";
    return Pattern.matches(allowedHeaderCharacters, headerValue);
  }

  public static String encodeLogging(String message) {
    if (StringUtils.isEmpty(message)) {
      return message;
    }

    //redundant checks to satisfy fortify scans
    message = message.replace('\n', '_').replace('\r', '_')
            .replace('\t', '_');

    String whiteList = "[^A-Za-z0-9\\-\\._~\\+\\/]";
    message = message.replaceAll(whiteList, "");

    message = quoteApostrophe(message);
    message = StringEscapeUtils.escapeHtml4(message);
    return message;
  }

  public static String encodeForUrl(String val) {

    if (StringUtils.isEmpty(val)) {
      return val;
    }

    val = val.replace( '\n' ,  '_' ).replace( '\r' , '_' )
            .replace( '\t' , '_' );

    return val;
  }

  public static String quoteApostrophe(String input) {
    if (!StringUtils.isEmpty(input))
      return input.replaceAll("[\']", "&rsquo;");
    else
      return null;
  }
}
